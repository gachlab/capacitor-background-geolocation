// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import SQLite3

final class SessionDAO {
    static let shared = SessionDAO()

    private var db: OpaquePointer? { SQLiteHelper.shared.db }

    private static let sessionActiveKey = "bg_session_active"

    private init() {}

    // MARK: - Session state

    var isSessionActive: Bool {
        get { UserDefaults.standard.bool(forKey: SessionDAO.sessionActiveKey) }
        set { UserDefaults.standard.set(newValue, forKey: SessionDAO.sessionActiveKey) }
    }

    // MARK: - Session lifecycle

    func startSession() {
        clearTable()
        isSessionActive = true
    }

    func clearSession() {
        clearTable()
        isSessionActive = false
    }

    private func clearTable() {
        SQLiteHelper.shared.execute("DELETE FROM session_locations")
    }

    // MARK: - Write

    func persistLocation(_ location: BGLocation) {
        guard let db = db else { return }

        let sql = """
            INSERT INTO session_locations (
                time, accuracy, altitude_accuracy, speed, bearing, altitude,
                latitude, longitude, provider, location_provider, recorded_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """

        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return }
        defer { sqlite3_finalize(stmt) }

        bindOptionalInt64(stmt, 1, location.time.map { Int64($0.timeIntervalSince1970 * 1000) })
        bindOptionalDouble(stmt, 2, location.accuracy)
        bindOptionalDouble(stmt, 3, location.altitudeAccuracy)
        bindOptionalDouble(stmt, 4, location.speed)
        bindOptionalDouble(stmt, 5, location.heading)
        bindOptionalDouble(stmt, 6, location.altitude)

        if let lat = location.latitude {
            sqlite3_bind_double(stmt, 7, lat)
        } else {
            sqlite3_bind_null(stmt, 7)
        }
        if let lon = location.longitude {
            sqlite3_bind_double(stmt, 8, lon)
        } else {
            sqlite3_bind_null(stmt, 8)
        }

        bindOptionalText(stmt, 9, location.provider)
        bindOptionalInt(stmt, 10, location.locationProvider)
        bindOptionalInt64(stmt, 11, location.recordedAt.map { Int64($0.timeIntervalSince1970 * 1000) })

        sqlite3_step(stmt)
    }

    // MARK: - Read

    func getLocations() -> [BGLocation] {
        guard let db = db else { return [] }

        var result: [BGLocation] = []
        let sql = "SELECT * FROM session_locations ORDER BY time ASC"
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return [] }
        defer { sqlite3_finalize(stmt) }

        while sqlite3_step(stmt) == SQLITE_ROW {
            result.append(rowToLocation(stmt!))
        }
        return result
    }

    func getCount() -> Int {
        guard let db = db else { return 0 }

        let sql = "SELECT COUNT(*) FROM session_locations"
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return 0 }
        defer { sqlite3_finalize(stmt) }

        if sqlite3_step(stmt) == SQLITE_ROW {
            return Int(sqlite3_column_int(stmt, 0))
        }
        return 0
    }

    // MARK: - Row mapping

    private func rowToLocation(_ stmt: OpaquePointer) -> BGLocation {
        let location = BGLocation()

        // id=0 (not stored in BGLocation)

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

        if sqlite3_column_type(stmt, 11) != SQLITE_NULL {
            let ms = sqlite3_column_int64(stmt, 11)
            location.recordedAt = Date(timeIntervalSince1970: Double(ms) / 1000.0)
        }

        return location
    }

    // MARK: - Bind helpers

    private func bindOptionalDouble(_ stmt: OpaquePointer?, _ idx: Int32, _ value: Double?) {
        if let v = value { sqlite3_bind_double(stmt, idx, v) } else { sqlite3_bind_null(stmt, idx) }
    }

    private func bindOptionalInt(_ stmt: OpaquePointer?, _ idx: Int32, _ value: Int?) {
        if let v = value { sqlite3_bind_int(stmt, idx, Int32(v)) } else { sqlite3_bind_null(stmt, idx) }
    }

    private func bindOptionalInt64(_ stmt: OpaquePointer?, _ idx: Int32, _ value: Int64?) {
        if let v = value { sqlite3_bind_int64(stmt, idx, v) } else { sqlite3_bind_null(stmt, idx) }
    }

    private func bindOptionalText(_ stmt: OpaquePointer?, _ idx: Int32, _ value: String?) {
        if let v = value {
            sqlite3_bind_text(stmt, idx, (v as NSString).utf8String, -1, nil)
        } else {
            sqlite3_bind_null(stmt, idx)
        }
    }
}
