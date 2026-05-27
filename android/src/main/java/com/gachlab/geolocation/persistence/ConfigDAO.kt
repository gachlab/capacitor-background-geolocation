// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.persistence

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.gachlab.capacitor.backgroundgeolocation.GachConfigMapper
import com.gachlab.geolocation.BGConfig

internal class ConfigDAO(context: Context) {

    private val db: SQLiteDatabase = LocationDbHelper.getInstance(context).writableDatabase

    fun retrieveConfig(): BGConfig? {
        db.query("configuration", null, null, null, null, null, null).use { c ->
            if (!c.moveToFirst()) return null
            // Primary path: config_json blob (added v4.4.1 / DB v20)
            val idxJson = c.getColumnIndex("config_json")
            if (idxJson >= 0 && !c.isNull(idxJson)) {
                val json = c.getString(idxJson)
                if (!json.isNullOrEmpty()) {
                    return try {
                        GachConfigMapper.fromJSONObject(org.json.JSONObject(json))
                    } catch (e: Exception) {
                        Log.w(TAG, "config_json parse error, falling back to columns: ${e.message}")
                        hydrateFromColumns(c)
                    }
                }
            }
            return hydrateFromColumns(c)
        }
    }

    fun persistConfig(config: BGConfig) {
        val configJson = try {
            GachConfigMapper.toJSONObject(config).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to serialize config: ${e.message}")
            return
        }

        val cv = ContentValues().apply {
            // Write the JSON blob (primary) and key legacy columns (compat with older readers).
            put("config_json", configJson)
            put("stationary_radius",   config.stationaryRadius ?: BGConfig.DEFAULT_STATIONARY_RADIUS)
            put("distance_filter",     config.distanceFilter   ?: BGConfig.DEFAULT_DISTANCE_FILTER)
            put("desired_accuracy",    config.desiredAccuracy  ?: BGConfig.DEFAULT_DESIRED_ACCURACY)
            put("debugging",           if (config.debug == true) 1 else 0)
            put("notification_title",  config.notificationTitle)
            put("notification_text",   config.notificationText)
            put("stop_terminate",      if (config.stopOnTerminate == true) 1 else 0)
            put("start_boot",          if (config.startOnBoot    == true) 1 else 0)
            put("start_foreground",    if (config.startForeground == true) 1 else 0)
            put("notifications_enabled", if (config.notificationsEnabled == true) 1 else 0)
            put("stop_still",          if (config.stopOnStillActivity == true) 1 else 0)
            put("service_provider",    config.locationProvider ?: BGConfig.DISTANCE_FILTER_PROVIDER)
            put("interval",            config.interval         ?: BGConfig.DEFAULT_INTERVAL)
            put("fastest_interval",    config.fastestInterval  ?: BGConfig.DEFAULT_FASTEST_INTERVAL)
            put("activities_interval", config.activitiesInterval ?: BGConfig.DEFAULT_ACTIVITIES_INTERVAL)
            put("url",                 config.url)
            put("sync_url",            config.syncUrl)
            put("sync_threshold",      config.syncThreshold    ?: BGConfig.DEFAULT_SYNC_THRESHOLD)
            put("sync_enabled",        if (config.syncEnabled  == true) 1 else 0)
            put("max_locations",       config.maxLocations     ?: BGConfig.DEFAULT_MAX_LOCATIONS)
            put("show_time",           if (config.showTime     == true) 1 else 0)
            put("show_distance",       if (config.showDistance == true) 1 else 0)
        }

        val rowCount = android.database.DatabaseUtils.queryNumEntries(db, "configuration")
        if (rowCount == 0L) {
            db.insertOrThrow("configuration", "NULLHACK", cv)
        } else {
            db.update("configuration", cv, null, null)
        }
    }

    // ── Fallback: hydrate from legacy per-column storage ─────────────────────

    private fun hydrateFromColumns(c: android.database.Cursor): BGConfig {
        val cfg = BGConfig.getDefault()
        cfg.stationaryRadius = getFloat(c, "stationary_radius") ?: cfg.stationaryRadius
        cfg.distanceFilter   = getInt(c, "distance_filter")     ?: cfg.distanceFilter
        cfg.desiredAccuracy  = getInt(c, "desired_accuracy")    ?: cfg.desiredAccuracy
        cfg.debug            = getBool(c, "debugging")           ?: cfg.debug
        cfg.notificationTitle = getString(c, "notification_title") ?: cfg.notificationTitle
        cfg.notificationText  = getString(c, "notification_text")  ?: cfg.notificationText
        cfg.notificationSyncTitle = getString(c, "notification_sync_title")
        cfg.notificationSyncText  = getString(c, "notification_sync_text")
        cfg.notificationSyncCompletedText = getString(c, "notification_sync_completed_text")
        cfg.notificationSyncFailedText    = getString(c, "notification_sync_failed_text")
        cfg.stopOnTerminate    = getBool(c, "stop_terminate")  ?: cfg.stopOnTerminate
        cfg.startOnBoot        = getBool(c, "start_boot")      ?: cfg.startOnBoot
        cfg.startForeground    = getBool(c, "start_foreground") ?: cfg.startForeground
        cfg.notificationsEnabled = getBool(c, "notifications_enabled") ?: cfg.notificationsEnabled
        cfg.stopOnStillActivity  = getBool(c, "stop_still")    ?: cfg.stopOnStillActivity
        cfg.locationProvider   = getInt(c, "service_provider") ?: cfg.locationProvider
        cfg.interval           = getInt(c, "interval")         ?: cfg.interval
        cfg.fastestInterval    = getInt(c, "fastest_interval") ?: cfg.fastestInterval
        cfg.activitiesInterval = getInt(c, "activities_interval") ?: cfg.activitiesInterval
        cfg.url                = getString(c, "url")            ?: cfg.url
        cfg.syncUrl            = getString(c, "sync_url")       ?: cfg.syncUrl
        cfg.syncThreshold      = getInt(c, "sync_threshold")   ?: cfg.syncThreshold
        cfg.syncEnabled        = getBool(c, "sync_enabled")    ?: cfg.syncEnabled
        cfg.maxLocations       = getInt(c, "max_locations")    ?: cfg.maxLocations
        cfg.showTime           = getBool(c, "show_time")       ?: cfg.showTime
        cfg.showDistance       = getBool(c, "show_distance")   ?: cfg.showDistance
        return cfg
    }

    private fun getFloat(c: android.database.Cursor, col: String): Float? {
        val idx = c.getColumnIndex(col)
        return if (idx >= 0 && !c.isNull(idx)) c.getFloat(idx) else null
    }
    private fun getInt(c: android.database.Cursor, col: String): Int? {
        val idx = c.getColumnIndex(col)
        return if (idx >= 0 && !c.isNull(idx)) c.getInt(idx) else null
    }
    private fun getString(c: android.database.Cursor, col: String): String? {
        val idx = c.getColumnIndex(col)
        return if (idx >= 0 && !c.isNull(idx)) c.getString(idx) else null
    }
    private fun getBool(c: android.database.Cursor, col: String): Boolean? =
        getInt(c, col)?.let { it != 0 }

    companion object {
        private const val TAG = "ConfigDAO"
    }
}
