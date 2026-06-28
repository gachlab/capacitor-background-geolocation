// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

/// The resolved location-tracking policy — a pure, immutable value object for the domain
/// layer (ARCHITECTURE.md domain set), the iOS twin of the Android `domain/TrackingConfig`.
/// Where `TripConfig` is the driving-event detection policy, TrackingConfig is *how
/// location is acquired*: accuracy, distance filter, activity interval, provider.
///
/// iOS exposes fewer knobs than Android (no AlarmManager polling / stationary-exit
/// intervals — CoreLocation handles stationary via significant-change monitoring), so
/// this mirrors only the iOS-relevant fields. Lives in Core because `BGConfig` and the
/// providers do. `maxAcceptedAccuracy` stays optional (nil = no accuracy gate).
///
/// Scope: pure value object + mapper. The providers still read `BGConfig` directly;
/// rebasing them onto TrackingConfig is Roadmap Fase 3 (hub → ports).
public struct TrackingConfig {
    public let stationaryRadius: Double
    public let distanceFilter: Double
    public let desiredAccuracy: Double
    public let locationProvider: Int
    public let activitiesInterval: Double
    public let activityConfidenceThreshold: Int
    public let maxAcceptedAccuracy: Double?
}
