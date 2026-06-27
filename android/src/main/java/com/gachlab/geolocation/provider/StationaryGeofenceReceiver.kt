// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

/**
 * Receives the EXIT transition for the internal stationary geofence (the
 * Doze-immune movement-engine backstop) and resumes the moving pace via
 * [GeofenceExitBackstop.fireExit]. Declared in AndroidManifest.xml.
 *
 * This is separate from [com.gachlab.geolocation.GeofenceBroadcastReceiver] (which
 * dispatches user-defined geofence events) so the internal stationary region never
 * surfaces as a user geofenceEnter/Exit event.
 */
class StationaryGeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        @Suppress("DEPRECATION")
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError()) {
            Log.w(TAG, "Stationary geofence event error: ${event.errorCode}")
            return
        }
        val isOurExit = event.geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT &&
            event.triggeringGeofences?.any { it.requestId == GeofenceExitBackstop.REQUEST_ID } == true
        if (isOurExit) {
            Log.i(TAG, "Stationary exit-geofence fired — resuming moving pace")
            GeofenceExitBackstop.fireExit()
        }
    }

    companion object {
        private const val TAG = "StationaryGeofenceReceiver"
    }
}
