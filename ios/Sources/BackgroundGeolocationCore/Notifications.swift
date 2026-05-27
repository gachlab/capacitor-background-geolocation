// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

public extension Notification.Name {
    static let BGBackgroundSyncDidStart    = Notification.Name("BGBackgroundSyncDidStart")
    static let BGBackgroundSyncDidSucceed  = Notification.Name("BGBackgroundSyncDidSucceed")
    static let BGBackgroundSyncDidFail     = Notification.Name("BGBackgroundSyncDidFail")
    static let BGBackgroundSyncDidProgress = Notification.Name("BGBackgroundSyncDidProgress")
    static let BGHeartbeat                 = Notification.Name("BGHeartbeat")
    static let BGTripStart                 = Notification.Name("BGTripStart")
    static let BGTripEnd                   = Notification.Name("BGTripEnd")
    static let BGMoving                    = Notification.Name("BGMoving")
    static let BGStopped                   = Notification.Name("BGStopped")
    static let BGSpeeding                  = Notification.Name("BGSpeeding")
    static let BGProviderChange            = Notification.Name("BGProviderChange")
    static let BGSOS                       = Notification.Name("BGSOS")
    static let BGHardBrake                 = Notification.Name("BGHardBrake")
    static let BGRapidAcceleration         = Notification.Name("BGRapidAcceleration")
    static let BGSharpTurn                 = Notification.Name("BGSharpTurn")
    static let BGPossibleCrash             = Notification.Name("BGPossibleCrash")
    static let BGPhoneUsageWhileDriving    = Notification.Name("BGPhoneUsageWhileDriving")
    /** Fired when the iOS location provider switches to a background fallback strategy. */
    static let BGFallbackActivated         = Notification.Name("BGFallbackActivated")
    // Driver intelligence events (v1.4)
    static let BGIdleStart                 = Notification.Name("BGIdleStart")
    static let BGIdleEnd                   = Notification.Name("BGIdleEnd")
    // Geofence transition events — userInfo: ["id": String, "action": String]
    static let BGGeofenceEnter             = Notification.Name("BGGeofenceEnter")
    static let BGGeofenceExit              = Notification.Name("BGGeofenceExit")
    static let BGGeofenceDwell             = Notification.Name("BGGeofenceDwell")
    static let BGGeofenceError             = Notification.Name("BGGeofenceError")
}
