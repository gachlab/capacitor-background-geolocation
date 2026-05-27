// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import org.json.JSONObject

/**
 * Typed events emitted by LocationService and consumed by BGFacade → plugin.
 *
 * Replaces the old int-keyed Bundle-over-LocalBroadcastManager IPC.
 * All location payloads carry the raw BGLocation so the bridge layer
 * controls serialization to JSON/JSObject.
 */
sealed class ServiceEvent {

    // ── Location events ───────────────────────────────────────────────────────
    data class Location(val loc: BGLocation) : ServiceEvent()
    data class Stationary(val loc: BGLocation, val radius: Float) : ServiceEvent()

    // ── Moving / stopped state ────────────────────────────────────────────────
    data class Moving(val loc: BGLocation) : ServiceEvent()
    data class Stopped(val loc: BGLocation) : ServiceEvent()

    // ── Trip lifecycle ────────────────────────────────────────────────────────
    data class TripStart(val loc: BGLocation) : ServiceEvent()
    data class TripEnd(val loc: BGLocation, val distanceMeters: Double, val durationMs: Long, val score: TripScore?) : ServiceEvent()
    data class IdleStart(val loc: BGLocation, val startedAt: Long) : ServiceEvent()
    data class IdleEnd(val loc: BGLocation, val durationMs: Long, val startedAt: Long) : ServiceEvent()

    // ── Driving events ────────────────────────────────────────────────────────
    data class Speeding(val loc: BGLocation, val speedKmh: Double, val limitKmh: Double) : ServiceEvent()
    data class HardBrake(val loc: BGLocation) : ServiceEvent()
    data class RapidAcceleration(val loc: BGLocation) : ServiceEvent()
    data class SharpTurn(val loc: BGLocation) : ServiceEvent()
    data class PossibleCrash(val loc: BGLocation) : ServiceEvent()

    // ── Geofence events ───────────────────────────────────────────────────────
    data class GeofenceEnter(val geofenceId: String, val loc: BGLocation?) : ServiceEvent()
    data class GeofenceExit(val geofenceId: String, val loc: BGLocation?)  : ServiceEvent()
    data class GeofenceDwell(val geofenceId: String, val loc: BGLocation?) : ServiceEvent()

    // ── System events ─────────────────────────────────────────────────────────
    data class Heartbeat(val loc: BGLocation?) : ServiceEvent()
    data class Error(val message: String) : ServiceEvent()
    data class ProviderChange(val provider: String) : ServiceEvent()
    data class Activity(val data: JSONObject?) : ServiceEvent()
    data class Sos(val locationId: Long?) : ServiceEvent()
    object ServiceStarted : ServiceEvent()
    object ServiceStopped : ServiceEvent()
    data class ServiceRestarted(val reason: String) : ServiceEvent()
    object AbortRequested : ServiceEvent()
    object HttpAuthorization : ServiceEvent()

    // ── Priority sync events ──────────────────────────────────────────────────
    data class PrioritySyncSuccess(val eventType: String, val attemptNumber: Int) : ServiceEvent()
    data class PrioritySyncFailed(val eventType: String, val httpStatus: Int, val attempts: Int) : ServiceEvent()

    companion object {
        const val REASON_WATCHDOG    = "watchdog"
        const val REASON_SYSTEM_KILL = "system_kill"
        const val REASON_BOOT        = "boot"
        const val REASON_APP_REMOVED = "app_removed"
    }
}
