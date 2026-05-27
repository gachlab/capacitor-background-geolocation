// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

/**
 * User-defined geofence region for entry/exit/dwell detection.
 *
 * iOS CLLocationManager supports monitoring up to 20 simultaneous regions (shared
 * between the stationary-detection region and user-defined geofences).
 * Dwell detection is implemented with a per-region Timer; timers do not fire when
 * the app is suspended — foreground or background-active state is required.
 */
public struct BGGeofence: Codable, Equatable {
    public let id:             String
    public let latitude:       Double
    public let longitude:      Double
    public let radius:         Double
    public let notifyOnEntry:  Bool
    public let notifyOnExit:   Bool
    public let notifyOnDwell:  Bool
    public let loiteringDelay: Int   // ms

    public init(id: String, latitude: Double, longitude: Double,
                radius: Double = 200, notifyOnEntry: Bool = true,
                notifyOnExit: Bool = false, notifyOnDwell: Bool = false,
                loiteringDelay: Int = 30_000) {
        self.id             = id
        self.latitude       = latitude
        self.longitude      = longitude
        self.radius         = radius
        self.notifyOnEntry  = notifyOnEntry
        self.notifyOnExit   = notifyOnExit
        self.notifyOnDwell  = notifyOnDwell
        self.loiteringDelay = loiteringDelay
    }

    public func toDictionary() -> [String: Any] {
        return [
            "id": id, "latitude": latitude, "longitude": longitude,
            "radius": radius, "notifyOnEntry": notifyOnEntry,
            "notifyOnExit": notifyOnExit, "notifyOnDwell": notifyOnDwell,
            "loiteringDelay": loiteringDelay
        ]
    }

    public static func from(dictionary d: [String: Any]) -> BGGeofence? {
        guard let id        = d["id"]        as? String,
              let latitude  = (d["latitude"]  as? NSNumber)?.doubleValue,
              let longitude = (d["longitude"] as? NSNumber)?.doubleValue else { return nil }
        return BGGeofence(
            id:             id,
            latitude:       latitude,
            longitude:      longitude,
            radius:         (d["radius"]         as? NSNumber)?.doubleValue ?? 200,
            notifyOnEntry:  (d["notifyOnEntry"]  as? Bool) ?? true,
            notifyOnExit:   (d["notifyOnExit"]   as? Bool) ?? false,
            notifyOnDwell:  (d["notifyOnDwell"]  as? Bool) ?? false,
            loiteringDelay: (d["loiteringDelay"] as? Int)  ?? 30_000
        )
    }
}
