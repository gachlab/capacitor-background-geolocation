// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.buffer

import com.gachlab.geolocation.BGLocation
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

@DisplayName("PositionBuffer — L2 shared last-fix cache")
class PositionBufferTest {

    @Test
    @DisplayName("empty until the first fix is recorded")
    fun emptyByDefault() {
        val b = PositionBuffer()
        assertNull(b.lastFix)
        assertEquals(0L, b.lastFixAtMs)
    }

    @Test
    @DisplayName("records the latest fix by reference (zero-copy) with its timestamp")
    fun recordsZeroCopy() {
        val b = PositionBuffer()
        val loc = BGLocation("gps")
        b.record(loc, 1_716_000_000_000L)
        assertSame(loc, b.lastFix) // same reference — no re-serialisation
        assertEquals(1_716_000_000_000L, b.lastFixAtMs)
    }

    @Test
    @DisplayName("a newer fix overwrites the previous one")
    fun overwrites() {
        val b = PositionBuffer()
        b.record(BGLocation("gps"), 1L)
        val newer = BGLocation("fused")
        b.record(newer, 2L)
        assertSame(newer, b.lastFix)
        assertEquals(2L, b.lastFixAtMs)
    }

    @Test
    @DisplayName("clear drops the cached fix")
    fun clearResets() {
        val b = PositionBuffer()
        b.record(BGLocation("gps"), 5L)
        b.clear()
        assertNull(b.lastFix)
        assertEquals(0L, b.lastFixAtMs)
    }
}
