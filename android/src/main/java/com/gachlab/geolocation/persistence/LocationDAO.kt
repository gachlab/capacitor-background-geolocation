// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.persistence

import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import com.gachlab.geolocation.BGLocation
import org.json.JSONArray

internal class LocationDAO(context: Context) : BaseLocationDAO() {

    private val db: SQLiteDatabase = LocationDbHelper.getInstance(context).writableDatabase

    // ── Queries ───────────────────────────────────────────────────────────────

    fun getAllLocations(): List<BGLocation> = query(null, null)

    fun getValidLocations(): List<BGLocation> =
        query("valid <> ?", arrayOf(BGLocation.STATUS_DELETED.toString()))

    fun getValidLocationsAndDelete(): List<BGLocation> {
        db.beginTransactionNonExclusive()
        return try {
            val list = getValidLocations()
            markAllDeleted()
            db.setTransactionSuccessful()
            list
        } finally {
            db.endTransaction()
        }
    }

    fun getLocationById(id: Long): BGLocation? =
        query("_id = ?", arrayOf(id.toString())).firstOrNull()

    fun getFirstUnpostedLocation(): BGLocation? = rawQueryFirst("""
        SELECT * FROM location
        WHERE _id = (SELECT MIN(_id) FROM location WHERE valid = ${BGLocation.STATUS_POST_PENDING})
    """.trimIndent())

    fun getNextUnpostedLocation(excludeId: Long): BGLocation? = rawQueryFirst("""
        SELECT * FROM location
        WHERE _id = (
            SELECT MIN(_id) FROM location
            WHERE valid = ${BGLocation.STATUS_POST_PENDING} AND _id <> $excludeId
        )
    """.trimIndent())

    fun getUnpostedCount(): Long =
        DatabaseUtils.queryNumEntries(db, "location", "valid = ?",
            arrayOf(BGLocation.STATUS_POST_PENDING.toString()))

    fun getSyncPendingCount(millisSinceLastBatch: Long): Long =
        DatabaseUtils.queryNumEntries(db, "location",
            "valid = ? AND (batch_start IS NULL OR batch_start < ?)",
            arrayOf(BGLocation.STATUS_SYNC_PENDING.toString(), millisSinceLastBatch.toString()))

    // ── Persist ───────────────────────────────────────────────────────────────

    fun persistLocation(location: BGLocation): Long {
        val id = db.insertOrThrow("location", "NULLHACK", toContentValues(location))
        location.locationId = id
        return id
    }

    /**
     * Persist with a row cap. At capacity, the oldest row is recycled (UPDATE, not INSERT)
     * to avoid unbounded growth.
     */
    fun persistLocation(location: BGLocation, maxRows: Int): Long {
        if (maxRows == 0) return -1

        val rowCount = DatabaseUtils.queryNumEntries(db, "location")

        if (rowCount < maxRows) return persistLocation(location)

        db.beginTransactionNonExclusive()
        return try {
            if (rowCount > maxRows) {
                db.execSQL(
                    "DELETE FROM location WHERE _id IN " +
                    "(SELECT _id FROM location ORDER BY time LIMIT ?)",
                    arrayOf(rowCount - maxRows)
                )
                db.execSQL("VACUUM")
            }

            val oldestId = db.rawQuery(
                "SELECT MIN(_id) FROM location WHERE time = (SELECT MIN(time) FROM location)",
                null
            ).use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }

            if (oldestId < 0) return persistLocation(location)

            db.execSQL(
                """UPDATE location SET
                    provider=?,time=?,accuracy=?,vertical_accuracy=?,speed=?,bearing=?,
                    altitude=?,radius=?,latitude=?,longitude=?,
                    has_accuracy=?,has_vertical_accuracy=?,has_speed=?,has_bearing=?,
                    has_altitude=?,has_radius=?,
                    service_provider=?,batch_start=?,valid=?,mock_flags=?,
                    events_json=?,battery_level=?,is_charging=?
                WHERE _id=?""",
                arrayOf(
                    location.provider, location.time,
                    location.accuracy, location.verticalAccuracy,
                    location.speed, location.bearing, location.altitude, location.radius,
                    location.latitude, location.longitude,
                    if (location.hasAccuracy) 1 else 0,
                    if (location.hasVerticalAccuracy) 1 else 0,
                    if (location.hasSpeed) 1 else 0,
                    if (location.hasBearing) 1 else 0,
                    if (location.hasAltitude) 1 else 0,
                    if (location.hasRadius) 1 else 0,
                    location.locationProvider, location.batchStartMillis,
                    location.status, location.mockFlags,
                    location.drivingEvents?.toString(),
                    location.batteryLevel,
                    location.isCharging?.let { if (it) 1 else 0 },
                    oldestId
                )
            )
            db.setTransactionSuccessful()
            location.locationId = oldestId
            oldestId
        } finally {
            db.endTransaction()
        }
    }

    fun persistLocationForSync(location: BGLocation, maxRows: Int): Long {
        val id = location.locationId
        return if (id == null) {
            location.status = BGLocation.STATUS_SYNC_PENDING
            persistLocation(location, maxRows)
        } else {
            val cv = ContentValues().apply { put("valid", BGLocation.STATUS_SYNC_PENDING) }
            db.update("location", cv, "_id = ?", arrayOf(id.toString()))
            id
        }
    }

    // ── Updates ───────────────────────────────────────────────────────────────

    fun updateForSync(locationId: Long) {
        val cv = ContentValues().apply { put("valid", BGLocation.STATUS_SYNC_PENDING) }
        db.update("location", cv, "_id = ?", arrayOf(locationId.toString()))
    }

    // ── Deletes ───────────────────────────────────────────────────────────────

    fun deleteById(locationId: Long) {
        if (locationId < 0) return
        val cv = ContentValues().apply { put("valid", BGLocation.STATUS_DELETED) }
        db.update("location", cv, "_id = ?", arrayOf(locationId.toString()))
    }

    /** POST_PENDING → SYNC_PENDING */
    fun deleteUnpostedLocations(): Int {
        val cv = ContentValues().apply { put("valid", BGLocation.STATUS_SYNC_PENDING) }
        return db.update("location", cv, "valid = ?",
            arrayOf(BGLocation.STATUS_POST_PENDING.toString()))
    }

    /** SYNC_PENDING → DELETED */
    fun deletePendingSyncLocations(): Int {
        val cv = ContentValues().apply { put("valid", BGLocation.STATUS_DELETED) }
        return db.update("location", cv, "valid = ?",
            arrayOf(BGLocation.STATUS_SYNC_PENDING.toString()))
    }

    fun markAllDeleted(): Int {
        val cv = ContentValues().apply { put("valid", BGLocation.STATUS_DELETED) }
        return db.update("location", cv, null, null)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun query(where: String?, args: Array<String>?): List<BGLocation> {
        val result = mutableListOf<BGLocation>()
        db.query("location", COLUMNS, where, args, null, null, "time ASC").use { c ->
            while (c.moveToNext()) result += hydrate(c)
        }
        return result
    }

    private fun rawQueryFirst(sql: String): BGLocation? {
        db.rawQuery(sql, null).use { c ->
            if (c.moveToFirst()) return hydrate(c)
        }
        return null
    }

    private fun hydrate(c: android.database.Cursor): BGLocation {
        val loc = hydrateCore(c)
        loc.batchStartMillis = c.getLong(c.getColumnIndexOrThrow("batch_start"))
        loc.status           = c.getInt(c.getColumnIndexOrThrow("valid"))

        val idxEv = c.getColumnIndex("events_json")
        if (idxEv >= 0 && !c.isNull(idxEv)) {
            val s = c.getString(idxEv)
            if (!s.isNullOrEmpty()) try { loc.drivingEvents = JSONArray(s) } catch (_: Exception) {}
        }
        val idxBat = c.getColumnIndex("battery_level")
        if (idxBat >= 0 && !c.isNull(idxBat)) loc.batteryLevel = c.getInt(idxBat)
        val idxChg = c.getColumnIndex("is_charging")
        if (idxChg >= 0 && !c.isNull(idxChg)) loc.isCharging = c.getInt(idxChg) == 1

        return loc
    }

    private fun toContentValues(l: BGLocation): ContentValues = coreContentValues(l).apply {
        put("valid",         l.status)
        put("batch_start",   l.batchStartMillis)
        if (l.drivingEvents != null) put("events_json", l.drivingEvents.toString())
        else putNull("events_json")
        if (l.batteryLevel != null) put("battery_level", l.batteryLevel!!)
        else putNull("battery_level")
        if (l.isCharging != null) put("is_charging", if (l.isCharging!!) 1 else 0)
        else putNull("is_charging")
    }

    companion object {
        private val COLUMNS = arrayOf(
            "_id", "provider", "time",
            "accuracy", "vertical_accuracy", "speed", "bearing", "altitude",
            "radius", "latitude", "longitude",
            "has_accuracy", "has_vertical_accuracy", "has_speed", "has_bearing",
            "has_altitude", "has_radius",
            "service_provider", "valid", "batch_start", "mock_flags",
            "events_json", "battery_level", "is_charging"
        )
    }
}
