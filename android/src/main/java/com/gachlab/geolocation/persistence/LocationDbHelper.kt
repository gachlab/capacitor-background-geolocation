// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.persistence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

internal class LocationDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        Log.d(TAG, "Creating $DB_NAME v$DB_VERSION")
        db.execSafe(SQL_CREATE_LOCATION)
        db.execSafe(SQL_CREATE_CONFIG)
        db.execSafe(SQL_CREATE_SESSION)
        db.execSafe(SQL_IDX_LOCATION_TIME)
        db.execSafe(SQL_IDX_LOCATION_BATCH)
        db.execSafe(SQL_IDX_SESSION_TIME)
        db.execSafe(SQL_CREATE_LOGS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // The gachlab DB starts at v1 — it has no historical schemas to migrate from
        // (it superseded the legacy Cordova `cordova_bg_geolocation.db`, whose
        // migration history was dropped). Future schema bumps add additive ALTER
        // branches here. The idempotent CREATE below is a forward-compat safety net.
        Log.d(TAG, "Upgrading $DB_NAME from $oldVersion to $newVersion")
        db.execSafe(SQL_CREATE_LOGS)
    }

    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "Downgrade from $oldVersion → dropping and recreating")
        db.execSafe("DROP TABLE IF EXISTS location")
        db.execSafe("DROP TABLE IF EXISTS configuration")
        db.execSafe("DROP TABLE IF EXISTS location_session")
        db.execSafe("DROP TABLE IF EXISTS logs")
        onCreate(db)
    }

    private fun SQLiteDatabase.execSafe(sql: String) {
        Log.d(TAG, sql)
        try { execSQL(sql) } catch (e: Exception) { Log.e(TAG, "execSQL error: ${e.message}") }
    }

    companion object {
        private const val TAG = "LocationDbHelper"
        const val DB_NAME = "gachlab_bg_geolocation.db"
        private const val DB_VERSION = 1

        @Volatile private var instance: LocationDbHelper? = null

        fun getInstance(context: Context): LocationDbHelper =
            instance ?: synchronized(this) {
                instance ?: LocationDbHelper(context).also { instance = it }
            }

        // ── DDL ──────────────────────────────────────────────────────────────────

        private const val SQL_CREATE_LOCATION = """
            CREATE TABLE location (
                _id INTEGER PRIMARY KEY,
                time INTEGER,
                accuracy REAL,
                vertical_accuracy REAL,
                speed REAL,
                bearing REAL,
                altitude REAL,
                latitude REAL,
                longitude REAL,
                radius REAL,
                has_accuracy INTEGER,
                has_vertical_accuracy INTEGER,
                has_speed INTEGER,
                has_bearing INTEGER,
                has_altitude INTEGER,
                has_radius INTEGER,
                provider TEXT,
                service_provider INTEGER,
                valid INTEGER,
                batch_start INTEGER,
                mock_flags INTEGER,
                events_json TEXT,
                battery_level INTEGER,
                is_charging INTEGER
            )"""

        // Config is persisted as a single JSON blob (GachConfigMapper). The legacy
        // per-column storage was removed along with the Cordova DB.
        private const val SQL_CREATE_CONFIG = """
            CREATE TABLE configuration (
                _id INTEGER PRIMARY KEY,
                config_json TEXT
            )"""

        private const val SQL_CREATE_SESSION = """
            CREATE TABLE location_session (
                _id INTEGER PRIMARY KEY AUTOINCREMENT,
                time INTEGER,
                accuracy REAL,
                vertical_accuracy REAL,
                speed REAL,
                bearing REAL,
                altitude REAL,
                latitude REAL,
                longitude REAL,
                radius REAL,
                has_accuracy INTEGER,
                has_vertical_accuracy INTEGER,
                has_speed INTEGER,
                has_bearing INTEGER,
                has_altitude INTEGER,
                has_radius INTEGER,
                provider TEXT,
                service_provider INTEGER,
                valid INTEGER,
                batch_start INTEGER,
                mock_flags INTEGER
            )"""

        // Mirrors the iOS `logs` table (SQLiteHelper). IF NOT EXISTS so it is safe
        // to run from both onCreate and onUpgrade.
        private const val SQL_CREATE_LOGS = """
            CREATE TABLE IF NOT EXISTS logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER,
                level INTEGER,
                msg TEXT,
                stack_trace TEXT
            )"""

        private const val SQL_IDX_LOCATION_TIME  = "CREATE INDEX time_idx ON location (time)"
        private const val SQL_IDX_LOCATION_BATCH = "CREATE INDEX batch_id_idx ON location (batch_start)"
        private const val SQL_IDX_SESSION_TIME   = "CREATE INDEX session_time_idx ON location_session (time)"
    }
}
