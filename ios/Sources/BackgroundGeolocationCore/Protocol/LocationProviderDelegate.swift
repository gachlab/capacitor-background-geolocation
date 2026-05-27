// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

public enum BGAuthorizationStatus: Int {
    case denied         = 0
    case always         = 1
    case foreground     = 2
    case notDetermined  = 99
}

public enum BGOperationalMode: Int {
    case background = 0
    case foreground = 1
}

public enum BGErrorCode: Int {
    case permissionDenied = 1000
    case settingsError    = 1001
    case configureError   = 1002
    case serviceError     = 1003
    case jsonError        = 1004
    case notImplemented   = 9999
}

public protocol LocationProviderDelegate: AnyObject {
    func onAuthorizationChanged(_ status: BGAuthorizationStatus)
    func onLocationChanged(_ location: BGLocation)
    func onStationaryChanged(_ location: BGLocation)
    func onLocationPause()
    func onLocationResume()
    func onActivityChanged(_ activity: BGActivity)
    func onAbortRequested()
    func onHttpAuthorization()
    func onError(_ error: Error)
}
