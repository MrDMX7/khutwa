package com.dmx.khutwa.domain

/**
 * Goal streaks.
 *
 * A streak is the only "gamification" in the app, and it is deliberately the
 * honest kind: it counts days the goal was actually met, it never counts today
 * until today is met, and nothing about it is celebrated with confetti. The
 * value is that a run of days is the thing that predicts a habit — a single
 * 20,000-step day does not.
 */
object Streaks {

    data class Result(val current: Int, val longest: Int)

    /**
     * [days] in any order; each needs a `date` (yyyy-MM-dd) and its own goal.
     *
     * Today is treated as pending rather than broken: a day that has not met
     * its goal *yet* must not read as a break, or the number would drop to zero
     * every midnight and only recover at the evening walk.
     */
    fun compute(days: List<DayStats>, todayIso: String): Result {
        if (days.isEmpty()) return Result(0, 0)
        val met = days.filter { it.goalMet }.map { it.date }.toHashSet()
        val sorted = days.map { it.date }.distinct().sorted()

        var longest = 0
        var run = 0
        var previous: String? = null
        for (date in sorted) {
            run = if (date in met) {
                if (previous != null && isNextDay(previous, date)) run + 1 else 1
            } else 0
            if (run > longest) longest = run
            previous = date
        }

        // Walk backwards from today (or yesterday, while today is still open).
        var cursor = if (todayIso in met) todayIso else previousDay(todayIso)
        var current = 0
        while (cursor in met) {
            current++
            cursor = previousDay(cursor)
        }
        return Result(current, maxOf(longest, current))
    }

    private fun isNextDay(a: String, b: String): Boolean = nextDay(a) == b

    private fun nextDay(iso: String): String = shift(iso, 1)

    private fun previousDay(iso: String): String = shift(iso, -1)

    private fun shift(iso: String, days: Int): String = try {
        val cal = java.util.Calendar.getInstance()
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        cal.time = fmt.parse(iso)!!
        cal.add(java.util.Calendar.DAY_OF_YEAR, days)
        fmt.format(cal.time)
    } catch (e: Exception) {
        iso
    }
}
