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
/// a single value to carry to the `tripEnd` event. `startedAtMs`/`endedAtMs` are Unix
/// epoch **milliseconds** — same unit and field names as the Android twin, so the
/// timestamps are safe to emit cross-platform (avoids a seconds-vs-ms trap).
struct Journey {
    let id: String
    let startedAtMs: Int64
    let endedAtMs: Int64
    let distanceMeters: Double
    let score: TripScore

    /// Trip span in milliseconds.
    var durationMs: Int64 { endedAtMs - startedAtMs }

    /// The Journey that `trip` becomes when it ends at `endedAt` (seconds) with `score`.
    static func completed(_ trip: Trip, endedAt: Double, score: TripScore) -> Journey {
        Journey(id: trip.id,
                startedAtMs: Int64(trip.startedAt * 1000),
                endedAtMs: Int64(endedAt * 1000),
                distanceMeters: trip.distanceMeters, score: score)
    }
}
