// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

data class ScoringWeights(
    val speeding:    Int = 30,
    val hardBraking: Int = 25,
    val rapidAccel:  Int = 20,
    val sharpTurn:   Int = 15,
    val phoneUsage:  Int = 10,
) {
    fun isValid() = (speeding + hardBraking + rapidAccel + sharpTurn + phoneUsage) == 100
}

data class TripScoreBreakdown(
    val speeding: Int,
    val hardBraking: Int,
    val rapidAcceleration: Int,
    val sharpTurns: Int,
    val phoneUsage: Int,
)

data class TripScoreEvent(
    val type: String,
    val timestamp: Long,
    val penalty: Int,
    val latitude: Double,
    val longitude: Double,
)

data class TripScore(
    val overall: Int,
    val breakdown: TripScoreBreakdown,
    val events: List<TripScoreEvent>,
    val tripId: String,
    val startedAt: Long,
    val endedAt: Long,
    val distanceKm: Double,
    val totalIdleMs: Long,
    val idleCount: Int,
)
