// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.persistence

import android.content.ContentValues
import android.database.Cursor
import com.gachlab.geolocation.BGLocation

/**
 * Shared hydration and content-values logic for both the main location table
 * and the session table, which share the same core column set.
 */
internal abstract class BaseLocationDAO {

    protected fun hydrateCore(c: Cursor): BGLocation {
        val loc = BGLocation().also {
            it.provider = c.getString(c.getColumnIndexOrThrow("provider"))
        }
        loc.locationId       = c.getLong(c.getColumnIndexOrThrow("_id"))
        loc.time             = c.getLong(c.getColumnIndexOrThrow("time"))
        loc.latitude         = c.getDouble(c.getColumnIndexOrThrow("latitude"))
        loc.longitude        = c.getDouble(c.getColumnIndexOrThrow("longitude"))
        loc.locationProvider = c.getInt(c.getColumnIndexOrThrow("service_provider"))
        loc.mockFlags        = c.getInt(c.getColumnIndexOrThrow("mock_flags"))

        if (c.getInt(c.getColumnIndexOrThrow("has_accuracy")) == 1)
            loc.accuracy = c.getFloat(c.getColumnIndexOrThrow("accuracy"))
        if (c.getInt(c.getColumnIndexOrThrow("has_vertical_accuracy")) == 1)
            loc.verticalAccuracy = c.getFloat(c.getColumnIndexOrThrow("vertical_accuracy"))
        if (c.getInt(c.getColumnIndexOrThrow("has_speed")) == 1)
            loc.speed = c.getFloat(c.getColumnIndexOrThrow("speed"))
        if (c.getInt(c.getColumnIndexOrThrow("has_bearing")) == 1)
            loc.bearing = c.getFloat(c.getColumnIndexOrThrow("bearing"))
        if (c.getInt(c.getColumnIndexOrThrow("has_altitude")) == 1)
            loc.altitude = c.getDouble(c.getColumnIndexOrThrow("altitude"))
        if (c.getInt(c.getColumnIndexOrThrow("has_radius")) == 1)
            loc.radius = c.getFloat(c.getColumnIndexOrThrow("radius"))

        return loc
    }

    protected fun coreContentValues(l: BGLocation): ContentValues = ContentValues().apply {
        put("provider",              l.provider)
        put("time",                  l.time)
        put("accuracy",              l.accuracy)
        put("vertical_accuracy",     l.verticalAccuracy)
        put("speed",                 l.speed)
        put("bearing",               l.bearing)
        put("altitude",              l.altitude)
        put("radius",                l.radius)
        put("latitude",              l.latitude)
        put("longitude",             l.longitude)
        put("has_accuracy",          if (l.hasAccuracy)         1 else 0)
        put("has_vertical_accuracy", if (l.hasVerticalAccuracy) 1 else 0)
        put("has_speed",             if (l.hasSpeed)            1 else 0)
        put("has_bearing",           if (l.hasBearing)          1 else 0)
        put("has_altitude",          if (l.hasAltitude)         1 else 0)
        put("has_radius",            if (l.hasRadius)           1 else 0)
        put("service_provider",      l.locationProvider)
        put("mock_flags",            l.mockFlags)
    }
}
