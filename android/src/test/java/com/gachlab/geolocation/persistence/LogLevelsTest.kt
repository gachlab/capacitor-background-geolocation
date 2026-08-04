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
    @DisplayName("int → string matches iOS levelString AND the published LogLevel union")
    fun toLevelMapping() {
        // Lowercase, because that is what `LogLevel` in values.ts declares and
        // what `LogEntry.level` is typed as.
        //
        // This test used to assert UPPERCASE and passed: it locked the two
        // natives to each other and never to the type they both feed. Both
        // platforms agreed, both were wrong, and `entries.filter { it.level ==
        // "error" }` on the JS side matched nothing on either. Keeping the two
        // natives identical is necessary and was never sufficient.
        assertEquals("debug", LogLevels.toLevel(0))
        assertEquals("info", LogLevels.toLevel(1))
        assertEquals("warn", LogLevels.toLevel(2))
        assertEquals("error", LogLevels.toLevel(3))
        assertEquals("debug", LogLevels.toLevel(99))
    }

    @Test
    @DisplayName("round trip: what we publish is what we can read back")
    fun roundTrip() {
        // Two assertions, and only the second one has teeth. `toInt` uppercases,
        // so the identity below held throughout the entire bug — it proves the
        // pair is consistent, never which spelling leaves the plugin. The
        // published spelling has to be asserted directly or nothing does.
        listOf(0, 1, 2, 3).forEach { ordinal ->
            val published = LogLevels.toLevel(ordinal)
            assertEquals(ordinal, LogLevels.toInt(published))
            assertEquals(published.lowercase(), published, "levels leave the plugin lowercase")
        }
    }
}
