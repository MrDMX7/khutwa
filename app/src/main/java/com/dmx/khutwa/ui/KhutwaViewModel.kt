package com.dmx.khutwa.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dmx.khutwa.data.Settings
import com.dmx.khutwa.data.StepDao
import com.dmx.khutwa.data.StepRepository
import com.dmx.khutwa.domain.ActivityClass
import com.dmx.khutwa.domain.Bout
import com.dmx.khutwa.domain.Cadence
import com.dmx.khutwa.domain.DayStats
import com.dmx.khutwa.domain.Ghost
import com.dmx.khutwa.domain.Insights
import com.dmx.khutwa.domain.Metrics
import com.dmx.khutwa.domain.MinuteBucket
import com.dmx.khutwa.domain.Profile
import com.dmx.khutwa.domain.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KhutwaState(
    val loading: Boolean = true,
    val today: DayStats? = null,
    val todayMinutes: List<MinuteBucket> = emptyList(),
    val hourly: IntArray = IntArray(24),
    val hourlyClass: Array<ActivityClass> = Array(24) { ActivityClass.INCIDENTAL },
    val bouts: List<Bout> = emptyList(),
    val history: List<DayStats> = emptyList(),
    val insights: List<Insights.Insight> = emptyList(),
    val ghost: Ghost.DayGhost? = null,
    val records: Ghost.Records = Ghost.Records(),
    val heatmap: Array<IntArray> = Array(7) { IntArray(24) },
    val sessions: List<Session> = emptyList(),
    val weeklyActiveMinutes: Int = 0,
    val profile: Profile = Profile(),
    val arabicDigits: Boolean = true,
    val totalCalories: Boolean = false,
) {
    // IntArray/Array fields make the generated equals() reference-based, which
    // would make recomposition unreliable. Compare by content instead.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is KhutwaState) return false
        return loading == other.loading &&
            today == other.today &&
            todayMinutes == other.todayMinutes &&
            hourly.contentEquals(other.hourly) &&
            hourlyClass.contentEquals(other.hourlyClass) &&
            bouts == other.bouts &&
            history == other.history &&
            insights == other.insights &&
            ghost == other.ghost &&
            records == other.records &&
            heatmap.contentDeepEquals(other.heatmap) &&
            sessions == other.sessions &&
            weeklyActiveMinutes == other.weeklyActiveMinutes &&
            profile == other.profile &&
            arabicDigits == other.arabicDigits &&
            totalCalories == other.totalCalories
    }

    override fun hashCode(): Int {
        var r = loading.hashCode()
        r = 31 * r + (today?.hashCode() ?: 0)
        r = 31 * r + hourly.contentHashCode()
        r = 31 * r + history.hashCode()
        r = 31 * r + profile.hashCode()
        return r
    }

    /** Calories as configured: active only by default, or active + resting. */
    fun displayedKcal(): Double {
        val active = today?.kcalActive ?: 0.0
        if (!totalCalories) return active
        val elapsedMinutes = Format.minuteOfDayNow().toDouble()
        return active + Metrics.bmrPerMinute(profile) * elapsedMinutes
    }
}

class KhutwaViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(KhutwaState())
    val state: StateFlow<KhutwaState> = _state.asStateFlow()

    init {
        refresh()
        // v1's number never updated while the screen was open — the checkpoint
        // wrote to the DB but nothing re-read it. Poll while visible instead.
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                refresh()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val next = withContext(Dispatchers.IO) { load() }
            _state.value = next
        }
    }

    /** Force a hardware read, then reload — used on resume and pull-to-refresh. */
    fun checkpointAndRefresh() {
        StepRepository.checkpoint(getApplication()) { refresh() }
    }

    private fun load(): KhutwaState {
        val ctx = getApplication<Application>()
        val dao = StepDao(ctx)
        val profile = Settings.profile(ctx)
        val todayIso = Format.todayIso()

        val today = dao.day(todayIso) ?: DayStats(date = todayIso, steps = 0, goal = profile.goalSteps)
        val minutes = dao.minutesFor(todayIso)
        val history = dao.recentDays(90)

        val hourly = IntArray(24)
        val hourlyPeak = Array(24) { ActivityClass.INCIDENTAL }
        for (m in minutes) {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = m.tsMin * 60_000L }
            val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
            hourly[h] += m.steps
            if (m.activityClass.id > hourlyPeak[h].id) hourlyPeak[h] = m.activityClass
        }

        val minutesByDate = dao.minutesBetween(Format.isoDaysAgo(60), todayIso)
        val heatmap = Array(7) { IntArray(24) }
        for ((date, list) in minutesByDate) {
            val dow = Insights.dayOfWeekKey(date)
            for (m in list) {
                val cal = java.util.Calendar.getInstance().apply { timeInMillis = m.tsMin * 60_000L }
                heatmap[dow][cal.get(java.util.Calendar.HOUR_OF_DAY)] += m.steps
            }
        }

        val nowMinute = Format.minuteOfDayNow()
        return KhutwaState(
            loading = false,
            today = today,
            todayMinutes = minutes,
            hourly = hourly,
            hourlyClass = hourlyPeak,
            bouts = dao.boutsFor(todayIso),
            history = history,
            insights = Insights.generate(history, minutes, profile, nowMinute),
            ghost = Ghost.sameWeekdayGhost(todayIso, minutes, minutesByDate, nowMinute),
            records = Ghost.records(history, dao.allBouts(500)),
            heatmap = heatmap,
            sessions = dao.sessions(50),
            weeklyActiveMinutes = history.take(7).sumOf { it.activeMinutes },
            profile = profile,
            arabicDigits = Settings.arabicDigits(ctx),
            totalCalories = Settings.totalCalories(ctx),
        )
    }

    fun saveProfile(profile: Profile) {
        val ctx = getApplication<Application>()
        Settings.saveProfile(ctx, profile)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val dao = StepDao(ctx)
                val today = Format.todayIso()
                dao.ensureDay(today, profile.goalSteps)
                dao.setGoal(today, profile.goalSteps)
                // Distance and calories are derived from the body measurements,
                // so changing them has to re-derive today rather than leave
                // numbers computed from the old profile on screen.
                StepRepository.recomputeDerived(ctx, today)
            }
            refresh()
        }
    }

    fun setArabicDigits(v: Boolean) {
        Settings.setArabicDigits(getApplication(), v)
        refresh()
    }

    fun setTotalCalories(v: Boolean) {
        Settings.setTotalCalories(getApplication(), v)
        refresh()
    }

    /**
     * Re-fit the stride model from collected GPS samples.
     * Returns null when there aren't enough well-spread samples to fit a line.
     */
    fun recalibrateStride(onResult: (Pair<Double, Double>?) -> Unit) {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val fit = withContext(Dispatchers.IO) {
                val samples = StepDao(ctx).strideSamples(200)
                Metrics.fitStride(samples)
            }
            Settings.saveStrideModel(ctx, fit?.first, fit?.second)
            refresh()
            onResult(fit)
        }
    }

    /** Manual calibration: the user walked [steps] steps over [metres] metres. */
    fun manualCalibrate(steps: Int, metres: Double) {
        if (steps <= 0 || metres <= 0) return
        val ctx = getApplication<Application>()
        // A single measurement gives a point, not a slope, so hold the slope at
        // zero and let the intercept carry it — an honest constant stride.
        Settings.saveStrideModel(ctx, metres / steps, 0.0)
        refresh()
    }

    fun weeklyActiveTarget(): Int = 150

    fun peakCadenceLabel(spm: Int): String = when {
        spm >= Cadence.RUN_MIN -> "ركض"
        spm >= Cadence.VIGOROUS_MIN -> "شدة عالية"
        spm >= Cadence.MODERATE_MIN -> "شدة معتدلة"
        spm > 0 -> "خفيف"
        else -> "—"
    }
}
