// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.ServiceEvent
import org.json.JSONObject
import java.util.Collections
import java.util.LinkedHashSet
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors

/**
 * Immediately POSTs safety-critical events to a priority endpoint, with exponential
 * retry and offline queuing via ConnectivityManager.NetworkCallback.
 *
 * Each [submit] call is fire-and-forget; results arrive via [onEvent].
 */
internal class PrioritySyncManager(
    context: Context,
    config: BGConfig,
    private val onEvent: (ServiceEvent) -> Unit,
) {
    private val appContext = context.applicationContext
    private val url: String = config.prioritySyncUrl?.takeIf { it.isNotEmpty() }
        ?: config.url?.takeIf { it.isNotEmpty() } ?: ""
    private val headers: Map<String, String>? = config.httpHeaders
    private val maxRetries: Int = config.prioritySyncRetries ?: DEFAULT_RETRIES
    private val retryDelays: List<Long> = config.prioritySyncRetryDelays ?: DEFAULT_RETRY_DELAYS

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    // LinkedHashSet preserves insertion order so we can trim oldest entries on overflow.
    private val sentTimestamps: MutableSet<Long> = Collections.synchronizedSet(LinkedHashSet())
    private val offlineQueue = CopyOnWriteArrayList<Pair<String, JSONObject>>()

    private val connectivityManager =
        appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init { registerConnectivityCallback() }

    /**
     * Submit an event for priority delivery. No-op when [url] is empty or when the
     * event timestamp has already been submitted (dedup).
     */
    fun submit(eventType: String, payload: JSONObject) {
        if (url.isEmpty()) return
        val ts = payload.optLong("timestamp", System.currentTimeMillis())
        synchronized(sentTimestamps) {
            if (!sentTimestamps.add(ts)) return
            if (sentTimestamps.size > MAX_DEDUP_SIZE) {
                val iter = sentTimestamps.iterator()
                repeat(MAX_DEDUP_SIZE / 2) { if (iter.hasNext()) { iter.next(); iter.remove() } }
            }
        }
        if (!isConnected()) {
            offlineQueue.add(Pair(eventType, payload))
            Log.d(TAG, "Offline — queued priority event: $eventType (${offlineQueue.size} pending)")
            return
        }
        executor.submit { postWithRetry(eventType, payload, 1) }
    }

    fun destroy() {
        networkCallback?.let {
            try { connectivityManager.unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        executor.shutdown()
    }

    private fun postWithRetry(eventType: String, payload: JSONObject, attempt: Int) {
        val client = HttpClient(url, headers = headers)
        val code = client.postJSON(payload)
        Log.d(TAG, "PrioritySync $eventType attempt $attempt → HTTP $code")
        when {
            code in 200..299 ->
                onEvent(ServiceEvent.PrioritySyncSuccess(eventType, attempt))
            attempt < maxRetries -> {
                val delay = retryDelays.getOrElse(attempt - 1) { retryDelays.lastOrNull() ?: 60_000L }
                mainHandler.postDelayed(
                    { executor.submit { postWithRetry(eventType, payload, attempt + 1) } },
                    delay
                )
            }
            else ->
                onEvent(ServiceEvent.PrioritySyncFailed(eventType, code, attempt))
        }
    }

    private fun isConnected(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = connectivityManager.activeNetwork ?: return false
            connectivityManager.getNetworkCapabilities(net)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }

    private fun registerConnectivityCallback() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val pending = offlineQueue.toList()
                if (pending.isEmpty()) return
                offlineQueue.clear()
                Log.d(TAG, "Connectivity restored — flushing ${pending.size} queued priority events")
                pending.forEach { (type, payload) ->
                    executor.submit { postWithRetry(type, payload, 1) }
                }
            }
        }.also { cb ->
            try {
                val req = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()
                connectivityManager.registerNetworkCallback(req, cb)
            } catch (e: Exception) {
                Log.w(TAG, "Could not register network callback: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "PrioritySyncManager"
        private const val MAX_DEDUP_SIZE = 200
        const val DEFAULT_RETRIES = 3
        val DEFAULT_RETRY_DELAYS = listOf(10_000L, 30_000L, 60_000L)
        val DEFAULT_EVENTS = listOf("possibleCrash", "sos")
    }
}
