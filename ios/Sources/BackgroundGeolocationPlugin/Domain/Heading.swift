// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

/// A compass heading in degrees — the directional twin of `GeoPoint`, the Swift twin of
/// the Android `domain/Heading` (Roadmap Fase 1). The detector computed the angular
/// difference between two bearings inline (`abs(a - b)`, folded above 180°) and tracked
/// the previous bearing as loose mutable fields (prevBearing + hasPrevBearing).
/// Modelling a heading as one value object moves the wrap-around delta into the shared
/// domain vocabulary — pure, trivially testable.
struct Heading: Equatable {
    let degrees: Double

    /// Smallest absolute angular difference to `other`, in degrees (0...180). Handles the
    /// 0/360 wrap-around (e.g. 350° and 10° are 20° apart). Symmetric. Preserves the
    /// legacy detector computation exactly.
    func deltaTo(_ other: Heading) -> Double {
        var d = abs(other.degrees - degrees)
        if d > 180 { d = 360 - d }
        return d
    }
}
