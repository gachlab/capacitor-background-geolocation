// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.gachlab.geolocation.BGConfig
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNetworkCapabilities
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Fase 0 hub integration: a priority safety event triggered on the hub
 * (triggerSOS) must route through fire() → maybePrioritySync() →
 * buildPriorityPayload() → PrioritySyncManager and reach the network as a POST
 * with the right payload. Pins the priority-routing glue the refactor will move.
 *
 * JVM/Robolectric — PrioritySyncManager POSTs on a background executor, so a
 * loopback ServerSocket on 127.0.0.1 receives it without an emulator.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocationServicePrioritySyncTest {

    /** Minimal loopback HTTP server: answers 200, records request count + last body. */
    private class LoopbackServer {
        private val server = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"))
        val requestCount = AtomicInteger(0)
        @Volatile var lastBody: String = ""
        @Volatile private var running = true
        private var onRequest: (() -> Unit)? = null

        fun onEachRequest(cb: () -> Unit) { onRequest = cb }

        fun start() = thread(isDaemon = true) {
            while (running) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                socket.use { s ->
                    val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                    var line = reader.readLine()
                    var contentLength = 0
                    while (!line.isNullOrEmpty()) {
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                        line = reader.readLine()
                    }
                    if (contentLength > 0) {
                        val buf = CharArray(contentLength)
                        reader.read(buf)
                        lastBody = String(buf)
                    }
                    val body = "ok"
                    s.getOutputStream().write(
                        "HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n\r\n$body".toByteArray(Charsets.UTF_8),
                    )
                    s.getOutputStream().flush()
                    requestCount.incrementAndGet()
                    onRequest?.invoke()
                }
            }
        }

        fun stop() { running = false; try { server.close() } catch (_: Exception) {} }
        fun url() = "http://127.0.0.1:${server.localPort}/"
    }

    private lateinit var controller: ServiceController<LocationService>
    private lateinit var service: LocationService
    private lateinit var server: LoopbackServer

    @Before
    fun setUp() {
        server = LoopbackServer().also { it.start() }
        controller = Robolectric.buildService(LocationService::class.java).create()
        service = controller.get()
        forceConnectedNetwork()
    }

    @After
    fun tearDown() {
        LocationService.eventListener = null
        try {
            controller.destroy()
        } catch (_: Exception) {
            // best-effort
        }
        LocationService.instance = null
        server.stop()
    }

    /** PrioritySyncManager.submit() short-circuits when offline; make the shadow report INTERNET. */
    private fun forceConnectedNetwork() {
        val cm = service.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        shadowOf(cm).setNetworkCapabilities(network, caps)
    }

    @Test
    fun sosRoutesThroughHubToPrioritySyncPost() {
        val cfg = BGConfig.getDefault().apply {
            locationProvider = BGConfig.RAW_PROVIDER
            includeBattery = false
            prioritySyncUrl = server.url()
            prioritySyncRetries = 1 // success path only — no main-looper retry under Robolectric
        }
        service.configure(cfg)
        service.start()

        val latch = CountDownLatch(1)
        server.onEachRequest { latch.countDown() }

        service.triggerSOS(null, JSONObject().put("driverId", "drv-1"))

        assertTrue("priority POST should reach the server within 5 s", latch.await(5, TimeUnit.SECONDS))
        assertEquals(1, server.requestCount.get())
        assertEquals(
            "the hub must build an 'sos' priority payload, body was ${server.lastBody}",
            "sos",
            JSONObject(server.lastBody).optString("type"),
        )
        assertEquals("driver payload must be merged into the sos body", "drv-1", JSONObject(server.lastBody).optString("driverId"))
    }
}
