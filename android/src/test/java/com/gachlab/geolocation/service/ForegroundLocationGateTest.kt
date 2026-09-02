// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.service

import android.Manifest
import android.content.Context
import com.gachlab.geolocation.ServiceEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
 * The service must stand down without the location permission, not crash-loop.
 *
 * WHAT THIS CANNOT PROVE, said plainly: Robolectric's `startForeground` is a
 * shadow and does not raise the platform's `SecurityException`, so no test here
 * reproduces the crash. The crash is pinned where it happens — on an Android 14
 * emulator, in GuestHub's `pickup/tests/e2e-mobile` suite, scenarios
 * `permission_revoked_hot` and `permission_auto_revoked`. Reproduced there twice
 * per run, with two PIDs seconds apart:
 *
 *   java.lang.SecurityException: Starting FGS with type location ... targetSDK=36
 *     requires ... anyOf=[ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION]
 *
 * What this file guards is the DECISION and its ORDER, which is the half that
 * can regress silently: a check moved two lines down still stops the crash and
 * leaves the fifteen-minute loop, because by then the service has already told
 * the reviver it should be running.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ForegroundLocationGateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("bgloc_diagnostics", Context.MODE_PRIVATE)
            .edit().clear().apply()
        Shadows.shadowOf(context as android.app.Application)
            .denyPermissions(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
    }

    private fun grant(vararg permissions: String) {
        Shadows.shadowOf(context as android.app.Application).grantPermissions(*permissions)
    }

    @Test
    fun `closed with no location permission at all`() {
        assertFalse(ForegroundLocationGate.canStartLocationService(context))
    }

    @Test
    fun `open with the fine permission`() {
        grant(Manifest.permission.ACCESS_FINE_LOCATION)
        assertTrue(ForegroundLocationGate.canStartLocationService(context))
    }

    @Test
    fun `open with COARSE alone`() {
        // Deliberate, and the reason it gets its own test: coarse is degraded
        // tracking, not absent tracking, and the platform accepts either for the
        // `location` foreground-service type. Refusing here would switch off a
        // shift that Android was willing to run — trading a crash for an outage.
        grant(Manifest.permission.ACCESS_COARSE_LOCATION)
        assertTrue(ForegroundLocationGate.canStartLocationService(context))
    }

    @Test
    fun `a gated start never tells the reviver it should be running`() {
        // THE ORDER, which is the actual fix. `setShouldBeRunning(true)` runs
        // four lines above `startForeground`, so a check placed just before the
        // foreground call would stop the crash and leave the out-of-process net
        // resurrecting the service every fifteen minutes to crash again.
        assertFalse(ServiceReviver.wasSupposedToRun(context))

        val controller = Robolectric.buildService(LocationService::class.java).create()
        controller.get().start()

        assertFalse(
            "the gate must return before setShouldBeRunning",
            ServiceReviver.wasSupposedToRun(context),
        )
        assertFalse("the service must not consider itself running", controller.get().isRunning)
    }

    @Test
    fun `a gated start leaves an open shift marked as one`() {
        // The opposite error, and the reason the gate does not simply stand the
        // flag down: the shift IS still supposed to be running. What changed is
        // that it cannot right now. Clearing the flag would mean that re-granting
        // the permission leaves the driver with a shift nothing ever resumes.
        ServiceReviver.setShouldBeRunning(context, true)

        val controller = Robolectric.buildService(LocationService::class.java).create()
        controller.get().start()

        // Without this line the assertion below passes for the WRONG reason: if
        // the gate never closes, `start()` runs to the end and sets the flag to
        // true itself. Caught exactly that way — the test was green while the
        // gate was inert.
        assertFalse("the gate must have closed", controller.get().isRunning)
        assertTrue(
            "an open shift stays open across a permission it will get back",
            ServiceReviver.wasSupposedToRun(context),
        )
    }

    @Test
    fun `a gated restart asks the platform to stop re-delivering`() {
        // The other half, and it belongs HERE rather than being an accident of
        // another file's setup: START_STICKY is what the platform honours, so
        // returning it while the permission is gone is asking to be restarted
        // into the same SecurityException, forever.
        val controller = Robolectric.buildService(LocationService::class.java).create()
        // A null intent is exactly how the OS re-delivers after a kill — which
        // is the path the crash loop came in through.
        val ret = controller.get().onStartCommand(null, 0, 1)
        assertEquals(android.app.Service.START_NOT_STICKY, ret)
    }

    @Test
    fun `a gated start records WHY, so the next app open can report it`() {
        // The whole point of the reason. GuestHub's monitor says "permission
        // revoked" instead of the silence that looks identical to a tunnel, and
        // it can only do that if something wrote down what happened while the
        // app had no chance to.
        val controller = Robolectric.buildService(LocationService::class.java).create()
        controller.get().start()

        val persisted = context.getSharedPreferences("bgloc_diagnostics", Context.MODE_PRIVATE)
            .getString("last_kill_reason", null)
        assertEquals(ServiceEvent.REASON_PERMISSION_LOST, persisted)
    }

    @Test
    fun `the reviver does not resurrect what cannot legally start`() {
        // The second door into `startForeground`. Guarding only the service
        // leaves this one open, and it is the one that turns a single crash into
        // a loop with a fifteen-minute heartbeat.
        ServiceReviver.setShouldBeRunning(context, true)
        LocationService.instance = null

        val worker = androidx.work.testing.TestWorkerBuilder<ServiceReviver>(
            context = context,
            executor = java.util.concurrent.Executors.newSingleThreadExecutor(),
        ).build()

        assertEquals(androidx.work.ListenableWorker.Result.success(), worker.doWork())
        // The assertion that has teeth: not the Result — a revive that CRASHES
        // still returns success, because the worker swallows on purpose — but
        // that nothing was asked to start at all.
        assertNull(
            "the reviver must not ask the platform to start a service it cannot start",
            Shadows.shadowOf(context as android.app.Application).nextStartedService,
        )
        assertTrue(
            "the shift stays marked: nothing here failed, it is just impossible right now",
            ServiceReviver.wasSupposedToRun(context),
        )
    }
}
