// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import SQLite3

final class ConfigDAO {
    static let shared = ConfigDAO()

    private var db: OpaquePointer? { SQLiteHelper.shared.db }

    private init() {}

    // MARK: - Write

    func persist(_ config: BGConfig) {
        guard let db = db else { return }

        let httpHeadersJSON = jsonString(from: config.httpHeaders)
        let queryParamsJSON = jsonString(from: config.queryParams)
        let templateJSON: String? = {
            guard let t = config.template else { return nil }
            return jsonString(fromAny: t)
        }()
        let drivingEventsJSON: String? = {
            guard let d = config.drivingEvents else { return nil }
            return jsonString(fromAny: d)
        }()

        let sql = """
            INSERT OR REPLACE INTO configuration (
                id,
                stationary_radius, distance_filter, desired_accuracy, debug,
                activity_type, activities_interval, stop_on_terminate,
                url, sync_url, sync_threshold, sync_enabled,
                http_headers_json, http_method, sync_http_method,
                http_mode, sync_mode, query_params_json,
                shows_bg_indicator, heartbeat_interval, mock_location_policy,
                driving_events_json, include_battery, activity_confidence_threshold,
                max_accepted_accuracy, save_battery_on_background, max_locations,
                pause_location_updates, location_provider, template_json
            ) VALUES (
                1,
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?
            )
        """

        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return }
        defer { sqlite3_finalize(stmt) }

        bindOptionalDouble(stmt, 1, config.stationaryRadius)
        bindOptionalDouble(stmt, 2, config.distanceFilter)
        bindOptionalDouble(stmt, 3, config.desiredAccuracy)
        bindOptionalBool(stmt, 4, config.debug)
        bindOptionalText(stmt, 5, config.activityType)
        bindOptionalDouble(stmt, 6, config.activitiesInterval)
        bindOptionalBool(stmt, 7, config.stopOnTerminate)
        bindOptionalText(stmt, 8, config.url)
        bindOptionalText(stmt, 9, config.syncUrl)
        bindOptionalInt(stmt, 10, config.syncThreshold)
        bindOptionalBool(stmt, 11, config.syncEnabled)
        bindOptionalText(stmt, 12, httpHeadersJSON)
        bindOptionalText(stmt, 13, config.httpMethod)
        bindOptionalText(stmt, 14, config.syncHttpMethod)
        bindOptionalText(stmt, 15, config.httpMode)
        bindOptionalText(stmt, 16, config.syncMode)
        bindOptionalText(stmt, 17, queryParamsJSON)
        bindOptionalBool(stmt, 18, config.showsBackgroundLocationIndicator)
        bindOptionalInt(stmt, 19, config.heartbeatInterval)
        bindOptionalText(stmt, 20, config.mockLocationPolicy)
        bindOptionalText(stmt, 21, drivingEventsJSON)
        bindOptionalBool(stmt, 22, config.includeBattery)
        bindOptionalInt(stmt, 23, config.activityConfidenceThreshold)
        bindOptionalDouble(stmt, 24, config.maxAcceptedAccuracy)
        bindOptionalBool(stmt, 25, config.saveBatteryOnBackground)
        bindOptionalInt(stmt, 26, config.maxLocations)
        bindOptionalBool(stmt, 27, config.pauseLocationUpdates)
        bindOptionalInt(stmt, 28, config.locationProvider)
        bindOptionalText(stmt, 29, templateJSON)

        sqlite3_step(stmt)
    }

    // MARK: - Read

    func retrieve() -> BGConfig? {
        guard let db = db else { return nil }

        let sql = "SELECT * FROM configuration WHERE id = 1"
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return nil }
        defer { sqlite3_finalize(stmt) }

        guard sqlite3_step(stmt) == SQLITE_ROW else { return nil }

        // Column index mapping (id=0 skipped):
        // 1: stationary_radius, 2: distance_filter, 3: desired_accuracy, 4: debug
        // 5: activity_type, 6: activities_interval, 7: stop_on_terminate
        // 8: url, 9: sync_url, 10: sync_threshold, 11: sync_enabled
        // 12: http_headers_json, 13: http_method, 14: sync_http_method
        // 15: http_mode, 16: sync_mode, 17: query_params_json
        // 18: shows_bg_indicator, 19: heartbeat_interval, 20: mock_location_policy
        // 21: driving_events_json, 22: include_battery, 23: activity_confidence_threshold
        // 24: max_accepted_accuracy, 25: save_battery_on_background, 26: max_locations
        // 27: pause_location_updates, 28: location_provider, 29: template_json

        var dict: [String: Any] = [:]

        setDouble(&dict, stmt, 1, key: "stationaryRadius")
        setDouble(&dict, stmt, 2, key: "distanceFilter")
        setDouble(&dict, stmt, 3, key: "desiredAccuracy")
        setBool(&dict, stmt, 4, key: "debug")
        setText(&dict, stmt, 5, key: "activityType")
        setDouble(&dict, stmt, 6, key: "activitiesInterval")
        setBool(&dict, stmt, 7, key: "stopOnTerminate")
        setText(&dict, stmt, 8, key: "url")
        setText(&dict, stmt, 9, key: "syncUrl")
        setInt(&dict, stmt, 10, key: "syncThreshold")
        setBool(&dict, stmt, 11, key: "syncEnabled")

        // http_headers_json -> httpHeaders
        if let headers = jsonDict(from: textColumn(stmt, 12)) {
            dict["httpHeaders"] = headers
        }

        setText(&dict, stmt, 13, key: "httpMethod")
        setText(&dict, stmt, 14, key: "syncHttpMethod")
        setText(&dict, stmt, 15, key: "httpMode")
        setText(&dict, stmt, 16, key: "syncMode")

        // query_params_json -> queryParams
        if let params = jsonDict(from: textColumn(stmt, 17)) {
            dict["queryParams"] = params
        }

        setBool(&dict, stmt, 18, key: "showsBackgroundLocationIndicator")
        setInt(&dict, stmt, 19, key: "heartbeatInterval")
        setText(&dict, stmt, 20, key: "mockLocationPolicy")

        // driving_events_json -> drivingEvents
        if let drivingEventsStr = textColumn(stmt, 21),
           let data = drivingEventsStr.data(using: .utf8),
           let parsed = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: Any] {
            dict["drivingEvents"] = parsed
        }

        setBool(&dict, stmt, 22, key: "includeBattery")
        setInt(&dict, stmt, 23, key: "activityConfidenceThreshold")
        setDouble(&dict, stmt, 24, key: "maxAcceptedAccuracy")
        setBool(&dict, stmt, 25, key: "saveBatteryOnBackground")
        setInt(&dict, stmt, 26, key: "maxLocations")
        setBool(&dict, stmt, 27, key: "pauseLocationUpdates")
        setInt(&dict, stmt, 28, key: "locationProvider")

        // template_json -> template
        if let templateStr = textColumn(stmt, 29),
           let data = templateStr.data(using: .utf8),
           let parsed = try? JSONSerialization.jsonObject(with: data, options: []) {
            dict["template"] = parsed
        }

        return BGConfig.from(dictionary: dict)
    }

    // MARK: - Column helpers

    private func textColumn(_ stmt: OpaquePointer?, _ idx: Int32) -> String? {
        guard sqlite3_column_type(stmt, idx) != SQLITE_NULL,
              let cStr = sqlite3_column_text(stmt, idx) else { return nil }
        return String(cString: cStr)
    }

    private func setText(_ dict: inout [String: Any], _ stmt: OpaquePointer?, _ idx: Int32, key: String) {
        if let v = textColumn(stmt, idx) { dict[key] = v }
    }

    private func setDouble(_ dict: inout [String: Any], _ stmt: OpaquePointer?, _ idx: Int32, key: String) {
        guard sqlite3_column_type(stmt, idx) != SQLITE_NULL else { return }
        dict[key] = sqlite3_column_double(stmt, idx)
    }

    private func setInt(_ dict: inout [String: Any], _ stmt: OpaquePointer?, _ idx: Int32, key: String) {
        guard sqlite3_column_type(stmt, idx) != SQLITE_NULL else { return }
        dict[key] = Int(sqlite3_column_int(stmt, idx))
    }

    private func setBool(_ dict: inout [String: Any], _ stmt: OpaquePointer?, _ idx: Int32, key: String) {
        guard sqlite3_column_type(stmt, idx) != SQLITE_NULL else { return }
        dict[key] = sqlite3_column_int(stmt, idx) != 0
    }

    // MARK: - JSON helpers

    private func jsonString(from dict: [String: String]?) -> String? {
        guard let d = dict,
              let data = try? JSONSerialization.data(withJSONObject: d, options: []) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func jsonString(fromAny value: Any) -> String? {
        guard JSONSerialization.isValidJSONObject(value),
              let data = try? JSONSerialization.data(withJSONObject: value, options: []) else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func jsonDict(from str: String?) -> [String: String]? {
        guard let s = str,
              let data = s.data(using: .utf8),
              let parsed = try? JSONSerialization.jsonObject(with: data, options: []) as? [String: String] else {
            return nil
        }
        return parsed
    }

    // MARK: - Bind helpers

    private func bindOptionalDouble(_ stmt: OpaquePointer?, _ idx: Int32, _ value: Double?) {
        if let v = value { sqlite3_bind_double(stmt, idx, v) } else { sqlite3_bind_null(stmt, idx) }
    }

    private func bindOptionalInt(_ stmt: OpaquePointer?, _ idx: Int32, _ value: Int?) {
        if let v = value { sqlite3_bind_int(stmt, idx, Int32(v)) } else { sqlite3_bind_null(stmt, idx) }
    }

    private func bindOptionalText(_ stmt: OpaquePointer?, _ idx: Int32, _ value: String?) {
        if let v = value {
            sqlite3_bind_text(stmt, idx, (v as NSString).utf8String, -1, nil)
        } else {
            sqlite3_bind_null(stmt, idx)
        }
    }

    private func bindOptionalBool(_ stmt: OpaquePointer?, _ idx: Int32, _ value: Bool?) {
        if let v = value { sqlite3_bind_int(stmt, idx, v ? 1 : 0) } else { sqlite3_bind_null(stmt, idx) }
    }
}
