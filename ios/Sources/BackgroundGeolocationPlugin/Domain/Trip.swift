// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

/// An in-progress driving trip — a pure, immutable value object for the journey the
/// engine is currently recording. The Swift twin of the Android `domain/Trip` (Roadmap
/// Fase 1): the trip was previously three loose mutable fields scattered through
/// `DrivingEventsDetector` (tripStartedAt / tripDistanceMeters / tripId); modelling it
/// as one value object gives the engine a shared vocabulary with no I/O or side effects.
///
/// `startedAt` is seconds (timeIntervalSince1970, the detector's clock); `id` is the
/// start time in milliseconds as a string, matching the Android `nowMs.toString()` and
/// the legacy `String(Int64(now * 1000))`.
struct Trip: Equatable {
    let id: String
    let startedAt: Double      // seconds (timeIntervalSince1970)
    var distanceMeters: Double = 0

    /// Trip extended by `meters` of travel (returns a new Trip; never mutates).
    func plusDistance(_ meters: Double) -> Trip {
        Trip(id: id, startedAt: startedAt, distanceMeters: distanceMeters + meters)
    }

    /// Elapsed duration at `now` (seconds), in milliseconds.
    func durationMs(_ now: Double) -> Int64 { Int64((now - startedAt) * 1000) }

    /// A trip beginning at `now` (seconds). The id preserves the legacy
    /// `String(Int64(now * 1000))` behaviour.
    static func startedAt(_ now: Double) -> Trip {
        Trip(id: String(Int64(now * 1000)), startedAt: now, distanceMeters: 0)
    }
}
