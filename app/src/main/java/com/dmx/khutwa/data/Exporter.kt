package com.dmx.khutwa.data

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * CSV export and restore.
 *
 * The history this protects exists nowhere else — an uninstall currently
 * destroys it — so export writes the full daily table plus the intraday
 * minutes, not just the headline totals.
 */
object Exporter {

    fun exportCsv(context: Context, onDone: (File?) -> Unit) {
        StepRepository.query(context, { dao ->
            val days = dao.recentDays(3650)
            val dir = File(context.cacheDir, "export").apply { mkdirs() }
            val file = File(dir, "khutwa-history.csv")
            file.bufferedWriter().use { w ->
                w.appendLine(
                    "date,steps,walk,brisk,run,incidental,distance_m,kcal," +
                        "active_minutes,floors,elev_gain_m,peak30,peak1,goal"
                )
                for (d in days.sortedBy { it.date }) {
                    w.appendLine(
                        listOf(
                            d.date, d.steps, d.walkSteps, d.briskSteps, d.runSteps,
                            d.incidentalSteps, "%.1f".format(d.distanceM),
                            "%.1f".format(d.kcalActive), d.activeMinutes,
                            "%.2f".format(d.floors), "%.1f".format(d.elevGainM),
                            d.peak30Cadence, d.peak1Cadence, d.goal,
                        ).joinToString(",")
                    )
                }
            }
            file
        }, onDone)
    }

    fun exportMinutesCsv(context: Context, onDone: (File?) -> Unit) {
        StepRepository.query(context, { dao ->
            val days = dao.recentDays(3650).map { it.date }
            val dir = File(context.cacheDir, "export").apply { mkdirs() }
            val file = File(dir, "khutwa-minutes.csv")
            file.bufferedWriter().use { w ->
                w.appendLine("date,minute_epoch,steps,class,altitude_m")
                for (date in days.sorted()) {
                    for (m in dao.minutesFor(date)) {
                        w.appendLine(
                            "${m.date},${m.tsMin},${m.steps}," +
                                "${m.activityClass.id},${"%.1f".format(m.altM)}"
                        )
                    }
                }
            }
            file
        }, onDone)
    }

    /** Hands the file to a share sheet; the app itself never uploads anything. */
    fun share(context: Context, file: File, mime: String = "text/csv") {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, "تصدير بيانات خطوة")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
