// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
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

        val transition = event.geofenceTransition
        val rawLocation = event.triggeringLocation
        val triggerLocation = rawLocation?.let { BGLocation.fromLocation(it) }

        event.triggeringGeofences?.forEach { gf ->
            val serviceEvent: ServiceEvent? = when (transition) {
                Geofence.GEOFENCE_TRANSITION_ENTER ->
                    ServiceEvent.GeofenceEnter(gf.requestId, triggerLocation)
                Geofence.GEOFENCE_TRANSITION_EXIT  ->
                    ServiceEvent.GeofenceExit(gf.requestId, triggerLocation)
                Geofence.GEOFENCE_TRANSITION_DWELL ->
                    ServiceEvent.GeofenceDwell(gf.requestId, triggerLocation)
                else -> null
            }
            serviceEvent?.let { GeofenceManager.dispatch(it) }
        }
    }

    companion object {
        private const val TAG = "GeofenceBroadcastReceiver"
    }
}
