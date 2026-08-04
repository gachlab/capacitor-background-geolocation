// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The titular defect, guarded where it actually failed.
 *
 * `ServiceRestartReasonTest` proves the mapping is correct in isolation, and that
 * was never the bug: the mapping was right, and one of the two exits to
 * JavaScript did not use it. A test of the pure function stays green while the
 * exit regresses — verified by review, which removed the call from the bridge and
 * watched the whole Android suite pass.
 *
 * So the translation moved to `BGFacade.getBackgroundKillReason()`, at the source
 * of the data, and this asserts the guarantee end to end: whatever spelling the
 * service persisted, what leaves is the published vocabulary. The bridge is now a
 * passthrough, so there is no second place to forget.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackgroundKillReasonTest {

    private fun facadeWithPersisted(reason: String?, at: Long? = null): BGFacade {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("bgloc_diagnostics", Context.MODE_PRIVATE)
        prefs.edit().clear().apply()
        if (reason != null) {
            val editor = prefs.edit().putString("last_kill_reason", reason)
            if (at != null) editor.putLong("last_kill_at", at)
            editor.apply()
        }
        return BGFacade(context)
    }

    @Test
    fun `the persisted snake_case reason leaves in the published spelling`() {
        // `LocationService.persistKillReason` writes ServiceEvent.REASON_SYSTEM_KILL,
        // which is the literal "system_kill". Returning that verbatim is what made
        // a consumer validating against the published union drop the answer — and
        // drop it silently, because the call still resolved.
        val (reason, _) = facadeWithPersisted(ServiceEvent.REASON_SYSTEM_KILL).getBackgroundKillReason()
        assertEquals("systemKill", reason)
    }

    @Test
    fun `app_removed too`() {
        val (reason, _) = facadeWithPersisted(ServiceEvent.REASON_APP_REMOVED).getBackgroundKillReason()
        assertEquals("appRemoved", reason)
    }

    @Test
    fun `the two that are spelled the same come through untouched`() {
        // These are why the bug survived review: three of four values looked
        // correct, and the one that broke was the only one anyone asks for.
        assertEquals("watchdog",
            facadeWithPersisted(ServiceEvent.REASON_WATCHDOG).getBackgroundKillReason().first)
        assertEquals("boot",
            facadeWithPersisted(ServiceEvent.REASON_BOOT).getBackgroundKillReason().first)
    }

    @Test
    fun `nothing persisted means nothing to report`() {
        val (reason, at) = facadeWithPersisted(null).getBackgroundKillReason()
        assertNull(reason)
        assertNull(at)
    }

    @Test
    fun `the timestamp travels alongside the reason`() {
        // Without it a caller cannot tell a kill from ten minutes ago from one
        // from three weeks ago — and the preference is never cleared, so that
        // distinction is the only thing preventing the same stale reason being
        // reported on every shift forever.
        val (reason, at) = facadeWithPersisted(ServiceEvent.REASON_SYSTEM_KILL, 1_785_800_000_000L)
            .getBackgroundKillReason()
        assertEquals("systemKill", reason)
        assertEquals(1_785_800_000_000L, at)
    }
}
