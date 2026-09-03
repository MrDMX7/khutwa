package com.dmx.khutwa.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dmx.khutwa.Scheduler
import com.dmx.khutwa.data.StepRepository

/**
 * Fired by AlarmManager — either a routine 25-minute checkpoint or the
 * midnight day-boundary rollover. goAsync() because the sensor read is a
 * short but real async wait (up to ~8s), which would otherwise exceed a
 * plain receiver's execution budget.
 */
class CheckpointReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val isMidnight = intent.action == Scheduler.ACTION_MIDNIGHT

        val onDone = {
            if (isMidnight) Scheduler.scheduleMidnight(context) else Scheduler.schedulePeriodic(context)
            pending.finish()
        }

        if (isMidnight) StepRepository.rolloverMidnight(context, onDone)
        else StepRepository.checkpoint(context, onDone)
    }
}
