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
        Log.d(TAG, "Upgrading $DB_NAME from $oldVersion to $newVersion")
        val stmts = mutableListOf<String>()
        when (oldVersion) {
            10 -> {
                stmts += "ALTER TABLE location ADD COLUMN valid INTEGER"
                stmts += SQL_IDX_LOCATION_TIME
                stmts += "DROP TABLE IF EXISTS configuration"
                stmts += SQL_CREATE_CONFIG
                stmts += alterLocation("radius REAL")
                stmts += alterLocation("has_accuracy INTEGER")
                stmts += alterLocation("has_speed INTEGER")
                stmts += alterLocation("has_bearing INTEGER")
                stmts += alterLocation("has_altitude INTEGER")
                stmts += alterLocation("has_radius INTEGER")
                stmts += alterLocation("batch_start INTEGER")
                stmts += SQL_IDX_LOCATION_BATCH
                stmts += "UPDATE location SET has_accuracy=1,has_speed=1,has_bearing=1,has_altitude=1,has_radius=1"
                stmts += alterConfig("template TEXT")
                stmts += alterLocation("mock_flags INTEGER")
                stmts += alterConfig("notifications_enabled INTEGER")
                stmts += alterLocation("vertical_accuracy REAL")
                stmts += alterLocation("has_vertical_accuracy INTEGER")
                stmts += "UPDATE location SET vertical_accuracy=-1,has_vertical_accuracy=0"
                stmts += alterConfig("notification_sync_title TEXT")
                stmts += alterConfig("notification_sync_text TEXT")
                stmts += alterConfig("notification_sync_completed_text TEXT")
                stmts += alterConfig("notification_sync_failed_text TEXT")
                stmts += alterConfig("sync_enabled INTEGER")
                stmts += alterConfig("show_time INTEGER")
                stmts += alterConfig("show_distance INTEGER")
                stmts += SQL_CREATE_SESSION
                stmts += SQL_IDX_SESSION_TIME
                stmts += alterConfig("config_json TEXT")
                stmts += alterLocation("events_json TEXT")
                stmts += alterLocation("battery_level INTEGER")
                stmts += alterLocation("is_charging INTEGER")
            }
            11 -> {
                stmts += alterLocation("radius REAL")
                stmts += alterLocation("has_accuracy INTEGER")
                stmts += alterLocation("has_speed INTEGER")
                stmts += alterLocation("has_bearing INTEGER")
                stmts += alterLocation("has_altitude INTEGER")
                stmts += alterLocation("has_radius INTEGER")
                stmts += alterLocation("batch_start INTEGER")
                stmts += SQL_IDX_LOCATION_BATCH
                stmts += "UPDATE location SET has_accuracy=1,has_speed=1,has_bearing=1,has_altitude=1,has_radius=1"
                stmts += alterConfig("template TEXT")
                stmts += alterLocation("mock_flags INTEGER")
                stmts += alterConfig("notifications_enabled INTEGER")
                stmts += alterLocation("vertical_accuracy REAL")
                stmts += alterLocation("has_vertical_accuracy INTEGER")
                stmts += "UPDATE location SET vertical_accuracy=-1,has_vertical_accuracy=0"
                stmts += alterConfig("notification_sync_title TEXT")
                stmts += alterConfig("notification_sync_text TEXT")
                stmts += alterConfig("notification_sync_completed_text TEXT")
                stmts += alterConfig("notification_sync_failed_text TEXT")
                stmts += alterConfig("sync_enabled INTEGER")
                stmts += alterConfig("show_time INTEGER")
                stmts += alterConfig("show_distance INTEGER")
                stmts += SQL_CREATE_SESSION
                stmts += SQL_IDX_SESSION_TIME
                stmts += alterConfig("config_json TEXT")
                stmts += alterLocation("events_json TEXT")
                stmts += alterLocation("battery_level INTEGER")
                stmts += alterLocation("is_charging INTEGER")
            }
            12 -> {
                stmts += alterLocation("mock_flags INTEGER")
                stmts += alterConfig("notifications_enabled INTEGER")
                stmts += alterLocation("vertical_accuracy REAL")
                stmts += alterLocation("has_vertical_accuracy INTEGER")
                stmts += "UPDATE location SET vertical_accuracy=-1,has_vertical_accuracy=0"
                stmts += alterConfig("notification_sync_title TEXT")
                stmts += alterConfig("notification_sync_text TEXT")
                stmts += alterConfig("notification_sync_completed_text TEXT")
                stmts += alterConfig("notification_sync_failed_text TEXT")
                stmts += alterConfig("sync_enabled INTEGER")
                stmts += alterConfig("show_time INTEGER")
                stmts += alterConfig("show_distance INTEGER")
                stmts += SQL_CREATE_SESSION
                stmts += SQL_IDX_SESSION_TIME
                stmts += alterConfig("config_json TEXT")
                stmts += alterLocation("events_json TEXT")
                stmts += alterLocation("battery_level INTEGER")
                stmts += alterLocation("is_charging INTEGER")
            }
            13 -> {
                stmts += alterConfig("notifications_enabled INTEGER")
                stmts += alterLocation("vertical_accuracy REAL")
                stmts += alterLocation("has_vertical_accuracy INTEGER")
                stmts += "UPDATE location SET vertical_accuracy=-1,has_vertical_accuracy=0"
                stmts += alterConfig("notification_sync_title TEXT")
                stmts += alterConfig("notification_sync_text TEXT")
                stmts += alterConfig("notification_sync_completed_text TEXT")
                stmts += alterConfig("notification_sync_failed_text TEXT")
                stmts += alterConfig("sync_enabled INTEGER")
                stmts += alterConfig("show_time INTEGER")
                stmts += alterConfig("show_distance INTEGER")
                stmts += SQL_CREATE_SESSION
                stmts += SQL_IDX_SESSION_TIME
                stmts += alterConfig("config_json TEXT")
                stmts += alterLocation("events_json TEXT")
                stmts += alterLocation("battery_level INTEGER")
                stmts += alterLocation("is_charging INTEGER")
            }
            14 -> {
                stmts += alterLocation("vertical_accuracy REAL")
                stmts += alterLocation("has_vertical_accuracy INTEGER")
                stmts += "UPDATE location SET vertical_accuracy=-1,has_vertical_accuracy=0"
                stmts += alterConfig("notification_sync_title TEXT")
                stmts += alterConfig("notification_sync_text TEXT")
                stmts += alterConfig("notification_sync_completed_text TEXT")
                stmts += alterConfig("notification_sync_failed_text TEXT")
                stmts += alterConfig("sync_enabled INTEGER")
                stmts += alterConfig("show_time INTEGER")
                stmts += alterConfig("show_distance INTEGER")
                stmts += SQL_CREATE_SESSION
                stmts += SQL_IDX_SESSION_TIME
                stmts += alterConfig("config_json TEXT")
                stmts += alterLocation("events_json TEXT")
                stmts += alterLocation("battery_level INTEGER")
                stmts += alterLocation("is_charging INTEGER")
            }
            15 -> {
                stmts += alterConfig("notification_sync_title TEXT")
                stmts += alterConfig("notification_sync_text TEXT")
                stmts += alterConfig("notification_sync_completed_text TEXT")
                stmts += alterConfig("notification_sync_failed_text TEXT")
                stmts += alterConfig("sync_enabled INTEGER")
                stmts += alterConfig("show_time INTEGER")
                stmts += alterConfig("show_distance INTEGER")
                stmts += SQL_CREATE_SESSION
                stmts += SQL_IDX_SESSION_TIME
                stmts += alterConfig("config_json TEXT")
                stmts += alterLocation("events_json TEXT")
                stmts += alterLocation("battery_level INTEGER")
                stmts += alterLocation("is_charging INTEGER")
            }
            16 -> {
                stmts += alterConfig("sync_enabled INTEGER")
                stmts += alterConfig("show_time INTEGER")
                stmts += alterConfig("show_distance INTEGER")
                stmts += SQL_CREATE_SESSION
                stmts += SQL_IDX_SESSION_TIME
                stmts += alterConfig("config_json TEXT")
                stmts += alterLocation("events_json TEXT")
                stmts += alterLocation("battery_level INTEGER")
                stmts += alterLocation("is_charging INTEGER")
            }
            17 -> {
                stmts += alterConfig("show_time INTEGER")
                stmts += alterConfig("show_distance INTEGER")
                stmts += SQL_CREATE_SESSION
                stmts += SQL_IDX_SESSION_TIME
                stmts += alterConfig("config_json TEXT")
                stmts += alterLocation("events_json TEXT")
                stmts += alterLocation("battery_level INTEGER")
                stmts += alterLocation("is_charging INTEGER")
            }
            18 -> {
                stmts += SQL_CREATE_SESSION
                stmts += SQL_IDX_SESSION_TIME
                stmts += alterConfig("config_json TEXT")
                stmts += alterLocation("events_json TEXT")
                stmts += alterLocation("battery_level INTEGER")
                stmts += alterLocation("is_charging INTEGER")
            }
            19 -> {
                stmts += alterConfig("config_json TEXT")
                stmts += alterLocation("events_json TEXT")
                stmts += alterLocation("battery_level INTEGER")
                stmts += alterLocation("is_charging INTEGER")
            }
            20 -> {
                stmts += alterLocation("events_json TEXT")
                stmts += alterLocation("battery_level INTEGER")
                stmts += alterLocation("is_charging INTEGER")
            }
            21 -> {
                // v22 adds nothing new — just the gachlab rewrite marker
            }
            22 -> {
                // v23 adds the `logs` table, created idempotently below.
            }
            else -> {
                onDowngrade(db, oldVersion, newVersion)
                return
            }
        }
        stmts.forEach { db.execSafe(it) }
        // v23: `logs` table. Idempotent (CREATE TABLE IF NOT EXISTS) so it covers
        // every upgrade path without threading it through each branch above.
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
        const val DB_NAME = "cordova_bg_geolocation.db"  // must not change — backward compat
        private const val DB_VERSION = 23

        @Volatile private var instance: LocationDbHelper? = null

        fun getInstance(context: Context): LocationDbHelper =
            instance ?: synchronized(this) {
                instance ?: LocationDbHelper(context).also { instance = it }
            }

        private fun alterLocation(col: String) = "ALTER TABLE location ADD COLUMN $col"
        private fun alterConfig(col: String)    = "ALTER TABLE configuration ADD COLUMN $col"

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

        private const val SQL_CREATE_CONFIG = """
            CREATE TABLE configuration (
                _id INTEGER PRIMARY KEY,
                stationary_radius REAL,
                distance_filter INTEGER,
                desired_accuracy INTEGER,
                debugging INTEGER,
                notification_title TEXT,
                notification_text TEXT,
                notification_sync_title TEXT,
                notification_sync_text TEXT,
                notification_sync_completed_text TEXT,
                notification_sync_failed_text TEXT,
                notification_icon_small TEXT,
                notification_icon_large TEXT,
                notification_icon_color TEXT,
                stop_terminate INTEGER,
                stop_still INTEGER,
                start_boot INTEGER,
                start_foreground INTEGER,
                notifications_enabled INTEGER,
                service_provider TEXT,
                interval INTEGER,
                fastest_interval INTEGER,
                activities_interval INTEGER,
                url TEXT,
                sync_url TEXT,
                sync_threshold INTEGER,
                sync_enabled INTEGER,
                http_headers TEXT,
                max_locations INTEGER,
                template TEXT,
                show_time INTEGER,
                show_distance INTEGER,
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
        // to run from both onCreate and every onUpgrade path.
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
