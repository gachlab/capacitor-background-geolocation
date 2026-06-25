// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.persistence

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gachlab.geolocation.BGConfig
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

    @Test fun retrieveBeforePersistReturnsNull() {
        assertEquals(null, ConfigDAO(ctx).retrieveConfig())
    }
}
