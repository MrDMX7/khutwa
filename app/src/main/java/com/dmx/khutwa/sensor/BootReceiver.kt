package com.dmx.khutwa.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dmx.khutwa.Scheduler
import com.dmx.khutwa.data.StepRepository

/**
 * Re-establishes the sensor baseline right after boot (the hardware counter
 * resets to 0 on every reboot) and re-arms both alarms, since Android does
 * not carry AlarmManager alarms across a reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        StepRepository.checkpoint(context) {
            Scheduler.scheduleAll(context)
            pending.finish()
        }
    }
}
