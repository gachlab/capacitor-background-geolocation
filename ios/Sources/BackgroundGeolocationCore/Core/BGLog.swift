// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import SQLite3

/**
 Central plugin logger. Mirrors each entry to the console (NSLog) and persists it
 to the `logs` table so `getLogEntries` can return it later — the cross-platform
 counterpart of the Android `BGLog`.

 Writing at the event source (BGFacade lifecycle, the sync delegate) means
 diagnostics survive even when no Capacitor bridge is attached. Wired at
 low-volume, high-signal points (lifecycle, sync, auth, errors) — never
 per-location, to keep the on-disk log bounded.

 iOS folds stack traces into `message` (per the `LogEntry` contract), so the
 `stack_trace` column stays null here and `getLogEntries` returns `stackTrace: ""`.
 */
public final class BGLog {

    public static let shared = BGLog()

    /// Maximum number of log rows retained on disk (matches Android LogDAO.MAX_ROWS).
    private static let maxRows = 10_000

    private var db: OpaquePointer? { SQLiteHelper.shared.db }
    private let lock = NSLock()

    private init() {}

    public func d(_ message: String) { write(0, message) }
    public func i(_ message: String) { write(1, message) }
    public func w(_ message: String) { write(2, message) }
    public func e(_ message: String) { write(3, message) }

    private func write(_ level: Int, _ message: String) {
        NSLog("[BGGeolocation] %@", message)

        lock.lock()
        defer { lock.unlock() }
        guard let db = db else { return }

        let sql = "INSERT INTO logs (created_at, level, msg, stack_trace) VALUES (?, ?, ?, NULL)"
        var stmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, sql, -1, &stmt, nil) == SQLITE_OK else { return }
        defer { sqlite3_finalize(stmt) }

        let ms = Int64(Date().timeIntervalSince1970 * 1000)
        sqlite3_bind_int64(stmt, 1, ms)
        sqlite3_bind_int(stmt, 2, Int32(level))
        sqlite3_bind_text(stmt, 3, (message as NSString).utf8String, -1, nil)
        sqlite3_step(stmt)

        trim(db)
    }

    /// Bound growth: drop the oldest rows once over the cap.
    private func trim(_ db: OpaquePointer) {
        var countStmt: OpaquePointer?
        guard sqlite3_prepare_v2(db, "SELECT COUNT(*) FROM logs", -1, &countStmt, nil) == SQLITE_OK else { return }
        var count = 0
        if sqlite3_step(countStmt) == SQLITE_ROW {
            count = Int(sqlite3_column_int(countStmt, 0))
        }
        sqlite3_finalize(countStmt)

        guard count > BGLog.maxRows else { return }
        let excess = count - BGLog.maxRows
        var delStmt: OpaquePointer?
        let delSQL = "DELETE FROM logs WHERE id IN (SELECT id FROM logs ORDER BY id ASC LIMIT ?)"
        guard sqlite3_prepare_v2(db, delSQL, -1, &delStmt, nil) == SQLITE_OK else { return }
        defer { sqlite3_finalize(delStmt) }
        sqlite3_bind_int(delStmt, 1, Int32(excess))
        sqlite3_step(delStmt)
    }
}
