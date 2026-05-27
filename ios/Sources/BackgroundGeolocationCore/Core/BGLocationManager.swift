// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import CoreLocation

public final class BGLocationManager: NSObject {

    // MARK: - Singleton

    public static let shared = BGLocationManager()

    // MARK: - Private state

    private let locationManager = CLLocationManager()
    private var lastAuthStatus: BGAuthorizationStatus?

    // MARK: - Public interface

    public weak var delegate: LocationProviderDelegate?

    public var currentLocation: CLLocation? {
        return locationManager.location
    }

    public var monitoredRegions: Set<CLRegion> {
        return locationManager.monitoredRegions
    }

    // MARK: - Forwarded CLLocationManager properties

    public var distanceFilter: CLLocationDistance {
        get { locationManager.distanceFilter }
        set { runOnMain { self.locationManager.distanceFilter = newValue } }
    }

    public var desiredAccuracy: CLLocationAccuracy {
        get { locationManager.desiredAccuracy }
        set { runOnMain { self.locationManager.desiredAccuracy = newValue } }
    }

    public var activityType: CLActivityType {
        get { locationManager.activityType }
        set { runOnMain { self.locationManager.activityType = newValue } }
    }

    public var pausesLocationUpdatesAutomatically: Bool {
        get { locationManager.pausesLocationUpdatesAutomatically }
        set { runOnMain { self.locationManager.pausesLocationUpdatesAutomatically = newValue } }
    }

    public var allowsBackgroundLocationUpdates: Bool {
        get { locationManager.allowsBackgroundLocationUpdates }
        set { runOnMain { self.locationManager.allowsBackgroundLocationUpdates = newValue } }
    }

    public var showsBackgroundLocationIndicator: Bool {
        get { locationManager.showsBackgroundLocationIndicator }
        set { runOnMain { self.locationManager.showsBackgroundLocationIndicator = newValue } }
    }

    // MARK: - Init

    private override init() {
        super.init()
        runOnMain {
            self.locationManager.delegate = self
            self.locationManager.allowsBackgroundLocationUpdates = true
            self.locationManager.pausesLocationUpdatesAutomatically = false
        }
    }

    // MARK: - Location updates

    public func start() {
        runOnMain { self.locationManager.startUpdatingLocation() }
    }

    public func stop() {
        runOnMain { self.locationManager.stopUpdatingLocation() }
    }

    // MARK: - Significant location changes

    public func startMonitoringSignificantChanges() {
        runOnMain { self.locationManager.startMonitoringSignificantLocationChanges() }
    }

    public func stopMonitoringSignificantChanges() {
        runOnMain { self.locationManager.stopMonitoringSignificantLocationChanges() }
    }

    // MARK: - Region monitoring

    public func startMonitoring(region: CLCircularRegion) {
        runOnMain { self.locationManager.startMonitoring(for: region) }
    }

    public func stopMonitoring(region: CLCircularRegion) {
        runOnMain { self.locationManager.stopMonitoring(for: region) }
    }

    public func stopMonitoringAllRegions() {
        runOnMain {
            for region in self.locationManager.monitoredRegions {
                self.locationManager.stopMonitoring(for: region)
            }
        }
    }

    // MARK: - Authorization

    public func requestAlwaysAuthorization() {
        runOnMain { self.locationManager.requestAlwaysAuthorization() }
    }

    public func requestWhenInUseAuthorization() {
        runOnMain { self.locationManager.requestWhenInUseAuthorization() }
    }

    // MARK: - Helpers

    private func runOnMain(_ block: @escaping () -> Void) {
        if Thread.isMainThread {
            block()
        } else {
            DispatchQueue.main.async(execute: block)
        }
    }

    private func authorizationStatus(from manager: CLLocationManager) -> BGAuthorizationStatus {
        let status: CLAuthorizationStatus
        if #available(iOS 14.0, *) {
            status = manager.authorizationStatus
        } else {
            status = CLLocationManager.authorizationStatus()
        }
        switch status {
        case .authorizedAlways:
            return .always
        case .authorizedWhenInUse:
            return .foreground
        case .denied, .restricted:
            return .denied
        case .notDetermined:
            return .notDetermined
        @unknown default:
            return .notDetermined
        }
    }
}

// MARK: - CLLocationManagerDelegate

extension BGLocationManager: CLLocationManagerDelegate {

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let delegate = delegate else { return }
        for loc in locations {
            let bgLoc = BGLocation.from(clLocation: loc)
            delegate.onLocationChanged(bgLoc)
        }
    }

    public func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        guard let delegate = delegate else { return }
        let clLoc = manager.location ?? CLLocation(latitude: 0, longitude: 0)
        let bgLoc = BGLocation.from(clLocation: clLoc)
        delegate.onStationaryChanged(bgLoc)
    }

    public func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        handleAuthorizationChange(manager: manager)
    }

    @available(iOS 14.0, *)
    public func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        handleAuthorizationChange(manager: manager)
    }

    public func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        delegate?.onError(error)
    }

    // MARK: - Private helpers

    private func handleAuthorizationChange(manager: CLLocationManager) {
        let status = authorizationStatus(from: manager)
        guard status != lastAuthStatus else { return }
        lastAuthStatus = status
        delegate?.onAuthorizationChanged(status)
    }
}
