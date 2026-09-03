package com.dmx.khutwa

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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

    private fun setExact(context: Context, triggerAt: Long, pi: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    private fun pendingIntent(context: Context, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, CheckpointReceiver::class.java).setAction(action)
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
