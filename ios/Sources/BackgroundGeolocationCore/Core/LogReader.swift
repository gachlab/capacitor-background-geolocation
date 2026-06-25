// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import SQLite3

final class LogReader {

    private var db: OpaquePointer? { SQLiteHelper.shared.db }

    init() {}

    func getEntries(limit: Int, fromId: Int, minLevel: String) -> [[String: Any]] {
        let minLevelInt = levelInt(from: minLevel)
        var result = [[String: Any]]()

        guard let db = db else { return result }

        let sql: String
        if fromId > 0 {
            sql = """
                SELECT id, created_at, level, msg, stack_trace
                FROM logs
                WHERE level >= ? AND id < ?
                ORDER BY id DESC
                LIMIT ?
            """
        } else {
            sql = """
                SELECT id, created_at, level, msg, stack_trace
                FROM logs
                WHERE level >= ?
                ORDER BY id DESC
                LIMIT ?
            """
        }

        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return result }
        defer { sqlite3_finalize(stmt) }

        sqlite3_bind_int(stmt, 1, Int32(minLevelInt))
        if fromId > 0 {
            sqlite3_bind_int64(stmt, 2, Int64(fromId))
            sqlite3_bind_int(stmt, 3, Int32(limit))
        } else {
            sqlite3_bind_int(stmt, 2, Int32(limit))
        }

        while sqlite3_step(stmt) == SQLITE_ROW {
            let id        = Int(sqlite3_column_int64(stmt, 0))
            let createdAt = sqlite3_column_int64(stmt, 1)
            let levelInt  = Int(sqlite3_column_int(stmt, 2))
            let msg: String
            if let cStr = sqlite3_column_text(stmt, 3) {
                msg = String(cString: cStr)
            } else {
                msg = ""
            }
            let stackTrace: String
            if let cStr = sqlite3_column_text(stmt, 4) {
                stackTrace = String(cString: cStr)
            } else {
                stackTrace = ""
            }
            // LogEntry contract shape: id, timestamp, level, message, stackTrace.
            result.append([
                "id":         id,
                "timestamp":  createdAt,
                "level":      levelString(from: levelInt),
                "message":    msg,
                "stackTrace": stackTrace
            ])
        }
        return result
    }

    private func levelInt(from string: String) -> Int {
        switch string.uppercased() {
        case "TRACE", "DEBUG": return 0
        case "INFO":           return 1
        case "WARN":           return 2
        case "ERROR":          return 3
        default:               return 0
        }
    }

    private func levelString(from int: Int) -> String {
        switch int {
        case 0: return "DEBUG"
        case 1: return "INFO"
        case 2: return "WARN"
        case 3: return "ERROR"
        default: return "DEBUG"
        }
    }
}
