// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.persistence

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The log-level mapping must stay byte-identical to the iOS LogReader
 * (levelInt / levelString) so getLogEntries filters and labels the same way on
 * both platforms.
 */
@DisplayName("LogLevels")
class LogLevelsTest {

    @Test
    @DisplayName("string → int matches iOS levelInt")
    fun toIntMapping() {
        assertEquals(0, LogLevels.toInt("TRACE"))
        assertEquals(0, LogLevels.toInt("DEBUG"))
        assertEquals(1, LogLevels.toInt("INFO"))
        assertEquals(2, LogLevels.toInt("WARN"))
        assertEquals(3, LogLevels.toInt("ERROR"))
    }

    @Test
    @DisplayName("toInt is case-insensitive and defaults to 0")
    fun toIntCaseAndDefault() {
        assertEquals(2, LogLevels.toInt("warn"))
        assertEquals(1, LogLevels.toInt("Info"))
        assertEquals(0, LogLevels.toInt("nonsense"))
        assertEquals(0, LogLevels.toInt(""))
    }

    @Test
    @DisplayName("int → string matches iOS levelString")
    fun toLevelMapping() {
        assertEquals("DEBUG", LogLevels.toLevel(0))
        assertEquals("INFO", LogLevels.toLevel(1))
        assertEquals("WARN", LogLevels.toLevel(2))
        assertEquals("ERROR", LogLevels.toLevel(3))
        assertEquals("DEBUG", LogLevels.toLevel(99))
    }
}
