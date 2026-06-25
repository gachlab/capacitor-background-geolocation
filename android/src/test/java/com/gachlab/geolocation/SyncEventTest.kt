// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the batch-sync ServiceEvent payloads emitted by BackgroundSync and
 * mapped to JS by the plugin. Field names/types mirror the iOS notifications
 * (syncStart {}, syncSuccess {sent}, syncError {httpStatus, message}).
 */
@DisplayName("Batch sync ServiceEvents")
class SyncEventTest {

    @Test
    @DisplayName("SyncStart is a singleton object")
    fun syncStartSingleton() {
        assertTrue(ServiceEvent.SyncStart === ServiceEvent.SyncStart)
    }

    @Test
    @DisplayName("SyncSuccess carries the sent count")
    fun syncSuccessSent() {
        val e = ServiceEvent.SyncSuccess(sent = 42)
        assertEquals(42, e.sent)
        assertEquals(ServiceEvent.SyncSuccess(42), e)
    }

    @Test
    @DisplayName("SyncError carries httpStatus and message")
    fun syncErrorFields() {
        val e = ServiceEvent.SyncError(httpStatus = 500, message = "boom")
        assertEquals(500, e.httpStatus)
        assertEquals("boom", e.message)
    }

    @Test
    @DisplayName("SyncProgress carries a 0..100 progress value")
    fun syncProgressValue() {
        assertEquals(100, ServiceEvent.SyncProgress(100).progress)
    }
}
