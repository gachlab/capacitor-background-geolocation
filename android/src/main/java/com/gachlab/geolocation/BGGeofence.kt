// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import org.json.JSONArray
import org.json.JSONObject

/**
 * User-defined geofence region for entry/exit/dwell detection.
 *
 * Android GMS enforces a maximum of 100 simultaneously monitored geofences per app.
 * Geofences are persisted in SharedPreferences and re-registered on startup.
 */
data class BGGeofence(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val radius: Float = 200f,
    val notifyOnEntry: Boolean = true,
    val notifyOnExit: Boolean = false,
    val notifyOnDwell: Boolean = false,
    val loiteringDelay: Int = 30_000
) {
    fun toJSON(): JSONObject = JSONObject().apply {
        put("id",              id)
        put("latitude",        latitude)
        put("longitude",       longitude)
        put("radius",          radius)
        put("notifyOnEntry",   notifyOnEntry)
        put("notifyOnExit",    notifyOnExit)
        put("notifyOnDwell",   notifyOnDwell)
        put("loiteringDelay",  loiteringDelay)
    }

    companion object {
        fun fromJSON(j: JSONObject) = BGGeofence(
            id              = j.getString("id"),
            latitude        = j.getDouble("latitude"),
            longitude       = j.getDouble("longitude"),
            radius          = j.optDouble("radius", 200.0).toFloat(),
            notifyOnEntry   = j.optBoolean("notifyOnEntry",  true),
            notifyOnExit    = j.optBoolean("notifyOnExit",   false),
            notifyOnDwell   = j.optBoolean("notifyOnDwell",  false),
            loiteringDelay  = j.optInt("loiteringDelay", 30_000)
        )

        fun listToJSON(list: List<BGGeofence>): JSONArray =
            JSONArray().also { arr -> list.forEach { arr.put(it.toJSON()) } }

        fun listFromJSON(arr: JSONArray): List<BGGeofence> =
            (0 until arr.length()).map { fromJSON(arr.getJSONObject(it)) }
    }
}
