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
            conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                requestMethod = method.uppercase()
                connectTimeout = 30_000
                readTimeout    = 120_000
                doOutput = true
                setFixedLengthStreamingMode(bytes.size)
                setRequestProperty("Content-Type", contentType)
                setRequestProperty("Content-Length", bytes.size.toString())
                headers?.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            conn.outputStream.use { it.write(bytes) }
            conn.responseCode
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
