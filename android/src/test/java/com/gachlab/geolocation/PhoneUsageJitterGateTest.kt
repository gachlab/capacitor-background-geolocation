// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sensor phone-usage thresholding (mirror of the iOS SensorFusionDetector phone-usage
 * path): sustained accel/gyro jitter held for the window, during an active trip with the
 * app foregrounded, fires once and then respects the cooldown.
 */
@DisplayName("PhoneUsageJitterGate (sensor phone usage)")
class PhoneUsageJitterGateTest {

    private fun gate() = PhoneUsageJitterGate(phoneUsageWindowMs = 4_000L, phoneUsageCooldownMs = 60_000L)

    private val accelAbove = PhoneUsageJitterGate.ACCEL_JITTER_G        // 0.5 g
    private val gyroAbove = PhoneUsageJitterGate.GYRO_JITTER_RADS       // 0.7 rad/s

    @Test
    @DisplayName("fires only after jitter is sustained for the full window")
    fun sustainedWindow() {
        val g = gate()
        // First qualifying sample only starts the window.
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 0L))
        // Still inside the window — no fire yet.
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 2_000L))
        // Window elapsed → fires.
        assertTrue(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 4_000L))
    }

    @Test
    @DisplayName("gyro jitter alone qualifies (OR with accel)")
    fun gyroAloneQualifies() {
        val g = gate()
        assertFalse(g.evaluate(0.0, gyroAbove, tripActive = true, appActive = true, now = 0L))
        assertTrue(g.evaluate(0.0, gyroAbove, tripActive = true, appActive = true, now = 5_000L))
    }

    @Test
    @DisplayName("a sub-threshold sample resets the window")
    fun calmResetsWindow() {
        val g = gate()
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 0L))
        // Calm sample resets the jitter window.
        assertFalse(g.evaluate(0.1, 0.1, tripActive = true, appActive = true, now = 1_000L))
        // Window restarts here; not yet elapsed at +3s.
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 1_000L))
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 4_000L))
        // Elapsed relative to the restart (1_000) → fires.
        assertTrue(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 5_000L))
    }

    @Test
    @DisplayName("does not fire outside a trip or with the app backgrounded")
    fun gatedByTripAndForeground() {
        val g = gate()
        // Not in a trip: never accumulates.
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = false, appActive = true, now = 0L))
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = false, appActive = true, now = 5_000L))
        // App backgrounded: never accumulates.
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = false, now = 6_000L))
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = false, now = 11_000L))
    }

    @Test
    @DisplayName("backgrounding mid-window cancels the pending fire")
    fun backgroundCancelsWindow() {
        val g = gate()
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 0L))
        // App goes to background before the window elapses → resets.
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = false, now = 2_000L))
        // Back to foreground restarts the window; not elapsed yet.
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 3_000L))
        assertFalse(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 6_000L))
        assertTrue(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 7_000L))
    }

    @Test
    @DisplayName("cooldown suppresses a second fire within the window")
    fun cooldown() {
        val g = gate()
        g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 0L)
        assertTrue(g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 4_000L), "first fires")
        // New sustained window opens, but still inside the 60 s cooldown → suppressed.
        g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 10_000L)
        assertFalse(
            g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 20_000L),
            "within cooldown is suppressed",
        )
        // That window stays open; once the cooldown (last fire +60 s = 64 000) clears, the
        // next qualifying sample fires.
        assertTrue(
            g.evaluate(accelAbove, 0.0, tripActive = true, appActive = true, now = 64_001L),
            "after cooldown fires again",
        )
    }
}
