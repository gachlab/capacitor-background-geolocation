// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import SQLite3

final class LocationDAO {
    static let shared = LocationDAO()

    private let q = DispatchQueue(label: "com.gachlab.geolocation.locationDAO")

    private var db: OpaquePointer? { SQLiteHelper.shared.db }

    private init() {}

    // MARK: - Write

    func persistLocation(_ location: BGLocation, maxRows: Int = 10_000) {
        q.sync {
            guard let db = self.db else { return }

            // Enforce row cap: delete oldest if at limit
            let countSQL = "SELECT COUNT(*) FROM locations WHERE status != 0"
            var countStmt: OpaquePointer?
            if sqlite3_prepare_v2(db, countSQL, -1, &countStmt, nil) == SQLITE_OK {
                if sqlite3_step(countStmt) == SQLITE_ROW {
                    let count = Int(sqlite3_column_int(countStmt, 0))
                    if count >= maxRows {
                        let deleteOldest = """
                            DELETE FROM locations WHERE id IN (
                                SELECT id FROM locations WHERE status != 0
                                ORDER BY recorded_at ASC
                                LIMIT ?
                            )
                        """
                        var delStmt: OpaquePointer?
                        if sqlite3_prepare_v2(db, deleteOldest, -1, &delStmt, nil) == SQLITE_OK {
                            sqlite3_bind_int(delStmt, 1, Int32(count - maxRows + 1))
                            sqlite3_step(delStmt)
                        }
                        sqlite3_finalize(delStmt)
                    }
                }
            }
            sqlite3_finalize(countStmt)

            // Set recordedAt if not already set
            if location.recordedAt == nil {
                location.recordedAt = Date()
            }

            // Serialize drivingEvents to JSON
            var eventsJSON: String? = nil
            if let events = location.drivingEvents,
               let data = try? JSONSerialization.data(withJSONObject: events, options: []) {
                eventsJSON = String(data: data, encoding: .utf8)
            }

            let sql = """
                INSERT INTO locations (
                    time, accuracy, altitude_accuracy, speed, bearing, altitude,
                    latitude, longitude, provider, location_provider, status,
                    recorded_at, radius, simulated, events_json, battery_level, is_charging
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """

            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else {
                return
            }
            defer { sqlite3_finalize(stmt) }

            bindOptionalInt64(stmt, 1, location.time.map { Int64($0.timeIntervalSince1970 * 1000) })
            bindOptionalDouble(stmt, 2, location.accuracy)
            bindOptionalDouble(stmt, 3, location.altitudeAccuracy)
            bindOptionalDouble(stmt, 4, location.speed)
            bindOptionalDouble(stmt, 5, location.heading)
            bindOptionalDouble(stmt, 6, location.altitude)
            bindOptionalDouble(stmt, 7, location.latitude)
            bindOptionalDouble(stmt, 8, location.longitude)
            bindOptionalText(stmt, 9, location.provider)
            bindOptionalInt(stmt, 10, location.locationProvider)
            sqlite3_bind_int(stmt, 11, Int32(BGLocationStatus.postPending.rawValue))
            bindOptionalInt64(stmt, 12, location.recordedAt.map { Int64($0.timeIntervalSince1970 * 1000) })
            bindOptionalDouble(stmt, 13, location.radius)
            bindOptionalBool(stmt, 14, location.simulated)
            bindOptionalText(stmt, 15, eventsJSON)
            bindOptionalInt(stmt, 16, location.batteryLevel)
            bindOptionalBool(stmt, 17, location.isCharging)

            if sqlite3_step(stmt) == SQLITE_DONE {
                location.locationId = sqlite3_last_insert_rowid(db)
            }
        }
    }

    // MARK: - Read

    func getAllLocations() -> [BGLocation] {
        var result: [BGLocation] = []
        q.sync {
            guard let db = self.db else { return }
            let sql = "SELECT * FROM locations WHERE status != 0 ORDER BY recorded_at ASC"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return }
            defer { sqlite3_finalize(stmt) }
            while sqlite3_step(stmt) == SQLITE_ROW {
                result.append(rowToLocation(stmt!))
            }
        }
        return result
    }

    func getValidLocations() -> [BGLocation] {
        var result: [BGLocation] = []
        q.sync {
            guard let db = self.db else { return }
            let sql = "SELECT * FROM locations WHERE status = 1 ORDER BY recorded_at ASC"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return }
            defer { sqlite3_finalize(stmt) }
            while sqlite3_step(stmt) == SQLITE_ROW {
                result.append(rowToLocation(stmt!))
            }
        }
        return result
    }

    func getLocationsForSync() -> [BGLocation] {
        var result: [BGLocation] = []
        q.sync {
            guard let db = self.db else { return }

            sqlite3_exec(db, "BEGIN TRANSACTION", nil, nil, nil)

            // SELECT pending locations
            let selectSQL = "SELECT * FROM locations WHERE status = 1 ORDER BY recorded_at ASC"
            var selectStmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, selectSQL, -1, &selectStmt, nil) == SQLITE_OK else {
                sqlite3_exec(db, "ROLLBACK", nil, nil, nil)
                return
            }

            var ids: [Int64] = []
            while sqlite3_step(selectStmt) == SQLITE_ROW {
                let loc = rowToLocation(selectStmt!)
                if let lid = loc.locationId {
                    ids.append(lid)
                }
                result.append(loc)
            }
            sqlite3_finalize(selectStmt)

            // UPDATE status to syncPending for those ids
            if !ids.isEmpty {
                let placeholders = ids.map { _ in "?" }.joined(separator: ",")
                let updateSQL = "UPDATE locations SET status = \(BGLocationStatus.syncPending.rawValue) WHERE id IN (\(placeholders))"
                var updateStmt: OpaquePointer?
                if sqlite3_prepare_v2(db, updateSQL, -1, &updateStmt, nil) == SQLITE_OK {
                    for (i, id) in ids.enumerated() {
                        sqlite3_bind_int64(updateStmt, Int32(i + 1), id)
                    }
                    sqlite3_step(updateStmt)
                }
                sqlite3_finalize(updateStmt)
            }

            sqlite3_exec(db, "COMMIT", nil, nil, nil)
        }
        return result
    }

    func getLocationsForSyncCount() -> Int {
        var count = 0
        q.sync {
            guard let db = self.db else { return }
            let sql = "SELECT COUNT(*) FROM locations WHERE status = 1"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return }
            defer { sqlite3_finalize(stmt) }
            if sqlite3_step(stmt) == SQLITE_ROW {
                count = Int(sqlite3_column_int(stmt, 0))
            }
        }
        return count
    }

    // MARK: - Delete / Status updates

    func deleteLocation(id: Int64) throws {
        try q.sync {
            guard let db = self.db else { return }
            let sql = "UPDATE locations SET status = \(BGLocationStatus.deleted.rawValue) WHERE id = ?"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return }
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_int64(stmt, 1, id)
            sqlite3_step(stmt)
        }
    }

    func deleteAllLocations() throws {
        try q.sync {
            guard let db = self.db else { return }
            let sql = "UPDATE locations SET status = \(BGLocationStatus.deleted.rawValue)"
            SQLiteHelper.shared.execute(sql)
        }
    }

    func deleteSyncedLocationsBefore(_ cutoff: Date) {
        q.sync {
            guard let db = self.db else { return }
            let cutoffMs = Int64(cutoff.timeIntervalSince1970 * 1000)
            let sql = "UPDATE locations SET status = \(BGLocationStatus.deleted.rawValue) WHERE status = \(BGLocationStatus.syncPending.rawValue) AND recorded_at <= ?"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return }
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_int64(stmt, 1, cutoffMs)
            sqlite3_step(stmt)
        }
    }

    func restoreFailedSyncLocations() {
        q.sync {
            guard let db = self.db else { return }
            let sql = "UPDATE locations SET status = \(BGLocationStatus.postPending.rawValue) WHERE status = \(BGLocationStatus.syncPending.rawValue)"
            SQLiteHelper.shared.execute(sql)
        }
    }

    /// Restore only the given rows (syncPending → postPending) so a failed upload
    /// retries WITHOUT touching siblings — used by `single` sync mode where N tasks
    /// share one flush and must not clobber each other's rows.
    func restoreLocations(_ ids: [Int64]) {
        q.sync {
            guard let db = self.db, !ids.isEmpty else { return }
            let placeholders = ids.map { _ in "?" }.joined(separator: ",")
            let sql = "UPDATE locations SET status = \(BGLocationStatus.postPending.rawValue) WHERE status = \(BGLocationStatus.syncPending.rawValue) AND id IN (\(placeholders))"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return }
            defer { sqlite3_finalize(stmt) }
            for (i, id) in ids.enumerated() { sqlite3_bind_int64(stmt, Int32(i + 1), id) }
            sqlite3_step(stmt)
        }
    }

    func restoreStaleSyncLocations(olderThan cutoff: Date) {
        q.sync {
            guard let db = self.db else { return }
            let cutoffMs = Int64(cutoff.timeIntervalSince1970 * 1000)
            let sql = "UPDATE locations SET status = \(BGLocationStatus.postPending.rawValue) WHERE status = \(BGLocationStatus.syncPending.rawValue) AND recorded_at < ?"
            var stmt: OpaquePointer?
            guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return }
            defer { sqlite3_finalize(stmt) }
            sqlite3_bind_int64(stmt, 1, cutoffMs)
            sqlite3_step(stmt)
        }
    }

    func deletePendingSyncLocations() {
        q.sync {
            guard let db = self.db else { return }
            let sql = "UPDATE locations SET status = \(BGLocationStatus.deleted.rawValue) WHERE status = \(BGLocationStatus.syncPending.rawValue)"
            SQLiteHelper.shared.execute(sql)
        }
    }

    // MARK: - Row mapping

    private func rowToLocation(_ stmt: OpaquePointer) -> BGLocation {
        let location = BGLocation()

        location.locationId = sqlite3_column_type(stmt, 0) != SQLITE_NULL
            ? sqlite3_column_int64(stmt, 0) : nil

        if sqlite3_column_type(stmt, 1) != SQLITE_NULL {
            let ms = sqlite3_column_int64(stmt, 1)
            location.time = Date(timeIntervalSince1970: Double(ms) / 1000.0)
        }

        location.accuracy = sqlite3_column_type(stmt, 2) != SQLITE_NULL
            ? sqlite3_column_double(stmt, 2) : nil
        location.altitudeAccuracy = sqlite3_column_type(stmt, 3) != SQLITE_NULL
            ? sqlite3_column_double(stmt, 3) : nil
        location.speed = sqlite3_column_type(stmt, 4) != SQLITE_NULL
            ? sqlite3_column_double(stmt, 4) : nil
        location.heading = sqlite3_column_type(stmt, 5) != SQLITE_NULL
            ? sqlite3_column_double(stmt, 5) : nil
        location.altitude = sqlite3_column_type(stmt, 6) != SQLITE_NULL
            ? sqlite3_column_double(stmt, 6) : nil
        location.latitude = sqlite3_column_type(stmt, 7) != SQLITE_NULL
            ? sqlite3_column_double(stmt, 7) : nil
        location.longitude = sqlite3_column_type(stmt, 8) != SQLITE_NULL
            ? sqlite3_column_double(stmt, 8) : nil

        if sqlite3_column_type(stmt, 9) != SQLITE_NULL,
           let cStr = sqlite3_column_text(stmt, 9) {
            location.provider = String(cString: cStr)
        }

        location.locationProvider = sqlite3_column_type(stmt, 10) != SQLITE_NULL
            ? Int(sqlite3_column_int(stmt, 10)) : nil

        // column 11 = status (not stored in BGLocation directly)

        if sqlite3_column_type(stmt, 12) != SQLITE_NULL {
            let ms = sqlite3_column_int64(stmt, 12)
            location.recordedAt = Date(timeIntervalSince1970: Double(ms) / 1000.0)
        }

        location.radius = sqlite3_column_type(stmt, 13) != SQLITE_NULL
            ? sqlite3_column_double(stmt, 13) : nil

        if sqlite3_column_type(stmt, 14) != SQLITE_NULL {
            location.simulated = sqlite3_column_int(stmt, 14) != 0
        }

        if sqlite3_column_type(stmt, 15) != SQLITE_NULL,
           let cStr = sqlite3_column_text(stmt, 15) {
            let jsonStr = String(cString: cStr)
            if let data = jsonStr.data(using: .utf8),
               let parsed = try? JSONSerialization.jsonObject(with: data, options: []) as? [[String: Any]] {
                location.drivingEvents = parsed
            }
        }

        if sqlite3_column_type(stmt, 16) != SQLITE_NULL {
            location.batteryLevel = Int(sqlite3_column_int(stmt, 16))
        }

        if sqlite3_column_type(stmt, 17) != SQLITE_NULL {
            location.isCharging = sqlite3_column_int(stmt, 17) != 0
        }

        return location
    }

    // MARK: - Bind helpers

    private func bindOptionalDouble(_ stmt: OpaquePointer?, _ idx: Int32, _ value: Double?) {
        if let v = value {
            sqlite3_bind_double(stmt, idx, v)
        } else {
            sqlite3_bind_null(stmt, idx)
        }
    }

    private func bindOptionalInt(_ stmt: OpaquePointer?, _ idx: Int32, _ value: Int?) {
        if let v = value {
            sqlite3_bind_int(stmt, idx, Int32(v))
        } else {
            sqlite3_bind_null(stmt, idx)
        }
    }

    private func bindOptionalInt64(_ stmt: OpaquePointer?, _ idx: Int32, _ value: Int64?) {
        if let v = value {
            sqlite3_bind_int64(stmt, idx, v)
        } else {
            sqlite3_bind_null(stmt, idx)
        }
    }

    private func bindOptionalText(_ stmt: OpaquePointer?, _ idx: Int32, _ value: String?) {
        if let v = value {
            sqlite3_bind_text(stmt, idx, (v as NSString).utf8String, -1, nil)
        } else {
            sqlite3_bind_null(stmt, idx)
        }
    }

    private func bindOptionalBool(_ stmt: OpaquePointer?, _ idx: Int32, _ value: Bool?) {
        if let v = value {
            sqlite3_bind_int(stmt, idx, v ? 1 : 0)
        } else {
            sqlite3_bind_null(stmt, idx)
        }
    }
}
