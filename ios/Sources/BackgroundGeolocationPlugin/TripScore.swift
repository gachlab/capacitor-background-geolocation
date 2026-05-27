// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

struct ScoringWeights {
    var speeding:    Int = 30
    var hardBraking: Int = 25
    var rapidAccel:  Int = 20
    var sharpTurn:   Int = 15
    var phoneUsage:  Int = 10

    var isValid: Bool { speeding + hardBraking + rapidAccel + sharpTurn + phoneUsage == 100 }
}

struct TripScoreBreakdown {
    let speeding: Int, hardBraking: Int, rapidAcceleration: Int, sharpTurns: Int, phoneUsage: Int
}

struct TripScoreEvent {
    let type: String, timestamp: Int64, penalty: Int, latitude: Double, longitude: Double
}

struct TripScore {
    let overall: Int
    let breakdown: TripScoreBreakdown
    let events: [TripScoreEvent]
    let tripId: String
    let startedAt: Int64
    let endedAt: Int64
    let distanceKm: Double
    let totalIdleMs: Int64
    let idleCount: Int
}
