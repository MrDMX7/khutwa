package com.dmx.khutwa.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Plain SQLite, same reasoning as Khatra: no Room, no annotation-processor
 * build cost (which matters when the whole toolchain runs on the phone).
 *
 * Held as a process-wide singleton via [get]. v1 opened a fresh helper per
 * query and never closed any of them; with minute-level writes arriving every
 * 60 s that would leak steadily.
 */
class StepDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    companion object {
        const val NAME = "khutwa.db"

        /**
         * v1: days(date, steps) only.
         * v2: per-class breakdown, derived metrics, and the intraday tables.
         * v3: explicit recorded sessions and their GPS routes.
         */
        const val VERSION = 3

        const val TABLE_DAYS = "days"
        const val TABLE_MINUTES = "minutes"
        const val TABLE_BOUTS = "bouts"
        const val TABLE_STRIDE = "stride_samples"
        const val TABLE_SESSIONS = "sessions"
        const val TABLE_ROUTE = "route_points"

        @Volatile private var instance: StepDb? = null

        fun get(context: Context): StepDb =
            instance ?: synchronized(this) {
                instance ?: StepDb(context).also { instance = it }
            }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        // WAL keeps the 60-second minute writes from blocking UI reads.
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_DAYS (
                date TEXT PRIMARY KEY,
                steps INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        upgradeToV2(db)
        upgradeToV3(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v1 shipped with an empty onUpgrade, so a version bump would have
        // silently done nothing. Real migrations from here on.
        if (oldVersion < 2) upgradeToV2(db)
        if (oldVersion < 3) upgradeToV3(db)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Never destroy history on downgrade — the default implementation throws,
        // which is safer than dropping tables, but an explicit no-op is safer still.
    }

    /**
     * Adds the v2 columns and tables. Purely additive: existing `days` rows
     * keep their `steps` value untouched and get zeros everywhere else.
     *
     * Days recorded before v2 therefore have a total but no breakdown. The UI
     * distinguishes that from a genuinely idle day via DayStats.hasBreakdown,
     * so old history renders as "—" rather than a misleading "0 km".
     */
    private fun upgradeToV2(db: SQLiteDatabase) {
        val newColumns = listOf(
            "walk_steps INTEGER NOT NULL DEFAULT 0",
            "brisk_steps INTEGER NOT NULL DEFAULT 0",
            "run_steps INTEGER NOT NULL DEFAULT 0",
            "incidental_steps INTEGER NOT NULL DEFAULT 0",
            "distance_m REAL NOT NULL DEFAULT 0",
            "kcal_active REAL NOT NULL DEFAULT 0",
            "active_minutes INTEGER NOT NULL DEFAULT 0",
            "floors REAL NOT NULL DEFAULT 0",
            "elev_gain_m REAL NOT NULL DEFAULT 0",
            "peak30_cadence INTEGER NOT NULL DEFAULT 0",
            "peak1_cadence INTEGER NOT NULL DEFAULT 0",
            "goal INTEGER NOT NULL DEFAULT 0",
        )
        val existing = columnsOf(db, TABLE_DAYS)
        for (col in newColumns) {
            val name = col.substringBefore(' ')
            if (name !in existing) {
                db.execSQL("ALTER TABLE $TABLE_DAYS ADD COLUMN $col")
            }
        }

        // Sparse: only minutes containing at least one step get a row, so a
        // day costs a few hundred rows rather than 1,440.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MINUTES (
                ts_min INTEGER PRIMARY KEY,
                date TEXT NOT NULL,
                steps INTEGER NOT NULL,
                class INTEGER NOT NULL,
                alt_m REAL NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_minutes_date ON $TABLE_MINUTES(date)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_BOUTS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER NOT NULL,
                steps INTEGER NOT NULL,
                class INTEGER NOT NULL,
                distance_m REAL NOT NULL DEFAULT 0,
                kcal REAL NOT NULL DEFAULT 0,
                avg_cadence INTEGER NOT NULL DEFAULT 0,
                elev_gain_m REAL NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bouts_date ON $TABLE_BOUTS(date)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_STRIDE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ts_ms INTEGER NOT NULL,
                cadence INTEGER NOT NULL,
                distance_m REAL NOT NULL,
                steps INTEGER NOT NULL,
                gps_accuracy REAL NOT NULL
            )
            """.trimIndent()
        )
    }

    /**
     * Explicitly recorded runs, and the GPS trace for each.
     *
     * Distinct from `bouts`, which are auto-detected from cadence after the
     * fact. A session is something the user deliberately started, so it can
     * carry a route, live coaching and a warm-up/cool-down structure that an
     * inferred bout cannot.
     */
    private fun upgradeToV3(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_SESSIONS (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                start_ms INTEGER NOT NULL,
                end_ms INTEGER NOT NULL,
                steps INTEGER NOT NULL DEFAULT 0,
                distance_m REAL NOT NULL DEFAULT 0,
                kcal REAL NOT NULL DEFAULT 0,
                avg_cadence INTEGER NOT NULL DEFAULT 0,
                max_cadence INTEGER NOT NULL DEFAULT 0,
                elev_gain_m REAL NOT NULL DEFAULT 0,
                active_minutes INTEGER NOT NULL DEFAULT 0,
                note TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_date ON $TABLE_SESSIONS(date)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_ROUTE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                ts_ms INTEGER NOT NULL,
                lat REAL NOT NULL,
                lon REAL NOT NULL,
                alt_m REAL NOT NULL DEFAULT 0,
                accuracy_m REAL NOT NULL DEFAULT 0,
                speed_mps REAL NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_route_session ON $TABLE_ROUTE(session_id, ts_ms)")
    }

    private fun columnsOf(db: SQLiteDatabase, table: String): Set<String> {
        val out = mutableSetOf<String>()
        db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndex("name")
            while (c.moveToNext()) out += c.getString(nameIdx)
        }
        return out
    }
}
