package com.dmx.khutwa.data

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.dmx.khutwa.domain.Cadence
import com.dmx.khutwa.domain.Knowledge
import com.dmx.khutwa.domain.Metrics
import com.dmx.khutwa.domain.Profile
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Spoken coaching during a run.
 *
 * The cadence advice is deliberately **relative to the runner's own baseline**,
 * not to 180 spm. The 180 figure comes from Jack Daniels counting elite runners
 * at race pace and was meant as a floor, not a universal target; elites
 * themselves span 160–200+, and roughly half that variation is explained by leg
 * length and speed alone. Worse, forcing a change larger than about 10% is
 * shown to *increase* oxygen cost and degrade running economy. So the target
 * here is baseline + 5–10%, capped, and the coach stays quiet when the runner
 * is already in range.
 *
 * Everything is opt-in and interruptible. Speech uses the assistant audio
 * stream so it ducks music rather than stopping it.
 */
class VoiceCoach(private val context: Context) {

    companion object {
        /** Don't nag more often than this, whatever else is due. */
        private const val MIN_GAP_MS = 25_000L

        /** Cadence coaching interval while running. */
        private const val CADENCE_INTERVAL_MS = 60_000L

        /** Periodic distance/pace report. */
        private const val REPORT_INTERVAL_MS = 300_000L

        /** A fact or nudge, spaced out so it stays welcome. */
        private const val FACT_INTERVAL_MS = 420_000L

        /** Below this deviation the cadence is fine — say nothing. */
        private const val CADENCE_DEADBAND = 6

        /** Never ask for more than this above baseline; beyond ~10% economy suffers. */
        private const val MAX_TARGET_UPLIFT = 0.10
    }

    private var tts: TextToSpeech? = null
    private var ready = false
    private var arabicAvailable = false

    private var lastSpokeAt = 0L
    private var lastCadenceAt = 0L
    private var lastReportAt = 0L
    private var lastFactAt = 0L
    private var factIndex = 0
    private var encouragementIndex = 0
    private var greyZoneWarned = false

    fun init(onReady: (Boolean) -> Unit = {}) {
        if (ready) { onReady(true); return }
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status != TextToSpeech.SUCCESS) {
                ready = false
                onReady(false)
                return@TextToSpeech
            }
            val engine = tts ?: return@TextToSpeech
            val arabic = engine.setLanguage(Locale("ar"))
            arabicAvailable = arabic != TextToSpeech.LANG_MISSING_DATA &&
                arabic != TextToSpeech.LANG_NOT_SUPPORTED
            if (!arabicAvailable) {
                // Without an Arabic voice the coach would read Arabic text with
                // English phonemes, which is worse than silence. Report the
                // failure so Settings can tell the user why it isn't speaking.
                engine.setLanguage(Locale.US)
            }
            engine.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            engine.setSpeechRate(0.95f)
            ready = true
            onReady(arabicAvailable)
        }
    }

    fun arabicVoiceAvailable(): Boolean = arabicAvailable

    fun shutdown() {
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        ready = false
    }

    fun reset() {
        lastSpokeAt = 0
        lastCadenceAt = 0
        lastReportAt = 0
        lastFactAt = 0
        greyZoneWarned = false
    }

    private fun speak(text: String, force: Boolean = false) {
        if (!ready) return
        val now = System.currentTimeMillis()
        if (!force && now - lastSpokeAt < MIN_GAP_MS) return
        lastSpokeAt = now
        // QUEUE_ADD would stack messages and talk over a whole minute of running;
        // the newest instruction is always the relevant one.
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "khutwa-${now}")
    }

    // ---- session lifecycle ------------------------------------------------

    fun announceWarmupStart(minutes: Int) =
        speak("ابدأ بإحماء $minutes دقائق. امشِ بسرعة أو هرول خفيف جداً.", force = true)

    fun announceWarmupEnd() =
        speak(
            "انتهى الإحماء. سوِّ تمارين ديناميكية: رفع ركبة، ركلة خلفية، مرجحة ساق. " +
                "لا تمدّد ساكناً الآن.",
            force = true,
        )

    fun announceRunStart(targetCadence: Int) =
        speak("ابدأ الجري. إيقاعك المستهدف حوالي $targetCadence خطوة في الدقيقة.", force = true)

    fun announceCooldown(minutes: Int) =
        speak(
            "ابدأ التهدئة. $minutes دقائق هرولة خفيفة ثم مشي. " +
                "الإطالة الساكنة مكانها الآن بعد ما تنتهي.",
            force = true,
        )

    fun announceSessionEnd(distanceM: Double, kcal: Double, durationMs: Long, arabicDigits: Boolean) {
        val km = String.format(Locale.US, "%.1f", distanceM / 1000.0)
        val minutes = (durationMs / 60_000L).toInt()
        speak(
            "انتهت الجلسة. $km كيلومتر في $minutes دقيقة، وحرقت ${kcal.roundToInt()} سعرة.",
            force = true,
        )
    }

    // ---- live coaching ----------------------------------------------------

    /**
     * The target cadence: the runner's own recent baseline lifted 5–10%,
     * never an absolute number, and never more than 10% above baseline.
     */
    fun targetCadence(baseline: Int): Int {
        if (baseline <= 0) return 0
        return (baseline * (1.0 + MAX_TARGET_UPLIFT * 0.75)).roundToInt()
            .coerceAtMost((baseline * (1.0 + MAX_TARGET_UPLIFT)).roundToInt())
    }

    /**
     * @param currentCadence the last full minute's step count
     * @param baselineCadence the runner's own typical running cadence
     */
    fun coachCadence(currentCadence: Int, baselineCadence: Int) {
        val now = System.currentTimeMillis()
        if (now - lastCadenceAt < CADENCE_INTERVAL_MS) return
        if (currentCadence < Cadence.INCIDENTAL_MAX) return  // not actually running
        lastCadenceAt = now

        val target = targetCadence(baselineCadence).takeIf { it > 0 } ?: return
        val delta = currentCadence - target

        when {
            abs(delta) <= CADENCE_DEADBAND ->
                // In range. Say something useful or nothing at all, rather than
                // repeating "good" every minute until it becomes noise.
                if (now - lastSpokeAt > CADENCE_INTERVAL_MS * 2) {
                    speak("إيقاعك $currentCadence. ممتاز، ثبّت عليه.")
                }
            delta < 0 ->
                speak(
                    "إيقاعك $currentCadence. زده قليلاً — خطوات أقصر وأسرع، " +
                        "بدون ما تزيد سرعتك."
                )
            else ->
                speak("إيقاعك $currentCadence، أعلى من هدفك. خفّف قليلاً واسترخِ.")
        }
    }

    /**
     * The 80/20 grey zone: not easy enough to recover from, not hard enough to
     * drive adaptation. Warned once per session, not repeatedly — it is a
     * pacing decision, not an emergency.
     */
    fun checkGreyZone(cadence: Int, minutesInZone: Int) {
        if (greyZoneWarned || minutesInZone < 8) return
        if (cadence !in Cadence.MODERATE_MIN until Cadence.VIGOROUS_MIN) return
        greyZoneWarned = true
        speak(
            "أنت في المنطقة المتوسطة من ثمان دقائق. إما خفّف لتصير جرية سهلة فعلاً، " +
                "أو زد لتصير قوية فعلاً. الوسط يتعبك بلا فائدة."
        )
    }

    fun periodicReport(distanceM: Double, kcal: Double, elapsedMs: Long, avgCadence: Int) {
        val now = System.currentTimeMillis()
        if (now - lastReportAt < REPORT_INTERVAL_MS) return
        lastReportAt = now
        val km = String.format(Locale.US, "%.1f", distanceM / 1000.0)
        val minutes = (elapsedMs / 60_000L).toInt()
        val paceText = if (distanceM > 200) {
            val secPerKm = (elapsedMs / 1000.0) / (distanceM / 1000.0)
            val m = (secPerKm / 60).toInt()
            val s = (secPerKm % 60).roundToInt()
            "، الوتيرة $m و $s دقيقة للكيلومتر"
        } else ""
        speak("$km كيلومتر في $minutes دقيقة$paceText، وحرقت ${kcal.roundToInt()} سعرة.")
    }

    /** A rotating fact or a word of encouragement, spaced far enough apart to stay welcome. */
    fun maybeSpeakFact() {
        val now = System.currentTimeMillis()
        if (now - lastFactAt < FACT_INTERVAL_MS) return
        lastFactAt = now
        // Alternate: a fact teaches, encouragement sustains. Only facts would
        // become a lecture; only encouragement would become hollow.
        if (factIndex % 2 == 0) {
            val fact = Knowledge.spokenFacts[(factIndex / 2) % Knowledge.spokenFacts.size]
            speak(fact)
        } else {
            val line = Knowledge.encouragement[encouragementIndex % Knowledge.encouragement.size]
            encouragementIndex++
            speak(line)
        }
        factIndex++
    }

    fun announceKilometre(km: Int, splitMs: Long) {
        val minutes = (splitMs / 60_000L).toInt()
        val seconds = ((splitMs % 60_000L) / 1000L).toInt()
        speak("الكيلومتر $km، في $minutes دقيقة و $seconds ثانية.", force = true)
    }

    /**
     * Heat warning. Relevant here: above ~80% humidity oxygen uptake efficiency
     * drops, and running above ~35°C is generally discouraged.
     */
    fun heatWarning() =
        speak("الجو حار. خفّف الشدة، واشرب قبل ما تعطش.", force = true)
}
