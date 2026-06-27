// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import kotlin.math.abs

/**
 * Pure decision logic for the stationary-region state machine — the core of the
 * movement engine, isolated from the AlarmManager/GMS plumbing so it can be unit
 * tested and shared by whatever mechanism (polling today, native geofence next)
 * actually delivers the location samples.
 *
 * "Has the device left the stationary region?" reduces to comparing the
 * accuracy-adjusted distance from the stationary centre against the region radius.
 */
internal object StationaryRegion {

    enum class Decision {
        /** The fix is outside the region — the device is moving again. */
        EXIT,

        /** Just outside the centre but still inside the region — poll aggressively. */
        POLL_FAST,

        /** Right at the centre — relax to the lazy poll cadence. */
        POLL_LAZY,
    }

    /**
     * Distance from the stationary centre, shrunk by both fixes' accuracy so GPS
     * jitter inside the combined uncertainty does not read as movement. Never
     * negative.
     */
    fun adjustedDistance(rawDistanceMeters: Float, centerAccuracy: Float, fixAccuracy: Float): Float =
        abs(rawDistanceMeters - centerAccuracy - fixAccuracy)

    /**
     * Decide the next action from the accuracy-adjusted distance and the region
     * radius. A native geofence makes [Decision.EXIT] an OS callback instead of a
     * polled comparison, but the threshold semantics stay identical.
     */
    fun decide(adjustedDistanceMeters: Float, radiusMeters: Float): Decision = when {
        adjustedDistanceMeters > radiusMeters -> Decision.EXIT
        adjustedDistanceMeters > 0f -> Decision.POLL_FAST
        else -> Decision.POLL_LAZY
    }
}
