// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

/// A geographic coordinate — a pure, immutable value object, the Swift twin of the
/// Android `domain/GeoPoint` (Roadmap Fase 1). The great-circle distance was previously
/// a private `haversineMeters` helper inside `DrivingEventsDetector`, and the "previous
/// fix" was three loose mutable fields (prevLat / prevLon / hasPrev). Modelling the
/// point as one value object moves the geometry into the shared domain vocabulary.
struct GeoPoint: Equatable {
    let latitude: Double
    let longitude: Double

    /// Mean earth radius, matching the legacy detector constant.
    static let earthRadiusMeters = 6_371_000.0

    /// Great-circle distance in metres to `other` via the haversine formula. Symmetric
    /// and zero for identical points. Preserves the legacy detector numerics exactly.
    func distanceTo(_ other: GeoPoint) -> Double {
        let dLat = (other.latitude - latitude) * .pi / 180
        let dLon = (other.longitude - longitude) * .pi / 180
        let a = sin(dLat / 2) * sin(dLat / 2)
              + cos(latitude * .pi / 180) * cos(other.latitude * .pi / 180) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * GeoPoint.earthRadiusMeters * asin(sqrt(a))
    }
}
