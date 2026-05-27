// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.os.Build
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.BGLocation

internal abstract class AbstractLocationProvider(protected val mContext: Context) {

    interface Delegate {
        fun onLocation(location: BGLocation)
        fun onStationary(location: BGLocation, radius: Float)
        fun onError(error: BGException)
    }

    protected var mConfig: BGConfig? = null
    protected var mDelegate: Delegate? = null
    protected var PROVIDER_ID: Int = BGConfig.DISTANCE_FILTER_PROVIDER

    open fun onCreate() {}
    abstract fun onStart()
    abstract fun onStop()
    abstract fun onDestroy()
    abstract fun isStarted(): Boolean

    open fun onConfigure(config: BGConfig) {
        mConfig = config
    }

    open fun onCommand(commandId: Int, arg1: Int) {}

    fun setDelegate(delegate: Delegate) {
        mDelegate = delegate
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    protected fun handleLocation(androidLocation: Location) {
        val maxAccuracy = mConfig?.maxAcceptedAccuracy
        if (maxAccuracy != null && maxAccuracy > 0 && androidLocation.accuracy > maxAccuracy) {
            Log.d(TAG, "Location accuracy ${androidLocation.accuracy} > max $maxAccuracy, discarding")
            return
        }
        val loc = BGLocation.fromLocation(androidLocation)
        loc.locationProvider = PROVIDER_ID
        mDelegate?.onLocation(loc)
    }

    protected fun handleStationary(androidLocation: Location, radius: Float) {
        val loc = BGLocation.fromLocation(androidLocation)
        loc.locationProvider = PROVIDER_ID
        mDelegate?.onStationary(loc, radius)
    }

    protected fun handleServiceError(message: String) {
        Log.e(TAG, message)
        mDelegate?.onError(BGException(message))
    }

    protected fun handleSecurityException(e: SecurityException) {
        Log.e(TAG, "SecurityException: ${e.message}")
        mDelegate?.onError(BGException("Location permission denied: ${e.message}", e))
    }

    protected fun hasMockLocationsEnabled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Settings.Secure.getString(
                    mContext.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION
                ) == "1"
            } else {
                @Suppress("DEPRECATION")
                Settings.Secure.getString(
                    mContext.contentResolver, Settings.Secure.ALLOW_MOCK_LOCATION
                ) == "1"
            }
        } catch (e: Exception) { false }
    }

    protected fun registerReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            mContext.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            mContext.registerReceiver(receiver, filter)
        }
    }

    protected fun unregisterReceiver(receiver: BroadcastReceiver) {
        try { mContext.unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    companion object {
        private const val TAG = "LocationProvider"

        // Command IDs
        const val CMD_SWITCH_MODE  = 1

        // Mode args
        const val BACKGROUND_MODE  = 0
        const val FOREGROUND_MODE  = 1
    }
}

/** Lightweight exception type for provider errors (replaces PluginException). */
class BGException(message: String, cause: Throwable? = null) : Exception(message, cause)
