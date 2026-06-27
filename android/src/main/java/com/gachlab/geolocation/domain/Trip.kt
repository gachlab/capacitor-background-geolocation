// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

/**
 * An in-progress driving trip — a pure, immutable value object for the journey the
 * engine is currently recording. First piece of the `domain/` layer (Roadmap Fase 1):
 * the trip was previously three loose mutable fields scattered through
 * `DrivingEventsDetector` (tripStartedAt / tripDistanceMeters / tripId); modelling it
 * as one value object gives the engine a shared vocabulary with no I/O or side effects,
 * so it is trivially testable and reusable by the other dies.
 */
data class Trip(
    val id: String,
    val startedAtMs: Long,
    val distanceMeters: Double = 0.0,
) {
    /** Trip extended by [meters] of travel (returns a new Trip; never mutates). */
    fun plusDistance(meters: Double): Trip = copy(distanceMeters = distanceMeters + meters)

    /** Elapsed duration at [nowMs]. */
    fun durationMs(nowMs: Long): Long = nowMs - startedAtMs

    companion object {
        /**
         * A trip beginning at [nowMs]. The id is the start timestamp as a string,
         * preserving the legacy `tripId = now.toString()` behaviour.
         */
        fun startedAt(nowMs: Long): Trip = Trip(id = nowMs.toString(), startedAtMs = nowMs)
    }
}
