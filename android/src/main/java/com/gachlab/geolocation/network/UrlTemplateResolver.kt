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

    // The closing brace MUST be escaped (\\}). On stricter Android/ART regex
    // engines a dangling '}' makes Pattern.compile throw at class-init time
    // (ExceptionInInitializerError), which silently kills every location POST.
    // Keep this in sync with the iOS resolver: #"\{([a-zA-Z0-9_]+)\}"#.
    private val PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}")

    // SimpleDateFormat is not thread-safe; one instance per thread avoids contention.
    // ThreadLocal.withInitial(Supplier) requires API 26; use object subclass for API 23+ compat.
    private val ISO_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
        override fun initialValue() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).also {
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
            val resolved = ctx[key]
            // Resolved values are percent-encoded; unknown/null placeholders are left as-is.
            // Encoding also keeps appendReplacement safe ($ and \ become %24/%5C).
            val replacement = if (resolved != null) encode(resolved.toString()) else m.group(0)
            m.appendReplacement(sb, replacement)
        }
        m.appendTail(sb)
        return sb.toString()
    }

    // RFC 3986 percent-encoding: keep unreserved chars, percent-encode every other
    // UTF-8 byte as uppercase %XX. MUST stay byte-identical to the iOS resolver's encode().
    private const val UNRESERVED =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"

    private fun encode(value: String): String {
        val sb = StringBuilder()
        for (b in value.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if (c < 128 && c.toChar() in UNRESERVED) sb.append(c.toChar())
            else sb.append('%').append("%02X".format(c))
        }
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
