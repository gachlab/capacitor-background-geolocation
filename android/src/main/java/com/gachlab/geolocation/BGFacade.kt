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
import com.gachlab.geolocation.service.ForegroundLocationGate
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
    // In-memory resolved-config cache (mirrors iOS `_config`): avoids a DB read+parse per
    // getConfig() call. Populated on first read, refreshed on configure(). @Volatile for the
    // cross-thread reads (getConfig runs off Capacitor's executor and the one-shot thread).
    @Volatile private var configCache: BGConfig? = null
    private val locationDAO = LocationDAO(context.applicationContext)
    private val sessionDAO  = SessionDAO(context.applicationContext)
    private val logDAO      = com.gachlab.geolocation.persistence.LogDAO(context.applicationContext)

    private var pluginListener: ((ServiceEvent) -> Unit)? = null

    // In-flight getCurrentLocation() waiters, keyed by requestId (facade-generated). A fix drains
    // ALL; cancel targets ONE by id. `cancelledIds` records a cancel that arrived before its
    // waiter registered, so the waiter aborts on registration — closes the cancel-before-register
    // race with the id itself, no bridge-thread generation capture needed.
    private val pendingLock = Any()
    private val pending = LinkedHashMap<String, (BGLocation?) -> Unit>()
    // Bounded (insertion-ordered): a cancel that arrives AFTER its request already completed adds an
    // id that never registers again (ids are monotonic), which would leak forever — evict oldest.
    private val cancelledIds = LinkedHashSet<String>()
    private var internalSeq = 0L

    /** Register a waiter; false if this id was already cancelled before it could register. */
    private fun registerPending(id: String, cb: (BGLocation?) -> Unit): Boolean = synchronized(pendingLock) {
        if (cancelledIds.remove(id)) return false
        pending[id] = cb; true
    }
    private fun removePending(id: String) = synchronized(pendingLock) { pending.remove(id) }
    /** Cancel one waiter by id; if it hasn't registered yet, remember the id so it aborts on register. */
    private fun takePending(id: String): ((BGLocation?) -> Unit)? = synchronized(pendingLock) {
        val cb = pending.remove(id)
        if (cb == null) {
            cancelledIds.add(id)
            while (cancelledIds.size > MAX_CANCELLED_IDS) {
                val it = cancelledIds.iterator(); it.next(); it.remove() // drop oldest
            }
        }
        cb
    }
    /** Atomically take and clear ALL waiters — each callback handed to exactly one drainer. */
    private fun drainPending(): List<(BGLocation?) -> Unit> = synchronized(pendingLock) {
        val copy = pending.values.toList(); pending.clear(); copy
    }
    private fun nextInternalId(): String = synchronized(pendingLock) { "internal-${++internalSeq}" }

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
        configCache = resolved // keep the cache consistent with what we just persisted
        // Hot-reload if running; otherwise next start() picks up the persisted config.
        startedService()?.configure(resolved)
    }

    /**
     * Start tracking. Returns false when the location permission is missing and
     * nothing was started.
     *
     * The guard belongs HERE, before `startForegroundService`, and that is the
     * whole point (#59). `startForegroundService` is a promise that the service
     * will call `startForeground` within a few seconds, and a service declared
     * `foregroundServiceType="location"` cannot keep that promise without the
     * permission: going foreground throws SecurityException, and not going
     * foreground gets the app killed with
     * `ForegroundServiceDidNotStartInTimeException`. Traced on an Android 14
     * emulator 15 ms after the service refused — one crash traded for another.
     *
     * So a service that has already been started cannot fix this, whatever it
     * does. The only move that works is not making the promise. The guard inside
     * `LocationService.start()` stays as a net for paths that reach the service
     * some other way, but it can no longer be the only one.
     *
     * `shouldBeRunning` is deliberately left alone: the shift is still supposed
     * to be running, it just cannot be, and re-granting the permission has to
     * resume it with no extra bookkeeping.
     */
    fun start(): Boolean {
        if (!ForegroundLocationGate.canStartLocationService(context.applicationContext)) {
            LocationService.recordPermissionLost(context.applicationContext)
            return false
        }
        val intent = Intent(context.applicationContext, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.applicationContext.startForegroundService(intent)
        } else {
            context.applicationContext.startService(intent)
        }
        return true
    }

    fun stop() {
        context.applicationContext.stopService(
            Intent(context.applicationContext, LocationService::class.java))
    }

    /**
     * Block the calling thread (max [timeout] ms) until a location fix arrives, or null.
     * Must be called from a background thread — the plugin runs it on its oneShotExecutor.
     * [requestId] correlates with cancelCurrentLocation(requestId); if that cancel already
     * arrived (before this waiter could register), returns null immediately instead of parking.
     * Concurrent calls are independent (keyed by id).
     */
    fun getCurrentLocation(
        timeout: Long = 20_000L,
        enableHighAccuracy: Boolean = false,
        requestId: String? = null,
        maximumAge: Long = 0L,
    ): BGLocation? {
        // Honor maximumAge (parity with iOS/web): a recent-enough cached fix short-circuits the
        // one-shot request. maximumAge <= 0 (the default) always fetches a fresh fix, preserving
        // the prior Android behavior for callers that don't ask for a cached fix.
        if (maximumAge > 0) {
            lastLocation?.let { last ->
                if (System.currentTimeMillis() - last.time <= maximumAge) return last
            }
        }
        val id = requestId ?: nextInternalId()
        val latch = CountDownLatch(1)
        val ref   = AtomicReference<BGLocation?>()
        val cb: (BGLocation?) -> Unit = { loc -> ref.set(loc); latch.countDown() }
        if (!registerPending(id, cb)) return null // cancelled before we could register
        // Standalone one-shot via the fused client so this works even when tracking is NOT
        // running (parity with iOS/web); if tracking IS running, the service's next fix also
        // satisfies the same latch — whichever arrives first wins. Route the fused fix through
        // takePending(id) (not cb directly) so it resolves the waiter EXACTLY ONCE and atomically
        // with a concurrent service-fix drain or a cancel — a late fused fix after either simply
        // finds the waiter gone and is discarded, never a double callback.
        val cts = requestFusedOneShot(enableHighAccuracy) { loc -> takePending(id)?.invoke(loc) }
        try {
            latch.await(timeout, TimeUnit.MILLISECONDS)
        } finally {
            removePending(id) // no-op if a fix/cancel already took it
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
     * Cancel an in-flight [getCurrentLocation] one-shot — wakes the waiter(s) with no fix so the
     * plugin resolves immediately (the JS caller already aborted). With [requestId] cancels just
     * that one (and remembers it if it hasn't registered yet); with null cancels all.
     */
    fun cancelCurrentLocation(requestId: String? = null) {
        if (requestId != null) {
            takePending(requestId)?.invoke(null)
        } else {
            drainPending().forEach { it(null) }
        }
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

    fun getConfig(): BGConfig =
        configCache ?: (configDAO.retrieveConfig() ?: BGConfig.getDefault()).also { configCache = it }

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

    /**
     * Why the service was last killed, in the PUBLIC spelling.
     *
     * Normalised here rather than at the bridge, and that placement is the fix
     * for the defect that started this change set. There are two ways out to
     * JavaScript — this query and the `serviceRestarted` event — and the bug was
     * never that the mapping was wrong: it was that one exit did not use it.
     * Fixing that by calling `publicReason` at each exit leaves the same shape
     * that failed, one caller away from failing again.
     *
     * With the translation at the source, the bridge is a passthrough and there
     * is no exit that can forget. It also makes the guarantee testable without a
     * `PluginCall`, which is why it had no test before.
     */
    fun getBackgroundKillReason(): Pair<String?, Long?> {
        val prefs = context.applicationContext
            .getSharedPreferences("bgloc_diagnostics", android.content.Context.MODE_PRIVATE)
        val reason = prefs.getString("last_kill_reason", null)?.let { ServiceEvent.publicReason(it) }
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
        private const val MAX_CANCELLED_IDS = 128 // cap the cancel-before-register race set
    }
}
