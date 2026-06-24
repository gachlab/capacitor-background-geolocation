// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The `authorization` event status must map to the same AuthorizationStatus
 * contract values (0/1/2) iOS reports, so JS consumers get identical semantics.
 */
@DisplayName("AuthorizationStatusMapper")
class AuthorizationStatusMapperTest {

    @Test
    @DisplayName("no foreground permission → NOT_AUTHORIZED (0)")
    fun denied() {
        assertEquals(0, AuthorizationStatusMapper.status(foregroundGranted = false, backgroundGranted = false))
        // Background flag is irrelevant without foreground.
        assertEquals(0, AuthorizationStatusMapper.status(foregroundGranted = false, backgroundGranted = true))
    }

    @Test
    @DisplayName("foreground + background → AUTHORIZED (1, iOS authorizedAlways)")
    fun authorizedAlways() {
        assertEquals(1, AuthorizationStatusMapper.status(foregroundGranted = true, backgroundGranted = true))
    }

    @Test
    @DisplayName("foreground only → AUTHORIZED_FOREGROUND (2, iOS authorizedWhenInUse)")
    fun authorizedForeground() {
        assertEquals(2, AuthorizationStatusMapper.status(foregroundGranted = true, backgroundGranted = false))
    }

    @Test
    @DisplayName("named constants match the contract values")
    fun constants() {
        assertEquals(0, AuthorizationStatusMapper.NOT_AUTHORIZED)
        assertEquals(1, AuthorizationStatusMapper.AUTHORIZED)
        assertEquals(2, AuthorizationStatusMapper.AUTHORIZED_FOREGROUND)
    }
}
