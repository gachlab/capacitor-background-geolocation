// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.service

import android.Manifest
import android.content.Context
import com.gachlab.geolocation.ServiceEvent
import com.gachlab.geolocation.network.ShiftGoneDetector
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * What happens after the detector decides, which is the half that has to be
 * exactly backwards from #59.
 *
 * Both features stop a foreground service that cannot do its job, and they must
 * leave the reviver in OPPOSITE states:
 *
 *  · permission lost (#59) — the shift is still supposed to be running, so
 *    `shouldBeRunning` stays TRUE and re-granting resumes it
 *  · shift gone (#63) — there is nothing left to track for, so
 *    `shouldBeRunning` must be cleared, or the out-of-process net starts the
 *    shift again in fifteen minutes to be 404ed some more
 *
 * Getting that backwards is how this fix would quietly become the loop it was
 * written to end, which is why the flag is asserted in both directions here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShiftGoneRetirementTest {

    private lateinit var context: Context
    private val events = mutableListOf<ServiceEvent>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        events.clear()
        ShiftGoneDetector.reset()
        context.getSharedPreferences("bgloc_diagnostics", Context.MODE_PRIVATE)
            .edit().clear().apply()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        LocationService.eventListener = { events.add(it) }
    }

    @After
    fun tearDown() {
        LocationService.eventListener = null
        LocationService.instance = null
        ShiftGoneDetector.reset()
    }

    private fun killReason(): String? =
        context.getSharedPreferences("bgloc_diagnostics", Context.MODE_PRIVATE)
            .getString("last_kill_reason", null)

    @Test
    fun `retiring a live service stops it and stands the reviver down`() {
        val controller = Robolectric.buildService(LocationService::class.java).create()
        val service = controller.get()
        service.start()
        assertTrue("precondition: the service is running", service.isRunning)
        assertTrue("precondition: the reviver is armed", ServiceReviver.wasSupposedToRun(context))

        LocationService.retireShiftGone(context)

        assertFalse("tracking must actually stop", service.isRunning)
        assertFalse(
            "the net must NOT bring back a shift that no longer exists",
            ServiceReviver.wasSupposedToRun(context),
        )
    }

    @Test
    fun `retiring with no live service still disarms the reviver`() {
        // The path that matters most, and the one a service-only fix would miss:
        // BackgroundSync is a WorkManager worker, so it can decide the shift is
        // gone at a moment when nothing is started. Leaving the flag set here
        // would rebuild the fifteen-minute loop through the second door.
        LocationService.instance = null
        ServiceReviver.setShouldBeRunning(context, true)

        LocationService.retireShiftGone(context)

        assertFalse(
            "a worker with no service must still stand the net down",
            ServiceReviver.wasSupposedToRun(context),
        )
    }

    @Test
    fun `retiring records WHY, so the next app open can explain the shift`() {
        LocationService.instance = null
        LocationService.retireShiftGone(context)

        assertEquals(ServiceEvent.REASON_SHIFT_GONE, killReason())
        assertEquals("shiftGone", ServiceEvent.publicReason(killReason()!!))
    }

    @Test
    fun `the reason is persisted before the stop is announced`() {
        // Ordering with teeth: `stop()` fires ServiceStopped, and a listener that
        // wakes on it and reads the reason must not find an empty preference.
        val controller = Robolectric.buildService(LocationService::class.java).create()
        controller.get().start()

        var reasonAtStopTime: String? = "<never stopped>"
        LocationService.eventListener = { event ->
            events.add(event)
            if (event is ServiceEvent.ServiceStopped) reasonAtStopTime = killReason()
        }

        LocationService.retireShiftGone(context)

        assertEquals(
            "the reason has to be readable by the time the stop is announced",
            ServiceEvent.REASON_SHIFT_GONE,
            reasonAtStopTime,
        )
    }

    @Test
    fun `a permission stand-down and a gone shift disagree about the reviver`() {
        // The two features side by side. If this ever goes green with both
        // assertions reading the same value, one of them has adopted the other's
        // policy and a real behaviour has been lost.
        val controller = Robolectric.buildService(LocationService::class.java).create()
        controller.get().start()
        val armedWhileRunning = ServiceReviver.wasSupposedToRun(context)

        LocationService.retireShiftGone(context)
        val armedAfterGone = ServiceReviver.wasSupposedToRun(context)

        assertTrue("a running shift arms the net", armedWhileRunning)
        assertFalse("a gone shift disarms it", armedAfterGone)
    }

    @Test
    fun `a fresh start clears any run of 404s left over from the last shift`() {
        // Pairs with the in-memory detector: `start()` is the only place that can
        // say the previous evidence belonged to something else.
        ShiftGoneDetector.observe(404, 1_000L)

        val controller = Robolectric.buildService(LocationService::class.java).create()
        controller.get().start()

        assertEquals("the clock must be clear after a start", 0L, ShiftGoneDetector.startedAt())
    }
}
