// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

import android.Manifest
import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.testing.TestWorkerBuilder
import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.BGLocation
import com.gachlab.geolocation.ServiceEvent
import com.gachlab.geolocation.persistence.ConfigDAO
import com.gachlab.geolocation.persistence.LocationDAO
import com.gachlab.geolocation.service.LocationService
import com.gachlab.geolocation.service.ServiceReviver
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

/**
 * The wiring, which is the part neither of the other two test files can prove.
 *
 * `ShiftGoneDetectorTest` pins the rule and `ShiftGoneRetirementTest` pins what
 * happens afterwards, but a detector nobody feeds is inert and both of those
 * files stay green while the sync path ignores it completely. This one runs the
 * real `BackgroundSync` worker against a real HTTP server that answers 404 and
 * asserts the shift is actually retired.
 *
 * The server is the JDK's own `com.sun.net.httpserver`, deliberately: it needs
 * no new dependency, and a fake `HttpClient` would test the mock rather than the
 * code that reads the status line.
 *
 * Time is not faked either. The detector is primed with a 404 stamped a minute
 * ago, so the worker's own rejection is the second one and lands past the
 * window — the same arithmetic a real runaway does, without a minute of waiting.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SustainedNotFoundWiringTest {

    private lateinit var context: Context
    private lateinit var server: HttpServer
    private val hits = AtomicInteger(0)
    private var status = 404

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        ShiftGoneDetector.reset()
        context.getSharedPreferences("bgloc_diagnostics", Context.MODE_PRIVATE)
            .edit().clear().apply()
        Shadows.shadowOf(context as android.app.Application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        LocationService.instance = null

        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/shift") { exchange ->
                hits.incrementAndGet()
                exchange.requestBody.readBytes()
                exchange.sendResponseHeaders(status, -1)
                exchange.close()
            }
            executor = Executors.newSingleThreadExecutor()
            start()
        }
    }

    @After
    fun tearDown() {
        server.stop(0)
        ShiftGoneDetector.reset()
        LocationService.instance = null
    }

    private fun url() = "http://127.0.0.1:${server.address.port}/shift"

    private fun seedShift() {
        ConfigDAO(context).persistConfig(
            BGConfig.getDefault().apply {
                syncUrl = url()
                syncEnabled = true
                syncThreshold = 0
                httpMode = "batch"
            },
        )
        val dao = LocationDAO(context)
        dao.persistLocationForSync(
            BGLocation("gps").apply {
                latitude = 25.77
                longitude = -80.19
                time = System.currentTimeMillis()
            },
            1_000,
        )
    }

    private fun runSync(): ListenableWorker.Result {
        val worker = TestWorkerBuilder<BackgroundSync>(
            context = context,
            executor = Executors.newSingleThreadExecutor(),
            inputData = Data.Builder().putBoolean(BackgroundSync.KEY_FORCED, true).build(),
        ).build()
        return worker.doWork()
    }

    private fun killReason(): String? =
        context.getSharedPreferences("bgloc_diagnostics", Context.MODE_PRIVATE)
            .getString("last_kill_reason", null)

    @Test
    fun `a sustained 404 through the real sync path retires the shift`() {
        seedShift()
        ServiceReviver.setShouldBeRunning(context, true)
        // The run started a minute ago; the worker's rejection is the one that
        // crosses the window.
        ShiftGoneDetector.observe(404, System.currentTimeMillis() - ShiftGoneDetector.WINDOW_MS)

        val result = runSync()

        assertTrue("the server must actually have been called", hits.get() > 0)
        assertEquals(
            "nothing failed — the server answered, so this is success, not retry",
            ListenableWorker.Result.success(),
            result,
        )
        assertEquals(ServiceEvent.REASON_SHIFT_GONE, killReason())
        assertFalse(
            "the net must not resurrect a shift the server has forgotten",
            ServiceReviver.wasSupposedToRun(context),
        )
    }

    @Test
    fun `a first 404 retries and changes nothing`() {
        // The other side of the same wire. Without this, a fix that retired on
        // every 404 would pass the test above and still be wrong.
        seedShift()
        ServiceReviver.setShouldBeRunning(context, true)

        val result = runSync()

        assertTrue(hits.get() > 0)
        assertEquals(
            "one 404 can race a legitimate shift change — it must only start the clock",
            ListenableWorker.Result.retry(),
            result,
        )
        assertEquals("nothing may be recorded yet", null, killReason())
        assertTrue(
            "the shift is still supposed to be running",
            ServiceReviver.wasSupposedToRun(context),
        )
    }

    @Test
    fun `a sustained 500 never retires, however long it lasts`() {
        // The regression that would undo the reviver (#54). A backend down for an
        // hour must not switch tracking off.
        status = 500
        seedShift()
        ServiceReviver.setShouldBeRunning(context, true)
        ShiftGoneDetector.observe(500, System.currentTimeMillis() - ShiftGoneDetector.WINDOW_MS * 60)

        val result = runSync()

        assertEquals(
            "a bad backend is a retry, forever if need be",
            ListenableWorker.Result.retry(),
            result,
        )
        assertEquals(null, killReason())
        assertTrue(
            "tracking must survive an outage",
            ServiceReviver.wasSupposedToRun(context),
        )
    }

    @Test
    fun `a success clears a run that was about to expire`() {
        // Proves the 2xx branch feeds the detector too. If only the failure paths
        // called `observe`, a shift that recovered would keep a stale clock and
        // could be retired by a single unrelated 404 later.
        status = 200
        seedShift()
        ShiftGoneDetector.observe(404, System.currentTimeMillis() - ShiftGoneDetector.WINDOW_MS)

        val result = runSync()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("the successful POST must clear the run", 0L, ShiftGoneDetector.startedAt())
        assertEquals(null, killReason())
    }
}
