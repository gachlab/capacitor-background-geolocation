// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.ServiceEvent
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Instrumented test (requires an emulator / device — needs a real Context for
 * ConnectivityManager and a real HttpURLConnection stack). Exercises the priority
 * sync end-to-end: a safety event is POSTed to a loopback server, the success event
 * is delivered, and duplicate timestamps are not re-sent.
 *
 * Run from Android Studio or `./gradlew connectedAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class PrioritySyncIntegrationTest {

    /** Minimal loopback HTTP server: counts requests, always answers 200. */
    private class LoopbackServer {
        private val server = ServerSocket(0, 0, java.net.InetAddress.getByName("127.0.0.1"))
        val port: Int get() = server.localPort
        val requestCount = AtomicInteger(0)
        @Volatile var running = true
        private var onRequest: (() -> Unit)? = null

        fun onEachRequest(cb: () -> Unit) { onRequest = cb }

        fun start() = thread(isDaemon = true) {
            while (running) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                socket.use { s ->
                    val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                    var line = reader.readLine()
                    var contentLength = 0
                    // Read request line + headers.
                    while (!line.isNullOrEmpty()) {
                        if (line.startsWith("Content-Length:", ignoreCase = true)) {
                            contentLength = line.substringAfter(":").trim().toIntOrNull() ?: 0
                        }
                        line = reader.readLine()
                    }
                    // Drain the body so the client's write completes cleanly.
                    if (contentLength > 0) reader.read(CharArray(contentLength))
                    val body = "ok"
                    s.getOutputStream().write(
                        ("HTTP/1.1 200 OK\r\nContent-Length: ${body.length}\r\n\r\n$body")
                            .toByteArray(Charsets.UTF_8)
                    )
                    s.getOutputStream().flush()
                    requestCount.incrementAndGet()
                    onRequest?.invoke()
                }
            }
        }

        fun stop() { running = false; try { server.close() } catch (_: Exception) {} }
        fun url() = "http://127.0.0.1:$port/"
    }

    private lateinit var server: LoopbackServer
    private lateinit var manager: PrioritySyncManager
    private val events = CopyOnWriteArrayList<ServiceEvent>()

    @Before fun setUp() {
        server = LoopbackServer().also { it.start() }
        val config = BGConfig().apply {
            prioritySyncUrl = server.url()
            prioritySyncRetries = 1            // fail fast — we never want the retry path here
        }
        manager = PrioritySyncManager(
            ApplicationProvider.getApplicationContext(),
            config,
        ) { events.add(it) }
    }

    @After fun tearDown() {
        manager.destroy()
        server.stop()
    }

    private fun crash(ts: Long) = JSONObject().put("type", "possibleCrash").put("timestamp", ts)

    @Test fun postsEventAndReportsSuccess() {
        val latch = CountDownLatch(1)
        server.onEachRequest { latch.countDown() }

        manager.submit("possibleCrash", crash(1_000L))

        assertTrue("server should receive the POST within 5s", latch.await(5, TimeUnit.SECONDS))
        assertEquals(1, server.requestCount.get())

        // Give the success callback a moment to propagate from the worker thread.
        Thread.sleep(200)
        assertTrue(
            "expected a PrioritySyncSuccess for possibleCrash, got $events",
            events.any { it is ServiceEvent.PrioritySyncSuccess && it.eventType == "possibleCrash" }
        )
    }

    @Test fun deduplicatesByTimestamp() {
        val firstHit = CountDownLatch(1)
        server.onEachRequest { firstHit.countDown() }

        manager.submit("possibleCrash", crash(2_000L))
        assertTrue(firstHit.await(5, TimeUnit.SECONDS))

        // Same timestamp → dropped by dedup, no second request.
        manager.submit("possibleCrash", crash(2_000L))
        Thread.sleep(500)
        assertEquals("duplicate timestamp must not be re-sent", 1, server.requestCount.get())

        // A new timestamp → a second request goes out.
        val secondHit = CountDownLatch(1)
        server.onEachRequest { secondHit.countDown() }
        manager.submit("possibleCrash", crash(3_000L))
        assertTrue(secondHit.await(5, TimeUnit.SECONDS))
        assertEquals(2, server.requestCount.get())
    }
}
