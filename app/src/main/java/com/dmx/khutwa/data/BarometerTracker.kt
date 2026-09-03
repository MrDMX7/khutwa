package com.dmx.khutwa.data

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.dmx.khutwa.domain.Metrics
import kotlin.math.abs

/**
 * Floors climbed and elevation gain, from the barometer.
 *
 * The Fold4 has an lps22hh pressure sensor, confirmed present. Absolute
 * altitude from it is worthless — weather alone moves the reading by tens of
 * metres over a day, and opening a door moves it measurably — but the
 * *difference* over a few minutes is reliable, and that is all a floor is.
 *
 * Two filters keep this honest:
 *  - a median-of-N then exponential smoothing pass, because raw pressure is
 *    noisy enough that unfiltered data would invent floors while sitting still;
 *  - gain is only counted for minutes that also contain steps, so lifts,
 *    escalators and driving up a hill don't register as climbing.
 */
object BarometerTracker {

    /** Ignore altitude changes smaller than this between minutes — that's noise. */
    private const val NOISE_FLOOR_M = 0.6

    /**
     * A single minute can't legitimately gain more than this on foot; anything
     * larger is a pressure artefact (a door, a car, an aircraft cabin).
     */
    private const val MAX_GAIN_PER_MINUTE_M = 12.0

    private const val EMA_ALPHA = 0.25

    @Volatile private var smoothedAltitude: Double? = null
    private val window = ArrayDeque<Float>()

    private var listener: SensorEventListener? = null

    fun available(context: Context): Boolean =
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)
            ?.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    /**
     * Start sampling. Registered at the slowest useful rate — pressure changes
     * far more slowly than a step, so a high rate would burn power for nothing.
     */
    fun start(context: Context) {
        if (listener != null) return
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = sm.getDefaultSensor(Sensor.TYPE_PRESSURE) ?: return

        val l = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                onPressure(event.values[0])
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        listener = l
        sm.registerListener(l, sensor, SensorManager.SENSOR_DELAY_UI, 30_000_000)
    }

    fun stop(context: Context) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        listener?.let { sm.unregisterListener(it) }
        listener = null
    }

    private fun onPressure(hpa: Float) {
        synchronized(window) {
            window.addLast(hpa)
            while (window.size > 5) window.removeFirst()
            if (window.size < 3) return
            val median = window.sorted()[window.size / 2]
            val alt = Metrics.altitudeMetres(median)
            val prev = smoothedAltitude
            smoothedAltitude = if (prev == null) alt else prev + EMA_ALPHA * (alt - prev)
        }
    }

    /** Current smoothed altitude in metres, or null before enough samples. */
    fun currentAltitude(): Double? = smoothedAltitude

    /**
     * Total elevation gain for a day, in metres — the sum of positive
     * minute-to-minute changes across minutes that contain steps.
     */
    fun gainFor(context: Context, date: String): Double {
        val minutes = StepDao(context).minutesFor(date).filter { it.altM != 0.0 }
        if (minutes.size < 2) return 0.0
        var gain = 0.0
        for (i in 1 until minutes.size) {
            // Only consecutive minutes are comparable; a gap means the phone was
            // idle and the altitude between them is unknown.
            if (minutes[i].tsMin - minutes[i - 1].tsMin > 2) continue
            val delta = minutes[i].altM - minutes[i - 1].altM
            if (delta > NOISE_FLOOR_M && delta < MAX_GAIN_PER_MINUTE_M) gain += delta
        }
        return gain
    }

    /**
     * Per-minute gradient for the calorie equations, as a fraction (0.05 = 5%).
     *
     * Grade is vertical over horizontal, and we already know the horizontal
     * distance for a minute from its step count and stride — so a climb costs
     * more calories than the same number of steps on the flat, which is the
     * whole reason the ACSM equations take a grade term.
     */
    fun gradeLookup(context: Context, date: String): (Long) -> Double {
        val minutes = StepDao(context).minutesFor(date)
        if (minutes.size < 2) return { 0.0 }
        val profile = Settings.profile(context)
        val byMinute = HashMap<Long, Double>(minutes.size)
        for (i in 1 until minutes.size) {
            val cur = minutes[i]
            val prev = minutes[i - 1]
            if (cur.altM == 0.0 || prev.altM == 0.0) continue
            if (cur.tsMin - prev.tsMin > 2) continue
            val rise = cur.altM - prev.altM
            if (abs(rise) < NOISE_FLOOR_M || abs(rise) > MAX_GAIN_PER_MINUTE_M) continue
            val run = Metrics.minuteDistanceMetres(profile, cur.steps)
            if (run < 5.0) continue // too little ground covered to define a slope
            byMinute[cur.tsMin] = (rise / run).coerceIn(-0.30, 0.30)
        }
        return { tsMin -> byMinute[tsMin] ?: 0.0 }
    }
}
