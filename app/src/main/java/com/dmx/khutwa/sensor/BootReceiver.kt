package com.dmx.khutwa.sensor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.dmx.khutwa.Scheduler
import com.dmx.khutwa.data.Settings
import com.dmx.khutwa.data.StepDetectorService
import com.dmx.khutwa.data.StepRepository

/**
 * Restores the alarm chain, which does not survive a reboot or an app update.
 *
 * v1 only listened for BOOT_COMPLETED, so alarms stayed dead after every
 * install of a new build until the app was next opened by hand — easy to miss,
 * and it silently costs a day of background counting.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        val pending = goAsync()
        // Re-baseline first: after a reboot the hardware counter has reset, and
        // the checkpoint is what records the new baseline before any steps accrue.
        StepRepository.checkpoint(context) {
            Scheduler.scheduleAll(context)
            if (Settings.isOnboarded(context)) {
                runCatching { StepDetectorService.start(context) }
            }
            pending.finish()
        }
    }
}
