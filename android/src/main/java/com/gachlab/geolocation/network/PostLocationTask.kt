// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.BGLocation
import com.gachlab.geolocation.LocationTemplate
import com.gachlab.geolocation.LocationTemplateFactory
import com.gachlab.geolocation.persistence.LocationDAO
import com.gachlab.geolocation.persistence.SessionDAO
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class PostLocationTask(
    private val context: Context,
    private val config: BGConfig,
    private val locationDAO: LocationDAO,
    private val sessionDAO: SessionDAO,
    private val callbacks: Callbacks
) : com.gachlab.geolocation.ports.LocationPublisher {

    interface Callbacks {
        fun onSyncRequested()
        fun onRequestedAbortUpdates()
        fun onHttpAuthorizationFailed()
    }

    private val executor: ExecutorService = Executors.newSingleThreadExecutor()

    override fun add(location: BGLocation) {
        // 1. Mock policy
        when (config.mockLocationPolicy ?: BGConfig.DEFAULT_MOCK_LOCATION_POLICY) {
            "deny"  -> if (location.mockFlags != 0) {
                Log.d(TAG, "Dropping mock location (policy=deny)")
                return
            }
            "flag"  -> if (location.mockFlags == 0) location.mockFlags = 1
            // "allow" → no-op
        }

        // 2. Persist to main queue
        val maxRows = config.maxLocations ?: BGConfig.DEFAULT_MAX_LOCATIONS
        val locationId = locationDAO.persistLocation(location, maxRows)
        if (locationId < 0) {
            Log.w(TAG, "persistLocation returned -1 (maxLocations=0?)")
            return
        }
        location.locationId = locationId

        // 3. Session persist
        sessionDAO.persistSessionLocation(location)

        // 4. Enqueue HTTP post
        executor.submit { post(location) }
    }

    private fun post(location: BGLocation) {
        val url = config.url?.takeIf { it.isNotEmpty() } ?: return
        if (!isConnected()) {
            locationDAO.updateForSync(location.locationId ?: return)
            checkSyncThreshold()
            return
        }

        val resolvedUrl = UrlTemplateResolver.resolve(
            url, location,
            config.queryParams?.mapValues { it.value }
        )

        val template: LocationTemplate = (config.template as? LocationTemplate)
            ?: LocationTemplateFactory.empty()

        val body = try { template.locationToJson(location) } catch (e: Exception) {
            Log.e(TAG, "Template error: ${e.message}")
            location.toJSONObject()
        }

        val contentType = if ((config.httpMode ?: BGConfig.DEFAULT_HTTP_MODE) == "batch")
            "application/json" else "application/x-www-form-urlencoded"

        val client = HttpClient(
            urlString   = resolvedUrl,
            method      = config.httpMethod ?: BGConfig.DEFAULT_HTTP_METHOD,
            headers     = config.httpHeaders,
            contentType = contentType
        )

        val code = when (body) {
            is JSONObject -> client.postJSON(body)
            is JSONArray  -> client.postJSON(body)
            else          -> client.postJSON(body.toString().let { JSONObject().put("data", it) })
        }

        Log.d(TAG, "POST $resolvedUrl → HTTP $code")

        when {
            code in 200..284 -> locationDAO.deleteById(location.locationId ?: return)
            code == 285       -> callbacks.onRequestedAbortUpdates()
            code == 401       -> callbacks.onHttpAuthorizationFailed()
            else              -> {
                locationDAO.updateForSync(location.locationId ?: return)
                checkSyncThreshold()
            }
        }
    }

    private fun checkSyncThreshold() {
        val syncUrl = config.syncUrl?.takeIf { it.isNotEmpty() } ?: return
        if (config.syncEnabled != true) return
        val threshold = config.syncThreshold ?: BGConfig.DEFAULT_SYNC_THRESHOLD
        val batchStart = System.currentTimeMillis()
        val count = locationDAO.getSyncPendingCount(batchStart)
        if (count >= threshold) callbacks.onSyncRequested()
    }

    private fun isConnected(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val net = cm.activeNetwork ?: return false
            cm.getNetworkCapabilities(net)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    override fun shutdown() { executor.shutdown() }

    companion object {
        private const val TAG = "PostLocationTask"
    }
}
