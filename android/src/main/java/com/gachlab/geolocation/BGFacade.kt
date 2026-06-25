// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.gachlab.geolocation.persistence.ConfigDAO
import com.gachlab.geolocation.persistence.LocationDAO
import com.gachlab.geolocation.persistence.SessionDAO
import com.gachlab.geolocation.service.LocationService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Coordinator between the Capacitor bridge and LocationService.
 *
 * Communicates with the service via a direct same-process callback
 * (LocationService.eventListener) instead of LocalBroadcastManager,
 * eliminating deprecated API usage and Bundle/Parcelable serialization overhead.
 */
class BGFacade(private val context: Context) {

    /** True while LocationService is running; updated via ServiceEvent. */
    @Volatile var isRunning = false
        private set

    @Volatile private var lastScore: TripScore? = null

    /** Last stationary fix and its radius, updated from ServiceEvent.Stationary. */
    @Volatile private var lastStationary: BGLocation? = null
    @Volatile private var lastStationaryRadius: Float = 0f

    private val configDAO   = ConfigDAO(context.applicationContext)
    private val locationDAO = LocationDAO(context.applicationContext)
    private val sessionDAO  = SessionDAO(context.applicationContext)
    private val logDAO      = com.gachlab.geolocation.persistence.LogDAO(context.applicationContext)

    private var pluginListener: ((ServiceEvent) -> Unit)? = null

    // One pending getCurrentLocation() callback at a time.
    @Volatile private var pendingLocation: ((BGLocation?) -> Unit)? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun init(onEvent: (ServiceEvent) -> Unit) {
        pluginListener = onEvent
        LocationService.eventListener = ::dispatch
        GeofenceManager.init(context.applicationContext, ::dispatch)
    }

    fun destroy() {
        pluginListener = null
        pendingLocation?.invoke(null)
        pendingLocation = null
        GeofenceManager.destroy()
        // Only clear the static listener if it's still ours.
        if (LocationService.eventListener === ::dispatch) {
            LocationService.eventListener = null
        }
    }

    // ── Plugin API ────────────────────────────────────────────────────────────

    fun configure(newConfig: BGConfig) {
        val stored = configDAO.retrieveConfig() ?: BGConfig.getDefault()
        val merged = BGConfig.merge(stored, newConfig)
        configDAO.persistConfig(merged)
        // Hot-reload if running; otherwise next start() picks up the persisted config.
        startedService()?.configure(merged)
    }

    fun start() {
        val intent = Intent(context.applicationContext, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.applicationContext.startForegroundService(intent)
        } else {
            context.applicationContext.startService(intent)
        }
    }

    fun stop() {
        context.applicationContext.stopService(
            Intent(context.applicationContext, LocationService::class.java))
    }

    /**
     * Block the calling thread (max [timeout] ms) until the next location fix arrives.
     * Must be called from a background thread — BackgroundGeolocationPlugin uses
     * bridge.execute {} for this purpose.
     */
    fun getCurrentLocation(timeout: Long = 20_000L): BGLocation? {
        val latch = CountDownLatch(1)
        val ref   = AtomicReference<BGLocation?>()
        pendingLocation = { loc -> ref.set(loc); latch.countDown() }
        latch.await(timeout, TimeUnit.MILLISECONDS)
        pendingLocation = null
        return ref.get()
    }

    // ── Location reads ────────────────────────────────────────────────────────

    fun getAllLocations()             = locationDAO.getAllLocations()
    fun getValidLocations()          = locationDAO.getValidLocations()
    fun getValidLocationsAndDelete() = locationDAO.getValidLocationsAndDelete()
    fun deleteLocation(id: Long)     = locationDAO.deleteById(id)
    fun deleteAllLocations()         = locationDAO.markAllDeleted()
    fun getPendingSyncCount()        = locationDAO.getSyncPendingCount(System.currentTimeMillis())

    // ── Config reads ──────────────────────────────────────────────────────────

    fun getConfig(): BGConfig = configDAO.retrieveConfig() ?: BGConfig.getDefault()

    // ── Stationary ──────────────────────────────────────────────────────────────

    /** Last stationary fix with its radius, or null if none observed yet. */
    fun getStationaryLocation(): Pair<BGLocation, Float>? =
        lastStationary?.let { it to lastStationaryRadius }

    // ── Logs ──────────────────────────────────────────────────────────────────

    fun getLogEntries(limit: Int, fromId: Int, minLevel: String) =
        logDAO.getEntries(limit, fromId, minLevel)

    // ── Session ───────────────────────────────────────────────────────────────

    fun startSession()          = sessionDAO.startSession()
    fun clearSession()          = sessionDAO.clearSession()
    fun getSessionLocations()   = sessionDAO.getSessionLocations()
    fun isSessionActive()       = sessionDAO.isSessionActive()

    // ── Sync ──────────────────────────────────────────────────────────────────

    fun forceSync()  = startedService()?.triggerSync(forced = true)
    fun clearSync()  = locationDAO.deletePendingSyncLocations()

    // ── Misc ──────────────────────────────────────────────────────────────────

    fun switchMode(mode: Int)          = startedService()?.switchMode(mode)
    fun triggerSOS(payload: org.json.JSONObject? = null) = startedService()?.triggerSOS(null, payload)

    // ── Driver intelligence ───────────────────────────────────────────────────

    fun getTripScore(): TripScore? = lastScore

    // ── Geofencing ────────────────────────────────────────────────────────────

    fun addGeofences(geofences: List<BGGeofence>) =
        GeofenceManager.add(context.applicationContext, geofences)

    fun removeGeofences(ids: List<String>?) =
        GeofenceManager.remove(context.applicationContext, ids)

    fun getGeofences(): List<BGGeofence> = GeofenceManager.getAll()

    fun getBackgroundKillReason(): Pair<String?, Long?> {
        val prefs = context.applicationContext
            .getSharedPreferences("bgloc_diagnostics", android.content.Context.MODE_PRIVATE)
        val reason = prefs.getString("last_kill_reason", null)
        val at = if (prefs.contains("last_kill_at")) prefs.getLong("last_kill_at", 0L) else null
        return Pair(reason, at)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun dispatch(event: ServiceEvent) {
        // Track service liveness.
        when (event) {
            is ServiceEvent.ServiceStarted -> isRunning = true
            is ServiceEvent.ServiceStopped -> isRunning = false
            is ServiceEvent.TripEnd        -> lastScore = event.score
            is ServiceEvent.Stationary     -> { lastStationary = event.loc; lastStationaryRadius = event.radius }
            else -> Unit
        }
        // Satisfy any pending getCurrentLocation() call.
        if (event is ServiceEvent.Location) {
            pendingLocation?.let { cb -> pendingLocation = null; cb(event.loc) }
        }
        pluginListener?.invoke(event)
    }

    /** Returns the running LocationService instance (same process), or null if stopped. */
    private fun startedService(): LocationService? = LocationService.instance

    companion object {
        private const val TAG = "BGFacade"
    }
}
