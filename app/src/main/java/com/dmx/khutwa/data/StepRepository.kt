package com.dmx.khutwa.data

import android.content.ContentValues
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Handler
import android.os.Looper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reads the hardware step counter and turns it into a per-day total.
 *
 * TYPE_STEP_COUNTER is cumulative since the last device boot and keeps
 * counting at the sensor-hub level with nobody listening — that's the whole
 * point of this app over the third-party one it replaces: correctness never
 * depends on staying resident, only on getting a chance to read the current
 * value periodically. See the plan doc for why this design was chosen.
 */
object StepRepository {

    private const val PREFS = "khutwa_prefs"
    private const val KEY_LAST_RAW = "last_raw"
    private const val KEY_BUCKET_DATE = "bucket_date"
    private const val SENSOR_WAIT_TIMEOUT_MS = 8_000L

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun today(): String = dayFormat.format(Date())

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Reads the current cumulative step count once. Registers a listener,
     * takes the first value delivered (TYPE_STEP_COUNTER fires promptly on
     * registration with the current total), then unregisters immediately —
     * never stays resident. Times out defensively since this commonly runs
     * inside a BroadcastReceiver with a bounded execution window.
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

    /**
     * One checkpoint: read the sensor, fold the delta into today's total.
     * Safe to call from anywhere, any time — this is the only correctness
     * primitive the whole app relies on.
     */
    fun checkpoint(context: Context, onDone: () -> Unit = {}) {
        readSensorOnce(context) { raw ->
            if (raw != null) applyReading(context, raw)
            onDone()
        }
    }

    private fun applyReading(context: Context, raw: Long) {
        val p = prefs(context)
        val bucketDate = p.getString(KEY_BUCKET_DATE, null)
        val lastRaw = p.getLong(KEY_LAST_RAW, -1L)
        val todayKey = today()

        if (bucketDate == null || lastRaw < 0) {
            // First run ever: nothing to diff against yet, just establish a baseline.
            ensureDayRow(context, todayKey)
            p.edit().putLong(KEY_LAST_RAW, raw).putString(KEY_BUCKET_DATE, todayKey).apply()
            return
        }

        if (bucketDate != todayKey) {
            // Midnight passed without the exact alarm firing (safety net) — start
            // fresh rather than attribute a stale multi-day span to either date.
            ensureDayRow(context, todayKey)
            p.edit().putLong(KEY_LAST_RAW, raw).putString(KEY_BUCKET_DATE, todayKey).apply()
            return
        }

        if (raw >= lastRaw) {
            val delta = raw - lastRaw
            if (delta > 0) addSteps(context, bucketDate, delta)
        }
        // raw < lastRaw means the device rebooted (the hardware counter reset).
        // Don't subtract — today's total already banked from before the reboot
        // stays as-is; we just resume counting from the new baseline.
        p.edit().putLong(KEY_LAST_RAW, raw).apply()
    }

    /**
     * Called by the midnight alarm specifically: finalizes whatever's pending
     * for the closing day, then rolls the bucket forward. Distinct from the
     * safety-net path above, which only fires if this was missed.
     */
    fun rolloverMidnight(context: Context, onDone: () -> Unit = {}) {
        checkpoint(context) {
            val todayKey = today()
            ensureDayRow(context, todayKey)
            prefs(context).edit().putString(KEY_BUCKET_DATE, todayKey).apply()
            onDone()
        }
    }

    private fun ensureDayRow(context: Context, date: String) {
        val db = StepDb(context).writableDatabase
        val cv = ContentValues().apply { put("date", date); put("steps", 0) }
        db.insertWithOnConflict(StepDb.TABLE, null, cv, android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun addSteps(context: Context, date: String, delta: Long) {
        val db = StepDb(context).writableDatabase
        ensureDayRow(context, date)
        db.execSQL("UPDATE ${StepDb.TABLE} SET steps = steps + ? WHERE date = ?", arrayOf(delta, date))
    }

    fun todaySteps(context: Context): Long = stepsFor(context, today())

    fun stepsFor(context: Context, date: String): Long {
        val db = StepDb(context).readableDatabase
        db.query(StepDb.TABLE, arrayOf("steps"), "date = ?", arrayOf(date), null, null, null).use { c ->
            return if (c.moveToFirst()) c.getLong(0) else 0L
        }
    }

    /** Most recent [limit] days, newest first, including days with 0 steps if they have a row. */
    fun recentDays(context: Context, limit: Int = 7): List<Pair<String, Long>> {
        val db = StepDb(context).readableDatabase
        val out = mutableListOf<Pair<String, Long>>()
        db.query(StepDb.TABLE, arrayOf("date", "steps"), null, null, null, null, "date DESC", limit.toString())
            .use { c -> while (c.moveToNext()) out.add(c.getString(0) to c.getLong(1)) }
        return out
    }
}
