// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import org.json.JSONArray

/**
 * Manages user-defined geofences using the Google Play Services GeofencingClient.
 *
 * Geofences are persisted in SharedPreferences so they survive app restarts. GMS
 * clears active geofences on device reboot or app update, so `init()` re-registers
 * all persisted geofences on startup.
 *
 * Android GMS allows up to 100 simultaneously monitored geofences per app.
 * `GeofenceBroadcastReceiver` dispatches transition events to `eventListener`.
 */
object GeofenceManager {

    private const val TAG = "GeofenceManager"
    private const val PREFS_KEY = "gachlab_geofences"
    private const val PREFS_NAME = "gachlab_gf_store"

    /** Receives entry/exit/dwell ServiceEvents. Set by BGFacade.init(). */
    var eventListener: ((ServiceEvent) -> Unit)? = null

    private var geofencingClient: GeofencingClient? = null
    private var pendingIntent: PendingIntent? = null

    // In-memory cache; source of truth is SharedPreferences
    private val geofences: MutableMap<String, BGGeofence> = mutableMapOf()

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    fun init(context: Context, listener: (ServiceEvent) -> Unit) {
        eventListener = listener
        geofencingClient = LocationServices.getGeofencingClient(context)
        pendingIntent = buildPendingIntent(context)
        loadFromPrefs(context)
        if (geofences.isNotEmpty()) {
            registerWithGms(context, geofences.values.toList())
        }
    }

    fun destroy() {
        eventListener = null
    }

    // ── Public API ────────────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun add(context: Context, incoming: List<BGGeofence>) {
        incoming.forEach { geofences[it.id] = it }
        persistToPrefs(context)
        registerWithGms(context, incoming)
    }

    fun remove(context: Context, ids: List<String>?) {
        if (ids == null) {
            val allIds = geofences.keys.toList()
            geofences.clear()
            persistToPrefs(context)
            if (allIds.isNotEmpty()) {
                geofencingClient?.removeGeofences(allIds)
                    ?.addOnFailureListener { Log.w(TAG, "removeGeofences failed: ${it.message}") }
            }
            pendingIntent?.let { geofencingClient?.removeGeofences(it) }
        } else {
            ids.forEach { geofences.remove(it) }
            persistToPrefs(context)
            geofencingClient?.removeGeofences(ids)
                ?.addOnFailureListener { Log.w(TAG, "removeGeofences($ids) failed: ${it.message}") }
        }
    }

    fun getAll(): List<BGGeofence> = geofences.values.toList()

    // ── Internal — called by GeofenceBroadcastReceiver ────────────────────────

    internal fun dispatch(event: ServiceEvent) {
        eventListener?.invoke(event)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    private fun registerWithGms(context: Context, list: List<BGGeofence>) {
        if (list.isEmpty()) return
        val gmsGeofences = list.mapNotNull { gf ->
            try {
                var transitionTypes = 0
                if (gf.notifyOnEntry) transitionTypes = transitionTypes or Geofence.GEOFENCE_TRANSITION_ENTER
                if (gf.notifyOnExit)  transitionTypes = transitionTypes or Geofence.GEOFENCE_TRANSITION_EXIT
                if (gf.notifyOnDwell) transitionTypes = transitionTypes or Geofence.GEOFENCE_TRANSITION_DWELL
                if (transitionTypes == 0) return@mapNotNull null
                Geofence.Builder()
                    .setRequestId(gf.id)
                    .setCircularRegion(gf.latitude, gf.longitude, gf.radius)
                    .setTransitionTypes(transitionTypes)
                    .setLoiteringDelay(gf.loiteringDelay)
                    .setExpirationDuration(Geofence.NEVER_EXPIRE)
                    .build()
            } catch (e: Exception) {
                Log.w(TAG, "Invalid geofence ${gf.id}: ${e.message}")
                null
            }
        }
        if (gmsGeofences.isEmpty()) return
        val request = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)
            .addGeofences(gmsGeofences)
            .build()
        val pi = pendingIntent ?: buildPendingIntent(context).also { pendingIntent = it }
        geofencingClient?.addGeofences(request, pi)
            ?.addOnSuccessListener { Log.d(TAG, "Registered ${gmsGeofences.size} geofence(s)") }
            ?.addOnFailureListener { Log.w(TAG, "addGeofences failed: ${it.message}") }
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context.applicationContext, GeofenceBroadcastReceiver::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(context.applicationContext, 0, intent, flags)
    }

    private fun persistToPrefs(context: Context) {
        val json = BGGeofence.listToJSON(geofences.values.toList()).toString()
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREFS_KEY, json).apply()
    }

    private fun loadFromPrefs(context: Context) {
        val json = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREFS_KEY, null) ?: return
        try {
            BGGeofence.listFromJSON(JSONArray(json)).forEach { geofences[it.id] = it }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load persisted geofences: ${e.message}")
        }
    }
}
