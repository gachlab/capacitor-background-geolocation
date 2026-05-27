// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import CoreLocation

public final class DistanceFilterLocationProvider: AbstractLocationProvider, CLLocationManagerDelegate {

    // MARK: - Own CLLocationManager

    private let locationManager = CLLocationManager()

    // MARK: - State

    private var isStarted = false
    private var isMoving = false
    private var isAcquiringStationaryLocation = false
    private var isAcquiringSpeed = false
    private var stationaryRegion: CLCircularRegion?
    private var stationarySince: Date?
    private var bestLocation: BGLocation?
    private var acquisitionStartTime: Date?
    private var scaledDistanceFilter: Double = 0
    private var operationMode: BGOperationalMode = .foreground
    private var lastAuthStatus: BGAuthorizationStatus?

    // MARK: - Lifecycle

    public override func onCreate() {
        runOnMain {
            self.locationManager.delegate = self
            self.locationManager.allowsBackgroundLocationUpdates = true
            self.locationManager.pausesLocationUpdatesAutomatically = false
        }
    }

    public override func onDestroy() {
        runOnMain {
            self.locationManager.stopUpdatingLocation()
            self.locationManager.stopMonitoringSignificantLocationChanges()
            self.removeStationaryRegion()
        }
    }

    public override func onStart() throws {
        runOnMain {
            self.locationManager.desiredAccuracy = self.config.decodedDesiredAccuracy()
            self.locationManager.activityType = self.config.decodedActivityType()
            if let showIndicator = self.config.showsBackgroundLocationIndicator {
                self.locationManager.showsBackgroundLocationIndicator = showIndicator
            }
            self.isAcquiringSpeed = true
            self.acquisitionStartTime = Date()
            self.bestLocation = nil
            self.locationManager.distanceFilter = kCLDistanceFilterNone
            self.locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
            self.locationManager.startUpdatingLocation()
            self.isStarted = true
            self.delegate?.onLocationResume()
        }
    }

    public override func onStop() throws {
        runOnMain {
            self.locationManager.stopUpdatingLocation()
            self.removeStationaryRegion()
            self.locationManager.stopMonitoringSignificantLocationChanges()
            self.isStarted = false
            self.isMoving = false
            self.isAcquiringSpeed = false
            self.isAcquiringStationaryLocation = false
            self.bestLocation = nil
            self.delegate?.onLocationPause()
        }
    }

    public override func onSwitchMode(_ mode: BGOperationalMode) {
        operationMode = mode
        runOnMain {
            let saveBattery = self.config.saveBatteryOnBackground ?? false
            if mode == .foreground || !saveBattery {
                self.isAcquiringSpeed = true
                self.acquisitionStartTime = Date()
                self.bestLocation = nil
                self.removeStationaryRegion()
                self.locationManager.stopMonitoringSignificantLocationChanges()
                self.locationManager.distanceFilter = kCLDistanceFilterNone
                self.locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
                self.locationManager.startUpdatingLocation()
            } else {
                // background AND saveBatteryOnBackground
                self.isAcquiringStationaryLocation = true
                self.acquisitionStartTime = Date()
                self.bestLocation = nil
                self.locationManager.startMonitoringSignificantLocationChanges()
                self.locationManager.distanceFilter = kCLDistanceFilterNone
                self.locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
                self.locationManager.startUpdatingLocation()
            }
        }
    }

    public override func onTerminate() {
        guard isStarted, !(config.stopOnTerminate ?? true) else { return }
        runOnMain {
            self.locationManager.startMonitoringSignificantLocationChanges()
        }
    }

    // MARK: - CLLocationManagerDelegate

    public func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        for clLocation in locations {
            processLocation(clLocation)
        }
    }

    public func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        guard let sr = stationaryRegion, region.identifier == sr.identifier else { return }
        removeStationaryRegion()
        isAcquiringSpeed = true
        acquisitionStartTime = Date()
        bestLocation = nil
        runOnMain {
            self.locationManager.distanceFilter = kCLDistanceFilterNone
            self.locationManager.desiredAccuracy = kCLLocationAccuracyBestForNavigation
            self.locationManager.startUpdatingLocation()
        }
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

    // MARK: - Location processing

    private func processLocation(_ clLocation: CLLocation) {
        // 1. Age filter: skip locations older than 30 seconds
        let age = Date().timeIntervalSince(clLocation.timestamp)
        guard age <= 30 else { return }

        // 2. Invalid accuracy
        guard clLocation.horizontalAccuracy >= 0 else { return }

        // 3. maxAcceptedAccuracy filter
        if let maxAcc = config.maxAcceptedAccuracy, clLocation.horizontalAccuracy > maxAcc {
            return
        }

        // 4. Mock location policy
        if config.mockLocationPolicy?.lowercased() == "drop" {
            if #available(iOS 15.0, *) {
                if clLocation.sourceInformation?.isSimulatedBySoftware == true {
                    return
                }
            }
        }

        // 5. Build BGLocation
        var bgLoc = BGLocation.from(clLocation: clLocation)
        bgLoc.provider = "gps"
        bgLoc.locationProvider = BGLocationProvider.distanceFilter.rawValue

        // 6. Acquisition modes
        if isAcquiringStationaryLocation || isAcquiringSpeed {
            let isBetter = bgLoc.isBetter(than: bestLocation) || bestLocation == nil
            if isBetter {
                bestLocation = bgLoc
            }

            let desiredAcc = config.desiredAccuracy ?? 100
            let timedOut: Bool
            if let start = acquisitionStartTime {
                timedOut = Date().timeIntervalSince(start) >= 15
            } else {
                timedOut = false
            }

            let accuracyMet = clLocation.horizontalAccuracy <= desiredAcc

            if accuracyMet || timedOut {
                let finalLocation = bestLocation ?? bgLoc

                if isAcquiringStationaryLocation {
                    isAcquiringStationaryLocation = false
                    isMoving = false
                    stationarySince = Date()
                    runOnMain { [weak self] in
                        guard let self = self else { return }
                        self.locationManager.stopUpdatingLocation()
                        self.startMonitoringStationary(at: finalLocation)
                    }
                    delegate?.onStationaryChanged(finalLocation)
                } else if isAcquiringSpeed {
                    isAcquiringSpeed = false
                    isMoving = true
                    let speed = max(clLocation.speed, 0)
                    scaledDistanceFilter = calculateDistanceFilter(speedMps: speed)
                    runOnMain { [weak self] in
                        guard let self = self else { return }
                        self.locationManager.distanceFilter = self.scaledDistanceFilter
                        self.locationManager.desiredAccuracy = self.config.decodedDesiredAccuracy()
                        self.locationManager.startUpdatingLocation()
                    }
                    delegate?.onLocationResume()
                }
            }
            return
        }

        // 7. Normal moving mode
        if isMoving {
            let speed = max(clLocation.speed, 0)
            let newDistanceFilter = calculateDistanceFilter(speedMps: speed)
            if abs(newDistanceFilter - scaledDistanceFilter) > 10 {
                scaledDistanceFilter = newDistanceFilter
                runOnMain { [weak self] in
                    guard let self = self else { return }
                    self.locationManager.distanceFilter = self.scaledDistanceFilter
                    self.locationManager.startUpdatingLocation()
                }
            }

            // Check if moved beyond stationary region
            if let sr = stationaryRegion {
                let regionCenter = BGLocation()
                regionCenter.latitude = sr.center.latitude
                regionCenter.longitude = sr.center.longitude
                if bgLoc.isBeyond(regionCenter, radius: sr.radius) {
                    removeStationaryRegion()
                    isAcquiringStationaryLocation = true
                    acquisitionStartTime = Date()
                    bestLocation = nil
                }
            }

            delegate?.onLocationChanged(bgLoc)
        }
    }

    // MARK: - Stationary region

    private func startMonitoringStationary(at location: BGLocation) {
        guard let lat = location.latitude, let lon = location.longitude else { return }
        let radius = config.stationaryRadius ?? 50
        let center = CLLocationCoordinate2D(latitude: lat, longitude: lon)
        let region = CLCircularRegion(
            center: center,
            radius: radius,
            identifier: "BGStationaryRegion"
        )
        region.notifyOnExit = true
        region.notifyOnEntry = false
        stationaryRegion = region
        locationManager.startMonitoring(for: region)
    }

    private func removeStationaryRegion() {
        if let region = stationaryRegion {
            locationManager.stopMonitoring(for: region)
            stationaryRegion = nil
        }
        stationarySince = nil
    }

    // MARK: - Distance filter calculation

    private func calculateDistanceFilter(speedMps: Double) -> Double {
        let speedKmh = speedMps * 3.6
        if speedKmh >= 100 {
            return config.distanceFilter ?? 500
        }
        let rounded = 5.0 * floor(abs(speedMps) / 5.0 + 0.5)
        let result = rounded * rounded + (config.distanceFilter ?? 500)
        return min(result, 1000)
    }

    // MARK: - Authorization

    private func handleAuthorizationChange(manager: CLLocationManager) {
        let status: CLAuthorizationStatus
        if #available(iOS 14.0, *) {
            status = manager.authorizationStatus
        } else {
            status = CLLocationManager.authorizationStatus()
        }

        let bgStatus: BGAuthorizationStatus
        switch status {
        case .authorizedAlways:
            bgStatus = .always
        case .authorizedWhenInUse:
            bgStatus = .foreground
        case .denied, .restricted:
            bgStatus = .denied
        case .notDetermined:
            bgStatus = .notDetermined
        @unknown default:
            bgStatus = .notDetermined
        }

        guard bgStatus != lastAuthStatus else { return }
        lastAuthStatus = bgStatus
        delegate?.onAuthorizationChanged(bgStatus)
    }
}
