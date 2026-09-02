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
    data class TripEnd(val loc: BGLocation, val journey: com.gachlab.geolocation.domain.Journey) : ServiceEvent()
    data class IdleStart(val loc: BGLocation, val startedAt: Long) : ServiceEvent()
    data class IdleEnd(val loc: BGLocation, val durationMs: Long, val startedAt: Long) : ServiceEvent()

    // ── Driving events ────────────────────────────────────────────────────────
    data class Speeding(val loc: BGLocation, val speedKmh: Double, val limitKmh: Double) : ServiceEvent()
    // value carries the measured magnitude (decel m/s², accel m/s², deg/s) per the TS contract.
    data class HardBrake(val loc: BGLocation, val value: Double) : ServiceEvent()
    data class RapidAcceleration(val loc: BGLocation, val value: Double) : ServiceEvent()
    data class SharpTurn(val loc: BGLocation, val value: Double) : ServiceEvent()
    // possibleCrash also carries its detection source ('gps' | 'sensor').
    data class PossibleCrash(val loc: BGLocation, val value: Double, val source: String) : ServiceEvent()
    data class PhoneUsageWhileDriving(val loc: BGLocation) : ServiceEvent()

    // ── Geofence events ───────────────────────────────────────────────────────
    data class GeofenceEnter(val geofenceId: String, val loc: BGLocation?) : ServiceEvent()
    data class GeofenceExit(val geofenceId: String, val loc: BGLocation?)  : ServiceEvent()
    data class GeofenceDwell(val geofenceId: String, val loc: BGLocation?) : ServiceEvent()
    /** Geofence registration/monitoring failure. [geofenceId] is null for bulk failures. */
    data class GeofenceError(val geofenceId: String?, val message: String) : ServiceEvent()

    // ── System events ─────────────────────────────────────────────────────────
    data class Heartbeat(val loc: BGLocation?) : ServiceEvent()
    data class Error(val message: String, val code: String = "unavailable") : ServiceEvent()
    data class ProviderChange(val provider: String) : ServiceEvent()
    data class Activity(val data: JSONObject?) : ServiceEvent()
    // `loc` carries the position the alert happened at. It used to be absent —
    // only a `locationId` that the single caller hard-coded to null — so an SOS
    // reached JavaScript on Android with no positional data at all, while iOS
    // and web both sent one. An alert nobody can locate is not an alert.
    data class Sos(val locationId: Long?, val payload: JSONObject? = null, val loc: BGLocation? = null) : ServiceEvent()
    object ServiceStarted : ServiceEvent()
    object ServiceStopped : ServiceEvent()
    data class ServiceRestarted(val reason: String) : ServiceEvent()
    object AbortRequested : ServiceEvent()
    object HttpAuthorization : ServiceEvent()

    // ── Priority sync events ──────────────────────────────────────────────────
    data class PrioritySyncSuccess(val eventType: String, val attemptNumber: Int) : ServiceEvent()
    data class PrioritySyncFailed(val eventType: String, val httpStatus: Int, val attempts: Int) : ServiceEvent()

    // ── Batch sync events (BackgroundSync → plugin) ───────────────────────────
    // Mirror the iOS BGBackgroundSync* notifications. SyncProgress has no producer
    // on either platform yet (iOS observes BGBackgroundSyncDidProgress but never
    // posts it); the type exists so the plumbing is symmetric across platforms.
    object SyncStart : ServiceEvent()
    data class SyncProgress(val progress: Int) : ServiceEvent()
    data class SyncSuccess(val sent: Int) : ServiceEvent()
    data class SyncError(val httpStatus: Int, val message: String) : ServiceEvent()

    companion object {
        // Internal, persisted spelling. These strings are written to
        // SharedPreferences by `persistKillReason`, so they survive upgrades and
        // must not change: renaming them would make every reason recorded by an
        // older build unreadable.
        const val REASON_WATCHDOG    = "watchdog"
        const val REASON_SYSTEM_KILL = "system_kill"
        const val REASON_BOOT        = "boot"
        const val REASON_APP_REMOVED = "app_removed"

        /**
         * The location permission was gone when the service tried to go
         * foreground (#59).
         *
         * It is the reason a shift goes quiet with nobody able to say why: the
         * platform revokes, kills, and restarts, and the restart cannot legally
         * start a `location`-typed foreground service. Recording it is what lets
         * whoever opens the app next report "permission revoked" instead of the
         * silence that looks identical to a tunnel.
         */
        const val REASON_PERMISSION_LOST = "permission_lost"

        /**
         * The server said 404 for a solid minute, so the shift it was posting
         * for does not exist any more (#63).
         *
         * Deliberately distinct from every other reason here: the others mean
         * "tracking should be running and something interrupted it". This one
         * means tracking should NOT be running — there is nothing left to track
         * for. It is the only reason that stands the reviver down rather than
         * leaving it armed, and recording it is what lets the next app open say
         * "that shift was deleted" instead of showing a driver a shift the
         * server has forgotten.
         */
        const val REASON_SHIFT_GONE = "shift_gone"

        /**
         * The spelling that leaves the plugin, matching `ServiceRestartedPayload.reason`.
         *
         * It lives here, as one function, because there are TWO exits to
         * JavaScript and they disagreed. The `serviceRestarted` event mapped
         * `system_kill` to `systemKill` inline; `getBackgroundKillReason`
         * returned the raw preference. Same fact, two spellings, and only one
         * of them was the documented vocabulary — so a consumer validating
         * against the published union silently dropped the startup path.
         *
         * `watchdog` and `boot` hid the bug: their internal and public spellings
         * are identical, so three of the four values looked fine and the one
         * that broke was `systemKill`, which is the reason anyone asks in the
         * first place.
         */
        @JvmStatic
        fun publicReason(reason: String): String = when (reason) {
            REASON_SYSTEM_KILL -> "systemKill"
            REASON_APP_REMOVED -> "appRemoved"
            REASON_PERMISSION_LOST -> "permissionLost"
            REASON_SHIFT_GONE -> "shiftGone"
            // watchdog / boot are already spelled the same on both sides. An
            // unknown value passes through rather than being swallowed: a reason
            // we did not anticipate is still more useful than none.
            else -> reason
        }
    }
}
