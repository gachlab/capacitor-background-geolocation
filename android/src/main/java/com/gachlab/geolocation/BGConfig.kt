// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
import org.json.JSONException
import org.json.JSONObject
import java.util.Locale

/**
 * Configuration for the background geolocation service.
 *
 * Kotlin port of `com.marianhello.bgloc.Config`.
 *
 * All fields are nullable so that a partial config object (e.g. one built from a
 * JS `configure()` call) can be merged onto a fully-populated default via [merge].
 * Companion object constants mirror the Java `DEFAULT_*` / provider-id constants.
 */
class BGConfig() : Parcelable {

    // ── Location provider ─────────────────────────────────────────────────────
    var stationaryRadius: Float?    = null
    var distanceFilter: Int?        = null
    var desiredAccuracy: Int?       = null
    var locationProvider: Int?      = null
    var interval: Int?              = null           // ms
    var fastestInterval: Int?       = null           // ms
    var activitiesInterval: Int?    = null           // ms

    // ── Debug / behaviour ─────────────────────────────────────────────────────
    var debug: Boolean?             = null
    var stopOnTerminate: Boolean?   = null
    var startOnBoot: Boolean?       = null
    var startForeground: Boolean?   = null
    var notificationsEnabled: Boolean? = null
    var stopOnStillActivity: Boolean?  = null

    // ── Notification strings ──────────────────────────────────────────────────
    var notificationTitle: String?              = null
    var notificationText: String?               = null
    var notificationSyncTitle: String?          = null
    var notificationSyncText: String?           = null
    var notificationSyncCompletedText: String?  = null
    var notificationSyncFailedText: String?     = null
    var notificationIconLarge: String?          = null
    var notificationIconSmall: String?          = null
    var notificationIconColor: String?          = null
    var showTime: Boolean?                      = null
    var showDistance: Boolean?                  = null

    // ── HTTP transport ────────────────────────────────────────────────────────
    var url: String?            = null
    var syncUrl: String?        = null
    var syncThreshold: Int?     = null
    var syncEnabled: Boolean?   = null
    var maxLocations: Int?      = null
    var httpHeaders: HashMap<String, String>?  = null
    var queryParams: HashMap<String, String>?  = null
    var httpMethod: String?     = null
    var syncHttpMethod: String? = null
    var httpMode: String?       = null
    var syncMode: String?       = null
    /**
     * JSON-serializable location body template. Stored as a serializable object for
     * Parcel compatibility; mappers are responsible for converting to/from
     * `com.marianhello.bgloc.data.LocationTemplate`.
     */
    var template: java.io.Serializable? = null

    // ── Diagnostics / watchdog ────────────────────────────────────────────────
    var heartbeatInterval: Int?          = null
    var mockLocationPolicy: String?      = null
    var enableWatchdog: Boolean?         = null
    var watchdogIntervalMs: Long?        = null
    var restartOnKill: Boolean?          = null  // true = START_STICKY (default); false = START_NOT_STICKY
    var headlessTaskTimeoutMs: Long?     = null  // WorkManager headless sync interval (ms); default 15 min

    // ── Driving events (v4.0+) ────────────────────────────────────────────────
    var drivingEvents: DrivingEventsOptions? = null

    // ── Battery / wake-lock (v4.4+) ───────────────────────────────────────────
    var includeBattery: Boolean? = null
    var wakeLockMode: String?    = null

    // ── Stationary detection knobs (v4.5.1) ──────────────────────────────────
    var stationaryTimeout: Int?     = null  // ms
    var stationaryPollInterval: Int? = null // ms
    var stationaryPollFast: Int?    = null  // ms

    // ── Provider hardening (v4.5.2) ───────────────────────────────────────────
    var activityConfidenceThreshold: Int? = null
    var maxAcceptedAccuracy: Float?       = null

    // ── Convenience computed properties ──────────────────────────────────────

    val hasValidUrl: Boolean get() = !url.isNullOrEmpty()

    val hasValidSyncUrl: Boolean get() = !syncUrl.isNullOrEmpty()

    /** True when syncEnabled is not explicitly false and syncUrl is non-empty. */
    val isSyncEnabled: Boolean get() = syncEnabled != false && hasValidSyncUrl

    // ── Driver-insight options ────────────────────────────────────────────────

    /**
     * Driving-event detection parameters (v4.0+, sensor-fusion extended in v4.2).
     *
     * Kotlin data class — all fields have the same defaults as the upstream Java
     * `Config.DrivingEventsOptions` inner class.
     */
    data class DrivingEventsOptions(
        val enabled: Boolean              = false,
        val speedLimitKmh: Double         = 0.0,
        val minMovingSpeedMps: Double     = 1.0,
        val stoppedDurationMs: Long       = 60_000L,
        val minTripSpeedMps: Double       = 3.0,
        val minTripDurationMs: Long       = 30_000L,
        // v4.1 GPS-derived sensor-like thresholds
        val hardBrakeMps2: Double         = 3.5,
        val rapidAccelMps2: Double        = 3.5,
        val sharpTurnDegPerSec: Double    = 30.0,
        val crashImpactKmh: Double        = 25.0,
        val crashWindowMs: Long           = 2_000L,
        // v4.2 sensor fusion
        val sensorFusion: Boolean         = false,
        val crashImpactG: Double          = 3.0,
        val sensorCrashCooldownMs: Long   = 10_000L,
        val phoneUsageWindowMs: Long      = 4_000L,
        val phoneUsageCooldownMs: Long    = 60_000L,
        // v1.4 driver intelligence
        val idleThresholdMs: Long         = 300_000L,
        val idleEndThresholdMs: Long      = 30_000L,
        val scoringWeights: ScoringWeights? = null,
    )

    // ── toString ──────────────────────────────────────────────────────────────

    override fun toString(): String = buildString {
        append("BGConfig[")
        append("distanceFilter=").append(distanceFilter)
        append(" stationaryRadius=").append(stationaryRadius)
        append(" desiredAccuracy=").append(desiredAccuracy)
        append(" interval=").append(interval)
        append(" fastestInterval=").append(fastestInterval)
        append(" activitiesInterval=").append(activitiesInterval)
        append(" debug=").append(debug)
        append(" stopOnTerminate=").append(stopOnTerminate)
        append(" stopOnStillActivity=").append(stopOnStillActivity)
        append(" startOnBoot=").append(startOnBoot)
        append(" startForeground=").append(startForeground)
        append(" notificationsEnabled=").append(notificationsEnabled)
        append(" locationProvider=").append(locationProvider)
        append(" url=").append(url)
        append(" syncUrl=").append(syncUrl)
        append(" syncThreshold=").append(syncThreshold)
        append(" syncEnabled=").append(syncEnabled)
        append(" maxLocations=").append(maxLocations)
        append("]")
    }

    // ── Parcelable ────────────────────────────────────────────────────────────

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeFloat(stationaryRadius ?: 0f)
        dest.writeInt(distanceFilter ?: 0)
        dest.writeInt(desiredAccuracy ?: 0)
        dest.writeValue(debug)
        dest.writeString(notificationTitle)
        dest.writeString(notificationText)
        dest.writeString(notificationSyncTitle)
        dest.writeString(notificationSyncText)
        dest.writeString(notificationSyncCompletedText)
        dest.writeString(notificationSyncFailedText)
        dest.writeString(notificationIconLarge)
        dest.writeString(notificationIconSmall)
        dest.writeString(notificationIconColor)
        dest.writeValue(stopOnTerminate)
        dest.writeValue(startOnBoot)
        dest.writeValue(startForeground)
        dest.writeValue(notificationsEnabled)
        dest.writeInt(locationProvider ?: 0)
        dest.writeInt(interval ?: 0)
        dest.writeInt(fastestInterval ?: 0)
        dest.writeInt(activitiesInterval ?: 0)
        dest.writeValue(stopOnStillActivity)
        dest.writeString(url)
        dest.writeString(syncUrl)
        dest.writeInt(syncThreshold ?: 0)
        dest.writeValue(syncEnabled)
        dest.writeInt(maxLocations ?: 0)
        dest.writeValue(enableWatchdog)
        dest.writeValue(showTime)
        dest.writeValue(showDistance)
        dest.writeString(httpMethod)
        dest.writeString(syncHttpMethod)
        dest.writeString(httpMode)
        dest.writeString(syncMode)
        dest.writeValue(heartbeatInterval)
        dest.writeString(mockLocationPolicy)
        // drivingEvents — written as primitives to mirror the original order exactly
        val de = drivingEvents
        dest.writeInt(if (de?.enabled == true) 1 else 0)
        dest.writeDouble(de?.speedLimitKmh ?: 0.0)
        dest.writeDouble(de?.minMovingSpeedMps ?: 1.0)
        dest.writeLong(de?.stoppedDurationMs ?: 60_000L)
        dest.writeDouble(de?.minTripSpeedMps ?: 3.0)
        dest.writeLong(de?.minTripDurationMs ?: 30_000L)
        dest.writeDouble(de?.hardBrakeMps2 ?: 3.5)
        dest.writeDouble(de?.rapidAccelMps2 ?: 3.5)
        dest.writeDouble(de?.sharpTurnDegPerSec ?: 30.0)
        dest.writeDouble(de?.crashImpactKmh ?: 25.0)
        dest.writeLong(de?.crashWindowMs ?: 2_000L)
        dest.writeInt(if (de?.sensorFusion == true) 1 else 0)
        dest.writeDouble(de?.crashImpactG ?: 3.0)
        dest.writeLong(de?.sensorCrashCooldownMs ?: 10_000L)
        dest.writeLong(de?.phoneUsageWindowMs ?: 4_000L)
        dest.writeLong(de?.phoneUsageCooldownMs ?: 60_000L)
        dest.writeInt(if (de != null) 1 else 0)
        // v4.4+
        dest.writeValue(includeBattery)
        dest.writeString(wakeLockMode)
        dest.writeValue(stationaryTimeout)
        dest.writeValue(stationaryPollInterval)
        dest.writeValue(stationaryPollFast)
        dest.writeValue(activityConfidenceThreshold)
        dest.writeValue(maxAcceptedAccuracy)
        dest.writeValue(watchdogIntervalMs)
        dest.writeValue(restartOnKill)
        // Bundle for map / template fields (classloader required for deserialization)
        val bundle = Bundle()
        bundle.putSerializable("httpHeaders", httpHeaders)
        bundle.putSerializable("queryParams", queryParams)
        bundle.putSerializable("template",    template as? java.io.Serializable)
        headlessTaskTimeoutMs?.let { bundle.putLong("headlessTaskTimeoutMs", it) }
        de?.idleThresholdMs?.let { bundle.putLong("idleThresholdMs", it) }
        de?.idleEndThresholdMs?.let { bundle.putLong("idleEndThresholdMs", it) }
        de?.scoringWeights?.let { sw ->
            bundle.putInt("scoring_speeding",    sw.speeding)
            bundle.putInt("scoring_hardBraking", sw.hardBraking)
            bundle.putInt("scoring_rapidAccel",  sw.rapidAccel)
            bundle.putInt("scoring_sharpTurn",   sw.sharpTurn)
            bundle.putInt("scoring_phoneUsage",  sw.phoneUsage)
        }
        dest.writeBundle(bundle)
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {

        // Provider IDs
        const val DISTANCE_FILTER_PROVIDER = 0
        const val ACTIVITY_PROVIDER        = 1
        const val RAW_PROVIDER             = 2

        // Sentinel for "JS explicitly passed null for this string field"
        @JvmField val NULL_STRING: String = String()

        // Default values (mirrors Config.getDefault())
        const val DEFAULT_STATIONARY_RADIUS            = 50f
        const val DEFAULT_DISTANCE_FILTER              = 500
        const val DEFAULT_DESIRED_ACCURACY             = 100
        const val DEFAULT_LOCATION_PROVIDER            = DISTANCE_FILTER_PROVIDER
        const val DEFAULT_INTERVAL                     = 600_000
        const val DEFAULT_FASTEST_INTERVAL             = 120_000
        const val DEFAULT_ACTIVITIES_INTERVAL          = 10_000
        const val DEFAULT_SYNC_THRESHOLD               = 100
        const val DEFAULT_MAX_LOCATIONS                = 10_000
        const val DEFAULT_HEARTBEAT_INTERVAL           = 0
        const val DEFAULT_STATIONARY_TIMEOUT           = 5 * 60 * 1000
        const val DEFAULT_STATIONARY_POLL_INTERVAL     = 3 * 60 * 1000
        const val DEFAULT_STATIONARY_POLL_FAST         = 60 * 1000
        const val DEFAULT_ACTIVITY_CONFIDENCE_THRESHOLD = 50
        const val DEFAULT_HTTP_METHOD                  = "POST"
        const val DEFAULT_SYNC_HTTP_METHOD             = "POST"
        const val DEFAULT_HTTP_MODE                    = "batch"
        const val DEFAULT_SYNC_MODE                    = "batch"
        const val DEFAULT_MOCK_LOCATION_POLICY         = "allow"
        const val DEFAULT_WAKE_LOCK_MODE               = "posting"

        /**
         * Returns a fully-populated config with all defaults set, equivalent to
         * `Config.getDefault()` in the Java original.
         */
        @JvmStatic
        fun getDefault(): BGConfig = BGConfig().apply {
            stationaryRadius           = DEFAULT_STATIONARY_RADIUS
            distanceFilter             = DEFAULT_DISTANCE_FILTER
            desiredAccuracy            = DEFAULT_DESIRED_ACCURACY
            debug                      = false
            notificationTitle          = "Background tracking"
            notificationText           = "ENABLED"
            notificationSyncTitle      = "Syncing locations"
            notificationSyncText       = "Sync in progress"
            notificationSyncCompletedText = "Sync completed"
            notificationSyncFailedText = "Sync failed"
            notificationIconLarge      = ""
            notificationIconSmall      = ""
            notificationIconColor      = ""
            locationProvider           = DEFAULT_LOCATION_PROVIDER
            interval                   = DEFAULT_INTERVAL
            fastestInterval            = DEFAULT_FASTEST_INTERVAL
            activitiesInterval         = DEFAULT_ACTIVITIES_INTERVAL
            stopOnTerminate            = true
            startOnBoot                = false
            startForeground            = true
            notificationsEnabled       = true
            stopOnStillActivity        = true
            url                        = ""
            syncUrl                    = ""
            syncThreshold              = DEFAULT_SYNC_THRESHOLD
            syncEnabled                = true
            httpHeaders                = null
            maxLocations               = DEFAULT_MAX_LOCATIONS
            template                   = null
            enableWatchdog             = false
            restartOnKill              = true
            showTime                   = false
            showDistance               = false
            httpMethod                 = DEFAULT_HTTP_METHOD
            syncHttpMethod             = DEFAULT_SYNC_HTTP_METHOD
            httpMode                   = DEFAULT_HTTP_MODE
            syncMode                   = DEFAULT_SYNC_MODE
            queryParams                = null
            heartbeatInterval          = DEFAULT_HEARTBEAT_INTERVAL
            mockLocationPolicy         = DEFAULT_MOCK_LOCATION_POLICY
            includeBattery             = true
            wakeLockMode               = DEFAULT_WAKE_LOCK_MODE
            stationaryTimeout          = DEFAULT_STATIONARY_TIMEOUT
            stationaryPollInterval     = DEFAULT_STATIONARY_POLL_INTERVAL
            stationaryPollFast         = DEFAULT_STATIONARY_POLL_FAST
            activityConfidenceThreshold = DEFAULT_ACTIVITY_CONFIDENCE_THRESHOLD
            maxAcceptedAccuracy        = null
        }

        /**
         * Merges [override] onto [base] (non-null fields in [override] win).
         * Returns a new [BGConfig]; neither argument is mutated.
         * If [base] is null, [getDefault] is used. If [override] is null, a copy of
         * [base] is returned.
         */
        @JvmStatic
        fun merge(base: BGConfig?, override: BGConfig?): BGConfig {
            val result = BGConfig()
            // Start from base (or default)
            val b = base ?: getDefault()

            result.stationaryRadius            = b.stationaryRadius
            result.distanceFilter              = b.distanceFilter
            result.desiredAccuracy             = b.desiredAccuracy
            result.debug                       = b.debug
            result.notificationTitle           = b.notificationTitle
            result.notificationText            = b.notificationText
            result.notificationSyncTitle       = b.notificationSyncTitle
            result.notificationSyncText        = b.notificationSyncText
            result.notificationSyncCompletedText = b.notificationSyncCompletedText
            result.notificationSyncFailedText  = b.notificationSyncFailedText
            result.notificationIconLarge       = b.notificationIconLarge
            result.notificationIconSmall       = b.notificationIconSmall
            result.notificationIconColor       = b.notificationIconColor
            result.locationProvider            = b.locationProvider
            result.interval                    = b.interval
            result.fastestInterval             = b.fastestInterval
            result.activitiesInterval          = b.activitiesInterval
            result.stopOnTerminate             = b.stopOnTerminate
            result.startOnBoot                 = b.startOnBoot
            result.startForeground             = b.startForeground
            result.notificationsEnabled        = b.notificationsEnabled
            result.stopOnStillActivity         = b.stopOnStillActivity
            result.url                         = b.url
            result.syncUrl                     = b.syncUrl
            result.syncThreshold               = b.syncThreshold
            result.syncEnabled                 = b.syncEnabled
            result.httpHeaders                 = b.httpHeaders?.let { HashMap(it) }
            result.maxLocations                = b.maxLocations
            result.template                    = b.template
            result.enableWatchdog              = b.enableWatchdog
            result.watchdogIntervalMs          = b.watchdogIntervalMs
            result.restartOnKill               = b.restartOnKill
            result.showTime                    = b.showTime
            result.showDistance                = b.showDistance
            result.httpMethod                  = b.httpMethod
            result.syncHttpMethod              = b.syncHttpMethod
            result.httpMode                    = b.httpMode
            result.syncMode                    = b.syncMode
            result.queryParams                 = b.queryParams?.let { HashMap(it) }
            result.heartbeatInterval           = b.heartbeatInterval
            result.mockLocationPolicy          = b.mockLocationPolicy
            result.drivingEvents               = b.drivingEvents
            result.includeBattery              = b.includeBattery
            result.wakeLockMode                = b.wakeLockMode
            result.stationaryTimeout           = b.stationaryTimeout
            result.stationaryPollInterval      = b.stationaryPollInterval
            result.stationaryPollFast          = b.stationaryPollFast
            result.activityConfidenceThreshold = b.activityConfidenceThreshold
            result.maxAcceptedAccuracy         = b.maxAcceptedAccuracy
            result.headlessTaskTimeoutMs       = b.headlessTaskTimeoutMs

            // Apply override
            val o = override ?: return result

            o.stationaryRadius?.let            { result.stationaryRadius            = it }
            o.distanceFilter?.let              { result.distanceFilter              = it }
            o.desiredAccuracy?.let             { result.desiredAccuracy             = it }
            o.debug?.let                       { result.debug                       = it }
            o.notificationTitle?.let           { result.notificationTitle           = it }
            o.notificationText?.let            { result.notificationText            = it }
            o.notificationSyncTitle?.let       { result.notificationSyncTitle       = it }
            o.notificationSyncText?.let        { result.notificationSyncText        = it }
            o.notificationSyncCompletedText?.let { result.notificationSyncCompletedText = it }
            o.notificationSyncFailedText?.let  { result.notificationSyncFailedText  = it }
            o.notificationIconLarge?.let       { result.notificationIconLarge       = it }
            o.notificationIconSmall?.let       { result.notificationIconSmall       = it }
            o.notificationIconColor?.let       { result.notificationIconColor       = it }
            o.locationProvider?.let            { result.locationProvider            = it }
            o.interval?.let                    { result.interval                    = it }
            o.fastestInterval?.let             { result.fastestInterval             = it }
            o.activitiesInterval?.let          { result.activitiesInterval          = it }
            o.stopOnTerminate?.let             { result.stopOnTerminate             = it }
            o.startOnBoot?.let                 { result.startOnBoot                 = it }
            o.startForeground?.let             { result.startForeground             = it }
            o.notificationsEnabled?.let        { result.notificationsEnabled        = it }
            o.stopOnStillActivity?.let         { result.stopOnStillActivity         = it }
            o.url?.let                         { result.url                         = it }
            o.syncUrl?.let                     { result.syncUrl                     = it }
            o.syncThreshold?.let               { result.syncThreshold               = it }
            o.syncEnabled?.let                 { result.syncEnabled                 = it }
            o.httpHeaders?.let                 { result.httpHeaders                 = HashMap(it) }
            o.maxLocations?.let                { result.maxLocations                = it }
            o.template?.let                    { result.template                    = it }
            o.enableWatchdog?.let              { result.enableWatchdog              = it }
            o.watchdogIntervalMs?.let          { result.watchdogIntervalMs          = it }
            o.restartOnKill?.let               { result.restartOnKill               = it }
            o.showTime?.let                    { result.showTime                    = it }
            o.showDistance?.let                { result.showDistance                = it }
            o.httpMethod?.let                  { result.httpMethod                  = it.uppercase(Locale.US) }
            o.syncHttpMethod?.let              { result.syncHttpMethod              = it.uppercase(Locale.US) }
            o.httpMode?.let                    { result.httpMode                    = it.lowercase(Locale.US) }
            o.syncMode?.let                    { result.syncMode                    = it.lowercase(Locale.US) }
            o.queryParams?.let                 { result.queryParams                 = HashMap(it) }
            o.heartbeatInterval?.let           { result.heartbeatInterval           = it }
            o.mockLocationPolicy?.let          { result.mockLocationPolicy          = it.lowercase(Locale.US) }
            o.drivingEvents?.let               { result.drivingEvents               = it }
            o.includeBattery?.let              { result.includeBattery              = it }
            o.wakeLockMode?.let                { result.wakeLockMode                = it }
            o.stationaryTimeout?.let           { result.stationaryTimeout           = it }
            o.stationaryPollInterval?.let      { result.stationaryPollInterval      = it }
            o.stationaryPollFast?.let          { result.stationaryPollFast          = it }
            o.activityConfidenceThreshold?.let { result.activityConfidenceThreshold = it }
            o.maxAcceptedAccuracy?.let         { result.maxAcceptedAccuracy         = it }
            o.headlessTaskTimeoutMs?.let       { result.headlessTaskTimeoutMs       = it }

            return result
        }

        // ── Parcelable CREATOR ────────────────────────────────────────────────
        @JvmField
        val CREATOR: Parcelable.Creator<BGConfig> = object : Parcelable.Creator<BGConfig> {
            @Suppress("UNCHECKED_CAST")
            override fun createFromParcel(parcel: Parcel): BGConfig {
                val c = BGConfig()
                c.stationaryRadius        = parcel.readFloat().takeIf { it != 0f }
                c.distanceFilter          = parcel.readInt().takeIf { it != 0 }
                c.desiredAccuracy         = parcel.readInt().takeIf { it != 0 }
                c.debug                   = parcel.readValue(null) as? Boolean
                c.notificationTitle       = parcel.readString()
                c.notificationText        = parcel.readString()
                c.notificationSyncTitle   = parcel.readString()
                c.notificationSyncText    = parcel.readString()
                c.notificationSyncCompletedText = parcel.readString()
                c.notificationSyncFailedText    = parcel.readString()
                c.notificationIconLarge   = parcel.readString()
                c.notificationIconSmall   = parcel.readString()
                c.notificationIconColor   = parcel.readString()
                c.stopOnTerminate         = parcel.readValue(null) as? Boolean
                c.startOnBoot             = parcel.readValue(null) as? Boolean
                c.startForeground         = parcel.readValue(null) as? Boolean
                c.notificationsEnabled    = parcel.readValue(null) as? Boolean
                c.locationProvider        = parcel.readInt().takeIf { it != 0 }
                c.interval                = parcel.readInt().takeIf { it != 0 }
                c.fastestInterval         = parcel.readInt().takeIf { it != 0 }
                c.activitiesInterval      = parcel.readInt().takeIf { it != 0 }
                c.stopOnStillActivity     = parcel.readValue(null) as? Boolean
                c.url                     = parcel.readString()
                c.syncUrl                 = parcel.readString()
                c.syncThreshold           = parcel.readInt().takeIf { it != 0 }
                c.syncEnabled             = parcel.readValue(null) as? Boolean
                c.maxLocations            = parcel.readInt().takeIf { it != 0 }
                c.enableWatchdog          = parcel.readValue(null) as? Boolean
                c.showTime                = parcel.readValue(null) as? Boolean
                c.showDistance            = parcel.readValue(null) as? Boolean
                c.httpMethod              = parcel.readString()
                c.syncHttpMethod          = parcel.readString()
                c.httpMode                = parcel.readString()
                c.syncMode                = parcel.readString()
                c.heartbeatInterval       = parcel.readValue(null) as? Int
                c.mockLocationPolicy      = parcel.readString()
                // drivingEvents primitives (order must match writeToParcel)
                val deEnabled     = parcel.readInt() != 0
                val deSpeedLimit  = parcel.readDouble()
                val deMinMove     = parcel.readDouble()
                val deStoppedDur  = parcel.readLong()
                val deMinTrip     = parcel.readDouble()
                val deMinTripDur  = parcel.readLong()
                val deHardBrake   = parcel.readDouble()
                val deRapidAccel  = parcel.readDouble()
                val deSharpTurn   = parcel.readDouble()
                val deCrashKmh    = parcel.readDouble()
                val deCrashWin    = parcel.readLong()
                val deSensorFus   = parcel.readInt() != 0
                val deCrashG      = parcel.readDouble()
                val deSensorCool  = parcel.readLong()
                val dePhoneWin    = parcel.readLong()
                val dePhoneCool   = parcel.readLong()
                val deHasOpts     = parcel.readInt() != 0
                if (deHasOpts) {
                    c.drivingEvents = DrivingEventsOptions(
                        enabled             = deEnabled,
                        speedLimitKmh       = deSpeedLimit,
                        minMovingSpeedMps   = deMinMove,
                        stoppedDurationMs   = deStoppedDur,
                        minTripSpeedMps     = deMinTrip,
                        minTripDurationMs   = deMinTripDur,
                        hardBrakeMps2       = deHardBrake,
                        rapidAccelMps2      = deRapidAccel,
                        sharpTurnDegPerSec  = deSharpTurn,
                        crashImpactKmh      = deCrashKmh,
                        crashWindowMs       = deCrashWin,
                        sensorFusion        = deSensorFus,
                        crashImpactG        = deCrashG,
                        sensorCrashCooldownMs = deSensorCool,
                        phoneUsageWindowMs  = dePhoneWin,
                        phoneUsageCooldownMs = dePhoneCool,
                    )
                }
                c.includeBattery              = parcel.readValue(null) as? Boolean
                c.wakeLockMode                = parcel.readString()
                c.stationaryTimeout           = parcel.readValue(null) as? Int
                c.stationaryPollInterval      = parcel.readValue(null) as? Int
                c.stationaryPollFast          = parcel.readValue(null) as? Int
                c.activityConfidenceThreshold = parcel.readValue(null) as? Int
                c.maxAcceptedAccuracy         = parcel.readValue(null) as? Float
                c.watchdogIntervalMs          = parcel.readValue(null) as? Long
                c.restartOnKill               = parcel.readValue(null) as? Boolean
                val bundle = parcel.readBundle(BGConfig::class.java.classLoader)
                if (bundle != null) {
                    @Suppress("DEPRECATION")
                    c.httpHeaders = bundle.getSerializable("httpHeaders") as? HashMap<String, String>
                    @Suppress("DEPRECATION")
                    c.queryParams = bundle.getSerializable("queryParams") as? HashMap<String, String>
                    @Suppress("DEPRECATION")
                    c.template    = bundle.getSerializable("template")
                    val htms = bundle.getLong("headlessTaskTimeoutMs", -1L)
                    if (htms >= 0) c.headlessTaskTimeoutMs = htms
                    if (c.drivingEvents != null) {
                        val idleMs    = bundle.getLong("idleThresholdMs", -1L)
                        val idleEndMs = bundle.getLong("idleEndThresholdMs", -1L)
                        val hasScoring = bundle.containsKey("scoring_speeding")
                        c.drivingEvents = c.drivingEvents?.copy(
                            idleThresholdMs    = if (idleMs    >= 0) idleMs    else 300_000L,
                            idleEndThresholdMs = if (idleEndMs >= 0) idleEndMs else 30_000L,
                            scoringWeights     = if (hasScoring) ScoringWeights(
                                speeding    = bundle.getInt("scoring_speeding",    30),
                                hardBraking = bundle.getInt("scoring_hardBraking", 25),
                                rapidAccel  = bundle.getInt("scoring_rapidAccel",  20),
                                sharpTurn   = bundle.getInt("scoring_sharpTurn",   15),
                                phoneUsage  = bundle.getInt("scoring_phoneUsage",  10),
                            ) else null
                        )
                    }
                }
                return c
            }

            override fun newArray(size: Int): Array<BGConfig?> = arrayOfNulls(size)
        }
    }
}
