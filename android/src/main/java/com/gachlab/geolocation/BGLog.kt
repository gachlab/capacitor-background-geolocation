// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.content.Context
import android.util.Log
import com.gachlab.geolocation.persistence.LogDAO
import com.gachlab.geolocation.persistence.LogLevels

/**
 * Central plugin logger. Mirrors each entry to logcat and persists it to the
 * `logs` table so `getLogEntries` can return it later (cross-platform parity
 * with the iOS BGLog). Writing at the event source (LocationService, the sync
 * worker) means diagnostics survive even when no Capacitor bridge is attached.
 *
 * Wired at low-volume, high-signal points (lifecycle, sync, auth, errors) — never
 * per-location, to keep the on-disk log bounded.
 */
object BGLog {

    private const val TAG = "BGGeolocation"

    @Volatile private var dao: LogDAO? = null

    /** Idempotent — safe to call from both the service and the plugin. */
    fun init(context: Context) {
        if (dao == null) synchronized(this) {
            if (dao == null) dao = LogDAO(context.applicationContext)
        }
    }

    fun d(message: String) = log(LogLevels.DEBUG, message, null)
    fun i(message: String) = log(LogLevels.INFO,  message, null)
    fun w(message: String, t: Throwable? = null) = log(LogLevels.WARN,  message, t)
    fun e(message: String, t: Throwable? = null) = log(LogLevels.ERROR, message, t)

    private fun log(level: Int, message: String, t: Throwable?) {
        when (level) {
            LogLevels.WARN  -> Log.w(TAG, message, t)
            LogLevels.ERROR -> Log.e(TAG, message, t)
            LogLevels.INFO  -> Log.i(TAG, message)
            else            -> Log.d(TAG, message)
        }
        val stack = t?.let { Log.getStackTraceString(it) }
        try {
            dao?.insert(level, message, stack, System.currentTimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist log entry: ${e.message}")
        }
    }
}
