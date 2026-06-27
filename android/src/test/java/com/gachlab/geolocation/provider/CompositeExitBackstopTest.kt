// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("CompositeExitBackstop — geofence + activity robustness")
class CompositeExitBackstopTest {

    private class FakeBackstop : StationaryExitBackstop {
        var armed = false
        var disarmed = false
        private var onExit: (() -> Unit)? = null
        override fun arm(latitude: Double, longitude: Double, radius: Float, onExit: () -> Unit) {
            armed = true; this.onExit = onExit
        }
        override fun disarm() { disarmed = true }
        fun trigger() = onExit?.invoke()
    }

    @Test
    @DisplayName("arms every backstop")
    fun armsAll() {
        val a = FakeBackstop(); val b = FakeBackstop()
        CompositeExitBackstop(listOf(a, b)).arm(19.0, -99.0, 50f) {}
        assertTrue(a.armed && b.armed)
    }

    @Test
    @DisplayName("first backstop to fire resumes once and disarms them all")
    fun firstFireResumesOnceAndDisarmsAll() {
        val a = FakeBackstop(); val b = FakeBackstop()
        val composite = CompositeExitBackstop(listOf(a, b))
        var resumed = 0
        composite.arm(19.0, -99.0, 50f) { resumed++ }

        a.trigger() // geofence (say) fires first
        assertEquals(1, resumed)
        assertTrue(a.disarmed && b.disarmed, "both backstops disarmed after resume")

        b.trigger() // a later/coalesced activity update must not resume again
        assertEquals(1, resumed)
    }
}
