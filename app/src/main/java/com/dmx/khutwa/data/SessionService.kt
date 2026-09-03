package com.dmx.khutwa.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

/**
 * Keeps a recorded session alive while the screen is off.
 *
 * This exists because of a failure observed on a real run: the session was
 * driven from the Activity, so once the phone went in a pocket Android
 * throttled background location to almost nothing — 95 fixes in the first three
 * minutes, then a 52-minute gap — and the periodic tick stopped being
 * scheduled, which in turn recorded half an hour of running as a single
 * "minute" and produced an impossible cadence.
 *
 * A foreground service with `location` in its type is the only way to keep
 * receiving location updates in the background on modern Android. `health`
 * is declared alongside it because the same service is what keeps the run's
 * step accounting ticking.
 */
class SessionService : Service() {

    companion object {
        const val CHANNEL_ID = "khutwa_session"
        const val NOTIFICATION_ID = 2

        fun start(context: Context) {
            val i = Intent(context, SessionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SessionService::class.java))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        // A partial wake lock keeps the CPU available for the 1-second tick.
        // Scoped strictly to the session and released in onDestroy, so it can
        // never outlive the run.
        val pm = getSystemService(POWER_SERVICE) as? PowerManager
        wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "khutwa:session")?.apply {
            setReferenceCounted(false)
            runCatching { acquire(4 * 60 * 60 * 1000L) }
        }

        handler.post(refresh)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(refresh)
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Keep the notification showing live numbers, and stop once the run ends. */
    private val refresh = object : Runnable {
        override fun run() {
            val live = SessionRecorder.state.value
            if (!live.active) {
                stopSelf()
                return
            }
            runCatching {
                (getSystemService(NOTIFICATION_SERVICE) as? NotificationManager)
                    ?.notify(NOTIFICATION_ID, buildNotification())
            }
            handler.postDelayed(this, 5_000)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "الجلسة الجارية", NotificationManager.IMPORTANCE_LOW)
                .apply {
                    description = "يبقي تسجيل المسار والإرشاد الصوتي يعمل والشاشة مطفأة"
                    setShowBadge(false)
                    enableVibration(false)
                    setSound(null, null)
                }
        )
    }

    private fun buildNotification(): Notification {
        val live = SessionRecorder.state.value
        val km = String.format(java.util.Locale.US, "%.2f", live.distanceM / 1000.0)
        val minutes = (live.elapsedMs / 60_000L).toInt()

        val open = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(this, com.dmx.khutwa.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION") Notification.Builder(this)
        }
        return builder
            .setContentTitle("جلسة جارية")
            .setContentText("$km كم · $minutes دقيقة · ${live.currentCadence} خطوة/د")
            .setSmallIcon(com.dmx.khutwa.R.drawable.ic_notification)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
