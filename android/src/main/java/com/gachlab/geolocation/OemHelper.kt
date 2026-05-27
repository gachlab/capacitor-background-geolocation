// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

internal object OemHelper {

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "requestIgnoreBatteryOptimizations: ${e.message}")
        }
    }

    fun openBatterySettings(context: Context) {
        try {
            val intent = Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "openBatterySettings: ${e.message}")
        }
    }

    fun openAutoStartSettings(context: Context) {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intents = oemAutoStartIntents(manufacturer)
        for (intent in intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        // Fallback: open app details
        try {
            context.startActivity(Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.e(TAG, "openAutoStartSettings fallback: ${e.message}")
        }
    }

    fun getManufacturerHelp(): JSONObject {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val json = JSONObject()
        json.put("manufacturer", Build.MANUFACTURER)
        val steps = JSONArray()
        when {
            manufacturer.contains("xiaomi") -> {
                steps.put("Open Security app → Permissions → Autostart → Enable for this app")
                steps.put("Battery & Performance → App Battery Saver → No Restrictions")
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                steps.put("Settings → Apps → Advanced → Ignore battery optimisations")
                steps.put("Phone Manager → Protected Apps → Enable")
            }
            manufacturer.contains("oppo") || manufacturer.contains("realme") -> {
                steps.put("Settings → Battery → Energy Saver → Disable for this app")
                steps.put("Privacy Permissions → Startup Manager → Enable")
            }
            manufacturer.contains("vivo") -> {
                steps.put("iManager → App Manager → Autostart Manager → Enable")
            }
            manufacturer.contains("samsung") -> {
                steps.put("Battery → Sleeping Apps → Remove this app")
                steps.put("Device Care → Battery → Background usage limits → Never sleeping apps → Add")
            }
            manufacturer.contains("oneplus") -> {
                steps.put("Settings → Battery → Battery Optimisation → Don't Optimise")
            }
            manufacturer.contains("asus") -> {
                steps.put("PowerMaster → Auto-start Manager → Enable")
            }
            else -> {
                steps.put("Settings → Apps → Select this app → Battery → Allow background activity")
            }
        }
        json.put("steps", steps)
        return json
    }

    private fun oemAutoStartIntents(manufacturer: String): List<Intent> {
        val intents = mutableListOf<Intent>()
        when {
            manufacturer.contains("xiaomi") -> {
                intents += Intent().setComponent(android.content.ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity"))
            }
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> {
                intents += Intent().setComponent(android.content.ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"))
            }
            manufacturer.contains("oppo") -> {
                intents += Intent().setComponent(android.content.ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.privacypermissionsentry.PermissionTopActivity"))
            }
            manufacturer.contains("vivo") -> {
                intents += Intent().setComponent(android.content.ComponentName(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"))
            }
            manufacturer.contains("samsung") -> {
                intents += Intent().setComponent(android.content.ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"))
            }
            manufacturer.contains("asus") -> {
                intents += Intent().setComponent(android.content.ComponentName(
                    "com.asus.mobilemanager",
                    "com.asus.mobilemanager.MainActivity"))
            }
        }
        return intents
    }

    private const val TAG = "OemHelper"
}
