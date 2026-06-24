// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test (requires an emulator / device — uses a real Context and
 * SharedPreferences). Covers the geofence registry + persistence in [GeofenceManager]:
 * add / remove / removeAll and that the set is written to SharedPreferences.
 *
 * `init()` is intentionally NOT called, so `geofencingClient` stays null and every
 * Play Services call is a no-op (`?.`). That keeps this test about the registry
 * contract and lets it run on AOSP emulators without Google Play Services. The
 * actual enter/exit/dwell transitions are an OS-level concern and must be exercised
 * with mock-location injection in a separate device test.
 *
 * Run from Android Studio or `./gradlew connectedAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class GeofenceIntegrationTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    // Mirrors GeofenceManager's private PREFS_NAME / PREFS_KEY constants.
    private val prefsName = "gachlab_gf_store"
    private val prefsKey = "gachlab_geofences"

    @Before fun clearRegistry() {
        GeofenceManager.eventListener = null
        GeofenceManager.remove(ctx, null) // clears in-memory map + prefs (GMS calls no-op)
    }

    @After fun tearDown() {
        GeofenceManager.remove(ctx, null)
    }

    private fun gf(id: String, lat: Double = 19.0, lon: Double = -99.0) =
        BGGeofence(id = id, latitude = lat, longitude = lon, radius = 200f, notifyOnEntry = true)

    @Test fun addRegistersAndReturnsAll() {
        GeofenceManager.add(ctx, listOf(gf("depot"), gf("client-a")))
        val ids = GeofenceManager.getAll().map { it.id }.toSet()
        assertEquals(setOf("depot", "client-a"), ids)
    }

    @Test fun addIsIdempotentById() {
        GeofenceManager.add(ctx, listOf(gf("depot", lat = 1.0)))
        GeofenceManager.add(ctx, listOf(gf("depot", lat = 2.0))) // same id → replace, not duplicate
        val all = GeofenceManager.getAll()
        assertEquals(1, all.size)
        assertEquals(2.0, all.first().latitude, 0.0001)
    }

    @Test fun removeByIdLeavesOthers() {
        GeofenceManager.add(ctx, listOf(gf("a"), gf("b"), gf("c")))
        GeofenceManager.remove(ctx, listOf("b"))
        val ids = GeofenceManager.getAll().map { it.id }.toSet()
        assertEquals(setOf("a", "c"), ids)
        assertFalse("b should be gone", "b" in ids)
    }

    @Test fun removeAllClearsRegistry() {
        GeofenceManager.add(ctx, listOf(gf("a"), gf("b")))
        GeofenceManager.remove(ctx, null)
        assertTrue(GeofenceManager.getAll().isEmpty())
    }

    @Test fun addPersistsToSharedPreferences() {
        GeofenceManager.add(ctx, listOf(gf("depot"), gf("client-a")))
        val json = ctx.getSharedPreferences(prefsName, Context.MODE_PRIVATE).getString(prefsKey, "[]")
        val arr = JSONArray(json)
        val persistedIds = (0 until arr.length()).map { arr.getJSONObject(it).getString("id") }.toSet()
        assertEquals(setOf("depot", "client-a"), persistedIds)
    }
}
