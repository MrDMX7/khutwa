package com.dmx.khutwa.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.dmx.khutwa.domain.Cadence
import com.dmx.khutwa.domain.Metrics
import com.dmx.khutwa.domain.Profile
import com.dmx.khutwa.domain.RoutePoint
import com.dmx.khutwa.domain.Routes
import com.dmx.khutwa.domain.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

/**
 * A live, user-started run: GPS route, running totals, and the voice coach.
 *
 * Distinct from the passive counting that always runs. Passive tracking answers
 * "how much did I move today"; a session answers "how did *this run* go", which
 * needs a start and end the user chose, a route, and coaching in the moment.
 *
 * Distance here comes from **GPS when the fix is good, and from stride×steps
 * when it is not** — indoors, on a treadmill, or in an urban canyon there may
 * be no usable fix at all, and a session that reports 0 km because the sky was
 * blocked would be worse than useless.
 */
object SessionRecorder {

    /** Fixes worse than this are not trusted for distance. */
    private const val MAX_ACCURACY_M = 25.0f

    /** Faster than this on foot means a GPS jump, not a sprint. */
    private const val MAX_PLAUSIBLE_SPEED_MPS = 8.0

    private const val TICK_MS = 1_000L

    data class Phase(val name: String, val id: Int) {
        companion object {
            val WARMUP = Phase("إحماء", 0)
            val MAIN = Phase("الجري", 1)
            val COOLDOWN = Phase("تهدئة", 2)
        }
    }

    data class LiveState(
        val active: Boolean = false,
        val paused: Boolean = false,
        val sessionId: Long = 0,
        val startMs: Long = 0,
        val elapsedMs: Long = 0,
        val steps: Int = 0,
        val distanceM: Double = 0.0,
        val kcal: Double = 0.0,
        val currentCadence: Int = 0,
        val avgCadence: Int = 0,
        val maxCadence: Int = 0,
        val elevGainM: Double = 0.0,
        val points: List<RoutePoint> = emptyList(),
        val gpsFix: Boolean = false,
        val phase: Phase = Phase.MAIN,
        val phaseRemainingMs: Long = 0,
        val distanceFromGps: Boolean = false,
    )

    private val _state = MutableStateFlow(LiveState())
    val state: StateFlow<LiveState> = _state.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())
    private var coach: VoiceCoach? = null
    private var voiceEnabled = false
    private var profile = Profile()
    private var appContext: Context? = null

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    private val points = mutableListOf<RoutePoint>()
    private var gpsDistance = 0.0
    private var stepsAtStart = 0
    private var sessionSteps = 0
    private var lastMinuteSteps = 0
    private var lastMinuteMark = 0L
    private var cadenceSamples = mutableListOf<Int>()
    private var lastKmAnnounced = 0
    private var lastKmAtMs = 0L
    private var greyZoneMinutes = 0

    private var warmupMs = 0L
    private var cooldownMs = 0L

    // ---- lifecycle --------------------------------------------------------

    fun start(
        context: Context,
        profile: Profile,
        voice: Boolean,
        warmupMinutes: Int = 0,
        cooldownMinutes: Int = 0,
    ) {
        if (_state.value.active) return
        val ctx = context.applicationContext
        appContext = ctx
        this.profile = profile
        voiceEnabled = voice

        points.clear()
        cadenceSamples.clear()
        gpsDistance = 0.0
        sessionSteps = 0
        lastMinuteSteps = 0
        lastKmAnnounced = 0
        greyZoneMinutes = 0
        warmupMs = warmupMinutes * 60_000L
        cooldownMs = cooldownMinutes * 60_000L

        val now = System.currentTimeMillis()
        lastMinuteMark = now
        lastKmAtMs = now

        val id = StepDao(ctx).startSession(StepRepository.dateOf(now), now)

        val startPhase = if (warmupMs > 0) Phase.WARMUP else Phase.MAIN
        _state.value = LiveState(
            active = true, sessionId = id, startMs = now,
            phase = startPhase,
            phaseRemainingMs = if (warmupMs > 0) warmupMs else 0,
        )

        if (voice) {
            coach = VoiceCoach(ctx).apply {
                init { }
                reset()
            }
            handler.postDelayed({
                if (warmupMinutes > 0) coach?.announceWarmupStart(warmupMinutes)
                else coach?.announceRunStart(coach?.targetCadence(baselineCadence(ctx)) ?: 0)
            }, 1_200)
        }

        startLocation(ctx)
        handler.post(tick)
    }

    fun pause() {
        if (!_state.value.active) return
        _state.value = _state.value.copy(paused = true)
    }

    fun resume() {
        if (!_state.value.active) return
        _state.value = _state.value.copy(paused = false)
    }

    fun stop(onSaved: (Session?) -> Unit = {}) {
        val ctx = appContext ?: return
        val s = _state.value
        if (!s.active) { onSaved(null); return }

        handler.removeCallbacks(tick)
        stopLocation(ctx)

        val now = System.currentTimeMillis()
        val session = Session(
            id = s.sessionId,
            date = StepRepository.dateOf(s.startMs),
            startMs = s.startMs,
            endMs = now,
            steps = s.steps,
            distanceM = s.distanceM,
            kcal = s.kcal,
            avgCadence = s.avgCadence,
            maxCadence = s.maxCadence,
            elevGainM = s.elevGainM,
            activeMinutes = cadenceSamples.count { Cadence.isActiveMinute(it) },
        )

        StepRepository.query(ctx, { dao ->
            dao.updateSession(session)
            for (p in points) dao.addRoutePoint(s.sessionId, p)
            dao.pruneEmptySessions()
            dao.session(s.sessionId)
        }) { saved ->
            if (voiceEnabled) {
                coach?.announceSessionEnd(session.distanceM, session.kcal, session.durationMs, true)
                // Let the closing line finish before tearing the engine down.
                handler.postDelayed({ coach?.shutdown(); coach = null }, 6_000)
            }
            _state.value = LiveState()
            onSaved(saved)
        }
    }

    fun setVoice(enabled: Boolean) {
        voiceEnabled = enabled
        if (!enabled) {
            coach?.shutdown()
            coach = null
        } else if (coach == null && _state.value.active) {
            appContext?.let { coach = VoiceCoach(it).apply { init { } } }
        }
    }

    // ---- the loop ---------------------------------------------------------

    private val tick = object : Runnable {
        override fun run() {
            val s = _state.value
            if (!s.active) return
            if (!s.paused) update()
            handler.postDelayed(this, TICK_MS)
        }
    }

    private fun update() {
        val ctx = appContext ?: return
        val s = _state.value
        val now = System.currentTimeMillis()
        val elapsed = now - s.startMs

        // Steps for this session come from the day total's movement, which is
        // itself anchored to the hardware counter — so a session can never
        // report more steps than the device actually recorded.
        StepRepository.query(ctx, { dao ->
            dao.day(StepRepository.dateOf(now))?.steps ?: 0L
        }) { dayTotal ->
            if (stepsAtStart == 0 && dayTotal > 0) stepsAtStart = dayTotal.toInt()
            sessionSteps = (dayTotal.toInt() - stepsAtStart).coerceAtLeast(0)
        }

        // Cadence over the last full minute.
        var cadence = s.currentCadence
        if (now - lastMinuteMark >= 60_000L) {
            cadence = (sessionSteps - lastMinuteSteps).coerceAtLeast(0)
            lastMinuteSteps = sessionSteps
            lastMinuteMark = now
            if (cadence > 0) cadenceSamples.add(cadence)

            if (cadence in Cadence.MODERATE_MIN until Cadence.VIGOROUS_MIN) greyZoneMinutes++
            else greyZoneMinutes = 0
        }

        // Distance: GPS while the fix is good, stride model otherwise.
        val usingGps = gpsDistance > 50.0
        val distance = if (usingGps) gpsDistance else strideDistance()

        val kcal = estimateKcal(elapsed, distance)
        val elevGain = Routes.elevationGain(points)

        val phase = phaseFor(elapsed)
        val phaseRemaining = phaseRemaining(elapsed, phase)

        _state.value = s.copy(
            elapsedMs = elapsed,
            steps = sessionSteps,
            distanceM = distance,
            kcal = kcal,
            currentCadence = cadence,
            avgCadence = if (cadenceSamples.isEmpty()) 0 else cadenceSamples.average().roundToInt(),
            maxCadence = maxOf(s.maxCadence, cadence),
            elevGainM = elevGain,
            points = points.toList(),
            phase = phase,
            phaseRemainingMs = phaseRemaining,
            distanceFromGps = usingGps,
        )

        if (voiceEnabled) speakIfDue(ctx, phase, cadence, distance, kcal, elapsed)
    }

    private fun speakIfDue(
        ctx: Context, phase: Phase, cadence: Int,
        distance: Double, kcal: Double, elapsed: Long,
    ) {
        val c = coach ?: return

        // Phase transitions are announced once, on the crossing.
        val prevPhase = _state.value.phase
        if (phase != prevPhase) {
            when (phase) {
                Phase.MAIN -> {
                    c.announceWarmupEnd()
                    handler.postDelayed(
                        { c.announceRunStart(c.targetCadence(baselineCadence(ctx))) }, 8_000
                    )
                }
                Phase.COOLDOWN -> c.announceCooldown((cooldownMs / 60_000L).toInt())
            }
            return
        }

        if (phase != Phase.MAIN) return

        c.coachCadence(cadence, baselineCadence(ctx))
        c.checkGreyZone(cadence, greyZoneMinutes)
        c.periodicReport(distance, kcal, elapsed, _state.value.avgCadence)
        c.maybeSpeakFact()

        val km = (distance / 1000.0).toInt()
        if (km > lastKmAnnounced) {
            val now = System.currentTimeMillis()
            c.announceKilometre(km, now - lastKmAtMs)
            lastKmAtMs = now
            lastKmAnnounced = km
        }
    }

    /**
     * The runner's own typical running cadence, from history — the baseline the
     * coach nudges 5–10% above. Falls back to a conservative default only when
     * there is no history to learn from.
     */
    private fun baselineCadence(context: Context): Int {
        val recent = cadenceSamples.filter { it >= Cadence.MODERATE_MIN }
        if (recent.size >= 3) return recent.average().roundToInt()
        val stored = StepDao(context).recentDays(30)
            .map { it.peak30Cadence }.filter { it > 0 }
        return if (stored.isEmpty()) 150 else stored.average().roundToInt()
    }

    private fun strideDistance(): Double {
        var d = 0.0
        for (c in cadenceSamples) d += Metrics.minuteDistanceMetres(profile, c)
        // Add the partial current minute so the number doesn't stall for 60s.
        val partial = (sessionSteps - cadenceSamples.sum()).coerceAtLeast(0)
        if (partial > 0) {
            d += partial * Metrics.strideMetres(profile, _state.value.currentCadence.coerceAtLeast(80))
        }
        return d
    }

    private fun estimateKcal(elapsedMs: Long, distanceM: Double): Double {
        if (elapsedMs <= 0) return 0.0
        val minutes = elapsedMs / 60_000.0
        if (minutes < 0.2) return 0.0
        // Grade from the route's own elevation profile, so a hilly run costs more.
        val grade = if (distanceM > 100) Routes.elevationGain(points) / distanceM else 0.0
        var total = 0.0
        for (c in cadenceSamples) total += Metrics.minuteKcal(profile, c, grade)
        return total
    }

    /**
     * Warm-up ends on a timer; cool-down does not. Only the runner knows when
     * the main effort is finished, so [beginCooldown] is an explicit action
     * rather than something inferred from the clock.
     */
    private fun phaseFor(elapsed: Long): Phase = when {
        _state.value.phase == Phase.COOLDOWN -> Phase.COOLDOWN
        warmupMs > 0 && elapsed < warmupMs -> Phase.WARMUP
        else -> Phase.MAIN
    }

    private fun phaseRemaining(elapsed: Long, phase: Phase): Long = when (phase) {
        Phase.WARMUP -> (warmupMs - elapsed).coerceAtLeast(0)
        else -> 0
    }

    /** Called by the user when they choose to begin cooling down. */
    fun beginCooldown(minutes: Int) {
        cooldownMs = minutes * 60_000L
        _state.value = _state.value.copy(phase = Phase.COOLDOWN, phaseRemainingMs = cooldownMs)
        if (voiceEnabled) coach?.announceCooldown(minutes)
    }

    // ---- location ---------------------------------------------------------

    private fun startLocation(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationManager = lm

        val l = LocationListener { loc -> onLocation(loc) }
        locationListener = l
        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2_000L, 0f, l, Looper.getMainLooper())
            }
        } catch (e: SecurityException) {
            locationListener = null
        }
    }

    private fun stopLocation(context: Context) {
        locationListener?.let { runCatching { locationManager?.removeUpdates(it) } }
        locationListener = null
    }

    private fun onLocation(loc: Location) {
        if (_state.value.paused) return
        if (loc.hasAccuracy() && loc.accuracy > MAX_ACCURACY_M) {
            _state.value = _state.value.copy(gpsFix = false)
            return
        }
        val p = RoutePoint(
            tsMs = loc.time.takeIf { it > 0 } ?: System.currentTimeMillis(),
            lat = loc.latitude,
            lon = loc.longitude,
            altM = if (loc.hasAltitude()) loc.altitude else 0.0,
            accuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else 0.0,
            speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else 0.0,
        )
        val prev = points.lastOrNull()
        if (prev != null) {
            val d = Routes.haversine(prev, p)
            val dt = (p.tsMs - prev.tsMs) / 1000.0
            // Reject teleports: a bad fix can otherwise add hundreds of metres
            // in one step and inflate the whole run.
            if (dt > 0 && d / dt <= MAX_PLAUSIBLE_SPEED_MPS) {
                gpsDistance += d
                points.add(p)
            }
        } else {
            points.add(p)
        }
        _state.value = _state.value.copy(gpsFix = true)
    }
}
