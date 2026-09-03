package com.dmx.khutwa.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dmx.khutwa.R
import com.dmx.khutwa.data.Settings
import com.dmx.khutwa.data.StepRepository
import com.dmx.khutwa.ui.Format
import com.dmx.khutwa.ui.MainActivity

/**
 * Home-screen widget.
 *
 * Reads the same database as the app rather than keeping its own count, so it
 * can never disagree with the main screen. It refreshes on the system's own
 * schedule and whenever a checkpoint lands, which is frequent enough for a
 * step count and cheap because it is only a database read.
 */
class StepWidgetProvider : AppWidgetProvider() {

    companion object {
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, StepWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            render(context, mgr, ids)
        }

        private fun render(context: Context, mgr: AppWidgetManager, ids: IntArray) {
            StepRepository.query(context, { dao ->
                val today = Format.todayIso()
                dao.day(today)
            }) { day ->
                val arabic = Settings.arabicDigits(context)
                val goal = Settings.goal(context)
                val steps = day?.steps ?: 0L
                val views = RemoteViews(context.packageName, R.layout.widget_steps).apply {
                    setTextViewText(R.id.widget_steps, Format.number(steps, arabic))
                    setTextViewText(R.id.widget_label, "خطوة")
                    setTextViewText(
                        R.id.widget_sub,
                        if (goal > 0) {
                            val pct = (steps * 100 / goal).coerceAtMost(999)
                            "${Format.number(pct, arabic)}٪ من الهدف"
                        } else ""
                    )
                    setOnClickPendingIntent(
                        R.id.widget_steps,
                        PendingIntent.getActivity(
                            context, 0, Intent(context, MainActivity::class.java),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        )
                    )
                }
                for (id in ids) mgr.updateAppWidget(id, views)
            }
        }
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        render(context, mgr, ids)
    }
}
