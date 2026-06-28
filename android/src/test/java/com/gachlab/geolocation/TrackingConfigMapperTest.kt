// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("BGConfig.toTrackingConfig — resolves the tracking policy to domain")
class TrackingConfigMapperTest {

    @Test
    @DisplayName("null overrides resolve to the DEFAULT_* values")
    fun resolvesDefaults() {
        val tc = BGConfig().toTrackingConfig()
        assertEquals(BGConfig.DEFAULT_STATIONARY_RADIUS, tc.stationaryRadius)
        assertEquals(BGConfig.DEFAULT_DISTANCE_FILTER, tc.distanceFilter)
        assertEquals(BGConfig.DEFAULT_DESIRED_ACCURACY, tc.desiredAccuracy)
        assertEquals(BGConfig.DEFAULT_LOCATION_PROVIDER, tc.locationProvider)
        assertEquals(BGConfig.DEFAULT_INTERVAL, tc.interval)
        assertEquals(BGConfig.DEFAULT_FASTEST_INTERVAL, tc.fastestInterval)
        assertEquals(BGConfig.DEFAULT_STATIONARY_TIMEOUT, tc.stationaryTimeout)
        assertEquals(BGConfig.DEFAULT_STATIONARY_EXIT_MODE, tc.stationaryExitMode)
        assertEquals(BGConfig.DEFAULT_ACTIVITY_CONFIDENCE_THRESHOLD, tc.activityConfidenceThreshold)
        assertNull(tc.maxAcceptedAccuracy) // null = no accuracy gate
    }

    @Test
    @DisplayName("explicit overrides pass through unchanged")
    fun passesOverridesThrough() {
        val tc = BGConfig().apply {
            distanceFilter = 25
            desiredAccuracy = 10
            stationaryExitMode = BGConfig.STATIONARY_EXIT_GEOFENCE
            maxAcceptedAccuracy = 50f
        }.toTrackingConfig()
        assertEquals(25, tc.distanceFilter)
        assertEquals(10, tc.desiredAccuracy)
        assertEquals(BGConfig.STATIONARY_EXIT_GEOFENCE, tc.stationaryExitMode)
        assertEquals(50f, tc.maxAcceptedAccuracy)
    }
}
