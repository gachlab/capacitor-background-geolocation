// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

final class ScoreCalculator {
    private let weights: ScoringWeights
    private var events = [TripScoreEvent]()
    private var speedingPenalty  = 0
    private var brakingPenalty   = 0
    private var accelPenalty     = 0
    private var turnPenalty      = 0
    private var phonePenalty     = 0
    private var totalIdleMs: Int64 = 0
    private var idleCount        = 0

    init(weights: ScoringWeights = ScoringWeights()) { self.weights = weights }

    func recordSpeeding(_ loc: DLLocation, ts: Double)   { speedingPenalty += 15; record("speeding",   ts, 15, loc) }
    func recordHardBrake(_ loc: DLLocation, ts: Double)  { brakingPenalty  += 12; record("hardBrake",  ts, 12, loc) }
    func recordRapidAccel(_ loc: DLLocation, ts: Double) { accelPenalty    += 10; record("rapidAccel", ts, 10, loc) }
    func recordSharpTurn(_ loc: DLLocation, ts: Double)  { turnPenalty     +=  8; record("sharpTurn",  ts,  8, loc) }
    func recordIdleStart()                               { idleCount += 1 }
    func recordIdleEnd(_ durationMs: Int64)              { totalIdleMs += durationMs }

    func compute(tripId: String, startedAt: Double, endedAt: Double, distanceMeters: Double) -> TripScore {
        let speedScore = max(0, 100 - speedingPenalty)
        let brakeScore = max(0, 100 - brakingPenalty)
        let accelScore = max(0, 100 - accelPenalty)
        let turnScore  = max(0, 100 - turnPenalty)
        let phoneScore = max(0, 100 - phonePenalty)

        let overall = (speedScore * weights.speeding + brakeScore * weights.hardBraking +
                       accelScore * weights.rapidAccel + turnScore * weights.sharpTurn +
                       phoneScore * weights.phoneUsage) / 100

        return TripScore(
            overall:     overall,
            breakdown:   TripScoreBreakdown(
                             speeding:           speedScore,
                             hardBraking:        brakeScore,
                             rapidAcceleration:  accelScore,
                             sharpTurns:         turnScore,
                             phoneUsage:         phoneScore),
            events:      events,
            tripId:      tripId,
            startedAt:   Int64(startedAt * 1000),
            endedAt:     Int64(endedAt   * 1000),
            distanceKm:  distanceMeters / 1000.0,
            totalIdleMs: totalIdleMs,
            idleCount:   idleCount
        )
    }

    private func record(_ type: String, _ ts: Double, _ penalty: Int, _ loc: DLLocation) {
        events.append(TripScoreEvent(
            type:      type,
            timestamp: Int64(ts * 1000),
            penalty:   penalty,
            latitude:  loc.latitude,
            longitude: loc.longitude))
    }
}
