package com.dmx.khutwa.data

import android.content.Context
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Optional root (Magisk) enhancements.
 *
 * The rule this file exists to enforce: **root is never required.** Khutwa's
 * whole design is that correctness depends only on getting to read the
 * hardware counter periodically, and that works on an unrooted phone. Every
 * function here returns a Result the caller can ignore, and nothing in the
 * counting path calls any of them.
 *
 * What root actually buys us, in order of value:
 *  - guaranteeing the alarm chain survives OEM battery management, instead of
 *    asking the user to find the right settings screen;
 *  - a backup that lives outside app-private storage, so uninstalling the app
 *    no longer destroys the history.
 */
object RootBridge {

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "khutwa-root").apply { isDaemon = true }
    }

    data class Result(val ok: Boolean, val output: String)

    @Volatile private var cachedAvailable: Boolean? = null

    /** True if an `su` binary exists at all — checked without invoking it. */
    fun suBinaryPresent(): Boolean = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su",
        "/su/bin/su", "/debug_ramdisk/su",
    ).any { File(it).exists() }

    /**
     * Whether root is actually grantable. The first call prompts the Magisk
     * dialog, so this is only ever invoked from an explicit user action in
     * Settings — never at launch.
     */
    fun checkAvailable(onResult: (Boolean) -> Unit) {
        cachedAvailable?.let { onResult(it); return }
        io.execute {
            val r = exec("id -u")
            val available = r.ok && r.output.trim() == "0"
            cachedAvailable = available
            onResult(available)
        }
    }

    fun invalidate() {
        cachedAvailable = null
    }

    /**
     * Exempt the app from Doze and background restrictions.
     *
     * This is the one-time step that was previously done by hand through an
     * external root shell after install. Doing it in-app means a reinstall
     * doesn't quietly leave the checkpoint chain throttled.
     */
    fun requestBackgroundExemption(context: Context, onResult: (Result) -> Unit) {
        val pkg = context.packageName
        io.execute {
            val r = exec(
                "dumpsys deviceidle whitelist +$pkg; " +
                    "cmd appops set $pkg RUN_ANY_IN_BACKGROUND allow; " +
                    "dumpsys deviceidle whitelist | grep $pkg"
            )
            onResult(r)
        }
    }

    /**
     * Copy the database somewhere an uninstall won't reach.
     *
     * Worth doing even though CSV export exists: the .db keeps the intraday
     * minute buckets and bouts, which a flat CSV of daily totals cannot.
     */
    fun backupDatabase(context: Context, onResult: (Result) -> Unit) {
        val src = context.getDatabasePath(StepDb.NAME).absolutePath
        val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
            .format(java.util.Date())
        val destDir = "/sdcard/Khutwa"
        io.execute {
            // Checkpoint the WAL first, or the copy can miss the newest writes.
            runCatching { StepDb.get(context).writableDatabase.rawQuery("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() } }
            val r = exec(
                "mkdir -p $destDir && " +
                    "cp '$src' '$destDir/khutwa-$stamp.db' && " +
                    "chmod 664 '$destDir/khutwa-$stamp.db' && " +
                    "echo '$destDir/khutwa-$stamp.db'"
            )
            onResult(r)
        }
    }

    /** Steps recorded by another pedometer, for cross-checking our own totals. */
    fun readForeignDb(path: String, onResult: (Result) -> Unit) {
        io.execute { onResult(exec("cat '$path' | wc -c")) }
    }

    // ---- plumbing ---------------------------------------------------------

    private fun exec(command: String): Result = try {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val finished = process.waitFor(15, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            Result(false, "timed out")
        } else {
            Result(process.exitValue() == 0, output.trim())
        }
    } catch (e: Exception) {
        // No su, denied by Magisk, or killed — all the same to the caller.
        Result(false, e.message ?: "root unavailable")
    }
}
