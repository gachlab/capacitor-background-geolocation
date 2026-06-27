// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

/// A completed driving trip — a pure, immutable value object for the domain layer
/// (ARCHITECTURE.md domain set), the Swift twin of the Android `domain/Journey`. Where
/// `Trip` is the journey in progress, Journey is its finished aggregate: the distance
/// travelled, the span, and the computed `score`.
///
/// It was three loose arguments threaded through `detectorOnTripEnd`
/// (distanceMeters / durationMs / score); bundling them gives the detector and the bridge
/// a single value to carry to the `tripEnd` event. `startedAt`/`endedAt` are seconds
/// (the iOS detector clock); `durationMs` is derived in milliseconds.
struct Journey {
    let id: String
    let startedAt: Double      // seconds (timeIntervalSince1970)
    let endedAt: Double
    let distanceMeters: Double
    let score: TripScore

    /// Trip span in milliseconds.
    var durationMs: Int64 { Int64((endedAt - startedAt) * 1000) }

    /// The Journey that `trip` becomes when it ends at `endedAt` (seconds) with `score`.
    static func completed(_ trip: Trip, endedAt: Double, score: TripScore) -> Journey {
        Journey(id: trip.id, startedAt: trip.startedAt, endedAt: endedAt,
                distanceMeters: trip.distanceMeters, score: score)
    }
}
