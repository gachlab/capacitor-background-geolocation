// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

import com.gachlab.geolocation.BGLocation
import com.gachlab.geolocation.LocationTemplateFactory
import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * #22 — guards the wire-shape distinction the backend cares about:
 *  - `syncMode='single'` POSTs a bare JSON **object** per location (what drivers-web
 *    relies on — the BE rejects arrays / wants real-time per-fix updates).
 *  - `syncMode='batch'` POSTs a JSON **array** of those same objects.
 *
 * Pure JVM — exercises the exact payload builders the worker uses, no network.
 */
@DisplayName("syncMode payload shape (#22)")
class SyncModePayloadTest {

    private fun loc(lat: Double, lng: Double): BGLocation = BGLocation().also {
        it.latitude = lat; it.longitude = lng; it.time = 1_700_000_000_000L
    }

    @Test
    @DisplayName("single mode payload is a bare JSON object, not an array")
    fun singleIsObject() {
        val template = LocationTemplateFactory.empty()
        val payload = template.locationToJson(loc(10.5, -66.9))
        assertTrue(payload is JSONObject, "single-mode payload must be a JSONObject, was ${payload::class.simpleName}")
        payload as JSONObject
        assertEquals(10.5, payload.getDouble("latitude"), 1e-9)
        assertEquals(-66.9, payload.getDouble("longitude"), 1e-9)
    }

    @Test
    @DisplayName("batch mode payload is a JSON array of per-location objects")
    fun batchIsArray() {
        val template = LocationTemplateFactory.empty()
        val locations = listOf(loc(1.0, 1.0), loc(2.0, 2.0), loc(3.0, 3.0))

        val array: JSONArray = BackgroundSync.buildBatchArray(locations, template)

        assertEquals(3, array.length())
        // Each element is the same bare object single mode would send.
        for (i in 0 until array.length()) {
            assertTrue(array.get(i) is JSONObject, "batch element $i must be a JSONObject")
        }
        assertEquals(2.0, array.getJSONObject(1).getDouble("latitude"), 1e-9)
    }

    @Test
    @DisplayName("a single-location batch is still an array of one (never a bare object)")
    fun batchOfOneStaysArray() {
        val array = BackgroundSync.buildBatchArray(listOf(loc(9.0, 9.0)), LocationTemplateFactory.empty())
        assertEquals(1, array.length())
        assertTrue(array.get(0) is JSONObject)
    }
}
