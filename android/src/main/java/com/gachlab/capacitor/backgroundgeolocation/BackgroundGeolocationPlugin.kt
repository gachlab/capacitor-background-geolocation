// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.capacitor.backgroundgeolocation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import com.getcapacitor.JSObject
import com.getcapacitor.PermissionState
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.getcapacitor.annotation.Permission
import com.getcapacitor.annotation.PermissionCallback
import com.gachlab.geolocation.BGFacade
import com.gachlab.geolocation.BGLocation
import com.gachlab.geolocation.ServiceEvent
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
            is ServiceEvent.HardBrake         -> notify("hardBrake",          event.loc.toJS())
            is ServiceEvent.RapidAcceleration -> notify("rapidAcceleration",  event.loc.toJS())
            is ServiceEvent.SharpTurn         -> notify("sharpTurn",          event.loc.toJS())
            is ServiceEvent.PossibleCrash     -> notify("possibleCrash",      event.loc.toJS())
            is ServiceEvent.TripEnd           -> notifyListeners("tripEnd", JSObject().apply {
                put("location",   event.loc.toJSONObjectWithId())
                put("distance",   event.distanceMeters)
                put("durationMs", event.durationMs)
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
                JSObject().apply { event.locationId?.let { put("locationId", it) } })
            ServiceEvent.ServiceStarted       -> notifyListeners("start",              JSObject())
            ServiceEvent.ServiceStopped       -> notifyListeners("stop",               JSObject())
            is ServiceEvent.ServiceRestarted  -> notifyListeners("serviceRestarted",
                JSObject().apply { put("reason", event.reason) })
            ServiceEvent.AbortRequested       -> notifyListeners("abort_requested",    JSObject())
            ServiceEvent.HttpAuthorization    -> notifyListeners("http_authorization", JSObject())
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
    fun getLogEntries(call: PluginCall) =
        call.resolve(JSObject().apply { put("entries", JSONArray()) })

    @PluginMethod
    fun checkStatus(call: PluginCall) {
        try {
            val lm = bridge.activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val locEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                             lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            val fineGranted = hasPermission(bridge.activity.applicationContext,
                Manifest.permission.ACCESS_FINE_LOCATION)
            call.resolve(JSObject().apply {
                put("isRunning",              facade.isRunning)
                put("locationServicesEnabled", locEnabled)
                put("authorization",          if (fineGranted) 3 else 2)
                put("hasPermissions",         fineGranted)
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
    fun triggerSOS(call: PluginCall) { facade.triggerSOS(null); call.resolve() }

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
    fun getBackgroundKillReason(call: PluginCall) {
        val (reason, timestamp) = facade.getBackgroundKillReason()
        call.resolve(JSObject().apply {
            if (reason != null) put("reason", reason) else put("reason", JSObject.NULL)
            if (timestamp != null) put("timestamp", timestamp) else put("timestamp", JSObject.NULL)
        })
    }

    @PluginMethod
    fun registerHeadlessTask(call: PluginCall) = call.resolve()

    @PluginMethod
    override fun removeAllListeners(call: PluginCall) = super.removeAllListeners(call)

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
        private const val PLUGIN_VERSION = "1.1.0"
    }
}
