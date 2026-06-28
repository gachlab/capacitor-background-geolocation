// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gachlab.geolocation.domain.GeoEvent
import com.gachlab.geolocation.domain.GeofenceTransition
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Receives geofencing transition intents from Google Play Services.
 *
 * Registered in AndroidManifest.xml as a BroadcastReceiver with the same
 * PendingIntent used in [GeofenceManager]. Dispatches events to
 * [GeofenceManager.eventListener].
 */
class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "GeofencingEvent error: ${event.errorCode}")
            return
        }

        // Map the GMS transition constant to the pure domain enum at the boundary,
        // keeping domain/ free of Play-services types.
        val transition = when (event.geofenceTransition) {
            Geofence.GEOFENCE_TRANSITION_ENTER -> GeofenceTransition.ENTER
            Geofence.GEOFENCE_TRANSITION_EXIT  -> GeofenceTransition.EXIT
            Geofence.GEOFENCE_TRANSITION_DWELL -> GeofenceTransition.DWELL
            else -> null
        }
        val rawLocation = event.triggeringLocation
        val triggerLocation = rawLocation?.let { BGLocation.fromLocation(it) }

        event.triggeringGeofences?.forEach { gf ->
            // GMS → domain GeoEvent at the boundary, then adapt to the ServiceEvent bus.
            val geoEvent = transition?.let { GeoEvent(gf.requestId, it) }
            val serviceEvent: ServiceEvent? = when (geoEvent?.transition) {
                GeofenceTransition.ENTER -> ServiceEvent.GeofenceEnter(geoEvent.geofenceId, triggerLocation)
                GeofenceTransition.EXIT  -> ServiceEvent.GeofenceExit(geoEvent.geofenceId, triggerLocation)
                GeofenceTransition.DWELL -> ServiceEvent.GeofenceDwell(geoEvent.geofenceId, triggerLocation)
                null -> null
            }
            serviceEvent?.let { GeofenceManager.dispatch(it) }
        }
    }

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
    }
}
