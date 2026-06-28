// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("Heading — compass heading value object")
class HeadingTest {

    @Test
    @DisplayName("delta to itself is zero")
    fun zeroDelta() {
        assertEquals(0.0, Heading(123.4).deltaTo(Heading(123.4)), 1e-9)
    }

    @Test
    @DisplayName("delta is the direct difference below 180°")
    fun directDelta() {
        assertEquals(30.0, Heading(40.0).deltaTo(Heading(10.0)), 1e-9)
    }

    @Test
    @DisplayName("delta folds across the 0/360 wrap-around")
    fun wrapAround() {
        assertEquals(20.0, Heading(350.0).deltaTo(Heading(10.0)), 1e-9)
        assertEquals(20.0, Heading(10.0).deltaTo(Heading(350.0)), 1e-9) // symmetric
    }

    @Test
    @DisplayName("opposite headings are 180° apart")
    fun opposite() {
        assertEquals(180.0, Heading(0.0).deltaTo(Heading(180.0)), 1e-9)
    }
}
