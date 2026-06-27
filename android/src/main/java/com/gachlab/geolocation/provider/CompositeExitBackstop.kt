// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

/**
 * Arms several [StationaryExitBackstop]s at once and resumes on whichever fires
 * first, then disarms them all. This is the robustness of the Transistorsoft model:
 * geofence (spatial) + activity recognition (motion) run together, so a missed
 * geofence transition or a quiet motion sensor on its own cannot strand the engine
 * in the stationary state — the other trigger still wakes it.
 */
internal class CompositeExitBackstop(
    private val backstops: List<StationaryExitBackstop>,
) : StationaryExitBackstop {

    private var fired = false

    override fun arm(latitude: Double, longitude: Double, radius: Float, onExit: () -> Unit) {
        fired = false
        val once = {
            if (!fired) {
                fired = true
                disarm()
                onExit()
            }
        }
        backstops.forEach { it.arm(latitude, longitude, radius, once) }
    }

    override fun disarm() {
        backstops.forEach { it.disarm() }
    }
}
