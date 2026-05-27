// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

internal class ScoreCalculator(private val weights: ScoringWeights = ScoringWeights()) {

    private val scoredEvents = mutableListOf<TripScoreEvent>()
    private var speedingPenalty  = 0
    private var brakingPenalty   = 0
    private var accelPenalty     = 0
    private var turnPenalty      = 0
    private var phonePenalty     = 0
    private var totalIdleMs      = 0L
    private var idleCount        = 0

    fun recordSpeeding(loc: BGLocation, ts: Long)   { speedingPenalty += 15; record("speeding",   ts, 15, loc) }
    fun recordHardBrake(loc: BGLocation, ts: Long)  { brakingPenalty  += 12; record("hardBrake",  ts, 12, loc) }
    fun recordRapidAccel(loc: BGLocation, ts: Long) { accelPenalty    += 10; record("rapidAccel", ts, 10, loc) }
    fun recordSharpTurn(loc: BGLocation, ts: Long)  { turnPenalty     +=  8; record("sharpTurn",  ts,  8, loc) }
    fun recordPhoneUsage(loc: BGLocation, ts: Long) { phonePenalty    += 20; record("phoneUsage", ts, 20, loc) }
    fun recordIdleStart()                           { idleCount++ }
    fun recordIdleEnd(durationMs: Long)             { totalIdleMs += durationMs }

    fun compute(tripId: String, startedAt: Long, endedAt: Long, distanceMeters: Double): TripScore {
        val speedScore = (100 - speedingPenalty).coerceAtLeast(0)
        val brakeScore = (100 - brakingPenalty ).coerceAtLeast(0)
        val accelScore = (100 - accelPenalty   ).coerceAtLeast(0)
        val turnScore  = (100 - turnPenalty    ).coerceAtLeast(0)
        val phoneScore = (100 - phonePenalty   ).coerceAtLeast(0)

        val overall = (
            speedScore * weights.speeding +
            brakeScore * weights.hardBraking +
            accelScore * weights.rapidAccel +
            turnScore  * weights.sharpTurn +
            phoneScore * weights.phoneUsage
        ) / 100

        return TripScore(
            overall     = overall,
            breakdown   = TripScoreBreakdown(speedScore, brakeScore, accelScore, turnScore, phoneScore),
            events      = scoredEvents.toList(),
            tripId      = tripId,
            startedAt   = startedAt,
            endedAt     = endedAt,
            distanceKm  = distanceMeters / 1000.0,
            totalIdleMs = totalIdleMs,
            idleCount   = idleCount,
        )
    }

    private fun record(type: String, ts: Long, penalty: Int, loc: BGLocation) {
        scoredEvents += TripScoreEvent(type, ts, penalty, loc.latitude, loc.longitude)
    }
}
