// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GPS-only driving events state machine.
 * Pure Kotlin, no Android imports — fully unit-testable.
 */
internal class DrivingEventsDetector(private val listener: Listener) {

    interface Listener {
        fun onMoving(loc: BGLocation)
        fun onStopped(loc: BGLocation)
        fun onTripStart(loc: BGLocation)
        fun onTripEnd(loc: BGLocation, distanceMeters: Double, durationMs: Long)
        fun onSpeeding(loc: BGLocation, speedKmh: Double, limitKmh: Double)
        fun onProviderChange(provider: String)
        fun onHardBrake(loc: BGLocation, decelMps2: Double)
        fun onRapidAcceleration(loc: BGLocation, accelMps2: Double)
        fun onSharpTurn(loc: BGLocation, degPerSec: Double)
        fun onPossibleCrash(loc: BGLocation, velocityDropKmh: Double)
    }

    data class Config(
        val enabled:             Boolean = false,
        val speedLimitKmh:       Double  = 0.0,
        val minMovingSpeedMps:   Double  = 1.0,
        val stoppedDurationMs:   Long    = 60_000L,
        val minTripSpeedMps:     Double  = 3.0,
        val minTripDurationMs:   Long    = 30_000L,
        val hardBrakeMps2:       Double  = 3.5,
        val rapidAccelMps2:      Double  = 3.5,
        val sharpTurnDegPerSec:  Double  = 30.0,
        val crashImpactKmh:      Double  = 25.0,
        val crashWindowMs:       Long    = 2_000L
    )

    private enum class MovingState { STATIONARY, MOVING, TRIP_ACTIVE }

    @Volatile private var cfg = Config()

    private var movingState          = MovingState.STATIONARY
    private var tripStartedAt        = 0L
    private var tripDistanceMeters   = 0.0
    private var aboveTripSpeedSince  = 0L
    private var belowMovingSince     = 0L
    private var wasSpeeding          = false
    private var lastProvider: String? = null
    private var prevLat              = 0.0
    private var prevLon              = 0.0
    private var hasPrev              = false
    private var prevSpeedMps         = 0.0
    private var prevSpeedAt          = 0L
    private var prevBearingDeg       = 0.0
    private var prevBearingAt        = 0L
    private var hasPrevBearing       = false
    private var lastHardBrakeAt      = 0L
    private var lastRapidAccelAt     = 0L
    private var lastSharpTurnAt      = 0L
    private var lastCrashAt          = 0L

    @Synchronized fun setConfig(c: Config) { cfg = c }

    @Synchronized fun reset() {
        movingState = MovingState.STATIONARY
        tripStartedAt = 0L; tripDistanceMeters = 0.0
        aboveTripSpeedSince = 0L; belowMovingSince = 0L; wasSpeeding = false
        lastProvider = null; hasPrev = false
        prevSpeedMps = 0.0; prevSpeedAt = 0L
        prevBearingDeg = 0.0; prevBearingAt = 0L; hasPrevBearing = false
        lastHardBrakeAt = 0L; lastRapidAccelAt = 0L; lastSharpTurnAt = 0L; lastCrashAt = 0L
    }

    @Synchronized fun onLocation(loc: BGLocation) {
        if (!cfg.enabled) return
        val now    = System.currentTimeMillis()
        val speed: Double = if (loc.hasSpeed) loc.speed.toDouble() else 0.0
        val curLat = loc.latitude
        val curLon = loc.longitude

        // Provider change
        val provider = loc.provider
        if (provider != null && provider != lastProvider) {
            lastProvider = provider
            listener.onProviderChange(provider)
        }

        // Accumulate trip distance
        if (hasPrev && movingState == MovingState.TRIP_ACTIVE)
            tripDistanceMeters += haversineMeters(prevLat, prevLon, curLat, curLon)
        prevLat = curLat; prevLon = curLon; hasPrev = true

        // ── Moving / stopped state machine ────────────────────────────────────
        val nowMoving = speed >= cfg.minMovingSpeedMps
        if (nowMoving) {
            belowMovingSince = 0L
            if (movingState == MovingState.STATIONARY) {
                movingState = MovingState.MOVING
                listener.onMoving(loc)
            }
            if (movingState != MovingState.TRIP_ACTIVE) {
                if (speed >= cfg.minTripSpeedMps) {
                    if (aboveTripSpeedSince == 0L) aboveTripSpeedSince = now
                    if (now - aboveTripSpeedSince >= cfg.minTripDurationMs) {
                        movingState = MovingState.TRIP_ACTIVE
                        tripStartedAt = now; tripDistanceMeters = 0.0
                        listener.onTripStart(loc)
                    }
                } else aboveTripSpeedSince = 0L
            }
        } else {
            aboveTripSpeedSince = 0L
            if (belowMovingSince == 0L) belowMovingSince = now
            if (movingState != MovingState.STATIONARY && (now - belowMovingSince) >= cfg.stoppedDurationMs) {
                val wasTripActive = movingState == MovingState.TRIP_ACTIVE
                movingState = MovingState.STATIONARY
                listener.onStopped(loc)
                if (wasTripActive) {
                    listener.onTripEnd(loc, tripDistanceMeters, now - tripStartedAt)
                }
            }
        }

        // ── Speeding ──────────────────────────────────────────────────────────
        if (cfg.speedLimitKmh > 0) {
            val kmh = speed * 3.6
            if (kmh > cfg.speedLimitKmh) {
                if (!wasSpeeding) { wasSpeeding = true; listener.onSpeeding(loc, kmh, cfg.speedLimitKmh) }
            } else wasSpeeding = false
        }

        // ── GPS-derived driving events (trip active only) ─────────────────────
        if (movingState == MovingState.TRIP_ACTIVE && prevSpeedAt > 0) {
            val dtMs = now - prevSpeedAt
            if (dtMs in 1L..5_000L) {
                val dt    = dtMs / 1000.0
                val dv    = speed - prevSpeedMps
                val accel = dv / dt
                if (cfg.hardBrakeMps2 > 0 && accel <= -cfg.hardBrakeMps2
                        && now - lastHardBrakeAt >= COOLDOWN_MS) {
                    lastHardBrakeAt = now; listener.onHardBrake(loc, accel)
                }
                if (cfg.rapidAccelMps2 > 0 && accel >= cfg.rapidAccelMps2
                        && now - lastRapidAccelAt >= COOLDOWN_MS) {
                    lastRapidAccelAt = now; listener.onRapidAcceleration(loc, accel)
                }
                if (cfg.crashImpactKmh > 0 && dtMs <= cfg.crashWindowMs) {
                    val dropKmh = (prevSpeedMps - speed) * 3.6
                    if (dropKmh >= cfg.crashImpactKmh && speed < 1.5
                            && prevSpeedMps * 3.6 >= cfg.crashImpactKmh
                            && now - lastCrashAt >= COOLDOWN_MS) {
                        lastCrashAt = now; listener.onPossibleCrash(loc, dropKmh)
                    }
                }
            }
        }

        // ── Sharp turn (bearing change rate) ──────────────────────────────────
        if (cfg.sharpTurnDegPerSec > 0 && loc.hasBearing && speed >= 5.0 && hasPrevBearing) {
            val dtMs = now - prevBearingAt
            if (dtMs in 1L..5_000L) {
                var diff = abs(loc.bearing.toDouble() - prevBearingDeg)
                if (diff > 180) diff = 360 - diff
                val rate = diff * 1000.0 / dtMs
                if (rate >= cfg.sharpTurnDegPerSec && now - lastSharpTurnAt >= COOLDOWN_MS) {
                    lastSharpTurnAt = now; listener.onSharpTurn(loc, rate)
                }
            }
        }
        if (loc.hasBearing) {
            prevBearingDeg = loc.bearing.toDouble(); prevBearingAt = now; hasPrevBearing = true
        }

        prevSpeedMps = speed; prevSpeedAt = now
    }

    companion object {
        private const val COOLDOWN_MS = 4_000L
        private const val R_METERS    = 6_371_000.0

        private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).let { it * it } +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).let { it * it }
            return 2 * R_METERS * asin(sqrt(a))
        }
    }
}
