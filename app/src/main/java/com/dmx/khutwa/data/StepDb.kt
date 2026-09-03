package com.dmx.khutwa.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Plain SQLite, same reasoning as Khatra: no Room, no annotation-processor build cost. */
class StepDb(context: Context) : SQLiteOpenHelper(context, NAME, null, VERSION) {

    companion object {
        const val NAME = "khutwa.db"
        const val VERSION = 1
        const val TABLE = "days"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                date TEXT PRIMARY KEY,
                steps INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No migrations yet; v1 is the first schema.
    }
}
