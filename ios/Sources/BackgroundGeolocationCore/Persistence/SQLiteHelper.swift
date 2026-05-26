// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import SQLite3

final class SQLiteHelper {
    static let shared = SQLiteHelper()

    var db: OpaquePointer?

    private init() {
        openDatabase()
        createTables()
    }

    private func openDatabase() {
        guard let appSupportDir = FileManager.default.urls(
            for: .applicationSupportDirectory,
            in: .userDomainMask
        ).first else {
            return
        }

        let dbURL = appSupportDir.appendingPathComponent("gachlab_geo.sqlite")
        let dbPath = dbURL.path

        if sqlite3_open(dbPath, &db) != SQLITE_OK {
            db = nil
        }
    }

    private func createTables() {
        execute("""
            CREATE TABLE IF NOT EXISTS locations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                time INTEGER,
                accuracy REAL,
                altitude_accuracy REAL,
                speed REAL,
                bearing REAL,
                altitude REAL,
                latitude REAL,
                longitude REAL,
                provider TEXT,
                location_provider INTEGER,
                status INTEGER DEFAULT 1,
                recorded_at INTEGER,
                radius REAL,
                simulated INTEGER,
                events_json TEXT,
                battery_level INTEGER,
                is_charging INTEGER
            )
        """)

        execute("""
            CREATE TABLE IF NOT EXISTS configuration (
                id INTEGER PRIMARY KEY,
                stationary_radius REAL,
                distance_filter REAL,
                desired_accuracy REAL,
                debug INTEGER,
                activity_type TEXT,
                activities_interval REAL,
                stop_on_terminate INTEGER,
                url TEXT,
                sync_url TEXT,
                sync_threshold INTEGER,
                sync_enabled INTEGER,
                http_headers_json TEXT,
                http_method TEXT,
                sync_http_method TEXT,
                http_mode TEXT,
                sync_mode TEXT,
                query_params_json TEXT,
                shows_bg_indicator INTEGER,
                heartbeat_interval INTEGER,
                mock_location_policy TEXT,
                driving_events_json TEXT,
                include_battery INTEGER,
                activity_confidence_threshold INTEGER,
                max_accepted_accuracy REAL,
                save_battery_on_background INTEGER,
                max_locations INTEGER,
                pause_location_updates INTEGER,
                location_provider INTEGER,
                template_json TEXT
            )
        """)

        execute("""
            CREATE TABLE IF NOT EXISTS session_locations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                time INTEGER,
                accuracy REAL,
                altitude_accuracy REAL,
                speed REAL,
                bearing REAL,
                altitude REAL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                provider TEXT,
                location_provider INTEGER,
                recorded_at INTEGER
            )
        """)

        execute("""
            CREATE TABLE IF NOT EXISTS logs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_at INTEGER,
                level INTEGER,
                msg TEXT
            )
        """)
    }

    func execute(_ sql: String) {
        guard let db = db else { return }
        var errMsg: UnsafeMutablePointer<CChar>? = nil
        sqlite3_exec(db, sql, nil, nil, &errMsg)
        if let msg = errMsg {
            sqlite3_free(msg)
        }
    }
}
