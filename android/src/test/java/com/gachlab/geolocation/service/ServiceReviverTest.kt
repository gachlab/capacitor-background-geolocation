// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.service

import android.content.Context
import com.gachlab.geolocation.BGConfig
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * "Was tracking supposed to be running" — the fact nothing recorded.
 *
 * `startOnBoot` was standing in for it and answers a different question. The
 * substitution failed in both directions: a reboot after the driver stopped
 * tracking started reporting the location of someone off duty, and an active
 * shift with `startOnBoot: false` was silently lost across a reboot.
 *
 * It is also what keeps the out-of-process reviver honest — without it the net
 * would resurrect a shift that was ended on purpose, which is worse than not
 * having a net.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServiceReviverTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("bgloc_diagnostics", Context.MODE_PRIVATE)
            .edit().clear().apply()
    }

    @Test
    fun `defaults to not running, so nothing is revived before a shift ever started`() {
        assertFalse(ServiceReviver.wasSupposedToRun(context))
    }

    @Test
    fun `remembers that tracking was started`() {
        ServiceReviver.setShouldBeRunning(context, true)
        assertTrue(ServiceReviver.wasSupposedToRun(context))
    }

    @Test
    fun `a deliberate stop is remembered as a stop`() {
        // The difference between "it died" and "it was stopped". Getting this
        // wrong means the net turns a driver's ended shift back on.
        ServiceReviver.setShouldBeRunning(context, true)
        ServiceReviver.setShouldBeRunning(context, false)
        assertFalse(ServiceReviver.wasSupposedToRun(context))
    }

    @Test
    fun `arming and standing down never throw`() {
        // WorkManager raises when the host app disabled its initializer and did
        // not initialise it by hand — which is exactly the case in this test
        // environment. A safety net that prevents tracking from starting would
        // turn a recoverable outage into a guaranteed one.
        ServiceReviver.schedule(context)
        ServiceReviver.cancel(context)
    }

    @Test
    fun `the flag is independent of startOnBoot`() {
        // They are different questions, and conflating them is the bug.
        val cfg = BGConfig.getDefault().apply { startOnBoot = false }
        ServiceReviver.setShouldBeRunning(context, true)
        assertTrue("an active shift survives a reboot even with startOnBoot off",
            ServiceReviver.wasSupposedToRun(context))
        assertFalse(cfg.startOnBoot == true)
    }
}
