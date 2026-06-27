// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

/// A single location fix as the domain layer sees it — a pure, immutable kinematic
/// snapshot, the Swift twin of the Android `domain/Position` (Roadmap Fase 1). It models
/// an *absent* reading honestly as `nil` (`speedMps`/`bearingDeg`) rather than the
/// CoreLocation sentinel pattern (negative speed = unavailable), so domain logic reasons
/// about typed optional kinematics instead of platform sentinels.
struct Position: Equatable {
    let point: GeoPoint
    let speedMps: Double?
    let bearingDeg: Double?
    let provider: String?

    /// Speed in m/s, treating an absent reading as stationary (0).
    var speedMpsOrZero: Double { speedMps ?? 0 }
}
