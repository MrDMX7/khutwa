package com.dmx.khutwa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoutsTest {

    private val profile = Profile(heightCm = 175, weightKg = 75.0)
    private val date = "2026-09-03"
    private fun dateOf(@Suppress("UNUSED_PARAMETER") ms: Long) = date

    private fun minutes(startMin: Long, cadences: List<Int>) =
        cadences.mapIndexed { i, c -> MinuteBucket(startMin + i, date, c) }

    @Test
    fun `bucketize counts steps into the minute they occurred in`() {
        val base = 1_788_000_000_000L
        val times = listOf(
            base, base + 1_000, base + 2_000,        // minute 0
            base + 61_000, base + 62_000,            // minute 1
        )
        val buckets = Bouts.bucketize(times, ::dateOf)
        assertEquals(2, buckets.size)
        assertEquals(3, buckets[0].steps)
        assertEquals(2, buckets[1].steps)
        // A minute bucket's step count *is* its cadence.
        assertEquals(3, buckets[0].cadence)
    }

    @Test
    fun `a short burst is not logged as a bout`() {
        // One minute of running to catch a lift shouldn't become a "run".
        val bouts = Bouts.segment(minutes(0, listOf(170)), profile)
        assertTrue(bouts.isEmpty())
    }

    @Test
    fun `sustained running is logged as a run`() {
        val bouts = Bouts.segment(minutes(0, List(10) { 165 }), profile)
        assertEquals(1, bouts.size)
        assertEquals(ActivityClass.RUN, bouts[0].activityClass)
        assertEquals(1650, bouts[0].steps)
    }

    @Test
    fun `a bout takes its peak class not its average`() {
        // A run bookended by walking is still a run; averaging would hide it.
        val bouts = Bouts.segment(minutes(0, listOf(85, 90, 170, 175, 168, 95, 88)), profile)
        assertEquals(1, bouts.size)
        assertEquals(ActivityClass.RUN, bouts[0].activityClass)
    }

    @Test
    fun `a long gap splits one bout into two`() {
        val morning = minutes(0, List(5) { 110 })
        val evening = minutes(600, List(5) { 110 })   // ten hours later
        val bouts = Bouts.segment(morning + evening, profile)
        assertEquals(2, bouts.size)
    }

    @Test
    fun `incidental pottering never becomes a bout`() {
        val bouts = Bouts.segment(minutes(0, List(30) { 40 }), profile)
        assertTrue(bouts.isEmpty())
    }

    @Test
    fun `aggregate splits steps by class`() {
        val agg = Bouts.aggregate(
            minutes(0, listOf(40, 90, 120, 160)),
            profile,
        )
        assertEquals(40, agg.incidentalSteps)
        assertEquals(90, agg.walkSteps)
        assertEquals(120, agg.briskSteps)
        assertEquals(160, agg.runSteps)
        // Only the two minutes at or above 100 spm count as active.
        assertEquals(2, agg.activeMinutes)
    }

    @Test
    fun `aggregate distance uses per-minute stride not a flat multiplier`() {
        // Same total steps, different distribution: the faster day covers more
        // ground, because stride grows with cadence.
        val slow = Bouts.aggregate(minutes(0, List(4) { 80 }), profile)
        val fast = Bouts.aggregate(minutes(0, listOf(160, 160, 0, 0).filter { it > 0 }), profile)
        assertEquals(320, slow.walkSteps + slow.incidentalSteps)
        assertEquals(320, fast.runSteps)
        assertTrue(
            "the same steps taken faster should cover more distance",
            fast.distanceM > slow.distanceM
        )
    }

    @Test
    fun `day totals always reconcile back to the hardware count`() {
        // The detector saw 300 of the 500 steps the counter recorded; the
        // remainder must surface as unclassified rather than being invented
        // into a class or silently dropped.
        val stats = DayStats(
            date = date, steps = 500,
            walkSteps = 100, briskSteps = 150, runSteps = 50, incidentalSteps = 0,
        )
        assertEquals(300, stats.classifiedSteps)
        assertEquals(200, stats.unclassifiedSteps)
        assertEquals(
            stats.steps,
            stats.classifiedSteps + stats.unclassifiedSteps
        )
    }

    @Test
    fun `pre-v2 days are distinguishable from idle days`() {
        val legacy = DayStats(date = date, steps = 12811)   // total, no breakdown
        val idle = DayStats(date = date, steps = 0)
        assertTrue(!legacy.hasBreakdown)
        assertTrue(!idle.hasBreakdown)
        // The UI uses steps>0 && !hasBreakdown to render "—" instead of "0 km".
        assertTrue(legacy.steps > 0 && !legacy.hasBreakdown)
    }
}

class InsightsTest {

    @Test
    fun `day of week matches known dates`() {
        // 2026-09-03 was a Thursday; the key is 0 = Sunday.
        assertEquals(4, Insights.dayOfWeekKey("2026-09-03"))
        assertEquals(3, Insights.dayOfWeekKey("2026-09-02"))
        assertEquals(2, Insights.dayOfWeekKey("2026-09-01"))
    }

    @Test
    fun `no insights are invented from an empty history`() {
        assertTrue(
            Insights.generate(emptyList(), emptyList(), Profile(), 600).isEmpty()
        )
    }

    @Test
    fun `hitting the goal is reported`() {
        val today = DayStats(date = "2026-09-03", steps = 8000, goal = 7000)
        val insights = Insights.generate(listOf(today), emptyList(), Profile(), 600)
        assertTrue(insights.any { it.id == "goal_met" })
    }
}
