// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import android.util.Log
import com.gachlab.geolocation.BGConfig
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round

/**
 * Distance-filter location provider backed by the Google Play Services
 * `FusedLocationProviderClient`. The legacy `LocationManager` fallback (for
 * devices without Play Services) was removed — the plugin now requires GMS.
 */
internal class DistanceFilterLocationProvider(context: Context) :
    AbstractLocationProvider(context) {

    private val locationManager by lazy {
        mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    private val alarmManager by lazy {
        mContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private var fusedClient: FusedLocationProviderClient? = null

    private var isMoving = false
    private var isAcquiringStationaryLocation = false
    private var isAcquiringSpeed = false
    private var locationAcquisitionAttempts = 0

    private var lastLocation: Location? = null
    private var stationaryLocation: Location? = null
    private var stationaryRadius = 0f
    private var scaledDistanceFilter = 0
    private var stationaryLocationPollingInterval = 0L
    private var isStarted = false

    // PendingIntents
    private lateinit var stationaryAlarmPI: PendingIntent
    private lateinit var stationaryLocationPollingPI: PendingIntent

    // ── FLP callbacks ─────────────────────────────────────────────────────────

    private val fusedCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { handleNewLocation(it) }
        }
    }

    private val fusedPollCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            fusedClient?.removeLocationUpdates(this)
            result.lastLocation?.let { onPollStationaryLocation(it) }
        }
    }

    // ── Receivers ─────────────────────────────────────────────────────────────

    private val stationaryAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            Log.i(TAG, "stationaryAlarm fired")
            setPace(false)
        }
    }

    private val stationaryLocationMonitorReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            Log.i(TAG, "Stationary location monitor fired")
            val fc = fusedClient ?: return
            val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 0L)
                .setMaxUpdates(1)
                .setWaitForAccurateLocation(false)
                .build()
            try {
                fc.requestLocationUpdates(req, fusedPollCallback, Looper.getMainLooper())
            } catch (e: SecurityException) { handleSecurityException(e) }
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        PROVIDER_ID = BGConfig.DISTANCE_FILTER_PROVIDER

        fusedClient = LocationServices.getFusedLocationProviderClient(mContext)
        Log.i(TAG, "Using FusedLocationProviderClient")

        val immutableFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        stationaryAlarmPI = PendingIntent.getBroadcast(
            mContext, 9000,
            Intent(mContext, StationaryAlarmReceiver::class.java).setAction(ACTION_STATIONARY_ALARM),
            immutableFlag)
        registerReceiver(stationaryAlarmReceiver, IntentFilter(ACTION_STATIONARY_ALARM))

        stationaryLocationPollingPI = PendingIntent.getBroadcast(
            mContext, 9002,
            Intent(mContext, StationaryMonitorReceiver::class.java).setAction(ACTION_STATIONARY_MONITOR),
            immutableFlag)
        registerReceiver(stationaryLocationMonitorReceiver, IntentFilter(ACTION_STATIONARY_MONITOR))
    }

    override fun onStart() {
        if (isStarted) return
        val cfg = mConfig ?: run { Log.w(TAG, "Started without config"); return }
        Log.i(TAG, "Start recording")
        scaledDistanceFilter = cfg.distanceFilter ?: BGConfig.DEFAULT_DISTANCE_FILTER
        isStarted = true
        setPace(false)
    }

    override fun onStop() {
        if (!isStarted) return
        try {
            unsubscribeLocationUpdates()
            alarmManager.cancel(stationaryAlarmPI)
            alarmManager.cancel(stationaryLocationPollingPI)
        } catch (_: SecurityException) {
        } finally {
            isStarted = false
        }
    }

    override fun onDestroy() {
        onStop()
        alarmManager.cancel(stationaryAlarmPI)
        alarmManager.cancel(stationaryLocationPollingPI)
        unregisterReceiver(stationaryAlarmReceiver)
        unregisterReceiver(stationaryLocationMonitorReceiver)
    }

    override fun isStarted() = isStarted

    override fun onConfigure(config: BGConfig) {
        super.onConfigure(config)
        if (isStarted) { onStop(); onStart() }
    }

    override fun onCommand(commandId: Int, arg1: Int) {
        if (commandId == CMD_SWITCH_MODE) setPace(arg1 != BACKGROUND_MODE)
    }

    // ── State machine ─────────────────────────────────────────────────────────

    private fun setPace(moving: Boolean) {
        if (!isStarted) return
        val cfg = mConfig ?: return
        Log.i(TAG, "setPace moving=$moving")
        val wasMoving = isMoving
        isMoving = moving
        isAcquiringStationaryLocation = false
        isAcquiringSpeed = false
        stationaryLocation = null
        try {
            unsubscribeLocationUpdates()
            if (isMoving && !wasMoving) isAcquiringSpeed = true
            if (!isMoving) isAcquiringStationaryLocation = true
            if (!anyProviderEnabled())
                handleServiceError("No location provider available (GPS and Network disabled).")
            subscribeFused()
        } catch (e: SecurityException) { handleSecurityException(e) }
    }

    private fun subscribeFused() {
        val fc = fusedClient ?: return
        val req = if (isAcquiringSpeed || isAcquiringStationaryLocation) {
            locationAcquisitionAttempts = 0
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, ACQUISITION_INTERVAL_MS)
                .setMinUpdateIntervalMillis(ACQUISITION_INTERVAL_MS)
                .setWaitForAccurateLocation(false)
                .build()
        } else {
            val cfg = mConfig!!
            val priority = translatePriority(cfg.desiredAccuracy)
            val interval = cfg.interval?.toLong() ?: BGConfig.DEFAULT_INTERVAL.toLong()
            LocationRequest.Builder(priority, interval)
                .setMinUpdateIntervalMillis(minOf(interval, 1000L))
                .setWaitForAccurateLocation(false)
                .apply {
                    val dist = scaledDistanceFilter.toFloat()
                    if (dist > 0) setMinUpdateDistanceMeters(dist)
                }
                .build()
        }
        try { fc.requestLocationUpdates(req, fusedCallback, Looper.getMainLooper()) }
        catch (e: SecurityException) { handleSecurityException(e) }
    }

    private fun handleNewLocation(location: Location) {
        val cfg = mConfig ?: return
        Log.d(TAG, "Location change: $location isMoving=$isMoving")

        if (!isMoving && !isAcquiringStationaryLocation && stationaryLocation == null) {
            setPace(false); return
        }

        if (isAcquiringStationaryLocation) {
            if (stationaryLocation == null || stationaryLocation!!.accuracy > location.accuracy)
                stationaryLocation = location
            if (++locationAcquisitionAttempts == MAX_STATIONARY_ATTEMPTS) {
                isAcquiringStationaryLocation = false
                enterStationary(stationaryLocation!!)
                handleStationary(stationaryLocation!!, stationaryRadius)
            }
            return
        }
        if (isAcquiringSpeed) {
            if (++locationAcquisitionAttempts == MAX_SPEED_ATTEMPTS) {
                isAcquiringSpeed = false
                scaledDistanceFilter = calculateDistanceFilter(location.speed)
                setPace(true)
            }
            return
        }
        if (isMoving) {
            if (location.speed >= 1 && location.accuracy <= (cfg.stationaryRadius ?: BGConfig.DEFAULT_STATIONARY_RADIUS))
                resetStationaryAlarm()
            val newFilter = calculateDistanceFilter(location.speed)
            if (newFilter != scaledDistanceFilter) {
                scaledDistanceFilter = newFilter
                setPace(true)
            }
            if (lastLocation != null &&
                location.distanceTo(lastLocation!!) < (cfg.distanceFilter ?: BGConfig.DEFAULT_DISTANCE_FILTER)) return
        } else if (stationaryLocation != null) {
            return
        }
        lastLocation = location
        handleLocation(location)
    }

    private fun resetStationaryAlarm() {
        alarmManager.cancel(stationaryAlarmPI)
        alarmManager.set(AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + stationaryTimeout(),
            stationaryAlarmPI)
    }

    private fun enterStationary(location: Location) {
        val cfg = mConfig ?: return
        try {
            unsubscribeLocationUpdates()
            val radius = cfg.stationaryRadius ?: BGConfig.DEFAULT_STATIONARY_RADIUS
            val proxRadius = if (location.accuracy < radius) radius else location.accuracy
            stationaryLocation = location
            stationaryRadius = proxRadius
            Log.i(TAG, "enterStationary lat=${location.latitude} lon=${location.longitude}")
            startPollingStationaryLocation(stationaryPollLazy())
        } catch (e: SecurityException) { handleSecurityException(e) }
    }

    fun onExitStationaryRegion(location: Location) {
        try {
            alarmManager.cancel(stationaryLocationPollingPI)
            setPace(true)
        } catch (e: SecurityException) { handleSecurityException(e) }
    }

    private fun startPollingStationaryLocation(interval: Long) {
        stationaryLocationPollingInterval = interval
        alarmManager.cancel(stationaryLocationPollingPI)
        val start = System.currentTimeMillis() + 60_000L
        alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, start, interval, stationaryLocationPollingPI)
    }

    private fun onPollStationaryLocation(location: Location) {
        val cfg = mConfig ?: return
        if (isMoving) return
        val radius = (cfg.stationaryRadius ?: BGConfig.DEFAULT_STATIONARY_RADIUS).toFloat()
        val stLoc = stationaryLocation ?: return
        val distance = abs(location.distanceTo(stLoc) - stLoc.accuracy - location.accuracy)
        if (distance > radius) onExitStationaryRegion(location)
        else if (distance > 0) startPollingStationaryLocation(stationaryPollFast())
        else if (stationaryLocationPollingInterval != stationaryPollLazy())
            startPollingStationaryLocation(stationaryPollLazy())
    }

    private fun unsubscribeLocationUpdates() {
        try {
            fusedClient?.removeLocationUpdates(fusedCallback)
            fusedClient?.removeLocationUpdates(fusedPollCallback)
        } catch (_: SecurityException) {}
    }

    private fun anyProviderEnabled(): Boolean {
        return try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false } ||
               try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { false }
    }

    private fun translatePriority(accuracy: Int?): Int = when {
        accuracy == null     -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        accuracy >= 10000    -> Priority.PRIORITY_PASSIVE
        accuracy >= 1000     -> Priority.PRIORITY_LOW_POWER
        accuracy >= 100      -> Priority.PRIORITY_BALANCED_POWER_ACCURACY
        else                 -> Priority.PRIORITY_HIGH_ACCURACY
    }

    private fun calculateDistanceFilter(speed: Float): Int {
        var d = (mConfig?.distanceFilter ?: BGConfig.DEFAULT_DISTANCE_FILTER).toDouble()
        if (speed < 100) {
            val rounded = (round(speed / 5) * 5).toFloat()
            d = rounded.toDouble().pow(2.0) + d
        }
        return if (d < 1000) d.toInt() else 1000
    }

    private fun stationaryTimeout(): Long =
        (mConfig?.stationaryTimeout ?: BGConfig.DEFAULT_STATIONARY_TIMEOUT).toLong()

    private fun stationaryPollLazy(): Long =
        (mConfig?.stationaryPollInterval ?: BGConfig.DEFAULT_STATIONARY_POLL_INTERVAL).toLong()

    private fun stationaryPollFast(): Long =
        (mConfig?.stationaryPollFast ?: BGConfig.DEFAULT_STATIONARY_POLL_FAST).toLong()

    // ── Stub receiver classes for PendingIntent targets ───────────────────────

    class StationaryAlarmReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {}
    }
    class StationaryMonitorReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {}
    }

    companion object {
        private const val TAG = "DistanceFilterProvider"
        private const val P_NAME = "com.gachlab.bgloc"
        private const val ACTION_STATIONARY_ALARM   = "$P_NAME.STATIONARY_ALARM"
        private const val ACTION_STATIONARY_MONITOR = "$P_NAME.STATIONARY_MONITOR"
        private const val MAX_STATIONARY_ATTEMPTS   = 5
        private const val MAX_SPEED_ATTEMPTS        = 3
        private const val ACQUISITION_INTERVAL_MS   = 1000L
    }
}
