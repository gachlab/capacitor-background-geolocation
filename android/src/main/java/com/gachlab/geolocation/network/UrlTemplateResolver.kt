// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

import com.gachlab.geolocation.BGLocation
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.regex.Pattern

internal object UrlTemplateResolver {

    private val PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)}")

    // SimpleDateFormat is not thread-safe; one instance per thread avoids contention.
    private val ISO_FORMAT = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).also {
            it.timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    fun resolve(urlTemplate: String, location: BGLocation,
                queryParams: Map<String, String>? = null): String {
        val ctx = buildContext(location, queryParams)
        val m = PLACEHOLDER.matcher(urlTemplate)
        val sb = StringBuffer()
        while (m.find()) {
            val key = m.group(1) ?: continue
            val value = ctx[key] ?: m.group(0)  // leave unresolved placeholders as-is
            m.appendReplacement(sb, value?.toString() ?: "")
        }
        m.appendTail(sb)
        return sb.toString()
    }

    private fun buildContext(loc: BGLocation,
                             queryParams: Map<String, String>?): Map<String, Any?> {
        val ctx = mutableMapOf<String, Any?>(
            "latitude"    to loc.latitude,
            "longitude"   to loc.longitude,
            "lat"         to loc.latitude,
            "lon"         to loc.longitude,
            "time"        to loc.time,
            "timestamp"   to (loc.time / 1000L),
            "timestamp_iso" to isoTimestamp(loc.time),
            "speed"       to loc.speed,
            "altitude"    to loc.altitude,
            "bearing"     to loc.bearing,
            "accuracy"    to loc.accuracy,
            "provider"    to loc.provider,
            "is_moving"   to (loc.speed > 0)
        )
        queryParams?.forEach { (k, v) -> ctx[k] = v }
        return ctx
    }

    private fun isoTimestamp(timeMs: Long): String = ISO_FORMAT.get()!!.format(Date(timeMs))
}
