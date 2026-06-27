// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

import com.gachlab.geolocation.ScoringWeights

/**
 * Trip / driving-event detection policy — a pure, immutable value object for the
 * `domain/` layer (Roadmap Fase 1; ARCHITECTURE.md domain set). These are the thresholds
 * the GPS `DrivingEventsDetector` reasons about (when a trip starts/ends, what counts as
 * speeding, a hard brake, a sharp turn, a possible crash, idle, phone-usage jitter).
 *
 * Previously a `Config` nested inside the detector; lifting it into the domain makes the
 * tracking policy a shared, testable vocabulary the hub builds and the engine consumes.
 * Durations are in milliseconds (the Android detector clock); the iOS twin uses seconds.
 */
data class TripConfig(
    val enabled:              Boolean = false,
    val speedLimitKmh:        Double  = 0.0,
    val minMovingSpeedMps:    Double  = 1.0,
    val stoppedDurationMs:    Long    = 60_000L,
    val minTripSpeedMps:      Double  = 3.0,
    val minTripDurationMs:    Long    = 30_000L,
    val hardBrakeMps2:        Double  = 3.5,
    val rapidAccelMps2:       Double  = 3.5,
    val sharpTurnDegPerSec:   Double  = 30.0,
    val crashImpactKmh:       Double  = 25.0,
    val crashWindowMs:        Long    = 2_000L,
    val idleThresholdMs:      Long    = 300_000L,
    val idleEndThresholdMs:   Long    = 30_000L,
    val scoringWeights:       ScoringWeights? = null,
    val crashConfirmWindowMs: Long    = 0L,
    val sensorFusion:         Boolean = false,
    val phoneUsageWindowMs:   Long    = 4_000L,
    val phoneUsageCooldownMs: Long    = 60_000L,
)
