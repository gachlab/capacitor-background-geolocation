// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import CoreLocation

public final class RawLocationProvider: AbstractLocationProvider {

    // MARK: - State

    private var isStarted = false

    // MARK: - Lifecycle

    public override func onStart() throws {
        isStarted = true
        let mgr = BGLocationManager.shared
        mgr.delegate = self
        mgr.distanceFilter = 0
        mgr.desiredAccuracy = config.decodedDesiredAccuracy()
        mgr.activityType = config.decodedActivityType()
        if let showIndicator = config.showsBackgroundLocationIndicator {
            mgr.showsBackgroundLocationIndicator = showIndicator
        }
        mgr.start()
        delegate?.onLocationResume()
    }

    public override func onStop() throws {
        BGLocationManager.shared.stop()
        BGLocationManager.shared.delegate = nil
        isStarted = false
        delegate?.onLocationPause()
    }

    public override func onTerminate() {
        guard isStarted, !(config.stopOnTerminate ?? true) else { return }
        BGLocationManager.shared.startMonitoringSignificantChanges()
    }
}

// MARK: - LocationProviderDelegate

extension RawLocationProvider: LocationProviderDelegate {

    public func onLocationChanged(_ location: BGLocation) {
        var loc = location
        loc.locationProvider = BGLocationProvider.raw.rawValue
        loc.provider = "gps"
        delegate?.onLocationChanged(loc)
    }

    public func onStationaryChanged(_ location: BGLocation) {
        delegate?.onStationaryChanged(location)
    }

    public func onAuthorizationChanged(_ status: BGAuthorizationStatus) {
        delegate?.onAuthorizationChanged(status)
    }

    public func onLocationPause() {
        delegate?.onLocationPause()
    }

    public func onLocationResume() {
        delegate?.onLocationResume()
    }

    public func onActivityChanged(_ activity: BGActivity) {
        delegate?.onActivityChanged(activity)
    }

    public func onAbortRequested() {
        delegate?.onAbortRequested()
    }

    public func onHttpAuthorization() {
        delegate?.onHttpAuthorization()
    }

    public func onError(_ error: Error) {
        delegate?.onError(error)
    }
}
