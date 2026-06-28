// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A geographic coordinate — a pure, immutable value object for the `domain/` layer
 * (Roadmap Fase 1). The great-circle distance was previously a private companion
 * helper inside `DrivingEventsDetector`, and the "previous fix" was three loose mutable
 * fields (prevLat / prevLon / hasPrev). Modelling the point as one value object moves
 * the geometry into the shared domain vocabulary — no I/O, no Android, no side effects —
 * so it is trivially testable and reusable by the other dies.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
) {
    /**
     * Great-circle distance in metres to [other] via the haversine formula. Symmetric
     * and zero for identical points. Preserves the legacy detector numerics exactly
     * (spherical earth, [EARTH_RADIUS_METERS]).
     */
    fun distanceTo(other: GeoPoint): Double {
        val dLat = Math.toRadians(other.latitude - latitude)
        val dLon = Math.toRadians(other.longitude - longitude)
        val a = sin(dLat / 2).let { it * it } +
                cos(Math.toRadians(latitude)) * cos(Math.toRadians(other.latitude)) *
                sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_METERS * asin(sqrt(a))
    }

    companion object {
        /** Mean earth radius, matching the legacy `DrivingEventsDetector.R_METERS`. */
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}
