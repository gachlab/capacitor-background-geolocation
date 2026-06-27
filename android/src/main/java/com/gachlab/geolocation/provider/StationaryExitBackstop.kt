// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

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

/**
 * Watches for the device leaving the stationary region so the movement engine can
 * resume the moving pace. The default polling implementation lives inline in
 * [DistanceFilterLocationProvider] (AlarmManager + fused poll); this seam lets a
 * Doze-immune native-geofence implementation replace that poll without touching
 * the engine's exit decision ([StationaryRegion]).
 */
internal interface StationaryExitBackstop {
    /** Begin watching for an exit from [radius] m around (lat,lon). [onExit] fires once, on exit. */
    fun arm(latitude: Double, longitude: Double, radius: Float, onExit: () -> Unit)

    /** Stop watching. Idempotent. */
    fun disarm()
}

/**
 * Native GMS-geofence backstop — the movement-engine win (pain point #1). Registers
 * a single EXIT geofence around the stationary point; the OS fires the transition
 * **even under Doze**, with zero app CPU while parked, unlike the AlarmManager poll
 * it replaces. Requires Google Play Services, so callers keep the polling path as the
 * fallback where GMS is unavailable.
 *
 * The transition is delivered via a PendingIntent broadcast (a fresh receiver
 * instance with no closure access), so the resume callback is bridged through the
 * companion — the same static-dispatch pattern as [com.gachlab.geolocation.GeofenceManager].
 */
@SuppressLint("MissingPermission")
internal class GeofenceExitBackstop(private val context: Context) : StationaryExitBackstop {

    private val client: GeofencingClient =
        LocationServices.getGeofencingClient(context.applicationContext)

    override fun arm(latitude: Double, longitude: Double, radius: Float, onExit: () -> Unit) {
        setOnExit(onExit)
        val geofence = Geofence.Builder()
            .setRequestId(REQUEST_ID)
            .setCircularRegion(latitude, longitude, radius)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_EXIT)
            .setExpirationDuration(Geofence.NEVER_EXPIRE)
            .build()
        // No INITIAL_TRIGGER — we are inside the region now and only care about leaving.
        val request = GeofencingRequest.Builder().addGeofence(geofence).build()
        client.addGeofences(request, pendingIntent())
            .addOnSuccessListener { Log.d(TAG, "Stationary exit-geofence armed (r=${radius}m)") }
            .addOnFailureListener {
                Log.w(TAG, "Stationary exit-geofence arm failed: ${it.message}")
                setOnExit(null)
            }
    }

    override fun disarm() {
        setOnExit(null)
        client.removeGeofences(listOf(REQUEST_ID))
            .addOnFailureListener { Log.w(TAG, "Stationary exit-geofence disarm failed: ${it.message}") }
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context.applicationContext, StationaryGeofenceReceiver::class.java)
        // GMS geofencing REQUIRES a MUTABLE PendingIntent (it injects the transition extras);
        // FLAG_IMMUTABLE silently breaks transitions on API 31+. Mirrors GeofenceManager.
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(
            context.applicationContext, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }

    companion object {
        internal const val REQUEST_ID = "gachlab_stationary_exit"
        private const val REQUEST_CODE = 9100
        private const val TAG = "GeofenceExitBackstop"

        @Volatile private var pendingOnExit: (() -> Unit)? = null

        internal fun setOnExit(cb: (() -> Unit)?) { pendingOnExit = cb }

        /**
         * Invoked by [StationaryGeofenceReceiver] on a real EXIT transition. Fires at
         * most once per arm — the callback is cleared before invocation so a coalesced
         * duplicate transition cannot resume twice.
         */
        internal fun fireExit() {
            val cb = pendingOnExit
            pendingOnExit = null
            cb?.invoke()
        }
    }
}
