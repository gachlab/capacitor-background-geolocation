// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.gachlab.geolocation.BGConfig
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity

/**
 * Motion-based stationary-exit backstop. While parked it subscribes to Activity
 * Recognition and resumes the moving pace when the OS reports a moving activity
 * (in_vehicle / on_foot / on_bicycle / running / walking).
 *
 * In the Transistorsoft model this is the *primary* resume trigger — the OS signals
 * the user started moving, often before they cross the geofence radius. Pairs with
 * [GeofenceExitBackstop] (spatial) via [CompositeExitBackstop] so either signal
 * resumes tracking, which is what makes the geofence approach robust instead of
 * fragile (a missed geofence transition no longer strands the engine).
 *
 * Ignores the (lat,lon,radius) of [StationaryExitBackstop.arm] — motion, not space.
 */
@SuppressLint("MissingPermission")
internal class ActivityExitBackstop(private val context: Context) : StationaryExitBackstop {

    private val client = ActivityRecognition.getClient(context.applicationContext)

    override fun arm(latitude: Double, longitude: Double, radius: Float, onExit: () -> Unit) {
        setOnExit(onExit)
        try {
            client.requestActivityUpdates(ACTIVITY_INTERVAL_MS, pendingIntent())
                .addOnFailureListener {
                    Log.w(TAG, "Activity exit-backstop arm failed: ${it.message}")
                    setOnExit(null)
                }
        } catch (e: Exception) {
            Log.w(TAG, "Activity exit-backstop arm error: ${e.message}")
            setOnExit(null)
        }
    }

    override fun disarm() {
        setOnExit(null)
        try {
            client.removeActivityUpdates(pendingIntent())
        } catch (_: Exception) {
            // best-effort
        }
    }

    private fun pendingIntent(): PendingIntent {
        val intent = Intent(context.applicationContext, ActivityExitReceiver::class.java)
        // ActivityRecognition does not mutate the PendingIntent, so IMMUTABLE is fine on API 31+.
        val mutability = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(
            context.applicationContext, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or mutability,
        )
    }

    companion object {
        private const val REQUEST_CODE = 9101
        private const val ACTIVITY_INTERVAL_MS = 10_000L
        private const val TAG = "ActivityExitBackstop"

        /** Activities meaning the user is on the move (vs STILL / TILTING / UNKNOWN). */
        internal fun isMovingActivity(type: Int): Boolean = when (type) {
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.ON_FOOT,
            DetectedActivity.RUNNING,
            DetectedActivity.WALKING -> true
            else -> false
        }

        @Volatile private var pendingOnExit: (() -> Unit)? = null
        internal fun setOnExit(cb: (() -> Unit)?) { pendingOnExit = cb }

        /** Invoked by [ActivityExitReceiver] on a confident moving-activity update. Fire-once. */
        internal fun fireExit() {
            val cb = pendingOnExit
            pendingOnExit = null
            cb?.invoke()
        }
    }
}

/**
 * Receives Activity Recognition updates for the stationary exit-backstop and resumes
 * the moving pace via [ActivityExitBackstop.fireExit]. Declared in AndroidManifest.xml.
 */
class ActivityExitReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return
        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val activity = result.mostProbableActivity
        if (activity.confidence >= BGConfig.DEFAULT_ACTIVITY_CONFIDENCE_THRESHOLD &&
            ActivityExitBackstop.isMovingActivity(activity.type)
        ) {
            Log.i(TAG, "Moving activity ${activity.type} (conf=${activity.confidence}) — resuming pace")
            ActivityExitBackstop.fireExit()
        }
    }

    companion object {
        private const val TAG = "ActivityExitReceiver"
    }
}
