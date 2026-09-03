package com.dmx.khutwa.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The "coach" — an on-device statistics engine over the user's own history.
 *
 * Deliberately not an LLM call. Everything here is a comparison the user's own
 * data can answer exactly, so it works offline, costs nothing, returns
 * instantly, and never ships health data off the phone. An LLM layer could sit
 * on top later; it would not make these particular answers any better.
 *
 * Rules only fire when they have enough history to mean something — a
 * three-day-old install should not be told its weekly trend is down.
 */
object Insights {

    enum class Tone { GOOD, NEUTRAL, WARN }

    data class Insight(
        val id: String,
        val text: String,
        val tone: Tone,
        /** Higher sorts first. */
        val priority: Int,
    )

    fun generate(
        history: List<DayStats>,      // newest first
        todayMinutes: List<MinuteBucket>,
        profile: Profile,
        nowMinuteOfDay: Int,
    ): List<Insight> {
        val out = mutableListOf<Insight>()
        if (history.isEmpty()) return out

        val today = history.first()
        val past = history.drop(1).filter { it.steps > 0 }

        goalProgress(today, nowMinuteOfDay, past)?.let(out::add)
        streak(history)?.let(out::add)
        stepsDebt(history, profile)?.let(out::add)
        cadenceTrend(history)?.let(out::add)
        bestTimeOfDay(history, todayMinutes)?.let(out::add)
        weakestWeekday(past)?.let(out::add)
        activeMinutesWeekly(history)?.let(out::add)

        return out.sortedByDescending { it.priority }
    }

    // ---- individual rules -------------------------------------------------

    /**
     * Pace against your own typical curve for this time of day, not against a
     * flat fraction of the goal. Being "behind" at 8am is meaningless; being
     * behind relative to where you normally are at 8am is not.
     */
    private fun goalProgress(today: DayStats, nowMinuteOfDay: Int, past: List<DayStats>): Insight? {
        if (today.goal <= 0) return null
        val remaining = today.goal - today.steps
        if (remaining <= 0) {
            return Insight(
                "goal_met",
                "حققت هدفك اليوم — ${today.steps} خطوة.",
                Tone.GOOD,
                priority = 100,
            )
        }
        // Only nudge once most of the day has passed, otherwise it's noise.
        if (nowMinuteOfDay < 17 * 60) return null
        if (past.size < 3) return null
        val typical = past.take(14).map { it.steps }.average()
        if (today.steps >= typical) return null
        return Insight(
            "behind_pace",
            "باقي $remaining خطوة على هدفك — حوالي ${(remaining / 100.0).roundToInt()} دقيقة مشي.",
            Tone.NEUTRAL,
            priority = 80,
        )
    }

    private fun streak(history: List<DayStats>): Insight? {
        // Today doesn't break a streak just because it isn't finished yet.
        val closed = history.drop(1)
        var n = 0
        for (d in closed) {
            if (d.goalMet) n++ else break
        }
        if (n < 2) return null
        return Insight("streak", "$n أيام متتالية حققت فيها هدفك.", Tone.GOOD, priority = 70)
    }

    /**
     * Rolling 7-day balance against the goal, instead of a pass/fail streak.
     *
     * A streak punishes one bad day by resetting to zero, which is the point at
     * which most people stop opening the app. A debt you can pay back keeps the
     * week recoverable.
     */
    private fun stepsDebt(history: List<DayStats>, profile: Profile): Insight? {
        val week = history.drop(1).take(7)
        if (week.size < 7) return null
        val goal = profile.goalSteps.toLong()
        val balance = week.sumOf { it.steps - goal }
        return when {
            balance >= goal -> Insight(
                "debt_credit",
                "أنت متقدم ${balance} خطوة على هدف الأسبوع.",
                Tone.GOOD,
                priority = 55,
            )
            balance <= -goal -> Insight(
                "debt_owed",
                "عليك ${abs(balance)} خطوة متأخرة عن هدف الأسبوع — يوم نشيط واحد يسددها.",
                Tone.WARN,
                priority = 60,
            )
            else -> null
        }
    }

    /** Peak-30 cadence is the intensity signal; volume can hide a drop in it. */
    private fun cadenceTrend(history: List<DayStats>): Insight? {
        val withCadence = history.drop(1).filter { it.peak30Cadence > 0 }
        if (withCadence.size < 10) return null
        val recent = withCadence.take(7).map { it.peak30Cadence }.average()
        val prior = withCadence.drop(7).take(7).map { it.peak30Cadence }.average()
        if (prior <= 0) return null
        val pct = ((recent - prior) / prior * 100).roundToInt()
        if (abs(pct) < 8) return null
        return if (pct > 0) {
            Insight("cadence_up", "إيقاعك الأقصى ارتفع $pct% عن الأسبوع الماضي.", Tone.GOOD, 50)
        } else {
            Insight("cadence_down", "إيقاعك الأقصى نزل ${abs(pct)}% عن الأسبوع الماضي.", Tone.WARN, 65)
        }
    }

    private fun bestTimeOfDay(history: List<DayStats>, todayMinutes: List<MinuteBucket>): Insight? {
        if (history.size < 7 || todayMinutes.isEmpty()) return null
        val byHour = todayMinutes.groupBy { ((it.tsMin * 60_000L) / 3_600_000L % 24).toInt() }
        val best = byHour.maxByOrNull { (_, v) -> v.sumOf { it.steps } } ?: return null
        val hour = best.key
        return Insight(
            "peak_hour",
            "أنشط ساعاتك اليوم كانت $hour:00 — ${best.value.sumOf { it.steps }} خطوة.",
            Tone.NEUTRAL,
            priority = 30,
        )
    }

    private fun weakestWeekday(past: List<DayStats>): Insight? {
        if (past.size < 21) return null
        val byDow = past.groupBy { dayOfWeekKey(it.date) }.filterValues { it.size >= 3 }
        if (byDow.size < 5) return null
        val worst = byDow.minByOrNull { (_, v) -> v.sumOf { it.steps } / v.size } ?: return null
        val overall = past.sumOf { it.steps } / past.size
        val worstAvg = worst.value.sumOf { it.steps } / worst.value.size
        if (worstAvg > overall * 0.75) return null
        return Insight(
            "weak_day",
            "${arabicDayName(worst.key)} أضعف أيامك — بمعدل $worstAvg خطوة مقابل $overall عموماً.",
            Tone.NEUTRAL,
            priority = 35,
        )
    }

    /** WHO: 150 minutes of moderate activity a week. */
    private fun activeMinutesWeekly(history: List<DayStats>): Insight? {
        val week = history.take(7)
        if (week.size < 7) return null
        val total = week.sumOf { it.activeMinutes }
        if (total == 0) return null
        return if (total >= 150) {
            Insight("who_met", "$total دقيقة نشاط هذا الأسبوع — تجاوزت توصية 150 دقيقة.", Tone.GOOD, 45)
        } else {
            Insight("who_short", "$total من 150 دقيقة نشاط أسبوعياً.", Tone.NEUTRAL, 40)
        }
    }

    // ---- helpers ----------------------------------------------------------

    /** Zeller-style day-of-week from an ISO date, 0 = Sunday. */
    internal fun dayOfWeekKey(iso: String): Int {
        val y = iso.substring(0, 4).toInt()
        val m = iso.substring(5, 7).toInt()
        val d = iso.substring(8, 10).toInt()
        val mm = if (m < 3) m + 12 else m
        val yy = if (m < 3) y - 1 else y
        val k = yy % 100
        val j = yy / 100
        val h = (d + 13 * (mm + 1) / 5 + k + k / 4 + j / 4 + 5 * j) % 7
        return (h + 6) % 7
    }

    private fun arabicDayName(dow: Int): String = when (dow) {
        0 -> "الأحد"
        1 -> "الاثنين"
        2 -> "الثلاثاء"
        3 -> "الأربعاء"
        4 -> "الخميس"
        5 -> "الجمعة"
        else -> "السبت"
    }
}
