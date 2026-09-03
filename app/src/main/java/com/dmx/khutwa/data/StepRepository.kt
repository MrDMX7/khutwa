package com.dmx.khutwa.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import com.dmx.khutwa.domain.Bouts
import com.dmx.khutwa.domain.DayStats
import com.dmx.khutwa.domain.MinuteBucket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Owns the authoritative step total.
 *
 * The design that makes this app correct, unchanged from v1: TYPE_STEP_COUNTER
 * is cumulative and keeps counting in the sensor hub with nobody listening, so
 * correctness only requires getting a chance to read it periodically — never
 * staying resident. Verified on-device: the stored day totals since the last
 * reboot sum to exactly the hardware counter's value.
 *
 * v2 adds a derived layer (classification, distance, calories) on top from
 * step-detector timestamps, but that layer is never allowed to change the
 * total. See [reconcile].
 */
object StepRepository {

    private const val SENSOR_WAIT_TIMEOUT_MS = 8_000L

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** All DB work happens here; callers on the main thread never block. */
    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "khutwa-io").apply { isDaemon = true }
    }

    @Synchronized
    private fun today(): String = dayFormat.format(Date())

    @Synchronized
    fun dateOf(millis: Long): String = dayFormat.format(Date(millis))

    private fun dao(ctx: Context) = StepDao(ctx)

    // ---- sensor -----------------------------------------------------------

    /**
     * Reads the cumulative step count once, then unregisters — never stays
     * resident. Times out defensively because this commonly runs inside a
     * BroadcastReceiver with a bounded execution window.
     */
    fun readSensorOnce(context: Context, onResult: (Long?) -> Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (sm == null || sensor == null) {
            onResult(null)
            return
        }

        var done = false
        val handler = Handler(Looper.getMainLooper())
        lateinit var listener: SensorEventListener

        val finish = { value: Long? ->
            if (!done) {
                done = true
                sm.unregisterListener(listener)
                handler.removeCallbacksAndMessages(null)
                onResult(value)
            }
        }

        listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                finish(event.values[0].toLong())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        handler.postDelayed({ finish(null) }, SENSOR_WAIT_TIMEOUT_MS)
    }

    // ---- checkpointing ----------------------------------------------------

    fun checkpoint(context: Context, onDone: () -> Unit = {}) {
        readSensorOnce(context) { raw ->
            if (raw == null) {
                onDone()
                return@readSensorOnce
            }
            io.execute {
                try {
                    applyReading(context, raw, closingDate = null)
                } finally {
                    onDone()
                }
            }
        }
    }

    /**
     * The midnight alarm. Fires at 00:00:05, i.e. *after* the date has already
     * rolled over.
     *
     * v1 routed this through the ordinary checkpoint, where `today()` was
     * already the new date while `bucket_date` still held the closing day. That
     * hit the day-mismatch branch, which re-baselines and returns **without
     * crediting the pending delta** — so the steps between the last periodic
     * checkpoint and midnight were silently discarded every single night. It
     * went unnoticed because the user is rarely walking at midnight, but the
     * loss was real whenever they were.
     *
     * The fix: tell [applyReading] explicitly which day is closing, so the
     * final delta is credited to it before the bucket rolls forward.
     */
    fun rolloverMidnight(context: Context, onDone: () -> Unit = {}) {
        val closing = Settings.prefs(context).getString(Settings.KEY_BUCKET_DATE, null)
        readSensorOnce(context) { raw ->
            if (raw == null) {
                onDone()
                return@readSensorOnce
            }
            io.execute {
                try {
                    applyReading(context, raw, closingDate = closing)
                } finally {
                    onDone()
                }
            }
        }
    }

    /**
     * @param closingDate when non-null, the pending delta belongs to this date
     *        rather than being dropped — the midnight path.
     */
    private fun applyReading(context: Context, raw: Long, closingDate: String?) {
        val p = Settings.prefs(context)
        val bucketDate = p.getString(Settings.KEY_BUCKET_DATE, null)
        val lastRaw = p.getLong(Settings.KEY_LAST_RAW, -1L)
        val todayKey = today()
        val goal = Settings.goal(context)
        val d = dao(context)

        if (bucketDate == null || lastRaw < 0) {
            // First run ever: nothing to diff against, just establish a baseline.
            d.ensureDay(todayKey, goal)
            p.edit()
                .putLong(Settings.KEY_LAST_RAW, raw)
                .putString(Settings.KEY_BUCKET_DATE, todayKey)
                .apply()
            Settings.saveBootId(context, Settings.currentBootId())
            return
        }

        // Reboot: the hardware counter reset, so `raw` is a new baseline rather
        // than a smaller total. Corroborated by the boot marker so a sensor-hub
        // glitch that happens to lower the count isn't mistaken for a restart.
        val rebooted = raw < lastRaw || Settings.rebootedSince(context)
        if (rebooted) {
            d.ensureDay(todayKey, goal)
            // `raw` is now the count since boot, so those steps are unbanked.
            // Credit them only when the boot itself happened today — otherwise
            // the span crosses a midnight we can no longer place, and guessing
            // would corrupt two days rather than losing part of one.
            val bootDate = dateOf(Settings.currentBootId() * 1000L)
            if (raw > 0 && bootDate == todayKey) d.addSteps(todayKey, raw, goal)
            p.edit()
                .putLong(Settings.KEY_LAST_RAW, raw)
                .putString(Settings.KEY_BUCKET_DATE, todayKey)
                .apply()
            Settings.saveBootId(context, Settings.currentBootId())
            recomputeDerived(context, todayKey)
            return
        }

        val delta = raw - lastRaw

        if (bucketDate != todayKey) {
            // The day changed. Credit the pending delta to whichever day owns it.
            val target = closingDate ?: bucketDate
            d.ensureDay(target, goal)
            d.ensureDay(todayKey, goal)
            if (delta > 0) d.addSteps(target, delta, goal)
            p.edit()
                .putLong(Settings.KEY_LAST_RAW, raw)
                .putString(Settings.KEY_BUCKET_DATE, todayKey)
                .apply()
            recomputeDerived(context, target)
            return
        }

        if (delta > 0) d.addSteps(bucketDate, delta, goal)
        // Keep today in step with the current goal; changing it mid-day should
        // move the ring, not leave it measuring against yesterday's target.
        d.setGoal(todayKey, goal)
        p.edit().putLong(Settings.KEY_LAST_RAW, raw).apply()
        Settings.saveBootId(context, Settings.currentBootId())
        recomputeDerived(context, bucketDate)
    }

    // ---- derived layer ----------------------------------------------------

    /**
     * Recompute a day's breakdown from its minute buckets.
     *
     * Wholesale rather than incremental, so re-running is idempotent and a bad
     * detector batch can't compound across the day.
     */
    fun recomputeDerived(context: Context, date: String) {
        val d = dao(context)
        val profile = Settings.profile(context)
        val minutes = d.minutesFor(date)
        if (minutes.isEmpty()) return
        val grade = BarometerTracker.gradeLookup(context, date)
        val agg = Bouts.aggregate(minutes, profile, grade)
        val elevGain = BarometerTracker.gainFor(context, date)
        d.writeAggregate(date, agg, com.dmx.khutwa.domain.Metrics.floorsFromGain(elevGain), elevGain)
        d.replaceBouts(date, Bouts.segment(minutes, profile, grade))
    }

    /** Called by the detector engine when a batch of step timestamps arrives. */
    fun ingestMinutes(context: Context, buckets: List<MinuteBucket>) {
        if (buckets.isEmpty()) return
        io.execute {
            val d = dao(context)
            val goal = Settings.goal(context)
            for (date in buckets.map { it.date }.distinct()) d.ensureDay(date, goal)
            d.upsertMinutes(buckets)
            for (date in buckets.map { it.date }.distinct()) recomputeDerived(context, date)
        }
    }

    /**
     * The invariant that protects the property this app was built for.
     *
     * The step detector is a non-wakeup sensor with a 300-event reserved FIFO;
     * if it overflows while the CPU is asleep the oldest events are dropped
     * silently. That makes the classified breakdown potentially incomplete —
     * but the hardware counter never misses, so `days.steps` stays
     * authoritative and the shortfall simply surfaces as unclassified steps.
     *
     * In other words: classification degrades gracefully, the total never does.
     */
    fun reconcile(context: Context, date: String): Long {
        val stats = dao(context).day(date) ?: return 0
        return stats.unclassifiedSteps
    }

    // ---- reads (all off the main thread via [query]) -----------------------

    fun <T> query(context: Context, block: (StepDao) -> T, onResult: (T) -> Unit) {
        io.execute {
            val result = block(dao(context))
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun todaySteps(context: Context): Long = dao(context).day(today())?.steps ?: 0L

    fun todayDate(): String = today()

    fun dayStats(context: Context, date: String): DayStats? = dao(context).day(date)

    fun recentDays(context: Context, limit: Int = 7): List<DayStats> =
        dao(context).recentDays(limit)

    /**
     * Diagnostics: the live hardware counter against the sum of stored days
     * since the last reboot. These should agree exactly — that equality is the
     * regression test for the whole engine.
     */
    fun diagnostics(context: Context, onResult: (raw: Long?, storedSinceBoot: Long) -> Unit) {
        readSensorOnce(context) { raw ->
            io.execute {
                val bootDate = dateOf(Settings.currentBootId() * 1000L)
                val stored = dao(context).sumStepsSince(bootDate)
                Handler(Looper.getMainLooper()).post { onResult(raw, stored) }
            }
        }
    }
}
