// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.persistence

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.gachlab.capacitor.backgroundgeolocation.GachConfigMapper
import com.gachlab.geolocation.BGConfig

/**
 * Persists [BGConfig] as a single JSON blob in the `configuration` table. The legacy
 * per-column storage (and its fallback hydration) was removed with the Cordova DB.
 */
internal class ConfigDAO(context: Context) : com.gachlab.geolocation.ports.ConfigRepository {

    private val db: SQLiteDatabase = LocationDbHelper.getInstance(context).writableDatabase

    override fun retrieveConfig(): BGConfig? {
        db.query("configuration", arrayOf("config_json"), null, null, null, null, null).use { c ->
            if (!c.moveToFirst() || c.isNull(0)) return null
            val json = c.getString(0)
            if (json.isNullOrEmpty()) return null
            return try {
                GachConfigMapper.fromJSONObject(org.json.JSONObject(json))
            } catch (e: Exception) {
                Log.w(TAG, "config_json parse error: ${e.message}")
                null
            }
        }
    }

    override fun persistConfig(config: BGConfig) {
        val configJson = try {
            GachConfigMapper.toJSONObject(config).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize config: ${e.message}")
            return
        }
        val cv = ContentValues().apply { put("config_json", configJson) }
        val rowCount = android.database.DatabaseUtils.queryNumEntries(db, "configuration")
        if (rowCount == 0L) {
            db.insertOrThrow("configuration", null, cv)
        } else {
            db.update("configuration", cv, null, null)
        }
    }

    companion object {
        private const val TAG = "ConfigDAO"
    }
}
