// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.util.Log
import com.gachlab.geolocation.BGConfig

internal class RawLocationProvider(context: Context) :
    AbstractLocationProvider(context), LocationListener {

    private val locationManager by lazy {
        mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    private var isStarted = false
    private val activeProviders = mutableListOf<String>()

    override fun onCreate() {
        super.onCreate()
        PROVIDER_ID = BGConfig.RAW_PROVIDER
    }

    override fun onStart() {
        if (isStarted) return
        val cfg = mConfig ?: run { Log.w(TAG, "Started without config"); return }
        val providers = pickProviders()
        if (providers.isEmpty()) {
            Log.w(TAG, "No location provider available")
            return
        }
        activeProviders.clear()
        providers.forEach { provider ->
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    cfg.interval?.toLong() ?: BGConfig.DEFAULT_INTERVAL.toLong(),
                    (cfg.distanceFilter ?: BGConfig.DEFAULT_DISTANCE_FILTER).toFloat(),
                    this
                )
                activeProviders += provider
            } catch (e: SecurityException) {
                handleSecurityException(e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "requestLocationUpdates($provider) failed: ${e.message}")
            }
        }
        isStarted = activeProviders.isNotEmpty()
    }

    override fun onStop() {
        if (!isStarted) return
        try { locationManager.removeUpdates(this) }
        catch (e: SecurityException) { handleSecurityException(e) }
        finally { activeProviders.clear(); isStarted = false }
    }

    override fun onDestroy() { onStop() }

    override fun isStarted() = isStarted

    override fun onConfigure(config: BGConfig) {
        super.onConfigure(config)
        if (isStarted) { onStop(); onStart() }
    }

    override fun onLocationChanged(location: Location) = handleLocation(location)
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onProviderEnabled(provider: String) { Log.d(TAG, "$provider enabled") }
    override fun onProviderDisabled(provider: String) {
        Log.w(TAG, "$provider disabled")
        if (pickProviders().isEmpty()) handleServiceError("Location provider disabled and no fallback.")
    }

    private fun pickProviders(): List<String> {
        val desired = mConfig?.desiredAccuracy ?: 100
        val wantGps = desired < 1000
        val wantNet = desired >= 10
        val gpsOn = try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
        val netOn = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { false }
        val result = mutableListOf<String>()
        if (wantGps && gpsOn) result += LocationManager.GPS_PROVIDER
        if (wantNet && netOn) result += LocationManager.NETWORK_PROVIDER
        if (result.isEmpty()) {
            if (gpsOn) result += LocationManager.GPS_PROVIDER
            else if (netOn) result += LocationManager.NETWORK_PROVIDER
        }
        return result
    }

    companion object {
        private const val TAG = "RawLocationProvider"
    }
}
