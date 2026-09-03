package com.dmx.khutwa.domain

import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

enum class Sex { MALE, FEMALE }

/** Everything the metric formulas need about the person. Never leaves the device. */
data class Profile(
    val heightCm: Int = 175,
    val weightKg: Double = 75.0,
    val age: Int = 30,
    val sex: Sex = Sex.MALE,
    val goalSteps: Int = 7_000,
    /**
     * Fitted stride model, or null to fall back to the height estimate.
     * stride_metres = strideA + strideB * cadence
     */
    val strideA: Double? = null,
    val strideB: Double? = null,
)

/**
 * Stride length, distance and energy expenditure.
 *
 * The two things most step apps get wrong, and what we do instead:
 *
 *  1. Stride is not a constant. It grows with cadence — you cover more ground
 *     per step when you move faster. We model stride as a linear function of
 *     cadence, seeded from height and refined by GPS samples, rather than
 *     multiplying the day's total steps by one number.
 *
 *  2. Calories are not steps * 0.04. We use the ACSM metabolic equations,
 *     which take speed (and grade, from the barometer) and yield VO2, then
 *     METs, then kcal. That is why the profile asks for weight, age and sex.
 */
object Metrics {

    /** Classic anthropometric ratios: walking stride ≈ 0.415 of height. */
    private const val WALK_STRIDE_RATIO = 0.415
    private const val RUN_STRIDE_RATIO = 0.50

    /** Cadence at which the seeded model transitions to the running ratio. */
    private const val SEED_PIVOT_CADENCE = Cadence.RUN_MIN

    /**
     * Stride length in metres for a given cadence.
     *
     * With a fitted model (from GPS samples) this is a straight line through
     * the observed data. Without one, it interpolates between the walking and
     * running height ratios so a jog isn't costed at a stroll's stride.
     */
    fun strideMetres(profile: Profile, stepsPerMinute: Int): Double {
        val a = profile.strideA
        val b = profile.strideB
        if (a != null && b != null) {
            // Clamp: extrapolating a fitted line to absurd cadences produces
            // absurd strides, and a bad sample set shouldn't corrupt a whole day.
            return (a + b * stepsPerMinute).coerceIn(0.30, 2.20)
        }
        val h = profile.heightCm / 100.0
        val walk = h * WALK_STRIDE_RATIO
        val run = h * RUN_STRIDE_RATIO
        return when {
            stepsPerMinute <= Cadence.INCIDENTAL_MAX -> walk * 0.85
            stepsPerMinute >= SEED_PIVOT_CADENCE -> run
            else -> {
                val t = (stepsPerMinute - Cadence.INCIDENTAL_MAX).toDouble() /
                    (SEED_PIVOT_CADENCE - Cadence.INCIDENTAL_MAX)
                walk + (run - walk) * t
            }
        }
    }

    /** Distance covered in one minute at this cadence, in metres. */
    fun minuteDistanceMetres(profile: Profile, stepsInMinute: Int): Double =
        stepsInMinute * strideMetres(profile, stepsInMinute)

    /**
     * ACSM metabolic equations. Speed in metres/minute, grade as a fraction
     * (0.05 = a 5% incline). Returns VO2 in ml/kg/min.
     *
     * Walking and running have genuinely different coefficients — running is
     * roughly twice as costly per unit speed on the flat but much less
     * penalised by gradient, because you spend less time supporting your
     * weight against it.
     */
    private fun vo2(speedMPerMin: Double, grade: Double, running: Boolean): Double {
        val g = grade.coerceIn(-0.30, 0.30)
        return if (running) {
            3.5 + 0.2 * speedMPerMin + 0.9 * speedMPerMin * g
        } else {
            3.5 + 0.1 * speedMPerMin + 1.8 * speedMPerMin * g
        }
    }

    /**
     * Active kcal burned in one minute of stepping.
     *
     * This is *active* expenditure — what the movement cost. It deliberately
     * excludes the resting metabolism you'd have burned sitting still, which
     * is what makes a step app's "calories" number comparable day to day.
     */
    fun minuteKcal(profile: Profile, stepsInMinute: Int, grade: Double = 0.0): Double {
        if (stepsInMinute <= 0) return 0.0
        val speed = minuteDistanceMetres(profile, stepsInMinute) // metres per minute
        val running = stepsInMinute >= Cadence.RUN_MIN
        val met = max(1.0, vo2(speed, grade, running) / 3.5)
        // kcal/min = MET * 3.5 * kg / 200
        return met * 3.5 * profile.weightKg / 200.0
    }

    /** Mifflin-St Jeor basal metabolic rate, kcal/day. Used only for "total" mode. */
    fun bmrPerDay(profile: Profile): Double {
        val base = 10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * profile.age
        return if (profile.sex == Sex.MALE) base + 5.0 else base - 161.0
    }

    fun bmrPerMinute(profile: Profile): Double = bmrPerDay(profile) / 1440.0

    /**
     * Altitude in metres from barometric pressure (hPa), international
     * barometric formula against standard sea-level pressure.
     *
     * The absolute value is meaningless for us — weather moves it by tens of
     * metres over a day — but the *difference* over minutes is what floors
     * are made of, and that is reliable.
     */
    fun altitudeMetres(pressureHpa: Float): Double =
        44_330.0 * (1.0 - (pressureHpa / 1013.25).toDouble().pow(1.0 / 5.255))

    /** One floor of stairs, in metres of climb. */
    const val METRES_PER_FLOOR = 3.0

    fun floorsFromGain(gainMetres: Double): Double = gainMetres / METRES_PER_FLOOR

    /**
     * Least-squares fit of stride against cadence over collected GPS samples.
     * Returns null when there is too little spread to fit a meaningful line —
     * twenty samples all taken at the same cadence tell you nothing about slope.
     */
    fun fitStride(samples: List<StrideSample>): Pair<Double, Double>? {
        if (samples.size < 8) return null
        val n = samples.size
        val xs = samples.map { it.cadence.toDouble() }
        val ys = samples.map { it.distanceM / it.steps }
        val meanX = xs.average()
        val meanY = ys.average()
        var sxx = 0.0
        var sxy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            sxx += dx * dx
            sxy += dx * (ys[i] - meanY)
        }
        if (sxx < 100.0) return null // cadences too tightly clustered to fit a slope
        val b = sxy / sxx
        val a = meanY - b * meanX
        // A negative slope means stride shrinks as you speed up, which is
        // biomechanically wrong — treat it as a bad sample set, not a model.
        if (b < 0) return null
        return a to b
    }

    fun formatKm(metres: Double): String = String.format("%.2f", metres / 1000.0)

    fun formatKcal(kcal: Double): String = kcal.roundToInt().toString()
}

/** One GPS-derived observation: [steps] steps covered [distanceM] metres at [cadence] spm. */
data class StrideSample(
    val tsMs: Long,
    val cadence: Int,
    val distanceM: Double,
    val steps: Int,
    val gpsAccuracy: Double,
)
