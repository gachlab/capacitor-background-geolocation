// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.buffer

import com.gachlab.geolocation.BGLocation

/**
 * The UMA / L2 shared buffer (Roadmap Fase 2). A thread-safe in-RAM cache of the latest
 * fix, **written through** on every location and read by blocks (heartbeat, SOS, idle,
 * sensor detector, …) instead of re-deriving it or round-tripping the DAO. It is *not* a
 * write-behind cache: the disk path stays eager/durable; this is purely a
 * read/share cache between blocks ("zero-copy" — readers share the same fix reference,
 * no re-serialisation).
 *
 * Where the hub previously kept the last fix as a private field (L1, reachable only
 * through the hub), [shared] makes it a first-class component the other blocks read
 * directly — the seam the hub→ports work (Fase 3) builds on. The in-progress Trip stays
 * L1 in the engine (`DrivingEventsDetector`), per the cache hierarchy.
 */
class PositionBuffer {
    @Volatile private var _lastFix: BGLocation? = null
    @Volatile private var _lastFixAtMs: Long = 0L

    /** The most recent fix, or null if none recorded since the last [clear]. */
    val lastFix: BGLocation? get() = _lastFix

    /** Wall-clock millis when [lastFix] was recorded (0 if none). */
    val lastFixAtMs: Long get() = _lastFixAtMs

    /** Write-through: record [fix] as the latest, stamped at [atMs]. */
    @Synchronized
    fun record(fix: BGLocation, atMs: Long) {
        _lastFix = fix
        _lastFixAtMs = atMs
    }

    /** Drop the cached fix (e.g. on a fresh service lifecycle). */
    @Synchronized
    fun clear() {
        _lastFix = null
        _lastFixAtMs = 0L
    }

    companion object {
        /** Process-wide shared buffer — the L2 instance blocks read/write. */
        val shared = PositionBuffer()
    }
}
