// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

internal object NotificationHelper {

    const val SERVICE_CHANNEL_ID = "bglocservice"
    const val SYNC_CHANNEL_ID    = "syncservice"

    fun registerAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(SERVICE_CHANNEL_ID, "Location Service",
                NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(SYNC_CHANNEL_ID, "Location Sync",
                NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) }
        )
    }

    fun buildServiceNotification(context: Context, config: BGConfig): Notification {
        val title   = config.notificationTitle?.takeIf { it !== BGConfig.NULL_STRING } ?: "Background location"
        val text    = config.notificationText?.takeIf { it !== BGConfig.NULL_STRING }  ?: "Recording location"
        val smallIconName = config.notificationIconSmall?.takeIf { it !== BGConfig.NULL_STRING }
        val largeIconName = config.notificationIconLarge?.takeIf { it !== BGConfig.NULL_STRING }
        val colorStr      = config.notificationIconColor?.takeIf { it !== BGConfig.NULL_STRING }

        val builder = NotificationCompat.Builder(context, SERVICE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSmallIcon(resolveIcon(context, smallIconName) ?: android.R.drawable.ic_menu_mylocation)

        largeIconName?.let { name ->
            resolveIcon(context, name)?.let { builder.setLargeIcon(
                android.graphics.BitmapFactory.decodeResource(context.resources, it)) }
        }
        colorStr?.let { parseColor(it)?.let { color -> builder.setColor(color) } }

        return builder.build()
    }

    private fun resolveIcon(context: Context, name: String?): Int? {
        name ?: return null
        val rid = context.resources.getIdentifier(name, "drawable", context.packageName)
        return if (rid != 0) rid else null
    }

    private fun parseColor(color: String): Int? = try {
        android.graphics.Color.parseColor(color)
    } catch (_: Exception) { null }
}
