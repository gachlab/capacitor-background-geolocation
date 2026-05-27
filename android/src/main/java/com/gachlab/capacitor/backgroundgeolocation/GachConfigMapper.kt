// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.capacitor.backgroundgeolocation

import com.getcapacitor.JSObject
import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.BGConfig.DrivingEventsOptions
import com.gachlab.geolocation.ArrayListLocationTemplate
import com.gachlab.geolocation.HashMapLocationTemplate
import com.gachlab.geolocation.LocationTemplateFactory
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/**
 * Maps between Capacitor's [JSObject] / [JSONObject] and [BGConfig].
 *
 * Kotlin port of `com.josuelmm.capacitor.backgroundgeolocation.ConfigMapper` and
 * `com.marianhello.bgloc.data.ConfigJsonMapper`.
 *
 * All public functions are `@JvmStatic` for interoperability with the existing Java
 * codebase during the migration period.
 */
object GachConfigMapper {

    // ── JS (Capacitor) → BGConfig ─────────────────────────────────────────────

    /**
     * Build a [BGConfig] from a Capacitor [JSObject] (which extends [JSONObject]).
     * Only keys present in [obj] are applied; absent keys leave the field null so
     * that [BGConfig.merge] can fill in defaults later.
     */
    @JvmStatic
    @Throws(JSONException::class)
    fun fromJSObject(obj: JSObject): BGConfig = fromJSONObject(obj)

    /**
     * Build a [BGConfig] from a plain [JSONObject] (e.g. read from SQLite storage).
     * Starts from [BGConfig.getDefault] and applies whatever keys are present.
     */
    @JvmStatic
    @Throws(JSONException::class)
    fun fromJSONObject(j: JSONObject): BGConfig {
        val c = BGConfig()

        if (j.has("stationaryRadius"))    c.stationaryRadius    = j.getDouble("stationaryRadius").toFloat()
        if (j.has("distanceFilter"))      c.distanceFilter      = j.getInt("distanceFilter")
        if (j.has("desiredAccuracy"))     c.desiredAccuracy     = j.getInt("desiredAccuracy")
        if (j.has("debug"))               c.debug               = j.getBoolean("debug")

        if (j.has("notificationTitle"))
            c.notificationTitle   = if (!j.isNull("notificationTitle"))  j.getString("notificationTitle")  else BGConfig.NULL_STRING
        if (j.has("notificationText"))
            c.notificationText    = if (!j.isNull("notificationText"))   j.getString("notificationText")   else BGConfig.NULL_STRING
        if (j.has("notificationSyncTitle"))
            c.notificationSyncTitle = if (j.isNull("notificationSyncTitle")) null else j.getString("notificationSyncTitle")
        if (j.has("notificationSyncText"))
            c.notificationSyncText  = if (j.isNull("notificationSyncText"))  null else j.getString("notificationSyncText")
        if (j.has("notificationSyncCompletedText"))
            c.notificationSyncCompletedText = if (j.isNull("notificationSyncCompletedText")) null else j.getString("notificationSyncCompletedText")
        if (j.has("notificationSyncFailedText"))
            c.notificationSyncFailedText    = if (j.isNull("notificationSyncFailedText"))    null else j.getString("notificationSyncFailedText")
        if (j.has("notificationsEnabled")) c.notificationsEnabled = j.getBoolean("notificationsEnabled")
        if (j.has("notificationIconColor"))
            c.notificationIconColor = if (!j.isNull("notificationIconColor")) j.getString("notificationIconColor") else BGConfig.NULL_STRING
        if (j.has("notificationIconLarge"))
            c.notificationIconLarge = if (!j.isNull("notificationIconLarge")) j.getString("notificationIconLarge") else BGConfig.NULL_STRING
        if (j.has("notificationIconSmall"))
            c.notificationIconSmall = if (!j.isNull("notificationIconSmall")) j.getString("notificationIconSmall") else BGConfig.NULL_STRING
        if (j.has("showTime"))     c.showTime     = j.getBoolean("showTime")
        if (j.has("showDistance")) c.showDistance = j.getBoolean("showDistance")

        if (j.has("stopOnTerminate"))    c.stopOnTerminate    = j.getBoolean("stopOnTerminate")
        if (j.has("startOnBoot"))        c.startOnBoot        = j.getBoolean("startOnBoot")
        if (j.has("startForeground"))    c.startForeground    = j.getBoolean("startForeground")
        if (j.has("stopOnStillActivity")) c.stopOnStillActivity = j.getBoolean("stopOnStillActivity")

        if (j.has("locationProvider"))    c.locationProvider    = j.getInt("locationProvider")
        if (j.has("interval"))            c.interval            = j.getInt("interval")
        if (j.has("fastestInterval"))     c.fastestInterval     = j.getInt("fastestInterval")
        if (j.has("activitiesInterval"))  c.activitiesInterval  = j.getInt("activitiesInterval")

        if (j.has("url"))
            c.url     = if (!j.isNull("url"))     j.getString("url")     else BGConfig.NULL_STRING
        if (j.has("syncUrl"))
            c.syncUrl = if (!j.isNull("syncUrl")) j.getString("syncUrl") else BGConfig.NULL_STRING
        if (j.has("syncThreshold")) c.syncThreshold = j.getInt("syncThreshold")
        // Both "sync" (JS API) and "syncEnabled" (storage) are accepted.
        if (j.has("sync"))         c.syncEnabled = j.getBoolean("sync")
        if (j.has("syncEnabled"))  c.syncEnabled = j.getBoolean("syncEnabled")
        if (j.has("maxLocations")) c.maxLocations = j.getInt("maxLocations")

        // HTTP headers — accepts both "httpHeaders" and "headers" keys (JS compat).
        if (j.has("httpHeaders")) c.httpHeaders = jsonObjectToHashMap(j.getJSONObject("httpHeaders"))
        if (j.has("headers"))     c.httpHeaders = jsonObjectToHashMap(j.getJSONObject("headers"))
        if (has(j, "queryParams")) c.queryParams = jsonObjectToHashMapAny(j.getJSONObject("queryParams"))

        // Location body template ("postTemplate" or "bodyTemplate").
        if (j.has("postTemplate")) {
            c.template = if (j.isNull("postTemplate")) LocationTemplateFactory.empty()
                         else LocationTemplateFactory.fromJSON(j.get("postTemplate"))
        }
        if (j.has("bodyTemplate")) {
            c.template = if (j.isNull("bodyTemplate")) LocationTemplateFactory.empty()
                         else LocationTemplateFactory.fromJSON(j.get("bodyTemplate"))
        }

        if (has(j, "httpMethod"))        c.httpMethod        = j.getString("httpMethod").uppercase(Locale.US)
        if (has(j, "syncHttpMethod"))    c.syncHttpMethod    = j.getString("syncHttpMethod").uppercase(Locale.US)
        if (has(j, "httpMode"))          c.httpMode          = j.getString("httpMode").lowercase(Locale.US)
        if (has(j, "syncMode"))          c.syncMode          = j.getString("syncMode").lowercase(Locale.US)
        if (has(j, "mockLocationPolicy")) c.mockLocationPolicy = j.getString("mockLocationPolicy").lowercase(Locale.US)
        if (j.has("heartbeatInterval") && !j.isNull("heartbeatInterval"))
            c.heartbeatInterval = j.getInt("heartbeatInterval")

        if (j.has("enableWatchdog")) c.enableWatchdog = j.getBoolean("enableWatchdog")
        if (j.has("watchdogIntervalMs") && !j.isNull("watchdogIntervalMs"))
            c.watchdogIntervalMs = j.getLong("watchdogIntervalMs")
        if (j.has("restartOnKill")) c.restartOnKill = j.getBoolean("restartOnKill")

        if (j.has("includeBattery")) c.includeBattery = j.getBoolean("includeBattery")
        if (has(j, "wakeLockMode"))  c.wakeLockMode    = j.getString("wakeLockMode")

        if (j.has("stationaryTimeout") && !j.isNull("stationaryTimeout"))
            c.stationaryTimeout      = j.getInt("stationaryTimeout")
        if (j.has("stationaryPollInterval") && !j.isNull("stationaryPollInterval"))
            c.stationaryPollInterval = j.getInt("stationaryPollInterval")
        if (j.has("stationaryPollFast") && !j.isNull("stationaryPollFast"))
            c.stationaryPollFast     = j.getInt("stationaryPollFast")
        if (j.has("activityConfidenceThreshold") && !j.isNull("activityConfidenceThreshold"))
            c.activityConfidenceThreshold = j.getInt("activityConfidenceThreshold")
        if (j.has("maxAcceptedAccuracy") && !j.isNull("maxAcceptedAccuracy"))
            c.maxAcceptedAccuracy = j.getDouble("maxAcceptedAccuracy").toFloat()

        if (has(j, "drivingEvents")) c.drivingEvents = drivingEventsFromJSON(j.getJSONObject("drivingEvents"))

        return c
    }

    // ── BGConfig → JS (Capacitor) ─────────────────────────────────────────────

    @JvmStatic
    @Throws(JSONException::class)
    fun toJSObject(config: BGConfig): JSObject {
        val json = JSObject()
        json.put("stationaryRadius",           config.stationaryRadius)
        json.put("distanceFilter",             config.distanceFilter)
        json.put("desiredAccuracy",            config.desiredAccuracy)
        json.put("debug",                      config.debug)
        json.put("notificationsEnabled",       config.notificationsEnabled)
        json.put("notificationTitle",          nullableString(config.notificationTitle))
        json.put("notificationText",           nullableString(config.notificationText))
        json.put("notificationSyncTitle",      config.notificationSyncTitle)
        json.put("notificationSyncText",       config.notificationSyncText)
        json.put("notificationSyncCompletedText", config.notificationSyncCompletedText)
        json.put("notificationSyncFailedText", config.notificationSyncFailedText)
        json.put("notificationIconLarge",      nullableString(config.notificationIconLarge))
        json.put("notificationIconSmall",      nullableString(config.notificationIconSmall))
        json.put("notificationIconColor",      nullableString(config.notificationIconColor))
        json.put("stopOnTerminate",            config.stopOnTerminate)
        json.put("startOnBoot",                config.startOnBoot)
        json.put("startForeground",            config.startForeground)
        json.put("locationProvider",           config.locationProvider)
        json.put("interval",                   config.interval)
        json.put("fastestInterval",            config.fastestInterval)
        json.put("activitiesInterval",         config.activitiesInterval)
        json.put("stopOnStillActivity",        config.stopOnStillActivity)
        json.put("url",                        nullableString(config.url))
        json.put("syncUrl",                    nullableString(config.syncUrl))
        json.put("syncThreshold",              config.syncThreshold)
        json.put("sync",                       config.syncEnabled)
        json.put("httpHeaders",                JSONObject(config.httpHeaders ?: emptyMap<String, String>()))
        json.put("maxLocations",               config.maxLocations)
        json.put("enableWatchdog",             config.enableWatchdog == true)
        json.put("watchdogIntervalMs",         config.watchdogIntervalMs)
        json.put("restartOnKill",              config.restartOnKill ?: true)
        json.put("showTime",                   config.showTime == true)
        json.put("showDistance",               config.showDistance == true)

        // Template serialization
        val tplObj: Any = when (val t = config.template) {
            is HashMapLocationTemplate   -> t.toDefinitionJson() ?: JSONObject.NULL
            is ArrayListLocationTemplate -> t.toDefinitionJson() ?: JSONObject.NULL
            else                         -> JSONObject.NULL
        }
        json.put("postTemplate", tplObj)

        json.put("httpMethod",      config.httpMethod ?: BGConfig.DEFAULT_HTTP_METHOD)
        json.put("syncHttpMethod",  config.syncHttpMethod ?: BGConfig.DEFAULT_SYNC_HTTP_METHOD)
        json.put("httpMode",        config.httpMode  ?: BGConfig.DEFAULT_HTTP_MODE)
        json.put("syncMode",        config.syncMode  ?: BGConfig.DEFAULT_SYNC_MODE)
        json.put("queryParams",     config.queryParams?.let { JSONObject(it) } ?: JSONObject.NULL)
        json.put("heartbeatInterval", config.heartbeatInterval ?: 0)
        json.put("mockLocationPolicy", config.mockLocationPolicy ?: BGConfig.DEFAULT_MOCK_LOCATION_POLICY)

        config.drivingEvents?.let { json.put("drivingEvents", drivingEventsToJSON(it)) }

        json.put("includeBattery",             config.includeBattery ?: true)
        json.put("wakeLockMode",               config.wakeLockMode ?: BGConfig.DEFAULT_WAKE_LOCK_MODE)
        json.put("stationaryTimeout",          config.stationaryTimeout)
        json.put("stationaryPollInterval",     config.stationaryPollInterval)
        json.put("stationaryPollFast",         config.stationaryPollFast)
        json.put("activityConfidenceThreshold", config.activityConfidenceThreshold)
        json.put("maxAcceptedAccuracy",        config.maxAcceptedAccuracy)

        return json
    }

    // ── BGConfig → JSONObject (for SQLite storage) ────────────────────────────

    @JvmStatic
    @Throws(JSONException::class)
    fun toJSONObject(config: BGConfig): JSONObject {
        val j = JSONObject()
        j.put("stationaryRadius",           config.stationaryRadius)
        j.put("distanceFilter",             config.distanceFilter)
        j.put("desiredAccuracy",            config.desiredAccuracy)
        j.put("debug",                      config.debug)
        j.put("notificationTitle",          nullableString(config.notificationTitle))
        j.put("notificationText",           nullableString(config.notificationText))
        j.put("notificationSyncTitle",      nullableString(config.notificationSyncTitle))
        j.put("notificationSyncText",       nullableString(config.notificationSyncText))
        j.put("notificationSyncCompletedText", nullableString(config.notificationSyncCompletedText))
        j.put("notificationSyncFailedText", nullableString(config.notificationSyncFailedText))
        j.put("notificationIconLarge",      nullableString(config.notificationIconLarge))
        j.put("notificationIconSmall",      nullableString(config.notificationIconSmall))
        j.put("notificationIconColor",      nullableString(config.notificationIconColor))
        j.put("locationProvider",           config.locationProvider)
        j.put("interval",                   config.interval)
        j.put("fastestInterval",            config.fastestInterval)
        j.put("activitiesInterval",         config.activitiesInterval)
        j.put("stopOnTerminate",            config.stopOnTerminate)
        j.put("startOnBoot",                config.startOnBoot)
        j.put("startForeground",            config.startForeground)
        j.put("notificationsEnabled",       config.notificationsEnabled)
        j.put("stopOnStillActivity",        config.stopOnStillActivity)
        j.put("url",                        nullableString(config.url))
        j.put("syncUrl",                    nullableString(config.syncUrl))
        j.put("syncThreshold",              config.syncThreshold)
        j.put("syncEnabled",                config.syncEnabled)
        j.put("maxLocations",               config.maxLocations)
        j.put("enableWatchdog",             config.enableWatchdog)
        config.watchdogIntervalMs?.let { j.put("watchdogIntervalMs", it) }
        j.put("restartOnKill",              config.restartOnKill ?: true)
        j.put("showTime",                   config.showTime)
        j.put("showDistance",               config.showDistance)
        j.put("httpMethod",                 config.httpMethod)
        j.put("syncHttpMethod",             config.syncHttpMethod)
        j.put("httpMode",                   config.httpMode)
        j.put("syncMode",                   config.syncMode)
        j.put("heartbeatInterval",          config.heartbeatInterval)
        j.put("mockLocationPolicy",         config.mockLocationPolicy)
        j.put("includeBattery",             config.includeBattery)
        j.put("wakeLockMode",               config.wakeLockMode)
        j.put("stationaryTimeout",          config.stationaryTimeout)
        j.put("stationaryPollInterval",     config.stationaryPollInterval)
        j.put("stationaryPollFast",         config.stationaryPollFast)
        j.put("activityConfidenceThreshold", config.activityConfidenceThreshold)
        j.put("maxAcceptedAccuracy",        config.maxAcceptedAccuracy)
        config.httpHeaders?.let { j.put("httpHeaders", JSONObject(it)) }
        config.queryParams?.let { j.put("queryParams", JSONObject(it)) }
        config.drivingEvents?.let { j.put("drivingEvents", drivingEventsToJSON(it)) }
        return j
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun has(j: JSONObject, key: String): Boolean = j.has(key) && !j.isNull(key)

    /** Map [BGConfig.NULL_STRING] or null to [JSONObject.NULL] so the sentinel survives. */
    private fun nullableString(s: String?): Any =
        if (s == null || s === BGConfig.NULL_STRING) JSONObject.NULL else s

    @Throws(JSONException::class)
    private fun jsonObjectToHashMap(obj: JSONObject): HashMap<String, String> {
        val map = HashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            map[k] = obj.getString(k)
        }
        return map
    }

    @Throws(JSONException::class)
    private fun jsonObjectToHashMapAny(obj: JSONObject): HashMap<String, String> {
        val map = HashMap<String, String>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = obj.get(k)
            map[k] = if (v == null || v == JSONObject.NULL) "" else v.toString()
        }
        return map
    }

    @Throws(JSONException::class)
    private fun drivingEventsFromJSON(de: JSONObject): DrivingEventsOptions {
        var enabled             = false
        var speedLimitKmh       = 0.0
        var minMovingSpeedMps   = 1.0
        var stoppedDurationMs   = 60_000L
        var minTripSpeedMps     = 3.0
        var minTripDurationMs   = 30_000L
        var hardBrakeMps2       = 3.5
        var rapidAccelMps2      = 3.5
        var sharpTurnDegPerSec  = 30.0
        var crashImpactKmh      = 25.0
        var crashWindowMs       = 2_000L
        var sensorFusion        = false
        var crashImpactG        = 3.0
        var sensorCrashCooldownMs = 10_000L
        var phoneUsageWindowMs  = 4_000L
        var phoneUsageCooldownMs = 60_000L

        if (de.has("enabled"))               enabled             = de.getBoolean("enabled")
        if (de.has("speedLimit"))            speedLimitKmh       = de.getDouble("speedLimit")
        if (de.has("minMovingSpeed"))        minMovingSpeedMps   = de.getDouble("minMovingSpeed")
        if (de.has("stoppedDuration"))       stoppedDurationMs   = de.getLong("stoppedDuration")
        if (de.has("minTripSpeed"))          minTripSpeedMps     = de.getDouble("minTripSpeed")
        if (de.has("minTripDuration"))       minTripDurationMs   = de.getLong("minTripDuration")
        if (de.has("hardBrakeMps2"))         hardBrakeMps2       = de.getDouble("hardBrakeMps2")
        if (de.has("rapidAccelMps2"))        rapidAccelMps2      = de.getDouble("rapidAccelMps2")
        if (de.has("sharpTurnDegPerSec"))    sharpTurnDegPerSec  = de.getDouble("sharpTurnDegPerSec")
        if (de.has("crashImpactKmh"))        crashImpactKmh      = de.getDouble("crashImpactKmh")
        if (de.has("crashWindowMs"))         crashWindowMs       = de.getLong("crashWindowMs")
        if (de.has("sensorFusion"))          sensorFusion        = de.getBoolean("sensorFusion")
        if (de.has("crashImpactG"))          crashImpactG        = de.getDouble("crashImpactG")
        if (de.has("sensorCrashCooldownMs")) sensorCrashCooldownMs = de.getLong("sensorCrashCooldownMs")
        if (de.has("phoneUsageWindowMs"))    phoneUsageWindowMs  = de.getLong("phoneUsageWindowMs")
        if (de.has("phoneUsageCooldownMs"))  phoneUsageCooldownMs = de.getLong("phoneUsageCooldownMs")

        return DrivingEventsOptions(
            enabled             = enabled,
            speedLimitKmh       = speedLimitKmh,
            minMovingSpeedMps   = minMovingSpeedMps,
            stoppedDurationMs   = stoppedDurationMs,
            minTripSpeedMps     = minTripSpeedMps,
            minTripDurationMs   = minTripDurationMs,
            hardBrakeMps2       = hardBrakeMps2,
            rapidAccelMps2      = rapidAccelMps2,
            sharpTurnDegPerSec  = sharpTurnDegPerSec,
            crashImpactKmh      = crashImpactKmh,
            crashWindowMs       = crashWindowMs,
            sensorFusion        = sensorFusion,
            crashImpactG        = crashImpactG,
            sensorCrashCooldownMs = sensorCrashCooldownMs,
            phoneUsageWindowMs  = phoneUsageWindowMs,
            phoneUsageCooldownMs = phoneUsageCooldownMs,
        )
    }

    @Throws(JSONException::class)
    private fun drivingEventsToJSON(de: DrivingEventsOptions): JSONObject {
        val j = JSONObject()
        j.put("enabled",             de.enabled)
        j.put("speedLimit",          de.speedLimitKmh)
        j.put("minMovingSpeed",      de.minMovingSpeedMps)
        j.put("stoppedDuration",     de.stoppedDurationMs)
        j.put("minTripSpeed",        de.minTripSpeedMps)
        j.put("minTripDuration",     de.minTripDurationMs)
        j.put("hardBrakeMps2",       de.hardBrakeMps2)
        j.put("rapidAccelMps2",      de.rapidAccelMps2)
        j.put("sharpTurnDegPerSec",  de.sharpTurnDegPerSec)
        j.put("crashImpactKmh",      de.crashImpactKmh)
        j.put("crashWindowMs",       de.crashWindowMs)
        j.put("sensorFusion",        de.sensorFusion)
        j.put("crashImpactG",        de.crashImpactG)
        j.put("sensorCrashCooldownMs", de.sensorCrashCooldownMs)
        j.put("phoneUsageWindowMs",  de.phoneUsageWindowMs)
        j.put("phoneUsageCooldownMs", de.phoneUsageCooldownMs)
        return j
    }
}
