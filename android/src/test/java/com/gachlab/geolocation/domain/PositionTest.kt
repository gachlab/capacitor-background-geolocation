// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@DisplayName("Position — kinematic fix value object")
class PositionTest {

    @Test
    @DisplayName("absent kinematics are modelled as null, not sentinels")
    fun absentKinematics() {
        val p = Position(GeoPoint(-25.2637, -57.5759))
        assertNull(p.speedMps)
        assertNull(p.bearingDeg)
        assertNull(p.provider)
    }

    @Test
    @DisplayName("speedMpsOrZero treats an absent speed as stationary")
    fun speedOrZeroAbsent() {
        assertEquals(0.0, Position(GeoPoint(0.0, 0.0)).speedMpsOrZero)
    }

    @Test
    @DisplayName("speedMpsOrZero returns the reading when present (incl. real zero)")
    fun speedOrZeroPresent() {
        assertEquals(12.5, Position(GeoPoint(0.0, 0.0), speedMps = 12.5).speedMpsOrZero)
        assertEquals(0.0, Position(GeoPoint(0.0, 0.0), speedMps = 0.0).speedMpsOrZero)
    }

    @Test
    @DisplayName("carries the point and present kinematics verbatim")
    fun carriesValues() {
        val pt = GeoPoint(-25.30, -57.60)
        val p = Position(pt, speedMps = 8.0, bearingDeg = 270.0, provider = "fused")
        assertEquals(pt, p.point)
        assertEquals(8.0, p.speedMps)
        assertEquals(270.0, p.bearingDeg)
        assertEquals("fused", p.provider)
    }
}
