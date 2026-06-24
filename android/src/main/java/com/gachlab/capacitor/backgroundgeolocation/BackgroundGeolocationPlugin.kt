// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.capacitor.backgroundgeolocation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import androidx.work.WorkManager
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import com.gachlab.geolocation.AuthorizationStatusMapper
import com.gachlab.geolocation.BGFacade
import com.gachlab.geolocation.BGGeofence
import com.gachlab.geolocation.BGLog
import com.gachlab.geolocation.BGLocation
import com.gachlab.geolocation.ServiceEvent
import com.gachlab.geolocation.TripScore
import org.json.JSONArray
import java.util.concurrent.atomic.AtomicInteger

@CapacitorPlugin(
    name = "BackgroundGeolocation",
    permissions = [
        Permission(
            alias = "location",
            strings = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION]
        ),
        Permission(alias = "backgroundLocation", strings = ["android.permission.ACCESS_BACKGROUND_LOCATION"]),
        Permission(alias = "activity",           strings = ["android.permission.ACTIVITY_RECOGNITION"]),
        Permission(alias = "notifications",      strings = ["android.permission.POST_NOTIFICATIONS"])
    ]
)
class BackgroundGeolocationPlugin : Plugin() {

    private lateinit var facade: BGFacade
    private val taskCounter = AtomicInteger(0)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun load() {
        super.load()
        BGLog.init(bridge.activity.applicationContext)
        facade = BGFacade(bridge.activity.applicationContext)
        facade.init(::handleServiceEvent)
    }

    override fun handleOnPause() {
        notifyListeners("background", JSObject())
        super.handleOnPause()
    }

    override fun handleOnResume() {
        notifyListeners("foreground", JSObject())
        super.handleOnResume()
    }

    override fun handleOnDestroy() {
        facade.destroy()
        super.handleOnDestroy()
    }

    // ── ServiceEvent dispatch ─────────────────────────────────────────────────

    private fun handleServiceEvent(event: ServiceEvent) {
        when (event) {
            is ServiceEvent.Location          -> notify("location",           event.loc.toJS())
            is ServiceEvent.Stationary        -> notify("stationary",         event.loc.toJS())
            is ServiceEvent.Moving            -> notify("moving",             event.loc.toJS())
            is ServiceEvent.Stopped           -> notify("stopped",            event.loc.toJS())
            is ServiceEvent.TripStart         -> notify("tripStart",          event.loc.toJS())
            is ServiceEvent.HardBrake         -> notifyListeners("hardBrake", JSObject().apply {
                put("location", event.loc.toJSONObjectWithId()); put("value", event.value)
            })
            is ServiceEvent.RapidAcceleration -> notifyListeners("rapidAcceleration", JSObject().apply {
                put("location", event.loc.toJSONObjectWithId()); put("value", event.value)
            })
            is ServiceEvent.SharpTurn         -> notifyListeners("sharpTurn", JSObject().apply {
                put("location", event.loc.toJSONObjectWithId()); put("value", event.value)
            })
            is ServiceEvent.PossibleCrash     -> notifyListeners("possibleCrash", JSObject().apply {
                put("location", event.loc.toJSONObjectWithId()); put("value", event.value); put("source", event.source)
            })
            is ServiceEvent.PhoneUsageWhileDriving -> notify("phoneUsageWhileDriving", event.loc.toJS())
            is ServiceEvent.TripEnd           -> notifyListeners("tripEnd", JSObject().apply {
                put("location",   event.loc.toJSONObjectWithId())
                put("distance",   event.distanceMeters)
                put("durationMs", event.durationMs)
                event.score?.let { put("score", scoreToJS(it)) }
            })
            is ServiceEvent.IdleStart         -> notifyListeners("idleStart", JSObject().apply {
                put("location", event.loc.toJSONObjectWithId())
                put("startedAt", event.startedAt)
            })
            is ServiceEvent.IdleEnd           -> notifyListeners("idleEnd", JSObject().apply {
                put("location", event.loc.toJSONObjectWithId())
                put("durationMs", event.durationMs)
                put("startedAt", event.startedAt)
            })
            is ServiceEvent.Speeding          -> notifyListeners("speeding", JSObject().apply {
                put("location", event.loc.toJSONObjectWithId())
                put("speedKmh", event.speedKmh)
                put("limitKmh", event.limitKmh)
            })
            is ServiceEvent.Heartbeat         -> notify("heartbeat",  event.loc?.toJS() ?: JSObject())
            is ServiceEvent.Error             -> notifyListeners("error",
                JSObject().apply { put("message", event.message) })
            is ServiceEvent.Activity          -> notifyListeners("activity",
                event.data?.let { try { JSObject.fromJSONObject(it) } catch (_: Exception) { JSObject() } } ?: JSObject())
            is ServiceEvent.ProviderChange    -> notifyListeners("providerChange",
                JSObject().apply { put("provider", event.provider) })
            is ServiceEvent.Sos               -> notifyListeners("sos",
                JSObject().apply {
                    event.locationId?.let { put("locationId", it) }
                    event.payload?.let { p -> p.keys().forEach { k -> put(k, p.get(k)) } }
                })
            ServiceEvent.ServiceStarted       -> notifyListeners("start",              JSObject())
            ServiceEvent.ServiceStopped       -> notifyListeners("stop",               JSObject())
            is ServiceEvent.ServiceRestarted  -> notifyListeners("serviceRestarted",
                JSObject().apply { put("reason", event.reason) })
            is ServiceEvent.GeofenceEnter     -> notifyListeners("geofenceEnter",
                JSObject().apply { put("id", event.geofenceId); put("action", "ENTER")
                    event.loc?.let { put("location", it.toJSONObjectWithId()) } })
            is ServiceEvent.GeofenceExit      -> notifyListeners("geofenceExit",
                JSObject().apply { put("id", event.geofenceId); put("action", "EXIT")
                    event.loc?.let { put("location", it.toJSONObjectWithId()) } })
            is ServiceEvent.GeofenceDwell     -> notifyListeners("geofenceDwell",
                JSObject().apply { put("id", event.geofenceId); put("action", "DWELL")
                    event.loc?.let { put("location", it.toJSONObjectWithId()) } })
            ServiceEvent.AbortRequested       -> notifyListeners("abort_requested",    JSObject())
            ServiceEvent.HttpAuthorization    -> notifyListeners("http_authorization", JSObject())
            is ServiceEvent.PrioritySyncSuccess -> notifyListeners("prioritySyncSuccess", JSObject().apply {
                put("eventType",     event.eventType)
                put("attemptNumber", event.attemptNumber)
            })
            is ServiceEvent.PrioritySyncFailed  -> notifyListeners("prioritySyncFailed", JSObject().apply {
                put("eventType",  event.eventType)
                put("httpStatus", event.httpStatus)
                put("attempts",   event.attempts)
            })
            ServiceEvent.SyncStart            -> notifyListeners("syncStart", JSObject())
            is ServiceEvent.SyncProgress      -> notifyListeners("syncProgress", JSObject().apply {
                put("progress", event.progress)
            })
            is ServiceEvent.SyncSuccess       -> notifyListeners("syncSuccess", JSObject().apply {
                put("sent", event.sent)
            })
            is ServiceEvent.SyncError         -> notifyListeners("syncError", JSObject().apply {
                put("httpStatus", event.httpStatus)
                put("message",    event.message)
            })
        }
    }

    // ── Plugin methods ────────────────────────────────────────────────────────

    @PluginMethod
    fun configure(call: PluginCall) {
        try {
            facade.configure(GachConfigMapper.fromJSObject(call.data))
            call.resolve()
        } catch (e: Exception) { call.reject("Configuration error: ${e.message}", "400", e) }
    }

    @PluginMethod
    fun start(call: PluginCall) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "startAfterPermission"); return
        }
        facade.start(); call.resolve()
    }

    @PermissionCallback
    private fun startAfterPermission(call: PluginCall) {
        emitAuthorization()
        if (getPermissionState("location") != PermissionState.GRANTED) {
            call.reject("Location permission denied", "403"); return
        }
        facade.start(); call.resolve()
    }

    @PluginMethod
    fun stop(call: PluginCall) { facade.stop(); call.resolve() }

    @PluginMethod
    fun getCurrentLocation(call: PluginCall) {
        val timeout = call.getLong("timeout") ?: 20_000L
        bridge.execute {
            val loc = facade.getCurrentLocation(timeout)
            bridge.activity.runOnUiThread {
                if (loc != null) {
                    try { call.resolve(JSObject.fromJSONObject(loc.toJSONObjectWithId())) }
                    catch (e: Exception) { call.reject("JSON error: ${e.message}", "400") }
                } else call.reject("Timeout waiting for location", "408")
            }
        }
    }

    @PluginMethod
    fun getStationaryLocation(call: PluginCall) {
        val stationary = facade.getStationaryLocation()
        if (stationary == null) {
            // Parity with iOS: resolve with no payload when there is no fix yet.
            call.resolve(); return
        }
        val (loc, radius) = stationary
        try {
            val js = JSObject.fromJSONObject(loc.toJSONObjectWithId())
            js.put("radius", radius)
            call.resolve(js)
        } catch (e: Exception) { call.reject("JSON error: ${e.message}", "400") }
    }

    @PluginMethod
    fun getLocations(call: PluginCall) {
        try { call.resolve(JSObject().apply { put("locations", locationsToArray(facade.getAllLocations())) }) }
        catch (e: Exception) { call.reject(e.message, "400", e) }
    }

    @PluginMethod
    fun getValidLocations(call: PluginCall) {
        try { call.resolve(JSObject().apply { put("locations", locationsToArray(facade.getValidLocations())) }) }
        catch (e: Exception) { call.reject(e.message, "400", e) }
    }

    @PluginMethod
    fun getValidLocationsAndDelete(call: PluginCall) {
        try { call.resolve(JSObject().apply { put("locations", locationsToArray(facade.getValidLocationsAndDelete())) }) }
        catch (e: Exception) { call.reject(e.message, "400", e) }
    }

    @PluginMethod
    fun getConfig(call: PluginCall) {
        try { call.resolve(GachConfigMapper.toJSObject(facade.getConfig())) }
        catch (e: Exception) { call.reject(e.message, "400", e) }
    }

    @PluginMethod
    fun deleteLocation(call: PluginCall) {
        val id = call.getLong("locationId") ?: run { call.reject("locationId required", "400"); return }
        facade.deleteLocation(id); call.resolve()
    }

    @PluginMethod fun deleteAllLocations(call: PluginCall) { facade.deleteAllLocations(); call.resolve() }

    @PluginMethod
    fun isLocationEnabled(call: PluginCall) {
        val lm = bridge.activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        call.resolve(JSObject().apply {
            put("enabled", lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                           lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
        })
    }

    @PluginMethod
    fun showAppSettings(call: PluginCall) {
        try {
            bridge.activity.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(android.net.Uri.parse("package:${bridge.activity.packageName}")))
        } catch (_: Exception) {}
        call.resolve()
    }

    @PluginMethod fun openSettings(call: PluginCall) = showAppSettings(call)

    @PluginMethod
    fun showLocationSettings(call: PluginCall) {
        try {
            bridge.activity.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } catch (_: Exception) {}
        call.resolve()
    }

    @PluginMethod fun watchLocationMode(call: PluginCall) = call.resolve()
    @PluginMethod fun stopWatchingLocationMode(call: PluginCall) = call.resolve()

    @PluginMethod
    fun getLogEntries(call: PluginCall) {
        try {
            val limit    = call.getInt("limit") ?: 0
            val fromId   = call.getInt("fromId") ?: 0
            val minLevel = call.getString("minLevel") ?: "DEBUG"
            val entries  = JSONArray()
            facade.getLogEntries(limit, fromId, minLevel).forEach { entries.put(it) }
            call.resolve(JSObject().apply { put("entries", entries) })
        } catch (e: Exception) { call.reject(e.message, "500", e) }
    }

    @PluginMethod
    fun checkStatus(call: PluginCall) {
        try {
            val lm = bridge.activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val locEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                             lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            val ctx = bridge.activity.applicationContext
            val foreground = hasPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ||
                             hasPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
            val background = if (Build.VERSION.SDK_INT >= 29)
                hasPermission(ctx, "android.permission.ACCESS_BACKGROUND_LOCATION") else true
            call.resolve(JSObject().apply {
                put("isRunning",              facade.isRunning)
                put("locationServicesEnabled", locEnabled)
                // AuthorizationStatus contract enum is 0/1/2 (was an invalid 3 here).
                put("authorization",          AuthorizationStatusMapper.status(foreground, background))
                put("hasPermissions",         foreground)
            })
        } catch (e: Exception) { call.reject(e.message, "500", e) }
    }

    @PluginMethod fun startTask(call: PluginCall) =
        call.resolve(JSObject().apply { put("taskKey", taskCounter.incrementAndGet()) })

    @PluginMethod fun endTask(call: PluginCall) = call.resolve()

    @PluginMethod fun forceSync(call: PluginCall)  { facade.forceSync();  call.resolve() }
    @PluginMethod fun clearSync(call: PluginCall)  { facade.clearSync();  call.resolve() }

    @PluginMethod
    fun getPendingSyncCount(call: PluginCall) {
        val count = facade.getPendingSyncCount()
        call.resolve(JSObject().apply { put("count", minOf(count, Int.MAX_VALUE.toLong()).toInt()) })
    }

    @PluginMethod
    fun switchMode(call: PluginCall) {
        val mode = call.getInt("mode") ?: run { call.reject("mode required", "400"); return }
        facade.switchMode(mode); call.resolve()
    }

    @PluginMethod fun startSession(call: PluginCall) { facade.startSession(); call.resolve() }
    @PluginMethod fun clearSession(call: PluginCall) { facade.clearSession(); call.resolve() }

    @PluginMethod
    fun getSessionLocations(call: PluginCall) {
        try { call.resolve(JSObject().apply { put("locations", locationsToArray(facade.getSessionLocations())) }) }
        catch (e: Exception) { call.reject(e.message, "400", e) }
    }

    @PluginMethod
    fun getSessionLocationsCount(call: PluginCall) =
        call.resolve(JSObject().apply { put("count", facade.getSessionLocations().size) })

    @PluginMethod
    fun getPluginVersion(call: PluginCall) =
        call.resolve(JSObject().apply { put("version", PLUGIN_VERSION) })

    @PluginMethod
    fun getDiagnostics(call: PluginCall) {
        try {
            val ctx = bridge.activity.applicationContext
            val lm  = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            call.resolve(JSObject().apply {
                put("isRunning", facade.isRunning)
                put("locationServicesEnabled",
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                put("fineLocationGranted",       hasPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION))
                put("coarseLocationGranted",     hasPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION))
                put("backgroundLocationGranted", if (Build.VERSION.SDK_INT >= 29)
                    hasPermission(ctx, "android.permission.ACCESS_BACKGROUND_LOCATION") else true)
                put("activityRecognitionGranted", if (Build.VERSION.SDK_INT >= 29)
                    hasPermission(ctx, "android.permission.ACTIVITY_RECOGNITION") else true)
                put("notificationPermissionGranted", if (Build.VERSION.SDK_INT >= 33)
                    hasPermission(ctx, "android.permission.POST_NOTIFICATIONS") else true)
                put("batteryOptimizationIgnored", isIgnoringBatteryOptimizations(ctx))
                put("manufacturer",    Build.MANUFACTURER ?: "")
                put("pendingSyncCount", minOf(facade.getPendingSyncCount(), Int.MAX_VALUE.toLong()).toInt())
                put("startOnBoot",     facade.getConfig().startOnBoot ?: false)
            })
        } catch (e: Exception) { call.reject(e.message, "500", e) }
    }

    @PluginMethod
    fun isIgnoringBatteryOptimizations(call: PluginCall) =
        call.resolve(JSObject().apply {
            put("whitelisted", isIgnoringBatteryOptimizations(bridge.activity.applicationContext))
        })

    @PluginMethod
    fun requestIgnoreBatteryOptimizations(call: PluginCall) {
        com.gachlab.geolocation.OemHelper.requestIgnoreBatteryOptimizations(bridge.activity)
        call.resolve(JSObject().apply {
            put("whitelisted", isIgnoringBatteryOptimizations(bridge.activity.applicationContext))
        })
    }

    @PluginMethod
    fun openBatterySettings(call: PluginCall) {
        com.gachlab.geolocation.OemHelper.openBatterySettings(bridge.activity); call.resolve()
    }

    @PluginMethod
    fun openAutoStartSettings(call: PluginCall) {
        com.gachlab.geolocation.OemHelper.openAutoStartSettings(bridge.activity); call.resolve()
    }

    @PluginMethod
    fun getManufacturerHelp(call: PluginCall) {
        try { call.resolve(JSObject.fromJSONObject(com.gachlab.geolocation.OemHelper.getManufacturerHelp())) }
        catch (e: Exception) { call.reject(e.message, "500", e) }
    }

    @PluginMethod
    fun triggerSOS(call: PluginCall) { facade.triggerSOS(call.getObject("payload")); call.resolve() }

    @PluginMethod
    fun requestBackgroundLocationPermission(call: PluginCall) {
        if (Build.VERSION.SDK_INT < 29) {
            call.resolve(JSObject().apply { put("granted", true); put("notRequired", true) }); return
        }
        if (getPermissionState("backgroundLocation") == PermissionState.GRANTED) {
            call.resolve(JSObject().apply { put("granted", true) }); return
        }
        requestPermissionForAlias("backgroundLocation", call, "backgroundLocationPermissionCallback")
    }

    @PermissionCallback
    private fun backgroundLocationPermissionCallback(call: PluginCall) {
        emitAuthorization()
        val granted = getPermissionState("backgroundLocation") == PermissionState.GRANTED
        call.resolve(JSObject().apply {
            put("granted", granted)
            if (!granted) put("denied", JSONArray().put("android.permission.ACCESS_BACKGROUND_LOCATION"))
        })
    }

    @PluginMethod
    fun requestActivityRecognitionPermission(call: PluginCall) {
        if (Build.VERSION.SDK_INT < 29) {
            call.resolve(JSObject().apply { put("granted", true); put("notRequired", true) }); return
        }
        if (getPermissionState("activity") == PermissionState.GRANTED) {
            call.resolve(JSObject().apply { put("granted", true) }); return
        }
        requestPermissionForAlias("activity", call, "activityPermissionCallback")
    }

    @PermissionCallback
    private fun activityPermissionCallback(call: PluginCall) {
        val granted = getPermissionState("activity") == PermissionState.GRANTED
        call.resolve(JSObject().apply {
            put("granted", granted)
            if (!granted) put("denied", JSONArray().put("android.permission.ACTIVITY_RECOGNITION"))
        })
    }

    @PluginMethod
    fun requestNotificationPermission(call: PluginCall) {
        if (Build.VERSION.SDK_INT < 33) {
            call.resolve(JSObject().apply { put("granted", true); put("notRequired", true) }); return
        }
        if (getPermissionState("notifications") == PermissionState.GRANTED) {
            call.resolve(JSObject().apply { put("granted", true) }); return
        }
        requestPermissionForAlias("notifications", call, "notificationsPermissionCallback")
    }

    @PermissionCallback
    private fun notificationsPermissionCallback(call: PluginCall) {
        val granted = getPermissionState("notifications") == PermissionState.GRANTED
        call.resolve(JSObject().apply {
            put("granted", granted)
            if (!granted) put("denied", JSONArray().put("android.permission.POST_NOTIFICATIONS"))
        })
    }

    @PluginMethod
    fun addGeofences(call: PluginCall) {
        try {
            val arr = call.getArray("geofences") ?: run { call.reject("geofences required", "400"); return }
            val list = (0 until arr.length()).map { BGGeofence.fromJSON(arr.getJSONObject(it)) }
            facade.addGeofences(list)
            call.resolve()
        } catch (e: Exception) { call.reject(e.message, "400", e) }
    }

    @PluginMethod
    fun removeGeofences(call: PluginCall) {
        try {
            val ids = call.getArray("ids")?.let { arr ->
                (0 until arr.length()).map { arr.getString(it) }
            }
            facade.removeGeofences(ids)
            call.resolve()
        } catch (e: Exception) { call.reject(e.message, "400", e) }
    }

    @PluginMethod
    fun getGeofences(call: PluginCall) {
        try {
            val arr = BGGeofence.listToJSON(facade.getGeofences())
            call.resolve(JSObject().apply { put("geofences", arr) })
        } catch (e: Exception) { call.reject(e.message, "400", e) }
    }

    @PluginMethod
    fun getBackgroundKillReason(call: PluginCall) {
        val (reason, timestamp) = facade.getBackgroundKillReason()
        call.resolve(JSObject().apply {
            if (reason != null) put("reason", reason) else put("reason", JSObject.NULL)
            if (timestamp != null) put("timestamp", timestamp) else put("timestamp", JSObject.NULL)
        })
    }

    @PluginMethod
    override fun removeAllListeners(call: PluginCall) = super.removeAllListeners(call)

    @PluginMethod
    fun getTripScore(call: PluginCall) {
        val score = facade.getTripScore()
        if (score != null) {
            call.resolve(scoreToJS(score))
        } else {
            call.resolve(JSObject().apply {
                put("overall", 100)
                put("breakdown", JSObject().apply {
                    put("speeding", 100); put("hardBraking", 100); put("rapidAcceleration", 100)
                    put("sharpTurns", 100); put("phoneUsage", 100)
                })
                put("events", JSONArray())
                put("tripId", ""); put("startedAt", 0); put("endedAt", 0)
                put("distanceKm", 0.0); put("totalIdleMs", 0L); put("idleCount", 0)
            })
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun BGLocation.toJS(): JSObject = try {
        JSObject.fromJSONObject(toJSONObjectWithId())
    } catch (_: Exception) { JSObject() }

    private fun notify(event: String, payload: JSObject) {
        try { notifyListeners(event, payload) } catch (_: Exception) {}
    }

    private fun locationsToArray(locations: List<BGLocation>): JSONArray {
        val arr = JSONArray()
        locations.forEach { try { arr.put(it.toJSONObjectWithId()) } catch (_: Exception) {} }
        return arr
    }

    private fun scoreToJS(score: TripScore): JSObject = JSObject().apply {
        put("overall", score.overall)
        put("breakdown", JSObject().apply {
            put("speeding",          score.breakdown.speeding)
            put("hardBraking",       score.breakdown.hardBraking)
            put("rapidAcceleration", score.breakdown.rapidAcceleration)
            put("sharpTurns",        score.breakdown.sharpTurns)
            put("phoneUsage",        score.breakdown.phoneUsage)
        })
        val evArr = JSONArray()
        score.events.forEach { e ->
            evArr.put(org.json.JSONObject().apply {
                put("type",      e.type)
                put("timestamp", e.timestamp)
                put("penalty",   e.penalty)
                put("location",  org.json.JSONObject().apply {
                    put("latitude",  e.latitude)
                    put("longitude", e.longitude)
                })
            })
        }
        put("events",      evArr)
        put("tripId",      score.tripId)
        put("startedAt",   score.startedAt)
        put("endedAt",     score.endedAt)
        put("distanceKm",  score.distanceKm)
        put("totalIdleMs", score.totalIdleMs)
        put("idleCount",   score.idleCount)
    }

    /**
     * Emit the `authorization` event reflecting the current location-permission
     * state. Android has no system callback for permission changes, so this is
     * fired from the permission-request callbacks — the parity equivalent of
     * iOS `locationManager(_:didChangeAuthorization:)`.
     */
    private fun emitAuthorization() {
        val ctx = bridge.activity.applicationContext
        val foreground = hasPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) ||
                         hasPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
        val background = if (Build.VERSION.SDK_INT >= 29)
            hasPermission(ctx, "android.permission.ACCESS_BACKGROUND_LOCATION") else true
        val status = AuthorizationStatusMapper.status(foreground, background)
        BGLog.i("Authorization changed: $status")
        notify("authorization", JSObject().apply { put("status", status) })
    }

    private fun hasPermission(ctx: Context, permission: String): Boolean = try {
        ctx.packageManager.checkPermission(permission, ctx.packageName) == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    private fun isIgnoringBatteryOptimizations(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return try {
            (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
                .isIgnoringBatteryOptimizations(ctx.packageName)
        } catch (_: Exception) { false }
    }

    companion object {
        private const val PLUGIN_VERSION = "1.5.0"
    }
}
