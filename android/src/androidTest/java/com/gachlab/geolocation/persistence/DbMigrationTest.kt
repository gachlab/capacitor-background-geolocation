// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.persistence

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented test (requires an emulator / device — run from Android Studio or
 * `./gradlew connectedAndroidTest`). Robolectric is not used because the unit-test
 * task is configured for the JUnit 5 platform, which can't host Robolectric's
 * JUnit 4 runner without extra wiring.
 *
 * Verifies that an old on-disk schema (the pre-rewrite Cordova DB) is upgraded by
 * [LocationDbHelper] to the current schema without losing rows — the backward-compat
 * guarantee behind `DB_NAME = "cordova_bg_geolocation.db"`.
 */
@RunWith(AndroidJUnit4::class)
class DbMigrationTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Before fun cleanSlate() {
        ctx.deleteDatabase(LocationDbHelper.DB_NAME)
    }

    @After fun tearDown() {
        ctx.deleteDatabase(LocationDbHelper.DB_NAME)
    }

    /** Seeds a minimal legacy (v11) `location`/`configuration` schema with one row. */
    private fun seedLegacyDb(version: Int) {
        val db = ctx.openOrCreateDatabase(LocationDbHelper.DB_NAME, Context.MODE_PRIVATE, null)
        db.execSQL(
            """CREATE TABLE location (
                 _id INTEGER PRIMARY KEY,
                 time INTEGER, accuracy REAL, speed REAL, bearing REAL, altitude REAL,
                 latitude REAL, longitude REAL, provider TEXT, service_provider INTEGER,
                 valid INTEGER
               )"""
        )
        db.execSQL(
            """CREATE TABLE configuration (
                 _id INTEGER PRIMARY KEY, url TEXT, sync_url TEXT
               )"""
        )
        db.insert("location", null, ContentValues().apply {
            put("time", 1_716_000_000_000L)
            put("latitude", 19.4326)
            put("longitude", -99.1332)
            put("speed", 12.5)
            put("provider", "gps")
            put("valid", 1)
        })
        db.version = version
        db.close()
    }

    private fun columnsOf(table: String): Set<String> {
        val helper = LocationDbHelper.getInstance(ctx)
        val cols = mutableSetOf<String>()
        helper.readableDatabase.rawQuery("PRAGMA table_info($table)", null).use { c ->
            val nameIdx = c.getColumnIndexOrThrow("name")
            while (c.moveToNext()) cols += c.getString(nameIdx)
        }
        return cols
    }

    @Test fun upgradesLegacyV11SchemaPreservingRows() {
        seedLegacyDb(11)

        // Opening through the helper triggers onUpgrade(11 → current).
        val db = LocationDbHelper.getInstance(ctx).readableDatabase

        // Row survived the migration.
        db.rawQuery("SELECT COUNT(*) FROM location", null).use { c ->
            c.moveToFirst()
            assertEquals(1, c.getInt(0))
        }

        // Columns added across v12–v22 now exist on `location`.
        val locCols = columnsOf("location")
        assertTrue("events_json missing: $locCols", "events_json" in locCols)
        assertTrue("battery_level missing: $locCols", "battery_level" in locCols)
        assertTrue("is_charging missing: $locCols", "is_charging" in locCols)
        assertTrue("vertical_accuracy missing: $locCols", "vertical_accuracy" in locCols)

        // `configuration` gained config_json; the session table was created.
        assertTrue("config_json missing", "config_json" in columnsOf("configuration"))
        assertTrue("location_session not created", columnsOf("location_session").isNotEmpty())
    }
}
