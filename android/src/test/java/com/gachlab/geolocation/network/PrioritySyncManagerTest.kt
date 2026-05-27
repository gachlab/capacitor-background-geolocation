// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

import com.gachlab.geolocation.BGConfig
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

@DisplayName("PrioritySyncManager")
class PrioritySyncManagerTest {

    @Test
    @DisplayName("default retry count is 3")
    fun defaultRetries() {
        assertEquals(3, PrioritySyncManager.DEFAULT_RETRIES)
    }

    @Test
    @DisplayName("default retry delays are 10s, 30s, 60s")
    fun defaultRetryDelays() {
        assertEquals(listOf(10_000L, 30_000L, 60_000L), PrioritySyncManager.DEFAULT_RETRY_DELAYS)
    }

    @Test
    @DisplayName("default events are possibleCrash and sos")
    fun defaultEvents() {
        assertEquals(listOf("possibleCrash", "sos"), PrioritySyncManager.DEFAULT_EVENTS)
    }

    @Test
    @DisplayName("null prioritySyncEvents falls back to default")
    fun nullEventsFallback() {
        val config = BGConfig()
        val effective = config.prioritySyncEvents ?: PrioritySyncManager.DEFAULT_EVENTS
        assertEquals(PrioritySyncManager.DEFAULT_EVENTS, effective)
    }

    @Test
    @DisplayName("custom prioritySyncEvents overrides default")
    fun customEvents() {
        val config = BGConfig().apply { prioritySyncEvents = listOf("hardBrake", "speeding") }
        val effective = config.prioritySyncEvents ?: PrioritySyncManager.DEFAULT_EVENTS
        assertEquals(listOf("hardBrake", "speeding"), effective)
    }

    @Test
    @DisplayName("custom prioritySyncRetries stored correctly")
    fun customRetries() {
        val config = BGConfig().apply { prioritySyncRetries = 5 }
        assertEquals(5, config.prioritySyncRetries)
    }

    @Test
    @DisplayName("custom prioritySyncRetryDelays stored correctly")
    fun customDelays() {
        val delays = listOf(5_000L, 15_000L, 45_000L)
        val config = BGConfig().apply { prioritySyncRetryDelays = delays }
        assertEquals(delays, config.prioritySyncRetryDelays)
    }

    @Test
    @DisplayName("empty url means no sync url - config check")
    fun emptyUrlConfig() {
        val config = BGConfig().apply { url = ""; prioritySyncUrl = null }
        val hasUrl = !config.prioritySyncUrl.isNullOrEmpty() || !config.url.isNullOrEmpty()
        assertEquals(false, hasUrl)
    }

    @Test
    @DisplayName("prioritySyncUrl takes precedence over url in url resolution")
    fun priorityUrlPrecedence() {
        val config = BGConfig().apply {
            url            = "http://regular.example.com"
            prioritySyncUrl = "http://priority.example.com"
        }
        val resolved = config.prioritySyncUrl?.takeIf { it.isNotEmpty() }
            ?: config.url?.takeIf { it.isNotEmpty() } ?: ""
        assertEquals("http://priority.example.com", resolved)
    }

    @Test
    @DisplayName("url fallback when prioritySyncUrl is null")
    fun urlFallback() {
        val config = BGConfig().apply {
            url            = "http://regular.example.com"
            prioritySyncUrl = null
        }
        val resolved = config.prioritySyncUrl?.takeIf { it.isNotEmpty() }
            ?: config.url?.takeIf { it.isNotEmpty() } ?: ""
        assertEquals("http://regular.example.com", resolved)
    }
}
