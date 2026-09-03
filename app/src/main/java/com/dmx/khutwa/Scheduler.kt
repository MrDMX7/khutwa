package com.dmx.khutwa

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.dmx.khutwa.sensor.CheckpointReceiver
import java.util.Calendar

/**
 * Two alarms, both self-rescheduling one-shots (repeating exact alarms are
 * not reliable long-term on Android, so each firing schedules the next one).
 */
object Scheduler {

    private const val PERIODIC_MINUTES = 25L
    const val ACTION_PERIODIC = "com.dmx.khutwa.action.PERIODIC_CHECKPOINT"
    const val ACTION_MIDNIGHT = "com.dmx.khutwa.action.MIDNIGHT_ROLLOVER"

    fun scheduleAll(context: Context) {
        schedulePeriodic(context)
        scheduleMidnight(context)
    }

    fun schedulePeriodic(context: Context) {
        val triggerAt = System.currentTimeMillis() + PERIODIC_MINUTES * 60_000L
        setExact(context, triggerAt, pendingIntent(context, ACTION_PERIODIC, 1))
    }

    fun scheduleMidnight(context: Context) {
        val cal = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }
        setExact(context, cal.timeInMillis, pendingIntent(context, ACTION_MIDNIGHT, 2))
    }

    /**
     * Whether the OS will let us set exact alarms.
     *
     * On Android 13+ `SCHEDULE_EXACT_ALARM` is not granted automatically to a
     * fresh install. v1 called `setExactAndAllowWhileIdle` unguarded, which
     * throws SecurityException there — from inside a receiver, which would kill
     * the self-rescheduling chain permanently.
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return am.canScheduleExactAlarms()
    }

    private fun setExact(context: Context, triggerAt: Long, pi: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        try {
            if (canScheduleExact(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                // Inexact still pierces Doze and still fires — it may just drift by
                // a few minutes. For a 25-minute checkpoint that is harmless, and
                // it keeps counting working rather than failing outright.
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call. Degrade rather than crash.
            runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
        }
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CheckpointReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
