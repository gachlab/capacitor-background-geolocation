// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The rule that decides whether a shift is dead, and every way it must NOT fire.
 *
 * The incident this comes from is a tablet that spent 38 days posting positions
 * for a shift deleted on 6-jul-2026 — 276 POSTs in 90 seconds, every one
 * answered 404, with the app closed. That is a personal phone tracking somebody
 * who believes it is switched off, so the fix has to be real.
 *
 * But the danger runs both ways, and most of this file guards the other
 * direction: a rule that retires a shift too eagerly is an outage with extra
 * steps, and it would undo the out-of-process reviver (#54), whose entire job is
 * keeping tracking alive through exactly the failures that are NOT 404s.
 *
 * The clock is driven explicitly rather than by sleeping, so these run in
 * microseconds and test the boundary instead of approximating it.
 */
class ShiftGoneDetectorTest {

    private val t0 = 1_000_000L
    private val minute = ShiftGoneDetector.WINDOW_MS

    @Before
    fun setUp() {
        // Process-wide singleton: without this a leftover run from another test
        // decides this one's outcome.
        ShiftGoneDetector.reset()
    }

    // ── It fires ──────────────────────────────────────────────────────────────

    @Test
    fun `a sustained minute of 404 retires the shift`() {
        assertFalse("the first 404 only starts the clock", ShiftGoneDetector.observe(404, t0))
        assertTrue(ShiftGoneDetector.observe(404, t0 + minute))
    }

    @Test
    fun `two rejections a minute apart are enough`() {
        // The weakest case that should count, and the reason this is a clock and
        // not a counter: with a slow posting interval there is no third attempt
        // to wait for, and the evidence is already a minute old.
        assertFalse(ShiftGoneDetector.observe(404, t0))
        assertTrue(ShiftGoneDetector.observe(404, t0 + minute + 1))
    }

    @Test
    fun `a burst of 404s inside the minute does not`() {
        // 276 POSTs in 90 seconds is the measured rate of the real incident, so
        // the volume must not be what decides — only the elapsed time.
        assertFalse(ShiftGoneDetector.observe(404, t0))
        repeat(300) { i ->
            assertFalse(
                "no volume of 404s inside the window may retire a shift",
                ShiftGoneDetector.observe(404, t0 + (i % 59_000).toLong()),
            )
        }
    }

    // ── It does not fire ──────────────────────────────────────────────────────

    @Test
    fun `a single 404 never retires`() {
        // One 404 can race a legitimate shift change.
        assertFalse(ShiftGoneDetector.observe(404, t0))
    }

    @Test
    fun `a success clears the run`() {
        // The only thing that can prove the shift exists.
        assertFalse(ShiftGoneDetector.observe(404, t0))
        assertFalse(ShiftGoneDetector.observe(200, t0 + 1))
        assertFalse(
            "a 404 after a success starts a fresh minute",
            ShiftGoneDetector.observe(404, t0 + minute + 10),
        )
    }

    @Test
    fun `a bad backend never retires a shift`() {
        // 500/502/503 mean the backend is having a bad time, not that the shift
        // is gone. Retiring here would switch off tracking during an outage.
        listOf(500, 502, 503).forEach { code ->
            ShiftGoneDetector.reset()
            assertFalse(ShiftGoneDetector.observe(code, t0))
            assertFalse(
                "HTTP $code must never retire a shift",
                ShiftGoneDetector.observe(code, t0 + minute * 10),
            )
        }
    }

    @Test
    fun `no network never retires a shift`() {
        // -1 is this codebase's "the request never completed" — a tunnel, which
        // is the commonest failure on a moving vehicle and the exact thing the
        // reviver exists to survive.
        assertFalse(ShiftGoneDetector.observe(-1, t0))
        assertFalse(ShiftGoneDetector.observe(-1, t0 + minute * 10))
    }

    @Test
    fun `an expired token never retires a shift`() {
        // 401/403 are a token to refresh, not a missing shift.
        listOf(401, 403).forEach { code ->
            ShiftGoneDetector.reset()
            assertFalse(ShiftGoneDetector.observe(code, t0))
            assertFalse(
                "HTTP $code must never retire a shift",
                ShiftGoneDetector.observe(code, t0 + minute * 10),
            )
        }
    }

    // ── The inconclusive middle ───────────────────────────────────────────────

    @Test
    fun `a network drop mid-run neither counts nor destroys the evidence`() {
        // The case worth being deliberate about. A backend that 404s, drops off
        // the network for ten minutes, and 404s again has not changed its
        // answer — so the clock must keep running. Resetting here would mean a
        // driver moving in and out of coverage could never accumulate a full
        // minute, and the runaway would never stop.
        assertFalse(ShiftGoneDetector.observe(404, t0))
        assertFalse(ShiftGoneDetector.observe(-1, t0 + 1_000))
        assertFalse(ShiftGoneDetector.observe(503, t0 + 2_000))
        assertTrue(
            "the 404 clock survives failures that say nothing about the shift",
            ShiftGoneDetector.observe(404, t0 + minute),
        )
    }

    @Test
    fun `a new shift does not inherit the previous run`() {
        // What `reset()` is for. The detector is in-memory and process-wide, so
        // without the reset in `LocationService.start()` a fresh shift could be
        // retired by evidence belonging to the last one.
        assertFalse(ShiftGoneDetector.observe(404, t0))
        ShiftGoneDetector.reset()
        assertFalse(
            "the clock must start again with the new shift",
            ShiftGoneDetector.observe(404, t0 + minute * 10),
        )
    }

    @Test
    fun `the boundary is inclusive`() {
        assertFalse(ShiftGoneDetector.observe(404, t0))
        assertFalse("one millisecond short must not fire", ShiftGoneDetector.observe(404, t0 + minute - 1))
        assertTrue("exactly the window must fire", ShiftGoneDetector.observe(404, t0 + minute))
    }
}
