// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The GMS registration in [GeofenceExitBackstop] needs a device to verify, but its
 * resume bridge — what runs when the OS fires the Doze-immune exit transition — is
 * pure static dispatch and unit-testable. This pins the fire-once contract so a
 * coalesced/duplicate EXIT transition cannot resume the engine twice.
 */
@DisplayName("GeofenceExitBackstop — exit bridge")
class GeofenceExitBackstopTest {

    @AfterEach
    fun clear() {
        GeofenceExitBackstop.setOnExit(null)
    }

    @Test
    @DisplayName("fireExit invokes the armed callback exactly once")
    fun firesOnce() {
        var count = 0
        GeofenceExitBackstop.setOnExit { count++ }

        GeofenceExitBackstop.fireExit()
        GeofenceExitBackstop.fireExit() // duplicate/coalesced transition — must be ignored

        assertEquals(1, count)
    }

    @Test
    @DisplayName("fireExit is a no-op when nothing is armed (or after disarm)")
    fun noOpWhenUnarmed() {
        var count = 0
        GeofenceExitBackstop.setOnExit { count++ }
        GeofenceExitBackstop.setOnExit(null) // disarm

        GeofenceExitBackstop.fireExit()

        assertEquals(0, count)
    }
}
