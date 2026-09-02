// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

/// Whether the server has been telling us, for long enough to believe it, that
/// the thing we are posting for no longer exists.
///
/// The Swift half of #63, and a deliberate transcription of
/// `ShiftGoneDetector.kt` rather than a fresh design: the rule is the contract
/// between the two platforms, and two implementations that drift on what counts
/// as "the shift is gone" would be worse than one platform not having it at all.
///
/// A 404 is the one code where the server answered and the answer was that this
/// shift is gone. Every other failure is either "the backend is having a bad
/// time" or "there is no network right now", and both of those are things a
/// moving vehicle does constantly. Retiring on those would trade a privacy bug
/// for an outage.
///
/// The clock exists because one 404 can race a legitimate shift change. A minute
/// of nothing but 404s cannot.
final class ShiftGoneDetector {

    static let shared = ShiftGoneDetector()

    /// How long the server has to keep saying 404 before we believe it.
    static let windowSeconds: TimeInterval = 60

    static let httpNotFound = 404

    /// When the current unbroken run of 404s began; nil when there is no run.
    private var firstSeenAt: Date?
    private let lock = NSLock()

    private init() {}

    /// Feed one HTTP response code in and get back whether tracking should be
    /// retired.
    ///
    /// The three-way split is the whole design, and the middle case is the one
    /// that is easy to get wrong:
    ///
    /// - **2xx** — the server just accepted a position for this shift, so the
    ///   shift exists. That is the only thing that can prove it, and it clears
    ///   the run.
    /// - **404** — evidence. The first one only starts the clock.
    /// - **anything else** — 5xx, 401, or the `0`/`-1` this code uses when the
    ///   request never produced an HTTP response. These say nothing about
    ///   whether the shift exists, so they neither count as evidence nor destroy
    ///   it. Resetting on them would be a real bug: a backend that 404s, drops
    ///   off the network for ten minutes, and 404s again has not changed its
    ///   answer, and a driver in and out of coverage could otherwise never
    ///   accumulate a full minute.
    ///
    /// Returns true only once the run has outlived the window, and keeps
    /// returning true while it continues — callers retire once and stop asking.
    @discardableResult
    func observe(_ code: Int, now: Date = Date()) -> Bool {
        lock.lock()
        defer { lock.unlock() }

        if (200...299).contains(code) {
            firstSeenAt = nil
            return false
        }
        guard code == Self.httpNotFound else { return false }
        guard let started = firstSeenAt else {
            firstSeenAt = now
            return false
        }
        return now.timeIntervalSince(started) >= Self.windowSeconds
    }

    /// Forget the current run.
    ///
    /// Called when tracking starts, which is what keeps this in-memory state
    /// from outliving the thing it describes: a new shift must never inherit a
    /// minute of 404s belonging to the last one.
    func reset() {
        lock.lock()
        defer { lock.unlock() }
        firstSeenAt = nil
    }

    /// Test seam. There is no other way to observe the clock without waiting.
    var startedAt: Date? {
        lock.lock()
        defer { lock.unlock() }
        return firstSeenAt
    }
}
