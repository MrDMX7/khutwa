package com.dmx.khutwa.domain

/**
 * One minute in which at least one step was taken.
 *
 * Stored sparsely — a minute with no steps has no row. A typical day produces
 * a few hundred rows rather than 1,440, which keeps a year of intraday history
 * comfortably small.
 *
 * Because a bucket is exactly one minute wide, [steps] *is* the cadence in
 * steps per minute. That identity is what makes the whole classification and
 * peak-30 machinery cheap.
 */
data class MinuteBucket(
    val tsMin: Long,          // epoch minute
    val date: String,         // yyyy-MM-dd, local
    val steps: Int,
    /**
     * Smoothed barometric altitude at this minute, metres. Absolute value is
     * meaningless (weather moves it), but the minute-to-minute difference is
     * what floors climbed and the calorie gradient are built from.
     */
    val altM: Double = 0.0,
) {
    val cadence: Int get() = steps
    val activityClass: ActivityClass get() = Cadence.classify(steps)
}

/** A continuous stretch of purposeful movement. */
data class Bout(
    val id: Long = 0,
    val date: String,
    val startMs: Long,
    val endMs: Long,
    val steps: Int,
    val activityClass: ActivityClass,
    val distanceM: Double,
    val kcal: Double,
    val avgCadence: Int,
    val elevGainM: Double = 0.0,
) {
    val durationMs: Long get() = endMs - startMs
    val durationMinutes: Int get() = (durationMs / 60_000L).toInt()
}

/**
 * A day's totals.
 *
 * [steps] is authoritative — it comes from the hardware TYPE_STEP_COUNTER and
 * is the number the app has always been correct about. The class breakdown is
 * derived from step-detector timestamps and may be incomplete if the sensor
 * FIFO dropped events while the CPU was asleep; [unclassifiedSteps] carries
 * that shortfall so the parts always sum back to [steps].
 */
data class DayStats(
    val date: String,
    val steps: Long,
    val walkSteps: Long = 0,
    val briskSteps: Long = 0,
    val runSteps: Long = 0,
    val incidentalSteps: Long = 0,
    val distanceM: Double = 0.0,
    val kcalActive: Double = 0.0,
    val activeMinutes: Int = 0,
    val floors: Double = 0.0,
    val elevGainM: Double = 0.0,
    val peak30Cadence: Int = 0,
    val peak1Cadence: Int = 0,
    val goal: Int = 0,
) {
    val classifiedSteps: Long get() = walkSteps + briskSteps + runSteps + incidentalSteps

    /** Steps the hardware counted that the detector never got timestamps for. */
    val unclassifiedSteps: Long get() = (steps - classifiedSteps).coerceAtLeast(0)

    /** True for days recorded before v2, which have a total but no breakdown. */
    val hasBreakdown: Boolean get() = classifiedSteps > 0

    val goalMet: Boolean get() = goal > 0 && steps >= goal
}
