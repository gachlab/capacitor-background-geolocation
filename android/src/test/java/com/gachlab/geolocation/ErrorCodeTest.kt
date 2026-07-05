// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import com.gachlab.geolocation.provider.BGException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * The `error` event carries a v3 LocationErrorCode string (`permissionDenied` |
 * `unavailable` | `timeout`). This locks the code plumbing: BGException → ServiceEvent.Error
 * → the emit. Generic provider failures are `unavailable`; SecurityExceptions are
 * `permissionDenied`.
 */
@DisplayName("error code (LocationErrorCode)")
class ErrorCodeTest {

    @Test
    @DisplayName("BGException defaults to unavailable; permission path is permissionDenied")
    fun bgExceptionCode() {
        assertEquals("unavailable", BGException("boom").code)
        assertEquals("permissionDenied", BGException("denied", code = "permissionDenied").code)
    }

    @Test
    @DisplayName("ServiceEvent.Error defaults to unavailable and carries the code through")
    fun serviceEventErrorCode() {
        assertEquals("unavailable", ServiceEvent.Error("boom").code)
        assertEquals("permissionDenied", ServiceEvent.Error("denied", "permissionDenied").code)
    }
}
