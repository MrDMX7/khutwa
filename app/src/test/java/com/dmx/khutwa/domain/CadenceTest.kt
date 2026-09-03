package com.dmx.khutwa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CadenceTest {

    @Test
    fun `classifies against Tudor-Locke thresholds`() {
        assertEquals(ActivityClass.INCIDENTAL, Cadence.classify(0))
        assertEquals(ActivityClass.INCIDENTAL, Cadence.classify(79))
        assertEquals(ActivityClass.WALK, Cadence.classify(80))
        assertEquals(ActivityClass.WALK, Cadence.classify(99))
        // 100 spm is the moderate-intensity heuristic (~3 METs).
        assertEquals(ActivityClass.BRISK, Cadence.classify(100))
        assertEquals(ActivityClass.BRISK, Cadence.classify(149))
        assertEquals(ActivityClass.RUN, Cadence.classify(150))
        assertEquals(ActivityClass.RUN, Cadence.classify(180))
    }

    @Test
    fun `active minutes start at the moderate threshold`() {
        assertTrue(!Cadence.isActiveMinute(99))
        assertTrue(Cadence.isActiveMinute(100))
    }

    @Test
    fun `peak30 pads short days with zeros rather than flattering them`() {
        // Ten brisk minutes and nothing else is not a 120 spm day.
        val short = List(10) { 120 }
        assertEquals(120 * 10 / 30, Cadence.peak30(short))

        // A full 30 minutes at 120 does score 120.
        assertEquals(120, Cadence.peak30(List(30) { 120 }))
    }

    @Test
    fun `peak30 takes the highest minutes even when scattered`() {
        val day = List(200) { 40 } + List(30) { 130 }
        assertEquals(130, Cadence.peak30(day.shuffled()))
    }

    @Test
    fun `peak30 of an empty day is zero`() {
        assertEquals(0, Cadence.peak30(emptyList()))
        assertEquals(0, Cadence.peak1(emptyList()))
    }
}

class MetricsTest {

    private val profile = Profile(heightCm = 175, weightKg = 75.0, age = 30, sex = Sex.MALE)

    @Test
    fun `stride grows with cadence`() {
        val slow = Metrics.strideMetres(profile, 85)
        val fast = Metrics.strideMetres(profile, 160)
        assertTrue("stride should lengthen as cadence rises", fast > slow)
    }

    @Test
    fun `seeded stride tracks the height ratios`() {
        // Running stride ≈ height * 0.50
        assertEquals(0.875, Metrics.strideMetres(profile, 160), 0.001)
    }

    @Test
    fun `a fitted model overrides the height estimate`() {
        val fitted = profile.copy(strideA = 0.4, strideB = 0.004)
        assertEquals(0.4 + 0.004 * 100, Metrics.strideMetres(fitted, 100), 0.0001)
    }

    @Test
    fun `an absurd fit is clamped rather than trusted`() {
        val bad = profile.copy(strideA = 50.0, strideB = 1.0)
        assertTrue(Metrics.strideMetres(bad, 120) <= 2.20)
    }

    @Test
    fun `calories rise with intensity and with gradient`() {
        val walk = Metrics.minuteKcal(profile, 90)
        val run = Metrics.minuteKcal(profile, 160)
        assertTrue("running should cost more than walking", run > walk)

        val flat = Metrics.minuteKcal(profile, 110, grade = 0.0)
        val uphill = Metrics.minuteKcal(profile, 110, grade = 0.08)
        assertTrue("climbing should cost more than the flat", uphill > flat)
    }

    @Test
    fun `no steps costs no active calories`() {
        assertEquals(0.0, Metrics.minuteKcal(profile, 0), 0.0)
    }

    @Test
    fun `BMR follows Mifflin-St Jeor and differs by sex`() {
        // 10*75 + 6.25*175 - 5*30 + 5 = 1698.75
        assertEquals(1698.75, Metrics.bmrPerDay(profile), 0.01)
        val female = profile.copy(sex = Sex.FEMALE)
        assertEquals(1698.75 - 166.0, Metrics.bmrPerDay(female), 0.01)
    }

    @Test
    fun `stride fit refuses samples that cannot define a slope`() {
        // All at the same cadence: no spread, so no slope is inferable.
        val flat = List(20) { StrideSample(0, 100, 75.0, 100, 5.0) }
        assertNull(Metrics.fitStride(flat))

        // Too few samples.
        assertNull(Metrics.fitStride(List(3) { StrideSample(0, 90 + it * 20, 70.0, 100, 5.0) }))
    }

    @Test
    fun `stride fit recovers a known linear relationship`() {
        // stride = 0.40 + 0.003 * cadence
        val samples = (60..180 step 6).map { c ->
            val stride = 0.40 + 0.003 * c
            StrideSample(0, c, stride * 100, 100, 5.0)
        }
        val fit = Metrics.fitStride(samples)
        requireNotNull(fit)
        assertEquals(0.40, fit.first, 0.01)
        assertEquals(0.003, fit.second, 0.0005)
    }

    @Test
    fun `stride fit rejects a negative slope as biomechanically wrong`() {
        val samples = (60..180 step 6).map { c ->
            StrideSample(0, c, (1.2 - 0.003 * c) * 100, 100, 5.0)
        }
        assertNull(Metrics.fitStride(samples))
    }
}
