// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.gachlab.geolocation.persistence.ConfigDAO
import com.gachlab.geolocation.persistence.LocationDAO
import com.gachlab.geolocation.persistence.SessionDAO
import com.gachlab.geolocation.service.LocationService
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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
    @Volatile private var lastLocation: BGLocation? = null

    private val configDAO   = ConfigDAO(context.applicationContext)
    private val locationDAO = LocationDAO(context.applicationContext)
    private val sessionDAO  = SessionDAO(context.applicationContext)
    private val logDAO      = com.gachlab.geolocation.persistence.LogDAO(context.applicationContext)

    private var pluginListener: ((ServiceEvent) -> Unit)? = null

    // In-flight getCurrentLocation() waiters — one callback per concurrent call. A fix (or
    // cancel) drains them atomically, so overlapping one-shots don't clobber each other's slot.
    private val pendingLock = Any()
    private val pendingLocations = mutableListOf<(BGLocation?) -> Unit>()
    // Bumped on cancel. A one-shot captures the generation on the bridge thread BEFORE its
    // blocking wait is dispatched; if a cancel bumps it before the waiter registers, the
    // waiter aborts immediately instead of parking — closes the cancel-before-register race.
    private var cancelGeneration = 0

    /** Read the cancel generation synchronously (call on the bridge thread before dispatching a wait). */
    fun currentCancelGeneration(): Int = synchronized(pendingLock) { cancelGeneration }

    private fun addPending(cb: (BGLocation?) -> Unit) = synchronized(pendingLock) { pendingLocations.add(cb) }
    private fun removePending(cb: (BGLocation?) -> Unit) = synchronized(pendingLock) { pendingLocations.remove(cb) }
    /** Atomically take and clear all waiters — each callback is handed to exactly one caller. */
    private fun drainPending(): List<(BGLocation?) -> Unit> = synchronized(pendingLock) {
        val copy = pendingLocations.toList(); pendingLocations.clear(); copy
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun init(onEvent: (ServiceEvent) -> Unit) {
        pluginListener = onEvent
        LocationService.eventListener = ::dispatch
        GeofenceManager.init(context.applicationContext, ::dispatch)
    }

    fun destroy() {
        pluginListener = null
        cancelCurrentLocation() // bumps the generation + drains waiters, so a task still dispatching aborts too
        GeofenceManager.destroy()
        // Only clear the static listener if it's still ours.
        if (LocationService.eventListener === ::dispatch) {
            LocationService.eventListener = null
        }
    }

    // ── Plugin API ────────────────────────────────────────────────────────────

    fun configure(newConfig: BGConfig) {
        // v3: the facade sends the FULLY-RESOLVED config, so it is the single source of
        // truth — apply it over DEFAULTS (replace), not merged onto the previously-stored
        // config, so a field the facade drops reverts to default instead of going stale.
        val resolved = BGConfig.merge(BGConfig.getDefault(), newConfig)
        configDAO.persistConfig(resolved)
        // Hot-reload if running; otherwise next start() picks up the persisted config.
        startedService()?.configure(resolved)
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
     * Block the calling thread (max [timeout] ms) until the next location fix arrives, or null.
     * Must be called from a background thread — the plugin runs it on its oneShotExecutor.
     * [sinceGeneration] is the cancel generation captured on the bridge thread before dispatch:
     * if a cancel bumped it in the meantime, this returns null immediately instead of parking.
     * Concurrent calls are safe (one waiter each).
     */
    fun getCurrentLocation(
        timeout: Long = 20_000L,
        enableHighAccuracy: Boolean = false,
        sinceGeneration: Int,
    ): BGLocation? {
        val latch = CountDownLatch(1)
        val ref   = AtomicReference<BGLocation?>()
        val cb: (BGLocation?) -> Unit = { loc -> ref.set(loc); latch.countDown() }
        synchronized(pendingLock) {
            if (cancelGeneration != sinceGeneration) return null // cancelled before we could register
            pendingLocations.add(cb)
        }
        // Standalone one-shot via the fused client so this works even when tracking is NOT
        // running (parity with iOS/web); if tracking IS running, the service's next fix also
        // satisfies the same latch — whichever arrives first wins.
        val cts = requestFusedOneShot(enableHighAccuracy) { loc -> cb(loc) }
        try {
            latch.await(timeout, TimeUnit.MILLISECONDS)
        } finally {
            removePending(cb) // no-op if a fix/cancel already drained it
            cts.cancel()      // stop the fused one-shot if it hasn't delivered
        }
        return ref.get()
    }

    /** Fire a standalone fused-provider one-shot; the returned source cancels it. */
    @SuppressLint("MissingPermission")
    private fun requestFusedOneShot(highAccuracy: Boolean, onFix: (BGLocation) -> Unit): CancellationTokenSource {
        val cts = CancellationTokenSource()
        try {
            val priority = if (highAccuracy) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY
            val req = CurrentLocationRequest.Builder().setPriority(priority).build()
            LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(req, cts.token)
                .addOnSuccessListener { loc -> if (loc != null) onFix(BGLocation.fromLocation(loc)) }
        } catch (e: SecurityException) {
            Log.w("BGFacade", "getCurrentLocation: no location permission for the fused one-shot")
        }
        return cts
    }

    /**
     * Cancel every in-flight [getCurrentLocation] one-shot — wakes the waiters with no fix so
     * the plugin resolves the pending calls immediately (the JS caller already aborted). Also
     * bumps the cancel generation so a one-shot still dispatching (not yet registered) aborts.
     */
    fun cancelCurrentLocation() {
        val cbs = synchronized(pendingLock) {
            cancelGeneration++
            val copy = pendingLocations.toList(); pendingLocations.clear(); copy
        }
        cbs.forEach { it(null) }
    }

    // ── Location reads ────────────────────────────────────────────────────────

    fun getAllLocations()             = locationDAO.getAllLocations()
    fun getValidLocations()          = locationDAO.getValidLocations()
    fun getValidLocationsAndDelete() = locationDAO.getValidLocationsAndDelete()
    fun deleteLocation(id: Long)     = locationDAO.deleteById(id)
    fun deleteAllLocations()         = locationDAO.markAllDeleted()
    fun getPendingSyncCount()        = locationDAO.getSyncPendingCount(System.currentTimeMillis())

    /** Last known fix (for sticky replay on subscribe), or null if none observed yet. */
    fun getLastLocation(): BGLocation? = lastLocation

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
            is ServiceEvent.TripEnd        -> lastScore = event.journey.score
            is ServiceEvent.Stationary     -> { lastStationary = event.loc; lastStationaryRadius = event.radius }
            else -> Unit
        }
        // Satisfy any pending getCurrentLocation() calls + cache for sticky replay. lastLocation
        // is simply the last known fix — it persists across stop/start (contract parity with iOS
        // lastBGLocation / web lastLocation), so a late subscriber always gets last-known.
        if (event is ServiceEvent.Location) {
            lastLocation = event.loc
            drainPending().forEach { it(event.loc) }
        }
        pluginListener?.invoke(event)
    }

    /** Returns the running LocationService instance (same process), or null if stopped. */
    private fun startedService(): LocationService? = LocationService.instance

    companion object {
        private const val TAG = "BGFacade"
    }
}
