// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #23 — sensor crash thresholding (mirrors the iOS SensorFusionDetector crash logic):
 * an impact must occur during an active trip, exceed `crashImpactG`, and respect the
 * cooldown. The TYPE_LINEAR_ACCELERATION → g conversion is also covered here.
 */
@DisplayName("CrashImpactGate (#23)")
class CrashImpactGateTest {

    @Test
    @DisplayName("linear-acceleration magnitude converts m/s² → g")
    fun magnitudeConversion() {
        // One g straight down.
        assertEquals(1.0, CrashImpactGate.magnitudeG(0f, 0f, 9.80665f), 1e-6)
        // 3-4-0 → 5 m/s² ≈ 0.5097 g.
        assertEquals(5.0 / 9.80665, CrashImpactGate.magnitudeG(3f, 4f, 0f), 1e-6)
    }

    @Test
    @DisplayName("fires only above threshold and only during an active trip")
    fun thresholdAndTrip() {
        val gate = CrashImpactGate(crashImpactG = 3.0, crashCooldownMs = 10_000L)
        assertFalse(gate.evaluate(2.9, tripActive = true, now = 1_000L), "below threshold must not fire")
        assertFalse(gate.evaluate(5.0, tripActive = false, now = 2_000L), "not in a trip must not fire")
        assertTrue(gate.evaluate(3.0, tripActive = true, now = 3_000L), "at threshold during trip should fire")
    }

    @Test
    @DisplayName("cooldown suppresses a second crash within the window")
    fun cooldown() {
        val gate = CrashImpactGate(crashImpactG = 3.0, crashCooldownMs = 10_000L)
        assertTrue(gate.evaluate(4.0, tripActive = true, now = 0L), "first crash fires")
        assertFalse(gate.evaluate(9.0, tripActive = true, now = 5_000L), "within cooldown is suppressed")
        assertTrue(gate.evaluate(4.0, tripActive = true, now = 10_000L), "after cooldown fires again")
    }
}
