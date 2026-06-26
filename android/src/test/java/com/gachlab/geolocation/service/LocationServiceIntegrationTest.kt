// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.service

import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.BGLocation
import com.gachlab.geolocation.ServiceEvent
import com.gachlab.geolocation.provider.AbstractLocationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * Integration test for the orchestration hub [LocationService] — Fase 0 safety net.
 *
 * Unlike the isolated detector/DAO/PrioritySync tests, this pins the *glue* in
 * `handleLocation()` (speed/bearing derivation → detector feed → event emission)
 * that the Apple-Silicon refactor will move out of the hub. It runs on the JVM via
 * Robolectric (no emulator): a real Service `onCreate` wires real SQLite DAOs, and
 * fixes are injected through the provider delegate (the same seam a real GPS
 * provider uses) so no real CoreLocation/GPS is required.
 *
 * Runs under the JUnit5 platform via junit-vintage-engine (see android/build.gradle).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationServiceIntegrationTest {

    private lateinit var controller: ServiceController<LocationService>
    private lateinit var service: LocationService
    private val events = mutableListOf<ServiceEvent>()

    @Before
    fun setUp() {
        events.clear()
        controller = Robolectric.buildService(LocationService::class.java).create()
        service = controller.get()
        LocationService.eventListener = { events.add(it) }
    }

    @After
    fun tearDown() {
        LocationService.eventListener = null
        try {
            controller.destroy()
        } catch (_: Exception) {
            // best-effort teardown
        }
        LocationService.instance = null
    }

    /** Persist config + start the hub, then clear the start-up events. */
    private fun startWith(cfg: BGConfig) {
        service.configure(cfg)
        service.start()
        events.clear()
    }

    /** Drive a fix through the same delegate a real provider would call. */
    private fun injectFix(loc: BGLocation) {
        val field = LocationService::class.java.getDeclaredField("providerDelegate")
        field.isAccessible = true
        val delegate = field.get(service) as AbstractLocationProvider.Delegate
        delegate.onLocation(loc)
    }

    private fun fix(
        lat: Double,
        lon: Double,
        timeMs: Long,
        speed: Float = 0f,
        hasSpeed: Boolean = false,
    ): BGLocation = BGLocation("gps").apply {
        latitude = lat
        longitude = lon
        time = timeMs
        this.speed = speed
        this.hasSpeed = hasSpeed
    }

    private fun baseConfig(): BGConfig = BGConfig.getDefault().apply {
        locationProvider = BGConfig.RAW_PROVIDER // simplest provider; onStart is try/caught
        url = null                                // no priority-sync / network
        includeBattery = false                    // skip battery sticky-broadcast read
    }

    @Test
    fun emitsLocationEventForEachInjectedFix() {
        startWith(baseConfig())

        injectFix(fix(19.4326, -99.1332, 1_716_000_000_000))
        injectFix(fix(19.4330, -99.1332, 1_716_000_001_000))

        val locationEvents = events.filterIsInstance<ServiceEvent.Location>()
        assertEquals("one Location event per injected fix", 2, locationEvents.size)
        assertEquals(19.4326, locationEvents[0].loc.latitude, 1e-9)
    }

    @Test
    fun derivesSpeedFromDisplacementWhenProviderReportsZero() {
        startWith(baseConfig())

        // Two fixes ~44 m apart over 1 s, both with speed=0 / hasSpeed=false.
        // handleLocation() must derive speed from displacement (~44 m/s).
        injectFix(fix(19.4326, -99.1332, 1_716_000_000_000, speed = 0f, hasSpeed = false))
        injectFix(fix(19.4330, -99.1332, 1_716_000_001_000, speed = 0f, hasSpeed = false))

        val second = events.filterIsInstance<ServiceEvent.Location>().last()
        assertTrue(
            "speed should be derived (>0) from displacement, was ${second.loc.speed}",
            second.loc.speed > 0f,
        )
    }

    @Test
    fun feedsDrivingDetectorSoSpeedingPropagatesToEvents() {
        val cfg = baseConfig().apply {
            drivingEvents = BGConfig.DrivingEventsOptions(
                enabled = true,
                speedLimitKmh = 50.0,
                minMovingSpeedMps = 1.0,
                minTripSpeedMps = 3.0,
                minTripDurationMs = 0L, // fire tripStart on first qualifying fix
            )
        }
        startWith(cfg)

        // Drive ~90 km/h (25 m/s) north; one fix every second. hasSpeed=true so the
        // detector sees the intended speed directly (no derivation ambiguity).
        var t = 1_716_000_000_000L
        var lat = 19.4326
        repeat(8) {
            injectFix(fix(lat, -99.1332, t, speed = 25f, hasSpeed = true))
            lat += 25.0 / 111_000.0 // ~25 m north
            t += 1_000L
        }

        assertTrue(
            "speeding must propagate from the driving detector through the hub: $events",
            events.filterIsInstance<ServiceEvent.Speeding>().isNotEmpty(),
        )
    }
}
