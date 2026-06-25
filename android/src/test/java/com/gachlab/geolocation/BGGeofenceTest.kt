// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import org.json.JSONArray
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pure-JVM coverage of the geofence serialization layer — the part of geofencing
 * that does not touch Play Services. Geofences round-trip through SharedPreferences
 * as JSON ([GeofenceManager.persistToPrefs]/[loadFromPrefs]), so a faithful
 * to/from JSON is the backward-compat contract. On-device enter/exit/dwell
 * transitions are covered by the instrumented `GeofenceIntegrationTest`.
 */
@DisplayName("BGGeofence — JSON serialization")
class BGGeofenceTest {

    @Test
    @DisplayName("round-trips all fields through JSON")
    fun roundTrip() {
        val g = BGGeofence(
            id = "depot-7",
            latitude = 19.4326,
            longitude = -99.1332,
            radius = 350f,
            notifyOnEntry = true,
            notifyOnExit = true,
            notifyOnDwell = true,
            loiteringDelay = 45_000,
        )
        val restored = BGGeofence.fromJSON(g.toJSON())
        assertEquals(g, restored, "geofence should survive a JSON round-trip unchanged")
    }

    @Test
    @DisplayName("fromJSON applies documented defaults for optional fields")
    fun defaults() {
        val minimal = org.json.JSONObject()
            .put("id", "z1")
            .put("latitude", 1.0)
            .put("longitude", 2.0)
        val g = BGGeofence.fromJSON(minimal)
        assertEquals(200f, g.radius, "default radius is 200m")
        assertTrue(g.notifyOnEntry, "notifyOnEntry defaults to true")
        assertTrue(!g.notifyOnExit, "notifyOnExit defaults to false")
        assertTrue(!g.notifyOnDwell, "notifyOnDwell defaults to false")
        assertEquals(30_000, g.loiteringDelay, "default loiteringDelay is 30s")
    }

    @Test
    @DisplayName("list round-trips preserving order and size")
    fun listRoundTrip() {
        val list = listOf(
            BGGeofence("a", 1.0, 1.0),
            BGGeofence("b", 2.0, 2.0, radius = 500f, notifyOnExit = true),
            BGGeofence("c", 3.0, 3.0, notifyOnDwell = true, loiteringDelay = 10_000),
        )
        val json: JSONArray = BGGeofence.listToJSON(list)
        assertEquals(3, json.length())
        val restored = BGGeofence.listFromJSON(json)
        assertEquals(list, restored, "list should survive round-trip in order")
    }

    @Test
    @DisplayName("radius stored as a JSON number is read back as Float")
    fun radiusNumericCoercion() {
        // SharedPreferences persists radius as a JSON double; fromJSON must coerce to Float.
        val j = org.json.JSONObject()
            .put("id", "r").put("latitude", 0.0).put("longitude", 0.0)
            .put("radius", 123.0)
        assertEquals(123f, BGGeofence.fromJSON(j).radius)
    }
}
