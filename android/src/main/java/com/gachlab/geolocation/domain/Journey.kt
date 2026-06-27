// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

import com.gachlab.geolocation.TripScore

/**
 * A completed driving trip — a pure, immutable value object for the `domain/` layer
 * (ARCHITECTURE.md domain set). Where [Trip] is the journey in progress, Journey is its
 * finished aggregate: the distance travelled, the span, and the computed [score].
 *
 * It was previously three loose arguments threaded through `onTripEnd`
 * (distanceMeters / durationMs / score); bundling them gives the hub and the bridge a
 * single value to carry from the detector out to the `tripEnd` event.
 */
data class Journey(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val distanceMeters: Double,
    val score: TripScore,
) {
    /** Trip span in milliseconds. */
    val durationMs: Long get() = endedAtMs - startedAtMs

    companion object {
        /** The Journey that [trip] becomes when it ends at [endedAtMs] with [score]. */
        fun completed(trip: Trip, endedAtMs: Long, score: TripScore): Journey =
            Journey(trip.id, trip.startedAtMs, endedAtMs, trip.distanceMeters, score)
    }
}
