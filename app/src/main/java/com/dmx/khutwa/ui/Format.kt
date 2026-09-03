package com.dmx.khutwa.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Number and date formatting.
 *
 * v1 was inconsistent here in a way that showed: the hero step count rendered
 * with Latin digits via string interpolation while the history rows rendered
 * Arabic-Indic via `Locale("ar")`, so the same number looked different in two
 * places on one screen. Everything goes through here now.
 */
object Format {

    private val arabicIndic = charArrayOf('٠', '١', '٢', '٣', '٤', '٥', '٦', '٧', '٨', '٩')

    fun digits(value: String, arabic: Boolean): String {
        if (!arabic) return value
        val sb = StringBuilder(value.length)
        for (ch in value) {
            sb.append(if (ch in '0'..'9') arabicIndic[ch - '0'] else ch)
        }
        return sb.toString()
    }

    /** Thousands-separated, then optionally converted to Arabic-Indic digits. */
    fun number(value: Long, arabic: Boolean): String =
        digits(String.format(Locale.US, "%,d", value), arabic)

    fun number(value: Int, arabic: Boolean): String = number(value.toLong(), arabic)

    fun km(metres: Double, arabic: Boolean): String =
        digits(String.format(Locale.US, "%.2f", metres / 1000.0), arabic)

    fun decimal(value: Double, places: Int, arabic: Boolean): String =
        digits(String.format(Locale.US, "%.${places}f", value), arabic)

    fun duration(ms: Long, arabic: Boolean): String {
        val totalMin = (ms / 60_000L).toInt()
        val h = totalMin / 60
        val m = totalMin % 60
        return if (h > 0) "${digits(h.toString(), arabic)} س ${digits(m.toString(), arabic)} د"
        else "${digits(m.toString(), arabic)} دقيقة"
    }

    fun clock(ms: Long, arabic: Boolean): String =
        digits(SimpleDateFormat("HH:mm", Locale.US).format(Date(ms)), arabic)

    /** "الخميس ٣ سبتمبر" */
    fun longDate(iso: String, arabic: Boolean): String = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)!!
        val text = SimpleDateFormat("EEEE d MMMM", Locale("ar")).format(d)
        digits(text, arabic)
    } catch (e: Exception) {
        iso
    }

    /** "الخميس ٣ سبتمبر" shortened for list rows. */
    fun shortDate(iso: String, arabic: Boolean): String = try {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso)!!
        digits(SimpleDateFormat("EEE d MMM", Locale("ar")).format(d), arabic)
    } catch (e: Exception) {
        iso
    }

    fun isToday(iso: String): Boolean =
        iso == SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun minuteOfDayNow(): Int {
        val c = Calendar.getInstance()
        return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
    }

    fun todayIso(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

    fun isoDaysAgo(n: Int): String {
        val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -n) }
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }
}
