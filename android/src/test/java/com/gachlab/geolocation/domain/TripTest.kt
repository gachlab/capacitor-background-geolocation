// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("Trip — in-progress journey value object")
class TripTest {

    @Test
    @DisplayName("startedAt uses the timestamp as id and zero distance")
    fun startedAt() {
        val t = Trip.startedAt(1_716_000_000_000L)
        assertEquals("1716000000000", t.id)
        assertEquals(1_716_000_000_000L, t.startedAtMs)
        assertEquals(0.0, t.distanceMeters)
    }

    @Test
    @DisplayName("plusDistance accumulates without mutating the original")
    fun plusDistanceImmutable() {
        val a = Trip.startedAt(1000L)
        val b = a.plusDistance(50.0).plusDistance(25.0)
        assertEquals(0.0, a.distanceMeters)
        assertEquals(75.0, b.distanceMeters)
    }

    @Test
    @DisplayName("durationMs is elapsed time from start")
    fun durationMs() {
        assertEquals(5000L, Trip.startedAt(1000L).durationMs(6000L))
    }
}
