// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

/// The UMA / L2 shared buffer (Roadmap Fase 2), the iOS twin of the Android
/// `buffer/PositionBuffer`. A thread-safe in-RAM cache of the latest fix, **written
/// through** on every location and read by blocks (heartbeat, SOS, sensor detector, …)
/// instead of re-deriving it or round-tripping the DAO.
///
/// The hub (`BGFacade`) kept the last fix as a private field (L1, reachable only through
/// the hub); `shared` makes it a first-class component blocks read directly — the seam
/// the hub→ports work (Fase 3) builds on. It is *not* write-behind: the disk path stays
/// eager; this is a read/share cache. The in-progress Trip stays L1 in the engine.
public final class PositionBuffer {
    /// Process-wide shared buffer — the L2 instance blocks read/write.
    public static let shared = PositionBuffer()

    private let lock = NSLock()
    private var _lastFix: BGLocation?
    private var _lastFixAtMs: Double = 0

    public init() {}

    /// The most recent fix, or nil if none recorded since the last `clear()`.
    public var lastFix: BGLocation? {
        lock.lock(); defer { lock.unlock() }
        return _lastFix
    }

    /// Seconds (timeIntervalSince1970) when `lastFix` was recorded (0 if none).
    public var lastFixAtMs: Double {
        lock.lock(); defer { lock.unlock() }
        return _lastFixAtMs
    }

    /// Write-through: record `fix` as the latest, stamped at `at` (seconds).
    public func record(_ fix: BGLocation, at: Double) {
        lock.lock(); _lastFix = fix; _lastFixAtMs = at; lock.unlock()
    }

    /// Drop the cached fix (e.g. on a fresh hub lifecycle).
    public func clear() {
        lock.lock(); _lastFix = nil; _lastFixAtMs = 0; lock.unlock()
    }
}
