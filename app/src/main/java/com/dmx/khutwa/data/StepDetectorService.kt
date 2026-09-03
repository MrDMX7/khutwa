package com.dmx.khutwa.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import com.dmx.khutwa.domain.Bouts
import com.dmx.khutwa.domain.MinuteBucket

/**
 * The timestamped-step engine.
 *
 * TYPE_STEP_COUNTER (in StepRepository) tells us *how many* steps; it cannot
 * tell us *when* they happened, so on its own it can never distinguish a walk
 * from a run or say which hour of the day you were active. TYPE_STEP_DETECTOR
 * fires one event per step with a timestamp, which is where every v2 metric
 * comes from.
 *
 * The battery trick is hardware batching. Registering with a 60-second
 * `maxReportLatencyUs` lets the sensor hub buffer steps in its own FIFO and
 * deliver them in one burst, so the application processor stays asleep between
 * bursts. And because the detector is an event sensor, standing still produces
 * no events at all — an idle day costs essentially nothing.
 *
 * Why 60 s specifically: this device reports
 * `FIFO (max, reserved) = (10000, 300)` for step_detector. Only the 300 is
 * guaranteed to us when other sensors compete. At a running cadence of ~170
 * spm, 60 s is ~170 events — comfortably inside the reserve. A longer latency
 * would risk overflow, and because this sensor is *non-wakeup*, an overflow
 * while the CPU is asleep drops the oldest events silently rather than waking
 * to deliver them. StepRepository.reconcile handles that shortfall.
 */
class StepDetectorService : Service() {

    companion object {
        const val CHANNEL_ID = "khutwa_tracking"
        const val NOTIFICATION_ID = 1

        /** Hardware batch window. See the class comment for why this is 60 s. */
        const val BATCH_LATENCY_US = 60_000_000

        /**
         * A hardware batch arrives as a rapid burst of individual callbacks.
         * Wait for the burst to go quiet before writing, so one batch becomes
         * one transaction instead of hundreds.
         */
        private const val BURST_QUIET_MS = 1_500L

        /** Safety net so a slow trickle of steps still gets persisted. */
        private const val MAX_BUFFER_AGE_MS = 90_000L

        /** Release GPS this long after the last step arrives. */
        private const val CALIBRATOR_IDLE_MS = 180_000L

        fun start(context: Context) {
            val i = Intent(context, StepDetectorService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, StepDetectorService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val buffer = ArrayList<Long>(512)
    private var firstBufferedAt = 0L
    private var sensorManager: SensorManager? = null
    private var listener: SensorEventListener? = null

    /**
     * SensorEvent.timestamp is nanoseconds since boot, not wall clock. This is
     * the offset that converts one to the other. Recomputed per batch: caching
     * it once would drift, and after a suspend/resume cycle it can shift by
     * enough to push steps into the wrong minute.
     */
    private fun bootToWallOffsetMs(): Long =
        System.currentTimeMillis() - SystemClock.elapsedRealtime()

    private val flushRunnable = Runnable { flush() }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification(0))
        registerSensor()
        BarometerTracker.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restart if the system kills us — losing the listener means losing the
        // classification for however long it takes to notice.
        return START_STICKY
    }

    override fun onDestroy() {
        flush()
        listener?.let { sensorManager?.unregisterListener(it) }
        listener = null
        handler.removeCallbacks(stopCalibratorRunnable)
        StrideCalibrator.stop(this)
        BarometerTracker.stop(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun registerSensor() {
        val sm = getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        sensorManager = sm
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) ?: return

        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val wallMs = bootToWallOffsetMs() + event.timestamp / 1_000_000L
                synchronized(buffer) {
                    if (buffer.isEmpty()) firstBufferedAt = SystemClock.elapsedRealtime()
                    buffer.add(wallMs)
                }
                handler.removeCallbacks(flushRunnable)
                val age = SystemClock.elapsedRealtime() - firstBufferedAt
                if (age >= MAX_BUFFER_AGE_MS) {
                    handler.post(flushRunnable)
                } else {
                    handler.postDelayed(flushRunnable, BURST_QUIET_MS)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        listener = l
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_NORMAL, BATCH_LATENCY_US)
    }

    private fun flush() {
        val batch: List<Long>
        synchronized(buffer) {
            if (buffer.isEmpty()) return
            batch = ArrayList(buffer)
            buffer.clear()
            firstBufferedAt = 0L
        }

        val altitude = BarometerTracker.currentAltitude() ?: 0.0
        val nowMinute = System.currentTimeMillis() / 60_000L

        val buckets = Bouts.bucketize(batch) { ms -> StepRepository.dateOf(ms) }
            .map { b ->
                // Only stamp altitude on minutes from this batch's own window —
                // a reading taken now says nothing about where you were an hour ago.
                if (nowMinute - b.tsMin <= 2) b.copy(altM = altitude) else b
            }

        StepRepository.ingestMinutes(applicationContext, buckets)

        // Stride calibration piggybacks on walking that is already happening:
        // GPS is only requested while steps are arriving, and released as soon
        // as they stop, so there is never a background location session.
        if (StrideCalibrator.enabled(applicationContext)) {
            StrideCalibrator.start(applicationContext)
            StrideCalibrator.addSteps(batch.size)
            handler.removeCallbacks(stopCalibratorRunnable)
            handler.postDelayed(stopCalibratorRunnable, CALIBRATOR_IDLE_MS)
        }

        updateNotification()
    }

    private val stopCalibratorRunnable = Runnable {
        StrideCalibrator.stop(applicationContext)
    }

    // ---- notification -----------------------------------------------------

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "تتبع الخطوات",
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = "يبقي عدّاد الخطوات يعمل بدقة في الخلفية"
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(steps: Long): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, com.dmx.khutwa.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("خطوة")
            .setContentText(if (steps > 0) "$steps خطوة اليوم" else "يتتبع خطواتك")
            .setSmallIcon(com.dmx.khutwa.R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun updateNotification() {
        StepRepository.query(applicationContext, { dao ->
            dao.day(StepRepository.todayDate())?.steps ?: 0L
        }) { steps ->
            val nm = getSystemService(NotificationManager::class.java) ?: return@query
            runCatching { nm.notify(NOTIFICATION_ID, buildNotification(steps)) }
        }
    }
}
