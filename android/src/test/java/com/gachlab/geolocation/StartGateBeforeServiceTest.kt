// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import com.gachlab.geolocation.persistence.ConfigDAO
import com.gachlab.geolocation.service.LocationService
import com.gachlab.geolocation.service.ServiceReviver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * Nobody may call `startForegroundService` without the location permission.
 *
 * WHAT THIS CANNOT PROVE, and it is the important half: Robolectric does not
 * enforce the `startForegroundService` contract, so the JVM suite stayed green
 * through the whole defect. The crash is pinned on an emulator — Android 14,
 * API 34 — where the app dies 15 ms after the service refuses:
 *
 *   RemoteServiceException$ForegroundServiceDidNotStartInTimeException:
 *     Context.startForegroundService() did not then call Service.startForeground()
 *
 * That is why the guard moved to the callers. `startForegroundService` is a
 * promise that the service will go foreground within a few seconds, and a
 * service declared `foregroundServiceType="location"` cannot keep it without the
 * permission: going foreground throws SecurityException, and not going foreground
 * is this crash. A service that has already been started has no legal move — the
 * only fix is not making the promise.
 *
 * So what this file guards is the one thing a JVM test can see and the thing that
 * actually went wrong: whether the intent is sent at all. There are THREE doors
 * and the first version of the fix guarded only one of them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StartGateBeforeServiceTest {

    private lateinit var context: Context
    private lateinit var app: Application
    private val events = mutableListOf<ServiceEvent>()

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        app = context as Application
        events.clear()
        context.getSharedPreferences("bgloc_diagnostics", Context.MODE_PRIVATE)
            .edit().clear().apply()
        Shadows.shadowOf(app).denyPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        // Drain anything an earlier test left queued, so `nextStartedService`
        // below can only be ours.
        while (Shadows.shadowOf(app).nextStartedService != null) { /* drain */ }
        LocationService.eventListener = { events.add(it) }
    }

    @After
    fun tearDown() {
        LocationService.eventListener = null
        LocationService.instance = null
    }

    private fun grantFine() =
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun startedService(): Intent? = Shadows.shadowOf(app).nextStartedService

    private fun killReason(): String? =
        context.getSharedPreferences("bgloc_diagnostics", Context.MODE_PRIVATE)
            .getString("last_kill_reason", null)

    // ── Door 1: the facade, which is the real-world path ──────────────────────

    @Test
    fun `the facade never starts the service without the permission`() {
        // The measured case: the host app calls start() on reopen, after Android
        // auto-revoked the permission for an unused app.
        val started = BGFacade(context).start()

        assertFalse("start() must report that nothing was started", started)
        assertNull(
            "no startForegroundService may be issued without the permission",
            startedService(),
        )
    }

    @Test
    fun `the facade starts normally once the permission is there`() {
        grantFine()

        val started = BGFacade(context).start()

        assertTrue(started)
        assertNotNull("a permitted start must still reach the service", startedService())
    }

    @Test
    fun `a refused facade start leaves the shift marked as one`() {
        // The deliberate non-change. The shift is still a shift that should be
        // running; what changed is that it cannot be. Clearing the flag would
        // leave the driver with a shift nothing resumes when the permission is
        // granted again.
        ServiceReviver.setShouldBeRunning(context, true)

        BGFacade(context).start()

        assertTrue(
            "an open shift stays open across a permission it will get back",
            ServiceReviver.wasSupposedToRun(context),
        )
    }

    @Test
    fun `a refused facade start records WHY`() {
        BGFacade(context).start()

        assertEquals(ServiceEvent.REASON_PERMISSION_LOST, killReason())
        assertEquals("permissionLost", ServiceEvent.publicReason(killReason()!!))
    }

    // ── Door 2: boot ──────────────────────────────────────────────────────────

    @Test
    fun `boot never starts the service without the permission`() {
        // A reboot is exactly when this bites: Android can auto-revoke while the
        // device is off, so the shift comes back to a permission that is gone.
        ConfigDAO(context).persistConfig(BGConfig.getDefault().apply { startOnBoot = true })

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertNull(
            "a boot start without the permission is the same broken promise",
            startedService(),
        )
    }

    @Test
    fun `boot starts normally once the permission is there`() {
        ConfigDAO(context).persistConfig(BGConfig.getDefault().apply { startOnBoot = true })
        grantFine()

        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))

        assertNotNull("startOnBoot must still work when it legally can", startedService())
    }

    // ── Coarse is enough, at every door ───────────────────────────────────────

    @Test
    fun `coarse alone is enough for the facade`() {
        // The platform accepts either for the `location` foreground-service type.
        // Refusing here would switch off a shift the system was willing to run.
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        assertTrue(BGFacade(context).start())
        assertNotNull(startedService())
    }
}
