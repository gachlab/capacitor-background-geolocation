// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test (requires an emulator / device). Exercises the real
 * write→read path of the `logs` table backing `getLogEntries`, asserting the
 * cross-platform `LogEntry` contract shape, level filtering, paging and ordering.
 */
@RunWith(AndroidJUnit4::class)
class LogDAOTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private lateinit var dao: LogDAO

    @Before fun setUp() {
        ctx.deleteDatabase(LocationDbHelper.DB_NAME)
        dao = LogDAO(ctx)
    }

    @After fun tearDown() {
        ctx.deleteDatabase(LocationDbHelper.DB_NAME)
    }

    @Test fun insertThenReadReturnsContractShape() {
        dao.insert(LogLevels.ERROR, "boom", "stack-here", 1_000L)

        val entries = dao.getEntries(limit = 10, fromId = 0, minLevel = "DEBUG")
        assertEquals(1, entries.size)
        val e = entries[0]
        // Contract LogEntry keys: id, timestamp, level, message, stackTrace.
        assertTrue(e.has("id"))
        assertEquals(1_000L, e.getLong("timestamp"))
        assertEquals("ERROR", e.getString("level"))
        assertEquals("boom", e.getString("message"))
        assertEquals("stack-here", e.getString("stackTrace"))
    }

    @Test fun nullStackTraceReadsAsEmptyString() {
        dao.insert(LogLevels.INFO, "hello", null, 2_000L)
        val e = dao.getEntries(10, 0, "DEBUG").single()
        assertEquals("", e.getString("stackTrace"))
    }

    @Test fun minLevelFiltersOutLowerSeverities() {
        dao.insert(LogLevels.DEBUG, "d", null, 1L)
        dao.insert(LogLevels.INFO,  "i", null, 2L)
        dao.insert(LogLevels.WARN,  "w", null, 3L)
        dao.insert(LogLevels.ERROR, "e", null, 4L)

        val warnAndUp = dao.getEntries(10, 0, "WARN").map { it.getString("message") }
        assertEquals(listOf("e", "w"), warnAndUp) // newest-first, DEBUG/INFO excluded
    }

    @Test fun newestFirstAndLimitApplied() {
        repeat(5) { dao.insert(LogLevels.INFO, "m$it", null, it.toLong()) }
        val top2 = dao.getEntries(2, 0, "DEBUG").map { it.getString("message") }
        assertEquals(listOf("m4", "m3"), top2)
    }

    @Test fun fromIdPagesOlderEntries() {
        repeat(4) { dao.insert(LogLevels.INFO, "m$it", null, it.toLong()) }
        // First page: 2 newest.
        val page1 = dao.getEntries(2, 0, "DEBUG")
        val lastId = page1.last().getLong("id")
        // Next page: entries strictly older than the smallest id seen.
        val page2 = dao.getEntries(2, lastId.toInt(), "DEBUG").map { it.getString("message") }
        assertEquals(listOf("m1", "m0"), page2)
    }

    @Test fun trimsToMaxRows() {
        val over = (LogDAO.MAX_ROWS + 50).toInt()
        repeat(over) { dao.insert(LogLevels.INFO, "m$it", null, it.toLong()) }
        val all = dao.getEntries(Int.MAX_VALUE, 0, "DEBUG")
        assertTrue("expected <= MAX_ROWS, got ${all.size}", all.size <= LogDAO.MAX_ROWS)
        // The newest entry survived; the oldest was trimmed.
        assertEquals("m${over - 1}", all.first().getString("message"))
    }
}
