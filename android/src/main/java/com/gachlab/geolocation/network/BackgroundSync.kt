// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.BGLocation
import com.gachlab.geolocation.BGLog
import com.gachlab.geolocation.LocationTemplate
import com.gachlab.geolocation.LocationTemplateFactory
import com.gachlab.geolocation.ServiceEvent
import com.gachlab.geolocation.persistence.ConfigDAO
import com.gachlab.geolocation.persistence.LocationDAO
import com.gachlab.geolocation.service.LocationService
import org.json.JSONArray
import org.json.JSONObject

/**
 * WorkManager worker that replaces the old SyncAdapter / SyncService / AuthenticatorService
 * complex. Reads all SYNC_PENDING locations, batches them, and POSTs to syncUrl.
 */
internal class BackgroundSync(appContext: Context, params: WorkerParameters) :
    Worker(appContext, params) {

    override fun doWork(): Result {
        BGLog.init(applicationContext)
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

        val mode = (config.syncMode ?: BGConfig.DEFAULT_SYNC_MODE).lowercase()
        emit(ServiceEvent.SyncStart)
        BGLog.i("Sync start: ${locations.size} locations (mode=$mode)")

        return if (mode == "single")
            syncSingle(config, syncUrl, locations, template, batchStart, locationDAO)
        else
            syncBatch(config, syncUrl, locations, template, batchStart, locationDAO)
    }

    /** One POST carrying a JSON array of all pending locations (`x-batch-id` header). */
    private fun syncBatch(
        config: BGConfig, syncUrl: String, locations: List<BGLocation>,
        template: LocationTemplate, batchStart: Long, locationDAO: LocationDAO
    ): Result {
        val array = buildBatchArray(locations, template)

        val resolvedUrl = UrlTemplateResolver.resolve(
            syncUrl, locations.first(), config.queryParams?.mapValues { it.value })
        val client = HttpClient(
            urlString   = resolvedUrl,
            method      = config.syncHttpMethod ?: BGConfig.DEFAULT_SYNC_HTTP_METHOD,
            headers     = batchHeaders(config, batchStart),
            contentType = "application/json"
        )

        val code = client.postJSON(array)
        Log.d(TAG, "Sync POST(batch) $resolvedUrl → HTTP $code (${locations.size} locations)")

        return if (code in 200..299) {
            locations.forEach { loc -> loc.locationId?.let { locationDAO.deleteById(it) } }
            // A single batched POST is atomic, so progress is coarse (0→100 in one step).
            emit(ServiceEvent.SyncProgress(100))
            emit(ServiceEvent.SyncSuccess(locations.size))
            BGLog.i("Sync success: ${locations.size} sent")
            Result.success()
        } else {
            emit(ServiceEvent.SyncError(code, ""))
            BGLog.w("Sync failed HTTP $code — will retry")
            Result.retry()
        }
    }

    /** One POST per location, with granular `syncProgress`. Succeeded rows are deleted
     *  as they go, so a retry only re-sends what remains. */
    private fun syncSingle(
        config: BGConfig, syncUrl: String, locations: List<BGLocation>,
        template: LocationTemplate, batchStart: Long, locationDAO: LocationDAO
    ): Result {
        val headers = batchHeaders(config, batchStart)
        val total   = locations.size
        var sent    = 0
        var firstFailCode = 0

        locations.forEachIndexed { i, loc ->
            val body: Any = try {
                template.locationToJson(loc)
            } catch (e: Exception) {
                Log.w(TAG, "Template error for loc ${loc.locationId}: ${e.message}"); loc.toJSONObject()
            }
            val resolvedUrl = UrlTemplateResolver.resolve(
                syncUrl, loc, config.queryParams?.mapValues { it.value })
            val client = HttpClient(
                urlString   = resolvedUrl,
                method      = config.syncHttpMethod ?: BGConfig.DEFAULT_SYNC_HTTP_METHOD,
                headers     = headers,
                contentType = "application/json"
            )
            val code = when (body) {
                is JSONObject -> client.postJSON(body)
                is JSONArray  -> client.postJSON(body)
                else          -> client.postJSON(loc.toJSONObject())
            }
            Log.d(TAG, "Sync POST(single) $resolvedUrl → HTTP $code")

            if (code in 200..299) {
                loc.locationId?.let { locationDAO.deleteById(it) }
                sent++
            } else if (firstFailCode == 0) {
                firstFailCode = code
            }
            emit(ServiceEvent.SyncProgress(((i + 1) * 100) / total))
        }

        if (sent > 0) { emit(ServiceEvent.SyncSuccess(sent)); BGLog.i("Sync success: $sent sent") }

        return if (firstFailCode == 0) {
            Result.success()
        } else {
            emit(ServiceEvent.SyncError(firstFailCode, ""))
            BGLog.w("Sync failed HTTP $firstFailCode ($sent/$total sent) — will retry")
            Result.retry()
        }
    }

    private fun batchHeaders(config: BGConfig, batchStart: Long): Map<String, String> =
        (config.httpHeaders ?: emptyMap<String, String>()).toMutableMap().apply {
            this["x-batch-id"] = batchStart.toString()
        }

    /**
     * Deliver a sync event to the plugin via the live LocationService listener.
     * Null when no Capacitor bridge is attached (e.g. WorkManager ran while the
     * app process had no foreground listener) — the event is simply dropped,
     * matching iOS where notifications reach no observer when the app is gone.
     */
    private fun emit(event: ServiceEvent) {
        LocationService.eventListener?.invoke(event)
    }

    companion object {
        private const val TAG = "BackgroundSync"
        const val KEY_FORCED = "forced"
        const val WORK_TAG   = "gachlab_bg_sync"

        /**
         * Flatten pending locations into the JSON array sent by `batch` mode. The
         * single-mode counterpart posts `template.locationToJson(loc)` per location
         * as a bare JSON object — the BE distinction drivers-web relies on. Pure, so
         * the payload SHAPE (array vs object) is unit-testable without the network.
         */
        internal fun buildBatchArray(locations: List<BGLocation>, template: LocationTemplate): JSONArray {
            val array = JSONArray()
            locations.forEach { loc ->
                try {
                    when (val item = template.locationToJson(loc)) {
                        is JSONObject -> array.put(item)
                        is JSONArray  -> for (i in 0 until item.length()) array.put(item.opt(i))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Template error for loc ${loc.locationId}: ${e.message}")
                    array.put(loc.toJSONObject())
                }
            }
            return array
        }
    }
}
