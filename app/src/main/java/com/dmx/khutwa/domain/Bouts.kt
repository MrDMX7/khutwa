package com.dmx.khutwa.domain

/**
 * Turns raw step-detector event timestamps into minute buckets, and minute
 * buckets into bouts.
 *
 * The engine feeds this batches of timestamps roughly once a minute; it is
 * pure and side-effect free so the classification logic can be unit-tested
 * without a device.
 */
object Bouts {

    /** Group raw per-step event times (epoch millis) into sparse minute buckets. */
    fun bucketize(eventTimesMs: List<Long>, dateOf: (Long) -> String): List<MinuteBucket> {
        if (eventTimesMs.isEmpty()) return emptyList()
        val counts = LinkedHashMap<Long, Int>()
        for (t in eventTimesMs.sorted()) {
            val minute = t / 60_000L
            counts[minute] = (counts[minute] ?: 0) + 1
        }
        return counts.map { (minute, steps) ->
            MinuteBucket(tsMin = minute, date = dateOf(minute * 60_000L), steps = steps)
        }
    }

    /**
     * Segment minute buckets into bouts.
     *
     * A bout runs while minutes are close together (no gap beyond
     * [Cadence.BOUT_GAP_MS]) and carries the class of its *peak* sustained
     * effort, not its mean: a run that starts and ends with a walk is still a
     * run, and averaging would hide it.
     *
     * Segments shorter than [Cadence.MIN_BOUT_SECONDS] or classified as
     * incidental are dropped — they are still counted in the day's totals,
     * they just aren't worth showing as an activity.
     */
    fun segment(
        buckets: List<MinuteBucket>,
        profile: Profile,
        gradeFor: (Long) -> Double = { 0.0 },
    ): List<Bout> {
        if (buckets.isEmpty()) return emptyList()
        val sorted = buckets.sortedBy { it.tsMin }
        val out = mutableListOf<Bout>()
        var run = mutableListOf<MinuteBucket>()

        fun flush() {
            val group = run
            run = mutableListOf()
            if (group.isEmpty()) return
            val bout = build(group, profile, gradeFor) ?: return
            out += bout
        }

        for (b in sorted) {
            if (run.isEmpty()) {
                run += b
                continue
            }
            val gapMs = (b.tsMin - run.last().tsMin) * 60_000L
            // Also break at midnight so a bout never straddles two days.
            if (gapMs > Cadence.BOUT_GAP_MS || b.date != run.last().date) {
                flush()
            }
            run += b
        }
        flush()
        return out
    }

    private fun build(
        group: List<MinuteBucket>,
        profile: Profile,
        gradeFor: (Long) -> Double,
    ): Bout? {
        // Strictly greater: one isolated minute is the resolution floor, not
        // evidence of a sustained effort. A 20-second dash that happens to
        // straddle a minute boundary would otherwise be logged as a run.
        val durationSec = group.size * 60
        if (durationSec <= Cadence.MIN_BOUT_SECONDS) return null

        val steps = group.sumOf { it.steps }
        val avgCadence = steps / group.size

        // Class of the bout = the highest class it sustains for a full minute.
        val cls = group.map { it.activityClass }.maxByOrNull { it.id } ?: ActivityClass.INCIDENTAL
        if (cls == ActivityClass.INCIDENTAL) return null

        var distance = 0.0
        var kcal = 0.0
        var gain = 0.0
        for (m in group) {
            val grade = gradeFor(m.tsMin)
            distance += Metrics.minuteDistanceMetres(profile, m.steps)
            kcal += Metrics.minuteKcal(profile, m.steps, grade)
            if (grade > 0) gain += grade * Metrics.minuteDistanceMetres(profile, m.steps)
        }

        return Bout(
            date = group.first().date,
            startMs = group.first().tsMin * 60_000L,
            endMs = (group.last().tsMin + 1) * 60_000L,
            steps = steps,
            activityClass = cls,
            distanceM = distance,
            kcal = kcal,
            avgCadence = avgCadence,
            elevGainM = gain,
        )
    }

    /**
     * Roll a day's minute buckets up into the per-class step split and the
     * derived distance/calorie/active-minute totals.
     *
     * Note this returns only what the *detector* saw. The caller reconciles
     * against the hardware counter, which stays authoritative for the day
     * total — see StepRepository.
     */
    fun aggregate(
        buckets: List<MinuteBucket>,
        profile: Profile,
        gradeFor: (Long) -> Double = { 0.0 },
    ): DayAggregate {
        var walk = 0L
        var brisk = 0L
        var run = 0L
        var incidental = 0L
        var distance = 0.0
        var kcal = 0.0
        var activeMinutes = 0

        for (m in buckets) {
            when (m.activityClass) {
                ActivityClass.WALK -> walk += m.steps
                ActivityClass.BRISK -> brisk += m.steps
                ActivityClass.RUN -> run += m.steps
                ActivityClass.INCIDENTAL -> incidental += m.steps
            }
            distance += Metrics.minuteDistanceMetres(profile, m.steps)
            kcal += Metrics.minuteKcal(profile, m.steps, gradeFor(m.tsMin))
            if (Cadence.isActiveMinute(m.cadence)) activeMinutes++
        }

        val cadences = buckets.map { it.cadence }
        return DayAggregate(
            walkSteps = walk,
            briskSteps = brisk,
            runSteps = run,
            incidentalSteps = incidental,
            distanceM = distance,
            kcalActive = kcal,
            activeMinutes = activeMinutes,
            peak30Cadence = Cadence.peak30(cadences),
            peak1Cadence = Cadence.peak1(cadences),
        )
    }
}

data class DayAggregate(
    val walkSteps: Long = 0,
    val briskSteps: Long = 0,
    val runSteps: Long = 0,
    val incidentalSteps: Long = 0,
    val distanceM: Double = 0.0,
    val kcalActive: Double = 0.0,
    val activeMinutes: Int = 0,
    val peak30Cadence: Int = 0,
    val peak1Cadence: Int = 0,
)
