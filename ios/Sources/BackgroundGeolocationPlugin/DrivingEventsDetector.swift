// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

// Minimal location value — keeps the detector free of CoreLocation imports.
struct DLLocation {
    let latitude: Double
    let longitude: Double
    let speed: Double    // m/s; negative = unavailable
    let bearing: Double? // nil if unavailable
    let provider: String?
}

protocol DrivingEventsDetectorDelegate: AnyObject {
    func detectorOnMoving(_ location: DLLocation)
    func detectorOnStopped(_ location: DLLocation)
    func detectorOnTripStart(_ location: DLLocation)
    func detectorOnTripEnd(_ location: DLLocation, distanceMeters: Double, durationMs: Int64, score: TripScore)
    func detectorOnSpeeding(_ location: DLLocation, speedKmh: Double, limitKmh: Double)
    func detectorOnProviderChange(provider: String)
    func detectorOnHardBrake(_ location: DLLocation, decelMps2: Double)
    func detectorOnRapidAcceleration(_ location: DLLocation, accelMps2: Double)
    func detectorOnSharpTurn(_ location: DLLocation, degPerSec: Double)
    func detectorOnPossibleCrash(_ location: DLLocation, dropKmh: Double)
    func detectorOnIdleStart(_ location: DLLocation, startedAt: Double)
    func detectorOnIdleEnd(_ location: DLLocation, durationMs: Int64, startedAt: Double)
    func detectorOnPhoneUsageWhileDriving(_ location: DLLocation)
}

// Pure-Swift driving-events state machine — exact port of the Java DrivingEventsDetector.
// No iOS framework dependencies; fully unit-testable without a simulator.
final class DrivingEventsDetector {

    // ── Config ────────────────────────────────────────────────────────────────

    var enabled             = false
    var speedLimitKmh       = 0.0
    var minMovingSpeedMps   = 1.0
    var stoppedDurationSec  = 60.0
    var minTripSpeedMps     = 3.0
    var minTripDurationSec  = 30.0
    var hardBrakeMps2       = 3.5
    var rapidAccelMps2      = 3.5
    var sharpTurnDegPerSec  = 30.0
    var crashImpactKmh        = 25.0
    var crashWindowSec        = 2.0
    var crashConfirmWindowSec = 0.0
    var sensorFusion          = false
    var phoneUsageWindowSec   = 4.0
    var phoneUsageCooldownSec = 60.0
    var idleThresholdSec      = 300.0
    var idleEndThresholdSec   = 30.0
    var scoringWeights: ScoringWeights?

    weak var delegate: DrivingEventsDetectorDelegate?

    // ── State (read-only for external sync) ───────────────────────────────────

    private(set) var tripActive = false

    // ── Private state ─────────────────────────────────────────────────────────

    private var isMoving            = false
    private var tripStartedAt       = 0.0
    private var tripDistanceMeters  = 0.0
    private var hasPrev             = false
    private var prevLat             = 0.0, prevLon = 0.0
    private var aboveTripSpeedSince = 0.0
    private var belowMovingSince    = 0.0
    private var wasSpeeding         = false
    private var lastProvider: String?

    private var prevSpeedMps    = 0.0
    private var prevSpeedAt     = 0.0
    private var prevBearing     = 0.0
    private var hasPrevBearing  = false
    private var prevBearingAt   = 0.0

    private var lastHardBrakeAt  = 0.0
    private var lastRapidAccelAt = 0.0
    private var lastSharpTurnAt  = 0.0
    private var lastCrashAt      = 0.0

    // Scoring / idle state
    private var scoreCalc:           ScoreCalculator?
    private var tripId               = ""
    private var idleStartedAt        = 0.0
    private var idleThresholdFired   = false
    private var postIdleMovingAt     = 0.0

    // Crash confirmation state
    private var pendingCrashLoc:          DLLocation?
    private var pendingCrashDropKmh       = 0.0
    private var pendingCrashDetectedAt    = 0.0

    // Phone usage jitter state
    private var jitterWindowStart  = 0.0
    private var jitterCount        = 0
    private var lastPhoneUsageAt   = 0.0

    private static let cooldown = 4.0

    // ── Public API ────────────────────────────────────────────────────────────

    func reset() {
        isMoving            = false
        tripActive          = false
        tripStartedAt       = 0
        tripDistanceMeters  = 0
        hasPrev             = false
        aboveTripSpeedSince = 0
        belowMovingSince    = 0
        wasSpeeding         = false
        lastProvider        = nil
        prevSpeedMps        = 0
        prevSpeedAt         = 0
        prevBearing         = 0
        hasPrevBearing      = false
        prevBearingAt       = 0
        lastHardBrakeAt     = 0
        lastRapidAccelAt    = 0
        lastSharpTurnAt     = 0
        lastCrashAt         = 0
        scoreCalc = nil; tripId = ""
        idleStartedAt = 0; idleThresholdFired = false; postIdleMovingAt = 0
        pendingCrashLoc = nil; pendingCrashDropKmh = 0; pendingCrashDetectedAt = 0
        jitterWindowStart = 0; jitterCount = 0; lastPhoneUsageAt = 0
    }

    /// Records a phone-usage penalty originating from the EXTERNAL sensor-fusion
    /// detector, which lives outside this GPS detector but shares its trip-scoped
    /// `scoreCalc`. The GPS bearing-jitter path is gated on `!sensorFusion`, so the two
    /// are mutually exclusive and never double-count. No-op outside an active trip.
    func recordExternalPhoneUsage(_ location: DLLocation?) {
        guard enabled, tripActive else { return }
        let loc = location ?? DLLocation(latitude: 0, longitude: 0, speed: -1, bearing: nil, provider: nil)
        scoreCalc?.recordPhoneUsage(loc, ts: Date().timeIntervalSince1970)
    }

    func feed(_ location: DLLocation) {
        guard enabled else { return }
        let now   = Date().timeIntervalSince1970
        let speed = max(0, location.speed < 0 ? 0 : location.speed)

        // Provider change
        if let p = location.provider, p != lastProvider {
            lastProvider = p
            delegate?.detectorOnProviderChange(provider: p)
        }

        // Distance accumulator
        let curLat = location.latitude, curLon = location.longitude
        if hasPrev && tripActive {
            tripDistanceMeters += haversineMeters(lat1: prevLat, lon1: prevLon, lat2: curLat, lon2: curLon)
        }
        prevLat = curLat; prevLon = curLon; hasPrev = true

        // Moving / stopped state machine
        let nowMoving = speed >= minMovingSpeedMps
        if nowMoving {
            belowMovingSince = 0
            if !isMoving { isMoving = true; delegate?.detectorOnMoving(location) }

            if !tripActive {
                if speed >= minTripSpeedMps {
                    if aboveTripSpeedSince == 0 { aboveTripSpeedSince = now }
                    if now - aboveTripSpeedSince >= minTripDurationSec {
                        tripActive         = true
                        tripStartedAt      = now
                        tripDistanceMeters = 0
                        tripId    = String(Int64(now * 1000))
                        scoreCalc = ScoreCalculator(weights: scoringWeights ?? ScoringWeights())
                        delegate?.detectorOnTripStart(location)
                    }
                } else {
                    aboveTripSpeedSince = 0
                }
            }
        } else {
            aboveTripSpeedSince = 0
            if belowMovingSince == 0 { belowMovingSince = now }
            if isMoving && (now - belowMovingSince) >= stoppedDurationSec {
                isMoving = false
                delegate?.detectorOnStopped(location)
                if tripActive {
                    let durMs = Int64((now - tripStartedAt) * 1000)
                    let dist  = tripDistanceMeters
                    let score = scoreCalc?.compute(tripId: tripId, startedAt: tripStartedAt,
                                                   endedAt: now, distanceMeters: dist)
                               ?? ScoreCalculator().compute(tripId: tripId, startedAt: tripStartedAt,
                                                            endedAt: now, distanceMeters: dist)
                    tripActive = false
                    scoreCalc  = nil
                    delegate?.detectorOnTripEnd(location, distanceMeters: dist, durationMs: durMs, score: score)
                }
            }
        }

        // Idle detection (trip active only)
        if tripActive {
            if !nowMoving {
                if idleStartedAt == 0 { idleStartedAt = now }
                if !idleThresholdFired && (now - idleStartedAt) >= idleThresholdSec {
                    idleThresholdFired = true
                    postIdleMovingAt = 0
                    scoreCalc?.recordIdleStart()
                    delegate?.detectorOnIdleStart(location, startedAt: idleStartedAt)
                }
            } else {
                if idleThresholdFired {
                    if postIdleMovingAt == 0 { postIdleMovingAt = now }
                    if (now - postIdleMovingAt) >= idleEndThresholdSec {
                        let durationMs = Int64((now - idleStartedAt) * 1000)
                        scoreCalc?.recordIdleEnd(durationMs)
                        delegate?.detectorOnIdleEnd(location, durationMs: durationMs, startedAt: idleStartedAt)
                        idleStartedAt = 0; idleThresholdFired = false; postIdleMovingAt = 0
                    }
                } else {
                    idleStartedAt = 0  // brief stop, not idle
                }
            }
        } else {
            idleStartedAt = 0; idleThresholdFired = false; postIdleMovingAt = 0
        }

        // Speeding
        if speedLimitKmh > 0 {
            let kmh = speed * 3.6
            if kmh > speedLimitKmh {
                if !wasSpeeding {
                    wasSpeeding = true
                    scoreCalc?.recordSpeeding(location, ts: now)
                    delegate?.detectorOnSpeeding(location, speedKmh: kmh, limitKmh: speedLimitKmh)
                }
            } else {
                wasSpeeding = false
            }
        }

        // GPS-derived events (trip must be active)
        if tripActive && prevSpeedAt > 0 {
            let dt = now - prevSpeedAt
            if dt > 0 && dt <= 5.0 {
                let accel = (speed - prevSpeedMps) / dt

                if hardBrakeMps2 > 0 && accel <= -hardBrakeMps2 && (now - lastHardBrakeAt) >= Self.cooldown {
                    lastHardBrakeAt = now
                    scoreCalc?.recordHardBrake(location, ts: now)
                    delegate?.detectorOnHardBrake(location, decelMps2: accel)
                }
                if rapidAccelMps2 > 0 && accel >= rapidAccelMps2 && (now - lastRapidAccelAt) >= Self.cooldown {
                    lastRapidAccelAt = now
                    scoreCalc?.recordRapidAccel(location, ts: now)
                    delegate?.detectorOnRapidAcceleration(location, accelMps2: accel)
                }
                if crashImpactKmh > 0 && dt <= crashWindowSec {
                    let dropKmh = (prevSpeedMps - speed) * 3.6
                    if dropKmh >= crashImpactKmh && speed < 1.5
                        && prevSpeedMps * 3.6 >= crashImpactKmh
                        && (now - lastCrashAt) >= Self.cooldown
                        && pendingCrashLoc == nil {
                        if crashConfirmWindowSec <= 0 {
                            lastCrashAt = now
                            delegate?.detectorOnPossibleCrash(location, dropKmh: dropKmh)
                        } else {
                            pendingCrashLoc = location
                            pendingCrashDropKmh = dropKmh
                            pendingCrashDetectedAt = now
                        }
                    }
                }
            }
        }

        // Sharp turn + phone usage (both computed before updating prevBearing)
        if let bearing = location.bearing {
            if hasPrevBearing {
                var diff = abs(bearing - prevBearing)
                if diff > 180 { diff = 360 - diff }
                let bearingDt = now - prevBearingAt

                if sharpTurnDegPerSec > 0 && speed >= 5.0 && bearingDt > 0 && bearingDt <= 5.0 {
                    let rate = diff / bearingDt
                    if rate >= sharpTurnDegPerSec && (now - lastSharpTurnAt) >= Self.cooldown {
                        lastSharpTurnAt = now
                        scoreCalc?.recordSharpTurn(location, ts: now)
                        delegate?.detectorOnSharpTurn(location, degPerSec: rate)
                    }
                }

                // ── Phone usage (GPS bearing jitter) ──────────────────────────
                if !sensorFusion && phoneUsageWindowSec > 0 && tripActive
                        && speed >= 1.39 && speed <= 22.2
                        && bearingDt > 0 && bearingDt <= 5.0 && diff >= 5.0 && diff <= 25.0 {
                    if jitterWindowStart == 0 { jitterWindowStart = now }
                    jitterCount += 1
                }
                if jitterWindowStart > 0 && (now - jitterWindowStart) >= phoneUsageWindowSec {
                    if jitterCount >= 3 && (now - lastPhoneUsageAt) >= phoneUsageCooldownSec {
                        lastPhoneUsageAt = now
                        scoreCalc?.recordPhoneUsage(location, ts: now)
                        delegate?.detectorOnPhoneUsageWhileDriving(location)
                    }
                    jitterWindowStart = 0; jitterCount = 0
                }
            }
            prevBearing = bearing; prevBearingAt = now; hasPrevBearing = true
        }

        // ── Pending crash confirmation ─────────────────────────────────────────
        if let pLoc = pendingCrashLoc {
            if speed > 2.0 {
                pendingCrashLoc = nil; pendingCrashDropKmh = 0; pendingCrashDetectedAt = 0
            } else if (now - pendingCrashDetectedAt) >= crashConfirmWindowSec {
                lastCrashAt = now
                pendingCrashLoc = nil
                delegate?.detectorOnPossibleCrash(pLoc, dropKmh: pendingCrashDropKmh)
                pendingCrashDropKmh = 0; pendingCrashDetectedAt = 0
            }
        }

        prevSpeedMps = speed; prevSpeedAt = now
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private func haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let r    = 6_371_000.0
        let dLat = (lat2 - lat1) * .pi / 180
        let dLon = (lon2 - lon1) * .pi / 180
        let a    = sin(dLat/2) * sin(dLat/2)
                 + cos(lat1 * .pi / 180) * cos(lat2 * .pi / 180) * sin(dLon/2) * sin(dLon/2)
        return 2 * r * asin(sqrt(a))
    }
}
