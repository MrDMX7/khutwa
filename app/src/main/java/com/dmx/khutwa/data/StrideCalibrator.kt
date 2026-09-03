package com.dmx.khutwa.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.dmx.khutwa.domain.StrideSample

/**
 * Learns the user's real stride length from GPS.
 *
 * Why GPS rather than a one-off manual measurement: a manual calibration is a
 * single sample, taken while the user is consciously "walking a measured
 * distance" — which changes their gait — and it is only valid at that one
 * speed. GPS collects many samples from ordinary walking across a range of
 * cadences, which is what lets [com.dmx.khutwa.domain.Metrics.fitStride] fit
 * stride *as a function of* cadence rather than pinning it to one number.
 *
 * Everything here is opt-in and opportunistic. There is no background location
 * session: updates are requested only while the detector service is already
 * awake and steps are actively arriving, and they stop the moment walking does.
 */
object StrideCalibrator {

    /** Fixes worse than this are too vague to measure a stride with. */
    private const val MAX_ACCURACY_M = 10.0f

    /** Enough ground for GPS noise to average out into a usable measurement. */
    private const val MIN_SAMPLE_DISTANCE_M = 300.0

    /** Beyond this the user probably got in a car mid-window. */
    private const val MAX_SEGMENT_SPEED_MPS = 6.0

    /** A gap this long means we lost them; the accumulated segment is void. */
    private const val MAX_FIX_GAP_MS = 30_000L

    private var listener: LocationListener? = null
    private var lastFix: Location? = null
    private var accumulatedM = 0.0
    private var accumulatedSteps = 0
    private var segmentStartMs = 0L

    fun enabled(context: Context): Boolean =
        Settings.gpsCalibration(context) && hasPermission(context)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun start(context: Context) {
        if (listener != null || !enabled(context)) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) return

        val l = LocationListener { loc -> onFix(context, loc) }
        listener = l
        reset()
        try {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                10_000L,      // every 10 s is plenty at walking pace
                10f,          // and only if we actually moved
                l,
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            listener = null
        }
    }

    fun stop(context: Context) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        listener?.let { runCatching { lm?.removeUpdates(it) } }
        listener = null
        reset()
    }

    /** Called by the detector service so a segment knows how many steps it covered. */
    fun addSteps(count: Int) {
        if (listener != null) accumulatedSteps += count
    }

    private fun onFix(context: Context, loc: Location) {
        if (!loc.hasAccuracy() || loc.accuracy > MAX_ACCURACY_M) return

        val prev = lastFix
        lastFix = loc
        if (prev == null) {
            segmentStartMs = loc.time
            return
        }

        val gap = loc.time - prev.time
        if (gap <= 0 || gap > MAX_FIX_GAP_MS) {
            // Lost the trail — a distance across an unknown gap is meaningless.
            reset()
            lastFix = loc
            segmentStartMs = loc.time
            return
        }

        val metres = prev.distanceTo(loc).toDouble()
        val speed = metres / (gap / 1000.0)
        if (speed > MAX_SEGMENT_SPEED_MPS) {
            // Vehicle, or a GPS jump. Either way it isn't a stride.
            reset()
            lastFix = loc
            segmentStartMs = loc.time
            return
        }

        accumulatedM += metres

        if (accumulatedM >= MIN_SAMPLE_DISTANCE_M && accumulatedSteps > 50) {
            val durationMin = (loc.time - segmentStartMs) / 60_000.0
            if (durationMin > 0.5) {
                val cadence = (accumulatedSteps / durationMin).toInt()
                // Cadences outside plausible human gait mean the step count and
                // the GPS window disagree; storing that would poison the fit.
                if (cadence in 50..220) {
                    val sample = StrideSample(
                        tsMs = loc.time,
                        cadence = cadence,
                        distanceM = accumulatedM,
                        steps = accumulatedSteps,
                        gpsAccuracy = loc.accuracy.toDouble(),
                    )
                    StepRepository.query(context, { dao -> dao.addStrideSample(sample) }) { }
                    maybeRefit(context)
                }
            }
            reset()
            lastFix = loc
            segmentStartMs = loc.time
        }
    }

    /** Refit once there are enough samples; the model only improves with data. */
    private fun maybeRefit(context: Context) {
        StepRepository.query(context, { dao ->
            com.dmx.khutwa.domain.Metrics.fitStride(dao.strideSamples(200))
        }) { fit ->
            if (fit != null) Settings.saveStrideModel(context, fit.first, fit.second)
        }
    }

    private fun reset() {
        accumulatedM = 0.0
        accumulatedSteps = 0
        lastFix = null
        segmentStartMs = 0L
    }
}
