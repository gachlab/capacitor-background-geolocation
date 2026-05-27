// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gachlab.geolocation.persistence.ConfigDAO
import com.gachlab.geolocation.service.LocationService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "Boot completed")
        try {
            val config = ConfigDAO(context).retrieveConfig() ?: return
            if (config.startOnBoot != true) return
            Log.i(TAG, "startOnBoot=true — starting LocationService")
            val serviceIntent = Intent(context, LocationService::class.java)
                .putExtra(LocationService.EXTRA_START_REASON, ServiceEvent.REASON_BOOT)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Boot start failed: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
