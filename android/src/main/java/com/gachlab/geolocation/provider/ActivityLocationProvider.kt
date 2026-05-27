// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import com.gachlab.geolocation.BGConfig
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

internal class ActivityLocationProvider(context: Context) :
    AbstractLocationProvider(context) {

    private var fusedClient: FusedLocationProviderClient? = null
    private var detectedActivitiesPI: PendingIntent? = null
    private var isStarted      = false
    private var isTracking     = false
    private var playServices   = false

    private var lastActivity = DetectedActivity(DetectedActivity.UNKNOWN, 100)

    // Track prev values to detect meaningful config changes
    private var prevDesiredAccuracy: Int? = null
    private var prevInterval: Int? = null
    private var prevFastestInterval: Int? = null
    private var prevDistanceFilter: Int? = null
    private var prevActivitiesInterval: Int? = null
    private var prevStopOnStillActivity: Boolean? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { onLocationReceived(it) }
        }
    }

    private val activityReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (!ActivityRecognitionResult.hasResult(intent)) return
            val result = ActivityRecognitionResult.extractResult(intent) ?: return
            val activity = result.mostProbableActivity
            val confidence = activity.confidence
            val threshold = mConfig?.activityConfidenceThreshold ?: BGConfig.DEFAULT_ACTIVITY_CONFIDENCE_THRESHOLD
            if (confidence >= threshold) {
                lastActivity = activity
                Log.d(TAG, "Activity detected: ${activity.type} confidence=$confidence")
                if (activity.type == DetectedActivity.STILL) {
                    stopTracking()
                } else if (!isTracking) {
                    startTracking()
                }
            }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        PROVIDER_ID = BGConfig.ACTIVITY_PROVIDER
        val gpsCode = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mContext)
        playServices = gpsCode == ConnectionResult.SUCCESS
        if (!playServices) {
            handleServiceError("Google Play Services unavailable for ACTIVITY_PROVIDER (code=$gpsCode)")
            return
        }
        fusedClient = LocationServices.getFusedLocationProviderClient(mContext)

        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        detectedActivitiesPI = PendingIntent.getBroadcast(
            mContext, 9100,
            Intent(mContext, ActivityUpdateReceiver::class.java).setAction(ACTION_ACTIVITY_UPDATE),
            flag
        )
        registerReceiver(activityReceiver, IntentFilter(ACTION_ACTIVITY_UPDATE))
    }

    override fun onStart() {
        if (!playServices) return
        Log.i(TAG, "Start recording")
        isStarted = true
        subscribeActivityUpdates()
        startTracking()
    }

    override fun onStop() {
        Log.i(TAG, "Stop recording")
        isStarted = false
        unsubscribeActivityUpdates()
        stopTracking()
    }

    override fun onDestroy() {
        onStop()
        unregisterReceiver(activityReceiver)
    }

    override fun isStarted() = isStarted

    override fun onConfigure(config: BGConfig) {
        val restart = isStarted && (
            prevDesiredAccuracy      != config.desiredAccuracy ||
            prevInterval             != config.interval ||
            prevFastestInterval      != config.fastestInterval ||
            prevDistanceFilter       != config.distanceFilter ||
            prevActivitiesInterval   != config.activitiesInterval ||
            prevStopOnStillActivity  != config.stopOnStillActivity
        )
        super.onConfigure(config)
        if (restart) { onStop(); onStart() }
        prevDesiredAccuracy     = config.desiredAccuracy
        prevInterval            = config.interval
        prevFastestInterval     = config.fastestInterval
        prevDistanceFilter      = config.distanceFilter
        prevActivitiesInterval  = config.activitiesInterval
        prevStopOnStillActivity = config.stopOnStillActivity
    }

    override fun onCommand(commandId: Int, arg1: Int) {
        if (commandId == CMD_SWITCH_MODE && isStarted && arg1 == FOREGROUND_MODE) startTracking()
    }

    // ── Tracking ──────────────────────────────────────────────────────────────

    private fun startTracking() {
        if (isTracking || fusedClient == null) return
        val cfg = mConfig ?: return
        val interval     = cfg.interval?.toLong()        ?: BGConfig.DEFAULT_INTERVAL.toLong()
        val fastInterval = cfg.fastestInterval?.toLong() ?: BGConfig.DEFAULT_FASTEST_INTERVAL.toLong()
        val priority     = translatePriority(cfg.desiredAccuracy)
        val req = LocationRequest.Builder(priority, interval)
            .setMinUpdateIntervalMillis(fastInterval)
            .setWaitForAccurateLocation(false)
            .apply {
                val dist = (cfg.distanceFilter ?: 0).toFloat()
                if (dist > 0) setMinUpdateDistanceMeters(dist)
            }.build()
        try {
            fusedClient!!.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
            isTracking = true
            Log.d(TAG, "Started FLP updates")
        } catch (e: SecurityException) { handleSecurityException(e) }
    }

    private fun stopTracking() {
        if (!isTracking) return
        try { fusedClient?.removeLocationUpdates(locationCallback) } catch (_: Exception) {}
        isTracking = false
    }

    private fun subscribeActivityUpdates() {
        val interval = (mConfig?.activitiesInterval ?: BGConfig.DEFAULT_ACTIVITIES_INTERVAL).toLong()
        try {
            ActivityRecognition.getClient(mContext)
                .requestActivityUpdates(interval, detectedActivitiesPI!!)
        } catch (e: Exception) { Log.e(TAG, "subscribeActivityUpdates: ${e.message}") }
    }

    private fun unsubscribeActivityUpdates() {
        try {
            ActivityRecognition.getClient(mContext)
                .removeActivityUpdates(detectedActivitiesPI!!)
        } catch (_: Exception) {}
    }

    private fun onLocationReceived(location: Location) {
        if (lastActivity.type == DetectedActivity.STILL) {
            handleStationary(location, 0f)
            stopTracking()
            return
        }
        handleLocation(location)
    }

    private fun translatePriority(accuracy: Int?): Int = when {
        accuracy == null  -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        accuracy >= 10000 -> Priority.PRIORITY_PASSIVE
        accuracy >= 1000  -> Priority.PRIORITY_LOW_POWER
        accuracy >= 100   -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        else              -> Priority.PRIORITY_HIGH_ACCURACY
    }

    // Stub receiver needed as PendingIntent target
    class ActivityUpdateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {}
    }

    companion object {
        private const val TAG = "ActivityLocationProvider"
        private const val ACTION_ACTIVITY_UPDATE = "com.gachlab.bgloc.ACTIVITY_UPDATE"
    }
}
