// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.persistence

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

/**
 * Reads and writes persisted plugin log entries (`logs` table).
 *
 * The write side is exercised by [com.gachlab.geolocation.BGLog]; the read side
 * backs `getLogEntries`. Output dictionaries follow the cross-platform `LogEntry`
 * contract (`src/definitions.ts`): `id`, `timestamp` (ms), `level` (string),
 * `message`, `stackTrace`.
 */
internal class LogDAO(context: Context) {

    private val db: SQLiteDatabase = LocationDbHelper.getInstance(context).writableDatabase

    /** Append a log row, trimming the table to [MAX_ROWS] newest entries. */
    fun insert(level: Int, message: String, stackTrace: String?, timestamp: Long) {
        db.insert("logs", null, ContentValues().apply {
            put("created_at", timestamp)
            put("level",      level)
            put("msg",        message)
            put("stack_trace", stackTrace)
        })
        // Bound growth: drop the oldest rows once over the cap.
        val count = DatabaseUtils.queryNumEntries(db, "logs")
        if (count > MAX_ROWS) {
            db.execSQL(
                "DELETE FROM logs WHERE id IN " +
                "(SELECT id FROM logs ORDER BY id ASC LIMIT ?)",
                arrayOf((count - MAX_ROWS).toString())
            )
        }
    }

    /**
     * Newest-first entries with `level >= minLevel`, optionally only those with
     * `id < fromId`, capped at [limit].
     */
    fun getEntries(limit: Int, fromId: Int, minLevel: String): List<JSONObject> {
        val minLevelInt = LogLevels.toInt(minLevel)
        val result = mutableListOf<JSONObject>()

        val sql: String
        val args: Array<String>
        if (fromId > 0) {
            sql = "SELECT id, created_at, level, msg, stack_trace FROM logs " +
                  "WHERE level >= ? AND id < ? ORDER BY id DESC LIMIT ?"
            args = arrayOf(minLevelInt.toString(), fromId.toString(), limit.toString())
        } else {
            sql = "SELECT id, created_at, level, msg, stack_trace FROM logs " +
                  "WHERE level >= ? ORDER BY id DESC LIMIT ?"
            args = arrayOf(minLevelInt.toString(), limit.toString())
        }

        db.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) {
                result.add(JSONObject().apply {
                    put("id",         c.getLong(0))
                    put("timestamp",  c.getLong(1))
                    put("level",      LogLevels.toLevel(c.getInt(2)))
                    put("message",    if (c.isNull(3)) "" else c.getString(3))
                    put("stackTrace", if (c.isNull(4)) "" else c.getString(4))
                })
            }
        }
        return result
    }

    companion object {
        /** Maximum number of log rows retained on disk. */
        const val MAX_ROWS = 10_000L
    }
}

/**
 * Pure mapping between the [com.gachlab.geolocation] string log levels and the
 * integer column stored in the `logs` table. Kept identical to iOS
 * `LogReader.levelInt` / `levelString`.
 */
internal object LogLevels {
    const val TRACE = 0
    const val DEBUG = 0
    const val INFO  = 1
    const val WARN  = 2
    const val ERROR = 3

    fun toInt(level: String): Int = when (level.uppercase()) {
        "TRACE", "DEBUG" -> 0
        "INFO"           -> 1
        "WARN"           -> 2
        "ERROR"          -> 3
        else             -> 0
    }

    fun toLevel(value: Int): String = when (value) {
        0 -> "DEBUG"
        1 -> "INFO"
        2 -> "WARN"
        3 -> "ERROR"
        else -> "DEBUG"
    }
}
