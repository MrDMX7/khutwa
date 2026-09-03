package com.dmx.khutwa.tile

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.dmx.khutwa.data.Settings
import com.dmx.khutwa.data.StepRepository
import com.dmx.khutwa.ui.Format

/**
 * Quick Settings tile showing today's step count.
 *
 * Cheaper than the cover screen and, on a foldable, more useful: it's reachable
 * from the notification shade whether the phone is open or closed.
 */
class StepTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        update()
    }

    override fun onClick() {
        super.onClick()
        // Force a hardware read so the tile shows the live number, not the last
        // checkpoint's, then refresh.
        StepRepository.checkpoint(applicationContext) { update() }
    }

    private fun update() {
        val tile = qsTile ?: return
        StepRepository.query(applicationContext, { dao ->
            dao.day(Format.todayIso())
        }) { day ->
            val arabic = Settings.arabicDigits(applicationContext)
            val steps = day?.steps ?: 0L
            val goal = Settings.goal(applicationContext)
            tile.label = "خطوة"
            tile.contentDescription = "عدّاد الخطوات"
            tile.state = if (goal > 0 && steps >= goal) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            runCatching {
                tile.icon = Icon.createWithResource(
                    applicationContext, com.dmx.khutwa.R.drawable.ic_notification
                )
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                tile.subtitle = Format.number(steps, arabic)
            }
            runCatching { tile.updateTile() }
        }
    }
}
