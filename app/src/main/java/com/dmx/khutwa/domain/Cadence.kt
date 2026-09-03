package com.dmx.khutwa.domain

/**
 * Cadence-based activity classification.
 *
 * Thresholds come from Tudor-Locke's cadence work, which is the established
 * basis for treating steps/minute as an intensity proxy: ~100 spm is the
 * heuristic for at least moderate intensity (~3 METs) in healthy adults, and
 * ~130 spm marks the start of vigorous intensity.
 *
 * We deliberately keep a separate "incidental" band below 80: steps taken
 * while pottering around the house are real steps, but calling them a walk
 * would inflate active minutes and distort the calorie estimate.
 */
enum class ActivityClass(val id: Int) {
    /** Scattered movement — not a purposeful bout. */
    INCIDENTAL(0),
    /** Light, purposeful walking. */
    WALK(1),
    /** >= 100 spm — moderate intensity, the threshold active-minutes counts. */
    BRISK(2),
    /** >= 150 spm — running. */
    RUN(3);

    companion object {
        fun fromId(id: Int): ActivityClass = entries.firstOrNull { it.id == id } ?: INCIDENTAL
    }
}

object Cadence {

    /** Below this, movement isn't purposeful enough to count as a bout. */
    const val INCIDENTAL_MAX = 79

    /** Tudor-Locke: ~100 spm ≈ 3 METs ≈ moderate intensity. */
    const val MODERATE_MIN = 100

    /** Tudor-Locke: ~130 spm ≈ vigorous walking. */
    const val VIGOROUS_MIN = 130

    /** Above this we treat the gait as running rather than very brisk walking. */
    const val RUN_MIN = 150

    /**
     * A bout must sustain its class for longer than this. Since minute buckets
     * are the resolution floor, that means at least two minutes — one lone
     * minute is not evidence of sustained effort, and without this a
     * ten-second dash for a lift would be logged as a run.
     */
    const val MIN_BOUT_SECONDS = 60

    /**
     * Gap between steps that ends a bout. Two minutes of standing still
     * separates "one long walk" from "two walks".
     */
    const val BOUT_GAP_MS = 120_000L

    fun classify(stepsPerMinute: Int): ActivityClass = when {
        stepsPerMinute >= RUN_MIN -> ActivityClass.RUN
        stepsPerMinute >= MODERATE_MIN -> ActivityClass.BRISK
        stepsPerMinute > INCIDENTAL_MAX -> ActivityClass.WALK
        else -> ActivityClass.INCIDENTAL
    }

    /** Active minutes count moderate intensity and above — the WHO 150 min/week target. */
    fun isActiveMinute(stepsPerMinute: Int): Boolean = stepsPerMinute >= MODERATE_MIN

    /**
     * Peak 30-minute cadence: the mean of the 30 highest-cadence minutes of the
     * day, which need not be consecutive. A validated marker of ambulatory
     * intensity that is associated with cardiometabolic risk independently of
     * total step volume — and one almost no consumer app surfaces.
     *
     * Days with fewer than 30 stepping minutes are padded with zeros, so a
     * mostly-sedentary day scores low rather than being flattered by its few
     * active minutes.
     */
    fun peak30(minuteCadences: List<Int>): Int {
        if (minuteCadences.isEmpty()) return 0
        val top = minuteCadences.sortedDescending().take(30)
        return top.sum() / 30
    }

    /** The single highest-cadence minute of the day. */
    fun peak1(minuteCadences: List<Int>): Int = minuteCadences.maxOrNull() ?: 0
}
