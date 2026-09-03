package com.dmx.khutwa.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dmx.khutwa.Scheduler
import com.dmx.khutwa.data.StepRepository
import com.dmx.khutwa.widget.StepWidgetProvider

/**
 * The self-perpetuating checkpoint loop: each firing re-arms its own alarm.
 *
 * If either chain is ever dropped (force-stop, an exception before re-arming),
 * it stays dropped until the app is opened or the device reboots — so the
 * re-arm happens in a `finally`-equivalent position, on every path.
 */
class CheckpointReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val isMidnight = intent.action == Scheduler.ACTION_MIDNIGHT

        val onDone = {
            if (isMidnight) Scheduler.scheduleMidnight(context) else Scheduler.schedulePeriodic(context)
            runCatching { StepWidgetProvider.refresh(context) }
            pending.finish()
        }

        if (isMidnight) StepRepository.rolloverMidnight(context, onDone)
        else StepRepository.checkpoint(context, onDone)
    }
}
