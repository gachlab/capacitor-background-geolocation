// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("GeoPoint — geographic coordinate value object")
class GeoPointTest {

    @Test
    @DisplayName("distance to itself is zero")
    fun zeroDistance() {
        val p = GeoPoint(-25.2637, -57.5759) // Asunción
        assertEquals(0.0, p.distanceTo(p), 1e-6)
    }

    @Test
    @DisplayName("distance is symmetric")
    fun symmetric() {
        val a = GeoPoint(-25.2637, -57.5759)
        val b = GeoPoint(-25.3000, -57.6000)
        assertEquals(a.distanceTo(b), b.distanceTo(a), 1e-9)
    }

    @Test
    @DisplayName("one degree of latitude is about 111 km")
    fun oneDegreeLatitude() {
        val d = GeoPoint(0.0, 0.0).distanceTo(GeoPoint(1.0, 0.0))
        // R*pi/180 with R = 6_371_000 ≈ 111_194.9 m
        assertTrue(abs(d - 111_194.9) < 1.0, "expected ~111195 m, got $d")
    }

    @Test
    @DisplayName("matches the legacy haversine numerics exactly")
    fun legacyNumerics() {
        // Reproduce the formula the detector used inline, byte-for-byte.
        val lat1 = -25.2637; val lon1 = -57.5759
        val lat2 = -25.3000; val lon2 = -57.6000
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val s = Math.sin(dLat / 2).let { it * it } +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2).let { it * it }
        val expected = 2 * 6_371_000.0 * Math.asin(Math.sqrt(s))
        assertEquals(expected, GeoPoint(lat1, lon1).distanceTo(GeoPoint(lat2, lon2)), 0.0)
    }
}
