// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.persistence

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.gachlab.geolocation.BGLocation

internal class SessionDAO(private val context: Context) : BaseLocationDAO() {

    private val db: SQLiteDatabase = LocationDbHelper.getInstance(context).writableDatabase
    private val prefs by lazy {
        context.applicationContext.getSharedPreferences("bgloc_session", Context.MODE_PRIVATE)
    }

    fun startSession() {
        db.delete("location_session", null, null)
        prefs.edit().putBoolean(KEY_ACTIVE, true).apply()
    }

    fun clearSession() {
        db.delete("location_session", null, null)
        prefs.edit().putBoolean(KEY_ACTIVE, false).apply()
    }

    fun isSessionActive(): Boolean = prefs.getBoolean(KEY_ACTIVE, false)

    fun persistSessionLocation(location: BGLocation) {
        if (!isSessionActive()) return
        db.insertOrThrow("location_session", "NULLHACK", toContentValues(location))
    }

    fun getSessionLocations(): List<BGLocation> {
        val result = mutableListOf<BGLocation>()
        db.query("location_session", COLUMNS, null, null, null, null, "time ASC").use { c ->
            while (c.moveToNext()) result += hydrate(c)
        }
        return result
    }

    fun getSessionLocationsCount(): Int {
        db.rawQuery("SELECT COUNT(*) FROM location_session", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun hydrate(c: android.database.Cursor): BGLocation {
        val loc = hydrateCore(c)
        loc.batchStartMillis = 0L
        loc.status           = c.getInt(c.getColumnIndexOrThrow("valid"))
        return loc
    }

    private fun toContentValues(l: BGLocation) = coreContentValues(l).apply {
        put("valid",       0)
        put("batch_start", 0L)
    }

    companion object {
        private const val KEY_ACTIVE = "session_active"
        private val COLUMNS = arrayOf(
            "_id", "provider", "time",
            "accuracy", "vertical_accuracy", "speed", "bearing", "altitude",
            "radius", "latitude", "longitude",
            "has_accuracy", "has_vertical_accuracy", "has_speed", "has_bearing",
            "has_altitude", "has_radius",
            "service_provider", "valid", "batch_start", "mock_flags"
        )
    }
}
