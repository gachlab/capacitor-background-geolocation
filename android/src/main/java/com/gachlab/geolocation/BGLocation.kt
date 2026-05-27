// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.location.Location
import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Represents a single location fix captured by the background geolocation service.
 *
 * Kotlin port of `com.marianhello.bgloc.data.BackgroundLocation`.
 * Fields use nullable types to distinguish "not set" from a zero-value; the `has*`
 * booleans mirror the originals and are set automatically by the setters.
 */
class BGLocation() : Parcelable {

    // ── DB identity ──────────────────────────────────────────────────────────
    var locationId: Long? = null
    var locationProvider: Int? = null
    var batchStartMillis: Long? = null

    // ── Core fix ─────────────────────────────────────────────────────────────
    var provider: String? = null
    var latitude: Double = 0.0
    var longitude: Double = 0.0
    var time: Long = 0L
    var elapsedRealtimeNanos: Long = 0L

    // ── Optional fix components (guarded by has* flags) ──────────────────────
    var accuracy: Float = 0f
        set(value) { field = value; hasAccuracy = true }
    var verticalAccuracy: Float = 0f
        set(value) { field = value; hasVerticalAccuracy = true }
    var speed: Float = 0f
        set(value) { field = value; hasSpeed = true }
    var bearing: Float = 0f
        set(value) { field = value; hasBearing = true }
    var altitude: Double = 0.0
        set(value) { field = value; hasAltitude = true }
    var radius: Float = 0f
        set(value) { field = value; hasRadius = true }

    var hasAccuracy: Boolean = false
    var hasVerticalAccuracy: Boolean = false
    var hasAltitude: Boolean = false
    var hasSpeed: Boolean = false
    var hasBearing: Boolean = false
    var hasRadius: Boolean = false

    /**
     * Mock flags (4-bit):
     *   bit 0 – isFromMockProvider
     *   bit 1 – hasIsFromMockProvider
     *   bit 2 – areMockLocationsEnabled
     *   bit 3 – hasMockLocationsEnabled
     */
    var mockFlags: Int = 0x0000

    var status: Int = STATUS_POST_PENDING

    // ── v4.3+ driving events ─────────────────────────────────────────────────
    /** Driving events attached to this fix. Persisted in SQLite as JSON text. */
    var drivingEvents: JSONArray? = null

    // ── v4.4+ battery snapshot ───────────────────────────────────────────────
    var batteryLevel: Int? = null
    var isCharging: Boolean? = null

    constructor(provider: String) : this() { this.provider = provider }

    // ── Copy constructor ─────────────────────────────────────────────────────
    constructor(src: BGLocation) : this() {
        locationId          = src.locationId
        locationProvider    = src.locationProvider
        batchStartMillis    = src.batchStartMillis
        provider            = src.provider
        latitude            = src.latitude
        longitude           = src.longitude
        time                = src.time
        elapsedRealtimeNanos = src.elapsedRealtimeNanos
        // Use backing fields directly to avoid triggering the setters' side-effects
        // (the has* booleans are copied explicitly below).
        this.accuracy         = src.accuracy
        this.verticalAccuracy = src.verticalAccuracy
        this.speed            = src.speed
        this.bearing          = src.bearing
        this.altitude         = src.altitude
        this.radius           = src.radius
        hasAccuracy         = src.hasAccuracy
        hasVerticalAccuracy = src.hasVerticalAccuracy
        hasAltitude         = src.hasAltitude
        hasSpeed            = src.hasSpeed
        hasBearing          = src.hasBearing
        hasRadius           = src.hasRadius
        mockFlags           = src.mockFlags
        status              = src.status
        src.drivingEvents?.let { arr ->
            try { drivingEvents = JSONArray(arr.toString()) } catch (_: JSONException) {}
        }
        batteryLevel = src.batteryLevel
        isCharging   = src.isCharging
    }

    fun makeClone(): BGLocation = BGLocation(this)

    // ── Mock helpers ─────────────────────────────────────────────────────────

    fun hasIsFromMockProvider(): Boolean = ((mockFlags and 0x0002) shr 1) == 1

    fun isFromMockProvider(): Boolean = (mockFlags and 0x0001) == 1

    fun setIsFromMockProvider(value: Boolean) {
        mockFlags = mockFlags or (if (value) 0x0003 else 0x0002)
    }

    fun hasMockLocationsEnabled(): Boolean = ((mockFlags and 0x0008) shr 3) == 1

    fun areMockLocationsEnabled(): Boolean = ((mockFlags and 0x0004) shr 2) == 1

    fun setMockLocationsEnabled(value: Boolean) {
        mockFlags = mockFlags or (if (value) 0x000C else 0x0008)
    }

    // ── Driving-event helpers ────────────────────────────────────────────────

    fun addDrivingEvent(event: JSONObject?) {
        event ?: return
        if (drivingEvents == null) drivingEvents = JSONArray()
        drivingEvents!!.put(event)
    }

    fun addDrivingEvent(type: String) =
        addDrivingEvent(JSONObject().put("type", type))

    fun hasDrivingEvents(): Boolean = drivingEvents != null && drivingEvents!!.length() > 0

    fun clearDrivingEvents() { drivingEvents = null }

    // ── android.location.Location bridge ────────────────────────────────────

    fun getLocation(): Location {
        val loc = Location(provider)
        loc.latitude  = latitude
        loc.longitude = longitude
        loc.time      = time
        if (hasAccuracy) loc.accuracy = accuracy
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasVerticalAccuracy) {
            loc.verticalAccuracyMeters = verticalAccuracy
        }
        if (hasAltitude) loc.altitude = altitude
        if (hasSpeed)    loc.speed    = speed
        if (hasBearing)  loc.bearing  = bearing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            loc.elapsedRealtimeNanos = elapsedRealtimeNanos
        }
        return loc
    }

    // ── isBetterLocationThan helpers ────────────────────────────────────────

    fun isBetterLocationThan(other: BGLocation?): Boolean =
        other == null || !isBetterLocation(other, this)

    fun isBetterLocationThan(other: Location?): Boolean =
        other == null || !isBetterLocation(fromLocation(other), this)

    // ── Serialization ────────────────────────────────────────────────────────

    @Throws(JSONException::class)
    fun toJSONObject(): JSONObject {
        val json = JSONObject()
        json.put("provider",         provider)
        json.put("locationProvider", locationProvider)
        json.put("time",             time)
        json.put("latitude",         latitude)
        json.put("longitude",        longitude)
        if (hasAccuracy)         json.put("accuracy",         accuracy)
        if (hasVerticalAccuracy) json.put("altitudeAccuracy", verticalAccuracy)
        if (hasSpeed)            json.put("speed",            speed)
        if (hasAltitude)         json.put("altitude",         altitude)
        if (hasBearing)          json.put("bearing",          bearing)
        if (hasRadius)           json.put("radius",           radius)
        if (hasIsFromMockProvider())   json.put("isFromMockProvider",    isFromMockProvider())
        if (hasMockLocationsEnabled()) json.put("mockLocationsEnabled",  areMockLocationsEnabled())
        drivingEvents?.takeIf { it.length() > 0 }?.let { json.put("events",     it) }
        batteryLevel?.let { json.put("battery",    it) }
        isCharging?.let   { json.put("isCharging", it) }
        return json
    }

    @Throws(JSONException::class)
    fun toJSONObjectWithId(): JSONObject {
        val json = toJSONObject()
        json.put("id", locationId)
        return json
    }

    /**
     * Returns the value mapped to the `@`-prefixed template key used in
     * [com.marianhello.bgloc.data.LocationTemplate] expansion, preserving the
     * same key-to-field mapping as the original Java class.
     */
    fun getValueForKey(key: String): Any? = when (key) {
        "@id"                  -> locationId
        "@provider"            -> provider
        "@locationProvider"    -> locationProvider
        "@time"                -> time
        "@latitude"            -> latitude
        "@longitude"           -> longitude
        "@accuracy"            -> if (hasAccuracy)         accuracy         else JSONObject.NULL
        "@altitudeAccuracy"    -> if (hasVerticalAccuracy) verticalAccuracy else JSONObject.NULL
        "@speed"               -> if (hasSpeed)            speed            else JSONObject.NULL
        "@altitude"            -> if (hasAltitude)         altitude         else JSONObject.NULL
        "@bearing"             -> if (hasBearing)          bearing          else JSONObject.NULL
        "@radius"              -> if (hasRadius)           radius           else JSONObject.NULL
        "@isFromMockProvider"  -> if (hasIsFromMockProvider())   isFromMockProvider()    else JSONObject.NULL
        "@mockLocationsEnabled"-> if (hasMockLocationsEnabled()) areMockLocationsEnabled() else JSONObject.NULL
        "@events"              -> drivingEvents ?: JSONObject.NULL
        "@battery"             -> batteryLevel  ?: JSONObject.NULL
        "@isCharging"          -> isCharging    ?: JSONObject.NULL
        else                   -> null
    }

    override fun toString(): String = buildString {
        append("BGLocation[").append(provider)
        append(String.format(" %.6f,%.6f", latitude, longitude))
        append(" id=").append(locationId)
        if (hasAccuracy) append(String.format(" acc=%.0f", accuracy)) else append(" acc=???")
        if (hasVerticalAccuracy) append(String.format(" altAcc=%.0f", verticalAccuracy)) else append(" altAcc=???")
        if (time == 0L) append(" t=?!?") else append(" t=").append(time)
        if (hasAltitude) append(" alt=").append(altitude)
        if (hasSpeed)    append(" vel=").append(speed)
        if (hasBearing)  append(" bear=").append(bearing)
        if (hasRadius)   append(" radius=").append(radius)
        if (isFromMockProvider())      append(" mock")
        if (areMockLocationsEnabled()) append(" mocksEnabled")
        append(" locprov=").append(locationProvider)
        append("]")
    }

    // ── Parcelable ───────────────────────────────────────────────────────────

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLong(locationId ?: 0L)
        dest.writeInt(locationProvider ?: 0)
        dest.writeLong(batchStartMillis ?: 0L)
        dest.writeString(provider)
        dest.writeDouble(latitude)
        dest.writeDouble(longitude)
        dest.writeLong(time)
        dest.writeLong(elapsedRealtimeNanos)
        dest.writeFloat(accuracy)
        dest.writeFloat(verticalAccuracy)
        dest.writeFloat(speed)
        dest.writeFloat(bearing)
        dest.writeDouble(altitude)
        dest.writeFloat(radius)
        dest.writeInt(if (hasAccuracy)         1 else 0)
        dest.writeInt(if (hasVerticalAccuracy) 1 else 0)
        dest.writeInt(if (hasAltitude)         1 else 0)
        dest.writeInt(if (hasSpeed)            1 else 0)
        dest.writeInt(if (hasBearing)          1 else 0)
        dest.writeInt(if (hasRadius)           1 else 0)
        dest.writeInt(mockFlags)
        dest.writeInt(status)
        // v4.5: driving events / battery / charging
        dest.writeString(drivingEvents?.toString())
        dest.writeValue(batteryLevel)
        dest.writeValue(isCharging)
    }

    companion object {

        // ── Status constants ──────────────────────────────────────────────────
        const val STATUS_DELETED      = 0
        const val STATUS_POST_PENDING = 1
        const val STATUS_SYNC_PENDING = 2

        private const val TWO_MINUTES_IN_NANOS = 1_000_000_000L * 60L * 2L

        // ── Factory from android.location.Location ────────────────────────────
        @JvmStatic
        fun fromLocation(loc: Location): BGLocation {
            val l = BGLocation()
            l.provider  = loc.provider
            l.latitude  = loc.latitude
            l.longitude = loc.longitude
            l.time      = loc.time
            // Use backing-field writes to avoid prematurely setting has* flags,
            // then explicitly set the flags from the source Location object.
            l.accuracy  = loc.accuracy;  l.hasAccuracy  = loc.hasAccuracy()
            l.altitude  = loc.altitude;  l.hasAltitude  = loc.hasAltitude()
            l.speed     = loc.speed;     l.hasSpeed     = loc.hasSpeed()
            l.bearing   = loc.bearing;   l.hasBearing   = loc.hasBearing()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                l.elapsedRealtimeNanos = loc.elapsedRealtimeNanos
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                l.setIsFromMockProvider(loc.isFromMockProvider)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                l.verticalAccuracy = loc.verticalAccuracyMeters
                l.hasVerticalAccuracy = loc.hasVerticalAccuracy()
            }
            return l
        }

        // ── Parcelable CREATOR ────────────────────────────────────────────────
        @JvmField
        val CREATOR: Parcelable.Creator<BGLocation> = object : Parcelable.Creator<BGLocation> {
            override fun createFromParcel(parcel: Parcel): BGLocation {
                val l = BGLocation()
                l.locationId           = parcel.readLong().takeIf { it != 0L }
                l.locationProvider     = parcel.readInt().takeIf { it != 0 }
                l.batchStartMillis     = parcel.readLong().takeIf { it != 0L }
                l.provider             = parcel.readString()
                l.latitude             = parcel.readDouble()
                l.longitude            = parcel.readDouble()
                l.time                 = parcel.readLong()
                l.elapsedRealtimeNanos = parcel.readLong()
                l.accuracy             = parcel.readFloat()
                l.verticalAccuracy     = parcel.readFloat()
                l.speed                = parcel.readFloat()
                l.bearing              = parcel.readFloat()
                l.altitude             = parcel.readDouble()
                l.radius               = parcel.readFloat()
                l.hasAccuracy          = parcel.readInt() != 0
                l.hasVerticalAccuracy  = parcel.readInt() != 0
                l.hasAltitude          = parcel.readInt() != 0
                l.hasSpeed             = parcel.readInt() != 0
                l.hasBearing           = parcel.readInt() != 0
                l.hasRadius            = parcel.readInt() != 0
                l.mockFlags            = parcel.readInt()
                l.status               = parcel.readInt()
                parcel.readString()?.let { s ->
                    try { l.drivingEvents = JSONArray(s) } catch (_: JSONException) {}
                }
                @Suppress("UNCHECKED_CAST")
                l.batteryLevel = parcel.readValue(null) as? Int
                @Suppress("UNCHECKED_CAST")
                l.isCharging   = parcel.readValue(null) as? Boolean
                return l
            }

            override fun newArray(size: Int): Array<BGLocation?> = arrayOfNulls(size)
        }

        // ── isBetterLocation algorithm ────────────────────────────────────────
        /**
         * Determines whether [newLoc] is a better fix than [currentBest].
         *
         * Algorithm: https://developer.android.com/guide/topics/location/strategies
         */
        @JvmStatic
        fun isBetterLocation(newLoc: BGLocation?, currentBest: BGLocation?): Boolean {
            newLoc ?: return false
            currentBest ?: return true

            val timeDeltaNanos: Long =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    newLoc.elapsedRealtimeNanos - currentBest.elapsedRealtimeNanos
                } else {
                    (newLoc.time - currentBest.time) * 1_000_000L
                }

            val isSignificantlyNewer = timeDeltaNanos >  TWO_MINUTES_IN_NANOS
            val isSignificantlyOlder = timeDeltaNanos < -TWO_MINUTES_IN_NANOS
            val isNewer              = timeDeltaNanos >  0

            if (isSignificantlyNewer) return true
            if (isSignificantlyOlder) return false

            val accuracyDelta: Int      = (newLoc.accuracy - currentBest.accuracy).toInt()
            val isLessAccurate          = accuracyDelta > 0
            val isMoreAccurate          = accuracyDelta < 0
            val isSignificantlyLess     = accuracyDelta > 200
            val isFromSameProvider      = newLoc.provider == currentBest.provider

            return when {
                isMoreAccurate                                   -> true
                isNewer && !isLessAccurate                       -> true
                isNewer && !isSignificantlyLess && isFromSameProvider -> true
                else                                             -> false
            }
        }
    }
}
