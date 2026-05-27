// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.BGLocation
import com.gachlab.geolocation.LocationTemplate
import com.gachlab.geolocation.LocationTemplateFactory
import com.gachlab.geolocation.persistence.ConfigDAO
import com.gachlab.geolocation.persistence.LocationDAO
import org.json.JSONArray
import org.json.JSONObject

/**
 * WorkManager worker that replaces the old SyncAdapter / SyncService / AuthenticatorService
 * complex. Reads all SYNC_PENDING locations, batches them, and POSTs to syncUrl.
 */
internal class BackgroundSync(appContext: Context, params: WorkerParameters) :
    Worker(appContext, params) {

    override fun doWork(): Result {
        val forced = inputData.getBoolean(KEY_FORCED, false)

        val configDAO   = ConfigDAO(applicationContext)
        val locationDAO = LocationDAO(applicationContext)

        val config = configDAO.retrieveConfig() ?: run {
            Log.w(TAG, "No config — skipping sync")
            return Result.success()
        }

        val syncUrl = config.syncUrl?.takeIf { it.isNotEmpty() } ?: run {
            Log.i(TAG, "No syncUrl — skipping sync")
            return Result.success()
        }

        if (config.syncEnabled != true) {
            Log.i(TAG, "Sync disabled in config")
            return Result.success()
        }

        val batchStart  = System.currentTimeMillis()
        val threshold   = if (forced) 0 else (config.syncThreshold ?: BGConfig.DEFAULT_SYNC_THRESHOLD)
        val pending     = locationDAO.getSyncPendingCount(batchStart)

        if (pending < threshold) {
            Log.i(TAG, "Sync deferred: $pending pending < threshold $threshold")
            return Result.success()
        }

        val locations = locationDAO.getAllLocations()
            .filter { loc ->
                val ms = loc.batchStartMillis
                loc.status == BGLocation.STATUS_SYNC_PENDING &&
                (ms == null || ms == 0L || ms < batchStart)
            }

        if (locations.isEmpty()) {
            Log.i(TAG, "Nothing to sync")
            return Result.success()
        }

        val template: LocationTemplate = (config.template as? LocationTemplate)
            ?: LocationTemplateFactory.empty()

        val array = JSONArray()
        locations.forEach { loc ->
            try {
                val item = template.locationToJson(loc)
                when (item) {
                    is JSONObject -> array.put(item)
                    is JSONArray  -> for (i in 0 until item.length()) array.put(item.opt(i))
                }
            } catch (e: Exception) {
                Log.w(TAG, "Template error for loc ${loc.locationId}: ${e.message}")
                array.put(loc.toJSONObject())
            }
        }

        val resolvedUrl = UrlTemplateResolver.resolve(
            syncUrl, locations.first(),
            config.queryParams?.mapValues { it.value }
        )
        val headers = (config.httpHeaders ?: emptyMap<String, String>()).toMutableMap()
        headers["x-batch-id"] = batchStart.toString()

        val client = HttpClient(
            urlString   = resolvedUrl,
            method      = config.syncHttpMethod ?: BGConfig.DEFAULT_SYNC_HTTP_METHOD,
            headers     = headers,
            contentType = "application/json"
        )

        val code = client.postJSON(array)
        Log.d(TAG, "Sync POST $resolvedUrl → HTTP $code (${locations.size} locations)")

        return if (code in 200..299) {
            // Mark batch as deleted (cleared) on success.
            locations.forEach { loc -> loc.locationId?.let { locationDAO.deleteById(it) } }
            Result.success()
        } else {
            Log.w(TAG, "Sync failed HTTP $code — will retry")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "BackgroundSync"
        const val KEY_FORCED = "forced"
        const val WORK_TAG   = "gachlab_bg_sync"
    }
}
