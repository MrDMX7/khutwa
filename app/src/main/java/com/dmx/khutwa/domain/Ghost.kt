package com.dmx.khutwa.domain

/**
 * Ghost Runner — competing against your own past instead of against strangers.
 *
 * All of this falls out of data already stored for other reasons: the minute
 * buckets give a cumulative curve for any past day, and bouts give comparable
 * efforts. Nothing here needs a network, an account, or a leaderboard.
 */
object Ghost {

    data class DayGhost(
        val referenceDate: String,
        val referenceStepsAtThisTime: Long,
        val todayStepsAtThisTime: Long,
    ) {
        val delta: Long get() = todayStepsAtThisTime - referenceStepsAtThisTime
        val ahead: Boolean get() = delta >= 0
    }

    data class Records(
        val bestDay: DayStats? = null,
        val bestPeak30: DayStats? = null,
        val longestBout: Bout? = null,
        val fastestKmSeconds: Int? = null,
        val fastestKmDate: String? = null,
    )

    /**
     * Cumulative steps for [minutes] up to [minuteOfDay], so today and a past
     * day can be compared at the same point on the clock rather than as whole
     * days. Comparing a half-finished day against a complete one is the
     * mistake that makes most "vs yesterday" widgets useless.
     */
    fun cumulativeAt(minutes: List<MinuteBucket>, minuteOfDay: Int): Long =
        minutes.filter { minuteOfDayOf(it.tsMin) <= minuteOfDay }.sumOf { it.steps.toLong() }

    /**
     * Compare today against the same weekday's most recent occurrence — a
     * fairer mirror than "yesterday", since weekday and weekend routines
     * differ far more than adjacent days do.
     */
    fun sameWeekdayGhost(
        todayDate: String,
        todayMinutes: List<MinuteBucket>,
        historyMinutesByDate: Map<String, List<MinuteBucket>>,
        minuteOfDay: Int,
    ): DayGhost? {
        val targetDow = Insights.dayOfWeekKey(todayDate)
        val candidate = historyMinutesByDate.keys
            .filter { it < todayDate && Insights.dayOfWeekKey(it) == targetDow }
            .maxOrNull() ?: return null
        val ref = historyMinutesByDate[candidate] ?: return null
        return DayGhost(
            referenceDate = candidate,
            referenceStepsAtThisTime = cumulativeAt(ref, minuteOfDay),
            todayStepsAtThisTime = cumulativeAt(todayMinutes, minuteOfDay),
        )
    }

    fun records(history: List<DayStats>, bouts: List<Bout>): Records = Records(
        bestDay = history.maxByOrNull { it.steps },
        bestPeak30 = history.filter { it.peak30Cadence > 0 }.maxByOrNull { it.peak30Cadence },
        longestBout = bouts.maxByOrNull { it.durationMs },
        fastestKmSeconds = bouts.filter { it.distanceM >= 1000 }
            .minOfOrNull { (it.durationMs / (it.distanceM / 1000.0) / 1000.0).toInt() },
        fastestKmDate = bouts.filter { it.distanceM >= 1000 }
            .minByOrNull { it.durationMs / (it.distanceM / 1000.0) }?.date,
    )

    private fun minuteOfDayOf(tsMin: Long): Int {
        val ms = tsMin * 60_000L
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }
}
