// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

/**
 * Whether the server has been telling us, for long enough to believe it, that
 * the thing we are posting for no longer exists.
 *
 * A 404 is the one code where the server answered and the answer was that this
 * shift is gone. Every other failure is either "the backend is having a bad
 * time" or "there is no network right now", and both of those are things a
 * moving vehicle does constantly. Retiring a shift on those would re-break
 * exactly what the out-of-process reviver was built for (#54): tracking that
 * survives a tunnel.
 *
 * The clock exists because one 404 can race a legitimate shift change. A minute
 * of nothing but 404s cannot.
 *
 * WHY THIS IS NOT A COUNTER. At the configured interval a runaway shift POSTs
 * every few seconds, so "N consecutive rejections" and "a minute of rejections"
 * look identical there — but they diverge badly when posting is slow or bursty,
 * and it is the elapsed time, not the attempt count, that makes the evidence
 * convincing. Two rejections a minute apart is the weakest case that should
 * count, and it does.
 */
internal object ShiftGoneDetector {

    /** How long the server has to keep saying 404 before we believe it. */
    const val WINDOW_MS = 60_000L

    const val HTTP_NOT_FOUND = 404

    /** When the current unbroken run of 404s began; 0 when there is no run. */
    private var firstSeenAt = 0L

    /**
     * Feed one HTTP response code in and get back whether tracking should be
     * retired.
     *
     * The three-way split is the whole design, and the middle case is the one
     * that is easy to get wrong:
     *
     *  · **2xx** — the server just accepted a position for this shift, so the
     *    shift exists. That is the only thing that can prove it, and it clears
     *    the run.
     *  · **404** — evidence. The first one only starts the clock.
     *  · **anything else** — 5xx, 401, or the `-1` this codebase uses for "the
     *    request never completed". These say nothing about whether the shift
     *    exists, so they neither count as evidence nor destroy it. Resetting on
     *    them would be a real bug: a backend that 404s, drops off the network
     *    for ten minutes, and 404s again has not changed its answer, and a
     *    driver in and out of coverage could otherwise never accumulate a full
     *    minute.
     *
     * Returns true only on the transition, and keeps returning true for as long
     * as the run continues — callers retire once and then stop calling.
     */
    @Synchronized
    fun observe(code: Int, now: Long = System.currentTimeMillis()): Boolean {
        if (code in 200..299) { firstSeenAt = 0L; return false }
        if (code != HTTP_NOT_FOUND) return false
        if (firstSeenAt == 0L) { firstSeenAt = now; return false }
        return now - firstSeenAt >= WINDOW_MS
    }

    /**
     * Forget the current run.
     *
     * Called when a shift starts, which is what keeps this in-memory state from
     * outliving the thing it describes: a new shift must never inherit a minute
     * of 404s belonging to the last one.
     */
    @Synchronized
    fun reset() { firstSeenAt = 0L }

    /** Test seam. There is no other way to observe the clock without waiting. */
    @Synchronized
    internal fun startedAt(): Long = firstSeenAt
}
