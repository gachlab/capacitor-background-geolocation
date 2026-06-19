// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.network

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

internal class HttpClient(
    private val urlString: String,
    private val method: String = "POST",
    private val headers: Map<String, String>? = null,
    private val contentType: String = "application/json"
) {

    /** @return HTTP response code, or -1 on network error */
    fun postJSON(body: JSONObject): Int = send(body.toString())

    fun postJSON(body: JSONArray): Int {
        if (contentType.contains("application/x-www-form-urlencoded", ignoreCase = true)) {
            // Post each array element as a separate request; return last code.
            var code = -1
            for (i in 0 until body.length()) {
                val item = body.optJSONObject(i) ?: continue
                code = postJSON(item)
            }
            return code
        }
        return send(body.toString())
    }

    private fun send(bodyStr: String): Int {
        val bytes = bodyStr.toByteArray(StandardCharsets.UTF_8)
        var conn: HttpURLConnection? = null
        return try {
            val c = URL(urlString).openConnection() as HttpURLConnection
            conn = c
            // Set request headers FIRST — before requestMethod / doOutput. On the
            // Android okhttp HttpURLConnection impl (observed on Samsung One UI /
            // SM-X810, API 34) the first call on a fresh connection must be a
            // setRequestProperty; if requestMethod or doOutput is touched first the
            // connection is treated as already "made", so every later
            // setRequestProperty throws "Cannot set request property after
            // connection is made" — which made every location POST fail with
            // HTTP -1 and the sync queue grow without bound.
            c.setRequestProperty("Content-Type", contentType)
            headers?.forEach { (k, v) -> c.setRequestProperty(k, v) }
            c.requestMethod = method.uppercase()
            c.connectTimeout = 30_000
            c.readTimeout    = 120_000
            c.doOutput = true
            // setFixedLengthStreamingMode also sets Content-Length itself.
            c.setFixedLengthStreamingMode(bytes.size)
            c.outputStream.use { it.write(bytes) }
            c.responseCode
        } catch (e: Exception) {
            Log.e(TAG, "HTTP $method $urlString failed: ${e.message}")
            -1
        } finally {
            conn?.disconnect()
        }
    }

    companion object {
        private const val TAG = "HttpClient"

        /** Build query string from JSON object for form-urlencoded mode. */
        fun jsonToFormParams(json: JSONObject): String {
            val sb = StringBuilder()
            val keys = json.keys()
            while (keys.hasNext()) {
                if (sb.isNotEmpty()) sb.append('&')
                val key = keys.next()
                sb.append(java.net.URLEncoder.encode(key, "UTF-8"))
                sb.append('=')
                sb.append(java.net.URLEncoder.encode(json.optString(key), "UTF-8"))
            }
            return sb.toString()
        }
    }
}
