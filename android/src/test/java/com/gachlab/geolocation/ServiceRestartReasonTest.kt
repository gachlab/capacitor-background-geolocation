// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Guards the ONE spelling of a restart reason that leaves the plugin.
 *
 * There are two doors to the same fact and they used to disagree. The
 * `serviceRestarted` event mapped the internal constants to camelCase inline in
 * the plugin bridge; `getBackgroundKillReason` returned the persisted
 * preference verbatim. So the event said `systemKill` and the query said
 * `system_kill`, for the same kill.
 *
 * What made it expensive is which values broke. `watchdog` and `boot` are
 * spelled identically on both sides, so three of the four looked correct and
 * the only one that fell through was `systemKill` — the reason anyone calls
 * this API for. A consumer validating against the published union dropped it
 * and, because the transport still answered success, dropped it in silence.
 */
@DisplayName("Restart reason — one public vocabulary, two exits")
class ServiceRestartReasonTest {

    @Test
    fun `translates the persisted spellings to the published union`() {
        assertEquals("systemKill", ServiceEvent.publicReason(ServiceEvent.REASON_SYSTEM_KILL))
        assertEquals("appRemoved", ServiceEvent.publicReason(ServiceEvent.REASON_APP_REMOVED))
    }

    @Test
    fun `leaves alone the two that are already spelled the same`() {
        // These are why the bug survived: they hid it.
        assertEquals("watchdog", ServiceEvent.publicReason(ServiceEvent.REASON_WATCHDOG))
        assertEquals("boot", ServiceEvent.publicReason(ServiceEvent.REASON_BOOT))
    }

    @Test
    fun `every persisted constant maps into the published union`() {
        // The list of four is the contract in ServiceRestartedPayload. If a fifth
        // reason is ever persisted, this fails until it is published too — which
        // is the whole point, because the failure mode is silent otherwise.
        val published = setOf("watchdog", "systemKill", "boot", "appRemoved")
        val persisted = listOf(
            ServiceEvent.REASON_WATCHDOG,
            ServiceEvent.REASON_SYSTEM_KILL,
            ServiceEvent.REASON_BOOT,
            ServiceEvent.REASON_APP_REMOVED,
        )
        persisted.forEach { reason ->
            assertEquals(true, ServiceEvent.publicReason(reason) in published, reason)
        }
    }

    @Test
    fun `passes an unknown value through instead of swallowing it`() {
        // A reason we did not anticipate is still worth more than none: the
        // caller can log it and we find out a value exists that we never mapped.
        assertEquals("something_new", ServiceEvent.publicReason("something_new"))
    }

    @Test
    fun `the persisted spellings do not change`() {
        // They are written to SharedPreferences and survive app upgrades.
        // Renaming one makes every reason recorded by an older build unreadable,
        // which is a data migration disguised as a rename.
        assertEquals("watchdog", ServiceEvent.REASON_WATCHDOG)
        assertEquals("system_kill", ServiceEvent.REASON_SYSTEM_KILL)
        assertEquals("boot", ServiceEvent.REASON_BOOT)
        assertEquals("app_removed", ServiceEvent.REASON_APP_REMOVED)
    }
}
