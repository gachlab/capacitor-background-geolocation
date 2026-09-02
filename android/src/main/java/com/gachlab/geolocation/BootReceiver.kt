// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.gachlab.geolocation.persistence.ConfigDAO
import com.gachlab.geolocation.service.ForegroundLocationGate
import com.gachlab.geolocation.service.LocationService

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Log.d(TAG, "Boot completed")
        try {
            val config = ConfigDAO(context).retrieveConfig() ?: return
            // Either the user asked for start-on-boot, or tracking was actually
            // running when the device went down. The second case had no way to be
            // known: an active shift with `startOnBoot: false` was silently lost
            // across a reboot, and `startOnBoot: true` restarted tracking for a
            // driver who had already ended theirs. They are different questions.
            val wasRunning = com.gachlab.geolocation.service.ServiceReviver.wasSupposedToRun(context)
            if (config.startOnBoot != true && !wasRunning) return
            // Same contract, same reason as `BGFacade.start()` (#59): a boot that
            // starts a `location`-typed foreground service without the permission
            // is a promise the service cannot keep, and the app is killed for it.
            // A reboot is exactly when this bites — Android can auto-revoke while
            // the device is off, so the shift comes back to a permission that is
            // no longer there.
            if (!ForegroundLocationGate.canStartLocationService(context.applicationContext)) {
                Log.w(TAG, "not starting on boot: the app has no location permission")
                LocationService.recordPermissionLost(context.applicationContext)
                return
            }
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
