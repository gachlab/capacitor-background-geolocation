// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import com.gachlab.geolocation.domain.GeoPoint
import com.gachlab.geolocation.domain.Heading
import com.gachlab.geolocation.domain.Journey
import com.gachlab.geolocation.domain.Position
import com.gachlab.geolocation.domain.Trip
import com.gachlab.geolocation.domain.TripConfig

/**
 * GPS-only driving events state machine.
 * Pure Kotlin, no Android imports — fully unit-testable.
 */
internal class DrivingEventsDetector(private val listener: Listener) {

    interface Listener {
        fun onMoving(loc: BGLocation)
        fun onStopped(loc: BGLocation)
        fun onTripStart(loc: BGLocation)
        fun onTripEnd(loc: BGLocation, journey: Journey)
        fun onSpeeding(loc: BGLocation, speedKmh: Double, limitKmh: Double)
        fun onProviderChange(provider: String)
        fun onHardBrake(loc: BGLocation, decelMps2: Double)
        fun onRapidAcceleration(loc: BGLocation, accelMps2: Double)
        fun onSharpTurn(loc: BGLocation, degPerSec: Double)
        fun onPossibleCrash(loc: BGLocation, velocityDropKmh: Double)
        fun onIdleStart(loc: BGLocation, startedAt: Long)
        fun onIdleEnd(loc: BGLocation, durationMs: Long, startedAt: Long)
        fun onPhoneUsageWhileDriving(loc: BGLocation)
    }

    private enum class MovingState { STATIONARY, MOVING, TRIP_ACTIVE }

    @Volatile private var cfg = TripConfig()

    private var movingState          = MovingState.STATIONARY
    private var activeTrip: Trip?    = null
    private var aboveTripSpeedSince  = 0L
    private var belowMovingSince     = 0L
    private var wasSpeeding          = false
    private var lastProvider: String? = null
    private var prevPoint: GeoPoint? = null
    private var prevSpeedMps         = 0.0
    private var prevSpeedAt          = 0L
    private var prevHeading: Heading? = null
    private var prevHeadingAt        = 0L
    private var lastHardBrakeAt      = 0L
    private var lastRapidAccelAt     = 0L
    private var lastSharpTurnAt      = 0L
    private var lastCrashAt          = 0L

    // Scoring / idle state
    private var scoreCalc:            ScoreCalculator? = null
    private var idleStartedAt:        Long = 0L
    private var idleThresholdFired:   Boolean = false
    private var postIdleMovingAt:     Long = 0L

    // Crash confirmation state
    private var pendingCrashLoc:          BGLocation? = null
    private var pendingCrashDropKmh:      Double      = 0.0
    private var pendingCrashDetectedAt:   Long        = 0L

    // Phone usage jitter state
    private var jitterWindowStart:    Long = 0L
    private var jitterCount:          Int  = 0
    private var lastPhoneUsageAt:     Long = 0L

    @Synchronized fun setConfig(c: TripConfig) { cfg = c }

    @Synchronized fun reset() {
        movingState = MovingState.STATIONARY
        activeTrip = null
        aboveTripSpeedSince = 0L; belowMovingSince = 0L; wasSpeeding = false
        lastProvider = null; prevPoint = null
        prevSpeedMps = 0.0; prevSpeedAt = 0L
        prevHeading = null; prevHeadingAt = 0L
        lastHardBrakeAt = 0L; lastRapidAccelAt = 0L; lastSharpTurnAt = 0L; lastCrashAt = 0L
        scoreCalc = null
        idleStartedAt = 0L; idleThresholdFired = false; postIdleMovingAt = 0L
        pendingCrashLoc = null; pendingCrashDropKmh = 0.0; pendingCrashDetectedAt = 0L
        jitterWindowStart = 0L; jitterCount = 0; lastPhoneUsageAt = 0L
    }

    /**
     * Records a phone-usage penalty originating from the EXTERNAL sensor-fusion
     * detector ([SensorFusionDetector]), which lives outside this GPS detector but
     * shares its trip-scoped [scoreCalc]. The GPS bearing-jitter path below is gated
     * on `!sensorFusion`, so the two are mutually exclusive and never double-count.
     * No-op outside an active trip (scoreCalc is null), mirroring the GPS path.
     */
    @Synchronized fun recordExternalPhoneUsage(loc: BGLocation) {
        if (!cfg.enabled) return
        scoreCalc?.recordPhoneUsage(loc, System.currentTimeMillis())
    }

    @Synchronized fun onLocation(loc: BGLocation) {
        if (!cfg.enabled) return
        val now = System.currentTimeMillis()
        val pos = Position(
            point      = GeoPoint(loc.latitude, loc.longitude),
            speedMps   = if (loc.hasSpeed) loc.speed.toDouble() else null,
            bearingDeg = if (loc.hasBearing) loc.bearing.toDouble() else null,
            provider   = loc.provider,
        )
        val speed: Double = pos.speedMpsOrZero
        val curHeading: Heading? = pos.bearingDeg?.let { Heading(it) }
        val priorHeading = prevHeading  // snapshot before this fix updates it below

        // Provider change
        val provider = pos.provider
        if (provider != null && provider != lastProvider) {
            lastProvider = provider
            listener.onProviderChange(provider)
        }

        // Accumulate trip distance
        if (movingState == MovingState.TRIP_ACTIVE)
            prevPoint?.let { activeTrip = activeTrip?.plusDistance(it.distanceTo(pos.point)) }
        prevPoint = pos.point

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
                        activeTrip = Trip.startedAt(now)
                        scoreCalc = ScoreCalculator(cfg.scoringWeights ?: ScoringWeights())
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
                    val trip = activeTrip ?: Trip.startedAt(now)
                    val score = scoreCalc?.compute(trip.id, trip.startedAtMs, now, trip.distanceMeters)
                        ?: ScoreCalculator().compute(trip.id, trip.startedAtMs, now, trip.distanceMeters)
                    scoreCalc = null
                    activeTrip = null
                    listener.onTripEnd(loc, Journey.completed(trip, now, score))
                }
            }
        }

        // ── Idle detection (TRIP_ACTIVE only) ────────────────────────────────
        if (movingState == MovingState.TRIP_ACTIVE) {
            if (!nowMoving) {
                if (idleStartedAt == 0L) idleStartedAt = now
                if (!idleThresholdFired && (now - idleStartedAt) >= cfg.idleThresholdMs) {
                    idleThresholdFired = true
                    postIdleMovingAt = 0L
                    scoreCalc?.recordIdleStart()
                    listener.onIdleStart(loc, idleStartedAt)
                }
            } else {
                if (idleThresholdFired) {
                    if (postIdleMovingAt == 0L) postIdleMovingAt = now
                    if ((now - postIdleMovingAt) >= cfg.idleEndThresholdMs) {
                        val durationMs = now - idleStartedAt
                        scoreCalc?.recordIdleEnd(durationMs)
                        listener.onIdleEnd(loc, durationMs, idleStartedAt)
                        idleStartedAt = 0L; idleThresholdFired = false; postIdleMovingAt = 0L
                    }
                } else {
                    idleStartedAt = 0L  // brief stop, not idle
                }
            }
        } else {
            idleStartedAt = 0L; idleThresholdFired = false; postIdleMovingAt = 0L
        }

        // ── Speeding ──────────────────────────────────────────────────────────
        if (cfg.speedLimitKmh > 0) {
            val kmh = speed * 3.6
            if (kmh > cfg.speedLimitKmh) {
                if (!wasSpeeding) {
                    wasSpeeding = true
                    scoreCalc?.recordSpeeding(loc, now)
                    listener.onSpeeding(loc, kmh, cfg.speedLimitKmh)
                }
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
                    lastHardBrakeAt = now
                    scoreCalc?.recordHardBrake(loc, now)
                    listener.onHardBrake(loc, accel)
                }
                if (cfg.rapidAccelMps2 > 0 && accel >= cfg.rapidAccelMps2
                        && now - lastRapidAccelAt >= COOLDOWN_MS) {
                    lastRapidAccelAt = now
                    scoreCalc?.recordRapidAccel(loc, now)
                    listener.onRapidAcceleration(loc, accel)
                }
                if (cfg.crashImpactKmh > 0 && dtMs <= cfg.crashWindowMs) {
                    val dropKmh = (prevSpeedMps - speed) * 3.6
                    if (dropKmh >= cfg.crashImpactKmh && speed < 1.5
                            && prevSpeedMps * 3.6 >= cfg.crashImpactKmh
                            && now - lastCrashAt >= COOLDOWN_MS
                            && pendingCrashLoc == null) {
                        if (cfg.crashConfirmWindowMs <= 0L) {
                            lastCrashAt = now
                            listener.onPossibleCrash(loc, dropKmh)
                        } else {
                            pendingCrashLoc = loc
                            pendingCrashDropKmh = dropKmh
                            pendingCrashDetectedAt = now
                        }
                    }
                }
            }
        }

        // ── Sharp turn (bearing change rate) ──────────────────────────────────
        if (cfg.sharpTurnDegPerSec > 0 && curHeading != null && speed >= 5.0 && priorHeading != null) {
            val dtMs = now - prevHeadingAt
            if (dtMs in 1L..5_000L) {
                val diff = curHeading.deltaTo(priorHeading)
                val rate = diff * 1000.0 / dtMs
                if (rate >= cfg.sharpTurnDegPerSec && now - lastSharpTurnAt >= COOLDOWN_MS) {
                    lastSharpTurnAt = now
                    scoreCalc?.recordSharpTurn(loc, now)
                    listener.onSharpTurn(loc, rate)
                }
            }
        }
        // ── Phone usage (GPS bearing jitter, disabled when sensorFusion=true) ──────
        if (!cfg.sensorFusion && cfg.phoneUsageWindowMs > 0
                && movingState == MovingState.TRIP_ACTIVE && curHeading != null && priorHeading != null
                && speed >= 1.39 && speed <= 22.2) {
            val bearingDtMs = now - prevHeadingAt
            if (bearingDtMs in 1L..5_000L) {
                val bearingDiff = curHeading.deltaTo(priorHeading)
                if (bearingDiff in 5.0..25.0) {
                    if (jitterWindowStart == 0L) jitterWindowStart = now
                    jitterCount++
                }
            }
            if (jitterWindowStart > 0L && (now - jitterWindowStart) >= cfg.phoneUsageWindowMs) {
                if (jitterCount >= 3 && now - lastPhoneUsageAt >= cfg.phoneUsageCooldownMs) {
                    lastPhoneUsageAt = now
                    scoreCalc?.recordPhoneUsage(loc, now)
                    listener.onPhoneUsageWhileDriving(loc)
                }
                jitterWindowStart = 0L; jitterCount = 0
            }
        }

        if (curHeading != null) {
            prevHeading = curHeading; prevHeadingAt = now
        }

        // ── Pending crash confirmation ─────────────────────────────────────────
        pendingCrashLoc?.let { pendLoc ->
            when {
                speed > 2.0 -> {
                    pendingCrashLoc = null; pendingCrashDropKmh = 0.0; pendingCrashDetectedAt = 0L
                }
                now - pendingCrashDetectedAt >= cfg.crashConfirmWindowMs -> {
                    lastCrashAt = now
                    pendingCrashLoc = null
                    listener.onPossibleCrash(pendLoc, pendingCrashDropKmh)
                    pendingCrashDropKmh = 0.0; pendingCrashDetectedAt = 0L
                }
            }
        }

        prevSpeedMps = speed; prevSpeedAt = now
    }

    companion object {
        private const val COOLDOWN_MS = 4_000L
    }
}
