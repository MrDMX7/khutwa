package com.dmx.khutwa.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.dmx.khutwa.domain.ActivityClass
import com.dmx.khutwa.domain.Bout
import com.dmx.khutwa.domain.Cadence
import com.dmx.khutwa.domain.DayAggregate
import com.dmx.khutwa.domain.DayStats
import com.dmx.khutwa.domain.MinuteBucket
import com.dmx.khutwa.domain.StrideSample

/**
 * All SQL lives here. Callers are expected to be off the main thread — see
 * StepRepository, which owns the threading.
 */
class StepDao(context: Context) {

    private val helper = StepDb.get(context)
    private val db: SQLiteDatabase get() = helper.writableDatabase

    // ---- days -------------------------------------------------------------

    fun ensureDay(date: String, goal: Int) {
        val cv = ContentValues().apply {
            put("date", date)
            put("steps", 0)
            put("goal", goal)
        }
        db.insertWithOnConflict(StepDb.TABLE_DAYS, null, cv, SQLiteDatabase.CONFLICT_IGNORE)
        // A day row created before the goal was set (or before onboarding) still
        // needs its goal stamped, so history renders the goal in force that day.
        db.execSQL(
            "UPDATE ${StepDb.TABLE_DAYS} SET goal = ? WHERE date = ? AND goal = 0",
            arrayOf<Any>(goal, date)
        )
    }

    /**
     * Change a day's goal outright.
     *
     * Only ever applied to the current day: past days keep whatever goal was in
     * force at the time, so history doesn't silently rewrite itself into
     * successes or failures when the target changes.
     */
    fun setGoal(date: String, goal: Int) {
        db.execSQL(
            "UPDATE ${StepDb.TABLE_DAYS} SET goal = ? WHERE date = ?",
            arrayOf<Any>(goal, date)
        )
    }

    /** The authoritative counter delta. Additive so concurrent writes can't clobber. */
    fun addSteps(date: String, delta: Long, goal: Int) {
        if (delta <= 0) return
        ensureDay(date, goal)
        db.execSQL(
            "UPDATE ${StepDb.TABLE_DAYS} SET steps = steps + ? WHERE date = ?",
            arrayOf<Any>(delta, date)
        )
    }

    /**
     * Overwrite the derived columns for a day. These are recomputed wholesale
     * from the minute buckets rather than accumulated, so a re-run is
     * idempotent and a bad batch can't compound.
     */
    fun writeAggregate(date: String, agg: DayAggregate, floors: Double, elevGain: Double) {
        val cv = ContentValues().apply {
            put("walk_steps", agg.walkSteps)
            put("brisk_steps", agg.briskSteps)
            put("run_steps", agg.runSteps)
            put("incidental_steps", agg.incidentalSteps)
            put("distance_m", agg.distanceM)
            put("kcal_active", agg.kcalActive)
            put("active_minutes", agg.activeMinutes)
            put("peak30_cadence", agg.peak30Cadence)
            put("peak1_cadence", agg.peak1Cadence)
            put("floors", floors)
            put("elev_gain_m", elevGain)
        }
        db.update(StepDb.TABLE_DAYS, cv, "date = ?", arrayOf(date))
    }

    fun day(date: String): DayStats? =
        db.query(StepDb.TABLE_DAYS, null, "date = ?", arrayOf(date), null, null, null)
            .use { c -> if (c.moveToFirst()) c.toDayStats() else null }

    /** Newest first. */
    fun recentDays(limit: Int): List<DayStats> =
        db.query(StepDb.TABLE_DAYS, null, null, null, null, null, "date DESC", limit.toString())
            .use { c -> buildList { while (c.moveToNext()) add(c.toDayStats()) } }

    fun daysBetween(from: String, to: String): List<DayStats> =
        db.query(
            StepDb.TABLE_DAYS, null, "date >= ? AND date <= ?", arrayOf(from, to),
            null, null, "date ASC"
        ).use { c -> buildList { while (c.moveToNext()) add(c.toDayStats()) } }

    fun sumStepsSince(date: String): Long =
        db.rawQuery(
            "SELECT COALESCE(SUM(steps),0) FROM ${StepDb.TABLE_DAYS} WHERE date >= ?",
            arrayOf(date)
        ).use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }

    // ---- minutes ----------------------------------------------------------

    /**
     * Merge a batch of minute buckets. A minute can be delivered twice (the
     * detector batch straddles the boundary), so steps accumulate rather than
     * replace — but the class is recomputed from the new total.
     */
    fun upsertMinutes(buckets: List<MinuteBucket>) {
        if (buckets.isEmpty()) return
        db.beginTransaction()
        try {
            for (b in buckets) {
                // INSERT-then-UPDATE rather than UPSERT: `ON CONFLICT ... DO UPDATE`
                // needs SQLite 3.24+, which only arrived on Android 11, and minSdk here is 26.
                val inserted = db.insertWithOnConflict(
                    StepDb.TABLE_MINUTES, null,
                    ContentValues().apply {
                        put("ts_min", b.tsMin)
                        put("date", b.date)
                        put("steps", b.steps)
                        put("class", b.activityClass.id)
                        put("alt_m", b.altM)
                    },
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (inserted == -1L) {
                    // Keep the altitude already recorded unless this batch has one.
                    db.execSQL(
                        "UPDATE ${StepDb.TABLE_MINUTES} SET steps = steps + ?, " +
                            "alt_m = CASE WHEN ? != 0 THEN ? ELSE alt_m END WHERE ts_min = ?",
                        arrayOf<Any>(b.steps, b.altM, b.altM, b.tsMin)
                    )
                }
            }
            // A minute can arrive in two batches, so reclassify from the merged
            // total. Thresholds come from Cadence so there is one definition.
            for (date in buckets.map { it.date }.distinct()) {
                db.execSQL(
                    """
                    UPDATE ${StepDb.TABLE_MINUTES} SET class =
                        CASE WHEN steps >= ${Cadence.RUN_MIN} THEN ${ActivityClass.RUN.id}
                             WHEN steps >= ${Cadence.MODERATE_MIN} THEN ${ActivityClass.BRISK.id}
                             WHEN steps >  ${Cadence.INCIDENTAL_MAX} THEN ${ActivityClass.WALK.id}
                             ELSE ${ActivityClass.INCIDENTAL.id} END
                    WHERE date = ?
                    """.trimIndent(),
                    arrayOf(date)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private val minuteCols = arrayOf("ts_min", "date", "steps", "alt_m")

    fun minutesFor(date: String): List<MinuteBucket> =
        db.query(
            StepDb.TABLE_MINUTES, minuteCols,
            "date = ?", arrayOf(date), null, null, "ts_min ASC"
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(MinuteBucket(c.getLong(0), c.getString(1), c.getInt(2), c.getDouble(3)))
                }
            }
        }

    fun minutesBetween(from: String, to: String): Map<String, List<MinuteBucket>> =
        db.query(
            StepDb.TABLE_MINUTES, minuteCols,
            "date >= ? AND date <= ?", arrayOf(from, to), null, null, "ts_min ASC"
        ).use { c ->
            val out = mutableMapOf<String, MutableList<MinuteBucket>>()
            while (c.moveToNext()) {
                val m = MinuteBucket(c.getLong(0), c.getString(1), c.getInt(2), c.getDouble(3))
                out.getOrPut(m.date) { mutableListOf() } += m
            }
            out
        }

    /** Steps per hour of day, 0..23 — the time-of-day chart. */
    fun hourlyFor(date: String): IntArray {
        val out = IntArray(24)
        for (m in minutesFor(date)) {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = m.tsMin * 60_000L }
            out[cal.get(java.util.Calendar.HOUR_OF_DAY)] += m.steps
        }
        return out
    }

    // ---- bouts ------------------------------------------------------------

    /** Bouts are recomputed for the whole day, so clear before writing. */
    fun replaceBouts(date: String, bouts: List<Bout>) {
        db.beginTransaction()
        try {
            db.delete(StepDb.TABLE_BOUTS, "date = ?", arrayOf(date))
            for (b in bouts) {
                db.insert(StepDb.TABLE_BOUTS, null, ContentValues().apply {
                    put("date", b.date)
                    put("start_ms", b.startMs)
                    put("end_ms", b.endMs)
                    put("steps", b.steps)
                    put("class", b.activityClass.id)
                    put("distance_m", b.distanceM)
                    put("kcal", b.kcal)
                    put("avg_cadence", b.avgCadence)
                    put("elev_gain_m", b.elevGainM)
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun boutsFor(date: String): List<Bout> =
        db.query(StepDb.TABLE_BOUTS, null, "date = ?", arrayOf(date), null, null, "start_ms ASC")
            .use { c -> buildList { while (c.moveToNext()) add(c.toBout()) } }

    fun allBouts(limit: Int = 500): List<Bout> =
        db.query(StepDb.TABLE_BOUTS, null, null, null, null, null, "start_ms DESC", limit.toString())
            .use { c -> buildList { while (c.moveToNext()) add(c.toBout()) } }

    // ---- stride samples ---------------------------------------------------

    fun addStrideSample(s: StrideSample) {
        db.insert(StepDb.TABLE_STRIDE, null, ContentValues().apply {
            put("ts_ms", s.tsMs)
            put("cadence", s.cadence)
            put("distance_m", s.distanceM)
            put("steps", s.steps)
            put("gps_accuracy", s.gpsAccuracy)
        })
    }

    fun strideSamples(limit: Int = 200): List<StrideSample> =
        db.query(StepDb.TABLE_STRIDE, null, null, null, null, null, "ts_ms DESC", limit.toString())
            .use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(
                            StrideSample(
                                tsMs = c.getLong(c.getColumnIndexOrThrow("ts_ms")),
                                cadence = c.getInt(c.getColumnIndexOrThrow("cadence")),
                                distanceM = c.getDouble(c.getColumnIndexOrThrow("distance_m")),
                                steps = c.getInt(c.getColumnIndexOrThrow("steps")),
                                gpsAccuracy = c.getDouble(c.getColumnIndexOrThrow("gps_accuracy")),
                            )
                        )
                    }
                }
            }

    fun clearStrideSamples() {
        db.delete(StepDb.TABLE_STRIDE, null, null)
    }

    // ---- cursor mapping ---------------------------------------------------

    private fun android.database.Cursor.toDayStats() = DayStats(
        date = getString(getColumnIndexOrThrow("date")),
        steps = getLong(getColumnIndexOrThrow("steps")),
        walkSteps = getLong(getColumnIndexOrThrow("walk_steps")),
        briskSteps = getLong(getColumnIndexOrThrow("brisk_steps")),
        runSteps = getLong(getColumnIndexOrThrow("run_steps")),
        incidentalSteps = getLong(getColumnIndexOrThrow("incidental_steps")),
        distanceM = getDouble(getColumnIndexOrThrow("distance_m")),
        kcalActive = getDouble(getColumnIndexOrThrow("kcal_active")),
        activeMinutes = getInt(getColumnIndexOrThrow("active_minutes")),
        floors = getDouble(getColumnIndexOrThrow("floors")),
        elevGainM = getDouble(getColumnIndexOrThrow("elev_gain_m")),
        peak30Cadence = getInt(getColumnIndexOrThrow("peak30_cadence")),
        peak1Cadence = getInt(getColumnIndexOrThrow("peak1_cadence")),
        goal = getInt(getColumnIndexOrThrow("goal")),
    )

    private fun android.database.Cursor.toBout() = Bout(
        id = getLong(getColumnIndexOrThrow("id")),
        date = getString(getColumnIndexOrThrow("date")),
        startMs = getLong(getColumnIndexOrThrow("start_ms")),
        endMs = getLong(getColumnIndexOrThrow("end_ms")),
        steps = getInt(getColumnIndexOrThrow("steps")),
        activityClass = ActivityClass.fromId(getInt(getColumnIndexOrThrow("class"))),
        distanceM = getDouble(getColumnIndexOrThrow("distance_m")),
        kcal = getDouble(getColumnIndexOrThrow("kcal")),
        avgCadence = getInt(getColumnIndexOrThrow("avg_cadence")),
        elevGainM = getDouble(getColumnIndexOrThrow("elev_gain_m")),
    )
}
