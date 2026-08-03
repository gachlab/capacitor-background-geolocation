// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.BGLocation
import com.gachlab.geolocation.LocationTemplate
import com.gachlab.geolocation.LocationTemplateFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test (requires an emulator / device — run from Android Studio or
 * `./gradlew connectedAndroidTest`). Robolectric is not used because the unit-test
 * task targets the JUnit 5 platform.
 *
 * The gachlab DB (`gachlab_bg_geolocation.db`, v1) is a fresh lineage — it replaced
 * the legacy Cordova DB and its migration history. These tests cover the onCreate
 * schema and the JSON-blob config storage.
 */
@RunWith(AndroidJUnit4::class)
class LocationDbHelperTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun cleanSlate() {
        resetSingleton()
        ctx.deleteDatabase(LocationDbHelper.DB_NAME)
    }

    @After fun tearDown() {
        resetSingleton()
        ctx.deleteDatabase(LocationDbHelper.DB_NAME)
    }

    /** Closes + clears the [LocationDbHelper] singleton so each test gets a fresh DB. */
    private fun resetSingleton() {
        val field = LocationDbHelper::class.java.getDeclaredField("instance")
        field.isAccessible = true
        (field.get(null) as? android.database.sqlite.SQLiteOpenHelper)?.close()
        field.set(null, null)
    }

    private fun columnsOf(table: String): Set<String> {
        val cols = mutableSetOf<String>()
        LocationDbHelper.getInstance(ctx).readableDatabase
            .rawQuery("PRAGMA table_info($table)", null).use { c ->
                val nameIdx = c.getColumnIndexOrThrow("name")
                while (c.moveToNext()) cols += c.getString(nameIdx)
            }
        return cols
    }

    @Test fun freshInstallCreatesAllTables() {
        // No seed → onCreate path at the current schema.
        val db = LocationDbHelper.getInstance(ctx).readableDatabase

        val locCols = columnsOf("location")
        assertTrue("events_json missing: $locCols", "events_json" in locCols)
        assertTrue("battery_level missing: $locCols", "battery_level" in locCols)

        // Config table is slim: just _id + config_json (legacy per-column storage gone).
        assertEquals(setOf("_id", "config_json"), columnsOf("configuration"))

        assertTrue("location_session not created", columnsOf("location_session").isNotEmpty())

        val logCols = columnsOf("logs")
        assertTrue("logs table missing", "msg" in logCols)
        db.rawQuery("SELECT COUNT(*) FROM logs", null).use { c ->
            c.moveToFirst(); assertEquals(0, c.getInt(0))
        }
    }

    @Test fun configRoundTripsViaJsonBlob() {
        val dao = ConfigDAO(ctx)
        val cfg = BGConfig.getDefault().apply {
            url = "https://example.test/loc"
            distanceFilter = 42
        }
        dao.persistConfig(cfg)

        val loaded = ConfigDAO(ctx).retrieveConfig()
        assertTrue("config not loaded", loaded != null)
        assertEquals("https://example.test/loc", loaded!!.url)
        assertEquals(42, loaded.distanceFilter)
    }

    /**
     * The template MUST survive the round trip. It did not: `toJSONObject` — the
     * serializer this DAO uses — skipped it while its twin `toJSObject` wrote it,
     * so every consumer that re-read the config from disk (service start, sync
     * flush, boot) POSTed the flat default payload instead of the configured
     * shape. This test used to pass while that was broken because it only
     * asserted `url` and `distanceFilter`.
     */
    @Test fun configRoundTripPreservesBodyTemplate() {
        val definition = org.json.JSONObject(
            """{"location":{"coords":{"latitude":"@latitude","longitude":"@longitude"},"timestamp":"@time"}}"""
        )
        val cfg = BGConfig.getDefault().apply {
            template = LocationTemplateFactory.fromJSON(definition)
        }
        ConfigDAO(ctx).persistConfig(cfg)

        val loaded = ConfigDAO(ctx).retrieveConfig()
        assertTrue("config not loaded", loaded != null)
        val template = loaded!!.template
        assertTrue("template lost on round trip (was ${template ?: "null"})", template is LocationTemplate)

        // Not just non-null: it has to be the SAME template, and it has to still
        // expand. A template that survives as an empty one is the exact bug.
        val location = BGLocation().apply {
            latitude = 25.794585
            longitude = -80.278061
            time = 1_785_768_313_000
        }
        val body = (template as LocationTemplate).locationToJson(location) as org.json.JSONObject
        val coords = body.getJSONObject("location").getJSONObject("coords")
        assertEquals(25.794585, coords.getDouble("latitude"), 0.000001)
        assertEquals(-80.278061, coords.getDouble("longitude"), 0.000001)
        assertEquals(1_785_768_313_000L, body.getJSONObject("location").getLong("timestamp"))
    }

    /** A config with no template must come back with none — not with an empty one. */
    @Test fun configRoundTripKeepsAbsentTemplateAbsent() {
        ConfigDAO(ctx).persistConfig(BGConfig.getDefault().apply { template = null })

        assertEquals(null, ConfigDAO(ctx).retrieveConfig()!!.template)
    }

    @Test fun retrieveBeforePersistReturnsNull() {
        assertEquals(null, ConfigDAO(ctx).retrieveConfig())
    }
}
