// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

/// Trip / driving-event detection policy — a pure, immutable value object for the domain
/// layer, the Swift twin of the Android `domain/TripConfig` (ARCHITECTURE.md domain set:
/// Position·Journey·Trip·TrackingConfig·TripConfig·GeoEvent). These are the thresholds
/// the GPS `DrivingEventsDetector` reasons about (trip start/end, speeding, hard brake,
/// rapid accel, sharp turn, possible crash, idle, phone-usage jitter).
///
/// Previously a flat set of `var`s on the detector; lifting them into one value object
/// makes the tracking policy a shared, testable vocabulary the plugin builds and the
/// engine consumes. Durations are in seconds (the iOS detector clock); the Android twin
/// uses milliseconds.
struct TripConfig {
    var enabled               = false
    var speedLimitKmh         = 0.0
    var minMovingSpeedMps     = 1.0
    var stoppedDurationSec    = 60.0
    var minTripSpeedMps       = 3.0
    var minTripDurationSec    = 30.0
    var hardBrakeMps2         = 3.5
    var rapidAccelMps2        = 3.5
    var sharpTurnDegPerSec    = 30.0
    var crashImpactKmh        = 25.0
    var crashWindowSec        = 2.0
    var crashConfirmWindowSec = 0.0
    var sensorFusion          = false
    var phoneUsageWindowSec   = 4.0
    var phoneUsageCooldownSec = 60.0
    var idleThresholdSec      = 300.0
    var idleEndThresholdSec   = 30.0
    var scoringWeights: ScoringWeights?
}
