// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.BGLocation
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLocationManager

/**
 * The state machine behind a 611-minute blackout.
 *
 * `RawLocationProvider` is the provider the shuttle app runs in production
 * (`provider: 'raw'`), and it had no test at all — which is why the original
 * defect shipped, and why the first two attempts at fixing it each shipped a
 * regression that a green suite did not notice.
 *
 * Every case here is one of those defects, written so that reverting its fix
 * turns this file red:
 *
 *  · subscribing only to providers that happen to be ON at start, with nothing
 *    alive to notice one coming back
 *  · `isStarted` reporting "delivering" whenever a registration merely succeeded
 *  · `onStop()` refusing to unregister when nothing was delivering, leaving the
 *    system listener attached for the life of the process
 *  · `onConfigure()` silently ignoring a new configuration for the same reason
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RawLocationProviderTest {

    private lateinit var locationManager: LocationManager
    private lateinit var shadow: ShadowLocationManager
    private lateinit var provider: RawLocationProvider

    private fun config() = BGConfig.getDefault().apply {
        locationProvider = BGConfig.RAW_PROVIDER
        desiredAccuracy = 100          // wants GPS and NETWORK, as the app does
        interval = 10_000
        distanceFilter = 0
    }

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication()
        locationManager = context.getSystemService(LocationManager::class.java)
        shadow = shadowOf(locationManager)
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        provider = RawLocationProvider(context)
        provider.onCreate()
        provider.onConfigure(config())
    }

    private fun listenerCount() =
        shadow.requestLocationUpdateListeners.size

    // ── The original defect ───────────────────────────────────────────────────

    @Test
    fun `subscribes even to a provider that is switched off at start`() {
        // The defect: `pickProviders()` filtered by `isProviderEnabled` and ran
        // once. A driver opening a shift with GPS off never got a GPS
        // subscription, and nothing ever tried again.
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, false)

        provider.onStart()

        assertTrue("a present-but-disabled provider must still be registered",
            listenerCount() > 0)
    }

    @Test
    fun `recovers when location comes back on after starting with everything off`() {
        // The whole point of the change. With every provider off there used to be
        // no listener at all, so the callback that could have recovered was never
        // registered and the tracker stayed silent for the entire shift.
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

        provider.onStart()
        assertFalse("nothing can deliver yet", provider.isStarted())

        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        provider.onProviderEnabled(LocationManager.GPS_PROVIDER)

        assertTrue("the provider has to report itself as delivering again",
            provider.isStarted())
    }

    // ── The regressions the first two attempts introduced ─────────────────────

    @Test
    fun `isStarted means delivering, not merely registered`() {
        // Reported true whenever a registration succeeded — and since we now
        // register through disabled providers, that included "everything is off".
        // `LocationService.checkWatchdog` gates its restart on this flag, so the
        // lie turned into an infinite restart loop.
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)

        provider.onStart()

        assertTrue("registration still happened", listenerCount() > 0)
        assertFalse("but nothing can deliver", provider.isStarted())
    }

    @Test
    fun `unregisters on stop even when nothing was delivering`() {
        // `onStop()` guarded on `isStarted`, so a shift opened with location off
        // never released the system listener: the service dropped its reference
        // and the GPS stayed on with a listener nobody could reach — one more per
        // shift.
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        provider.onStart()
        assertTrue(listenerCount() > 0)

        provider.onStop()

        assertEquals("the system listener has to be released", 0, listenerCount())
    }

    @Test
    fun `applies a new configuration even when nothing was delivering`() {
        // Same guard, same shape: `onConfigure` did nothing while `isStarted` was
        // false, so a reconfiguration mid-shift was ignored for the rest of it and
        // the live subscription stopped matching `mConfig`.
        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        provider.onStart()
        val before = shadow.requestLocationUpdateListeners.size

        provider.onConfigure(config().apply { interval = 30_000 })

        assertEquals("the subscription has to be rebuilt, not skipped",
            before, shadow.requestLocationUpdateListeners.size)
        assertTrue("and it has to still be registered", listenerCount() > 0)
    }

    @Test
    fun `a late callback cannot re-register after stop`() {
        // `removeUpdates` does not un-post callbacks already on the looper, and
        // the service drops its provider reference right after stopping — so one
        // arriving late used to re-register the GPS with nothing left able to
        // turn it off.
        provider.onStart()
        provider.onStop()

        provider.onProviderEnabled(LocationManager.GPS_PROVIDER)

        assertEquals("nothing may re-register after stop", 0, listenerCount())
        assertFalse(provider.isStarted())
    }

    @Test
    fun `a provider going down mid-shift is reflected`() {
        // Only `onStart()` maintained the flag, so losing every provider left
        // `isStarted` true with nothing able to deliver — the same lie, mirrored.
        provider.onStart()
        assertTrue(provider.isStarted())

        shadow.setProviderEnabled(LocationManager.GPS_PROVIDER, false)
        shadow.setProviderEnabled(LocationManager.NETWORK_PROVIDER, false)
        provider.onProviderDisabled(LocationManager.GPS_PROVIDER)

        assertFalse("nothing can deliver, and the flag has to say so",
            provider.isStarted())
    }

    @Test
    fun `re-subscribing is idempotent`() {
        provider.onStart()
        val count = listenerCount()

        provider.onProviderEnabled(LocationManager.GPS_PROVIDER)
        provider.onProviderEnabled(LocationManager.GPS_PROVIDER)

        assertEquals("a duplicate callback must not stack registrations",
            count, listenerCount())
    }

    // ── The subscription outliving the thread that made it ────────────────────

    private fun collectDeliveries(): MutableList<BGLocation> {
        val delivered = mutableListOf<BGLocation>()
        provider.setDelegate(object : AbstractLocationProvider.Delegate {
            override fun onLocation(location: BGLocation) { delivered += location }
            override fun onStationary(location: BGLocation, radius: Float) {}
            override fun onError(error: BGException) {}
        })
        return delivered
    }

    private fun fix(lat: Double, lon: Double) =
        Location(LocationManager.GPS_PROVIDER).apply {
            latitude = lat
            longitude = lon
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = System.nanoTime()
        }

    /**
     * Subscribe the way production does, then let that thread die.
     *
     * The host app calls `configure()` from JavaScript; Capacitor runs a
     * `@PluginMethod` off the main thread, and `onConfigure()` re-subscribes —
     * so the LIVE registration is the one made by that thread. This reproduces
     * exactly that, with a `HandlerThread` standing in for the bridge.
     */
    private fun subscribeOn(thread: HandlerThread, block: () -> Unit) {
        val done = CountDownLatch(1)
        Handler(thread.looper).post { block(); done.countDown() }
        assertTrue("the subscribing thread never ran", done.await(5, TimeUnit.SECONDS))
    }

    private fun kill(thread: HandlerThread) {
        thread.quitSafely()
        thread.join(5_000)
        assertFalse("the subscribing thread must actually be gone", thread.isAlive)
    }

    @Test
    fun `keeps delivering after the thread that subscribed is gone`() {
        // The 120-second blackout. `requestLocationUpdates`'s four-argument
        // overload binds delivery to the CALLING thread's Looper, so the whole
        // subscription lived exactly as long as whoever happened to call it.
        // Swiping the app out of recents took that thread down and every fix the
        // system delivered was rejected at the executor boundary:
        //
        //   RejectedExecutionException: Handler {...} is shutting down
        //     at android.os.HandlerExecutor.execute
        //
        // Nothing looked wrong: same PID, service still listed, notification
        // still posted, zero log lines — and zero positions.
        val delivered = collectDeliveries()
        val bridge = HandlerThread("fake-capacitor-bridge").apply { start() }

        subscribeOn(bridge) { provider.onStart() }
        kill(bridge)

        shadow.simulateLocation(LocationManager.GPS_PROVIDER, fix(25.77, -80.19))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a fix delivered after the subscribing thread died must still arrive",
            1, delivered.size)
    }

    @Test
    fun `keeps delivering after a reconfiguration from a thread that then dies`() {
        // The path that actually bit, and the reason the previous test is not
        // enough on its own: `onStart()` may well have run on the main thread
        // (boot receiver, sticky restart), and then `configure()` from JavaScript
        // tore that subscription down and rebuilt it on the bridge thread. The
        // healthy registration is REPLACED by a doomed one, which is why a
        // production tablet could post for 38 days while an emulator went silent
        // immediately — two start paths, two lifetimes, not two states of one.
        val delivered = collectDeliveries()
        provider.onStart()

        val bridge = HandlerThread("fake-capacitor-bridge").apply { start() }
        subscribeOn(bridge) { provider.onConfigure(config().apply { interval = 30_000 }) }
        kill(bridge)

        shadow.simulateLocation(LocationManager.GPS_PROVIDER, fix(25.78, -80.20))
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("a reconfiguration must not bind the shift to the caller's lifetime",
            1, delivered.size)
    }
}
