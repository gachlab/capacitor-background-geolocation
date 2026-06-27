// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("StationaryRegion — movement-engine exit decision")
class StationaryRegionTest {

    @Test
    @DisplayName("adjustedDistance shrinks the raw distance by both accuracies and never goes negative")
    fun adjustedDistance() {
        assertEquals(70f, StationaryRegion.adjustedDistance(rawDistanceMeters = 100f, centerAccuracy = 10f, fixAccuracy = 20f))
        // Inside the combined uncertainty → clamped to a positive magnitude, not signed.
        assertEquals(20f, StationaryRegion.adjustedDistance(rawDistanceMeters = 10f, centerAccuracy = 15f, fixAccuracy = 15f))
    }

    @Test
    @DisplayName("a fix beyond the radius is an EXIT")
    fun exitBeyondRadius() {
        assertEquals(StationaryRegion.Decision.EXIT, StationaryRegion.decide(adjustedDistanceMeters = 60f, radiusMeters = 50f))
    }

    @Test
    @DisplayName("just outside the centre but within the radius polls fast")
    fun pollFastInsideRadius() {
        assertEquals(StationaryRegion.Decision.POLL_FAST, StationaryRegion.decide(adjustedDistanceMeters = 25f, radiusMeters = 50f))
        // Exactly at the radius is still inside (strictly-greater threshold).
        assertEquals(StationaryRegion.Decision.POLL_FAST, StationaryRegion.decide(adjustedDistanceMeters = 50f, radiusMeters = 50f))
    }

    @Test
    @DisplayName("right at the centre relaxes to the lazy poll")
    fun pollLazyAtCentre() {
        assertEquals(StationaryRegion.Decision.POLL_LAZY, StationaryRegion.decide(adjustedDistanceMeters = 0f, radiusMeters = 50f))
    }
}
