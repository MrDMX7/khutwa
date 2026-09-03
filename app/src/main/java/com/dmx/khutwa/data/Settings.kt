package com.dmx.khutwa.data

import android.content.Context
import com.dmx.khutwa.domain.Profile
import com.dmx.khutwa.domain.Sex

/**
 * All persisted preferences, including the body measurements the distance and
 * calorie formulas need. Plain SharedPreferences in the app's private storage;
 * nothing here is ever transmitted anywhere.
 */
object Settings {

    private const val PREFS = "khutwa_prefs"

    // Existing v1 keys — do not rename, the counter's correctness depends on them.
    const val KEY_LAST_RAW = "last_raw"
    const val KEY_BUCKET_DATE = "bucket_date"

    private const val KEY_BOOT_ID = "boot_id"
    private const val KEY_HEIGHT = "height_cm"
    private const val KEY_WEIGHT = "weight_kg"
    private const val KEY_AGE = "age"
    private const val KEY_SEX = "sex"
    private const val KEY_GOAL = "goal_steps"
    private const val KEY_STRIDE_A = "stride_a"
    private const val KEY_STRIDE_B = "stride_b"
    private const val KEY_ONBOARDED = "onboarded"
    private const val KEY_ARABIC_DIGITS = "arabic_digits"
    private const val KEY_TOTAL_CALORIES = "total_calories"
    private const val KEY_GPS_CALIBRATION = "gps_calibration"
    private const val KEY_ROOT_ENABLED = "root_enabled"
    private const val KEY_METRONOME_TARGET = "metronome_target"

    fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun profile(ctx: Context): Profile {
        val p = prefs(ctx)
        val a = p.getFloat(KEY_STRIDE_A, Float.NaN)
        val b = p.getFloat(KEY_STRIDE_B, Float.NaN)
        return Profile(
            heightCm = p.getInt(KEY_HEIGHT, 175),
            weightKg = p.getFloat(KEY_WEIGHT, 75f).toDouble(),
            age = p.getInt(KEY_AGE, 30),
            sex = if (p.getString(KEY_SEX, "MALE") == "FEMALE") Sex.FEMALE else Sex.MALE,
            goalSteps = p.getInt(KEY_GOAL, 7_000),
            strideA = if (a.isNaN()) null else a.toDouble(),
            strideB = if (b.isNaN()) null else b.toDouble(),
        )
    }

    fun saveProfile(ctx: Context, profile: Profile) {
        prefs(ctx).edit()
            .putInt(KEY_HEIGHT, profile.heightCm)
            .putFloat(KEY_WEIGHT, profile.weightKg.toFloat())
            .putInt(KEY_AGE, profile.age)
            .putString(KEY_SEX, profile.sex.name)
            .putInt(KEY_GOAL, profile.goalSteps)
            .apply()
    }

    fun saveStrideModel(ctx: Context, a: Double?, b: Double?) {
        val e = prefs(ctx).edit()
        if (a == null || b == null) {
            e.remove(KEY_STRIDE_A).remove(KEY_STRIDE_B)
        } else {
            e.putFloat(KEY_STRIDE_A, a.toFloat()).putFloat(KEY_STRIDE_B, b.toFloat())
        }
        e.apply()
    }

    fun goal(ctx: Context): Int = prefs(ctx).getInt(KEY_GOAL, 7_000)

    fun isOnboarded(ctx: Context) = prefs(ctx).getBoolean(KEY_ONBOARDED, false)
    fun setOnboarded(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ONBOARDED, v).apply()

    fun arabicDigits(ctx: Context) = prefs(ctx).getBoolean(KEY_ARABIC_DIGITS, true)
    fun setArabicDigits(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ARABIC_DIGITS, v).apply()

    /** false = active calories only (default), true = active + BMR. */
    fun totalCalories(ctx: Context) = prefs(ctx).getBoolean(KEY_TOTAL_CALORIES, false)
    fun setTotalCalories(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_TOTAL_CALORIES, v).apply()

    fun gpsCalibration(ctx: Context) = prefs(ctx).getBoolean(KEY_GPS_CALIBRATION, false)
    fun setGpsCalibration(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_GPS_CALIBRATION, v).apply()

    fun rootEnabled(ctx: Context) = prefs(ctx).getBoolean(KEY_ROOT_ENABLED, false)
    fun setRootEnabled(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_ROOT_ENABLED, v).apply()

    fun metronomeTarget(ctx: Context) = prefs(ctx).getInt(KEY_METRONOME_TARGET, 110)
    fun setMetronomeTarget(ctx: Context, v: Int) =
        prefs(ctx).edit().putInt(KEY_METRONOME_TARGET, v).apply()

    // ---- boot identity ----------------------------------------------------

    /**
     * v1 inferred a reboot purely from `raw < lastRaw`, which cannot tell a
     * genuine reboot from a sensor-hub glitch that resets the counter mid-boot.
     * Approximate boot time (wall clock minus uptime) is stable to within a
     * second or two across a single boot and jumps on a real restart, so it
     * makes the two distinguishable.
     */
    fun currentBootId(): Long =
        (System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()) / 1000L

    fun storedBootId(ctx: Context): Long = prefs(ctx).getLong(KEY_BOOT_ID, 0L)

    fun saveBootId(ctx: Context, id: Long) =
        prefs(ctx).edit().putLong(KEY_BOOT_ID, id).apply()

    /** True when the boot marker has moved by more than clock jitter. */
    fun rebootedSince(ctx: Context): Boolean {
        val stored = storedBootId(ctx)
        if (stored == 0L) return false
        return kotlin.math.abs(currentBootId() - stored) > 60
    }
}
