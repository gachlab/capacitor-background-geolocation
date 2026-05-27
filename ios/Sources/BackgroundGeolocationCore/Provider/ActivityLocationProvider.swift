// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import CoreLocation
import CoreMotion

public final class ActivityLocationProvider: AbstractLocationProvider {

    // MARK: - Motion type

    private enum MotionType: String {
        case still, walking, running, cycling, driving, unknown
    }

    // MARK: - State

    private var isStarted = false
    private var isTracking = false
    private var lastMotionType = MotionType.unknown

    // MARK: - CoreMotion

    private var activityManager = CMMotionActivityManager()
    private var activityQueue: OperationQueue = {
        let q = OperationQueue()
        q.name = "BGActivityQueue"
        q.maxConcurrentOperationCount = 1
        return q
    }()

    // MARK: - Lifecycle

    public override func onStart() throws {
        isStarted = true
        startTracking()

        guard CMMotionActivityManager.isActivityAvailable() else {
            // Fall back: no activity recognition, always track
            return
        }

        activityManager.startActivityUpdates(to: activityQueue) { [weak self] activity in
            guard let activity = activity else { return }
            self?.handleActivity(activity)
        }
    }

    public override func onStop() throws {
        if CMMotionActivityManager.isActivityAvailable() {
            activityManager.stopActivityUpdates()
        }
        stopTracking()
        isStarted = false
        lastMotionType = .unknown
    }

    public override func onTerminate() {
        guard isStarted, !(config.stopOnTerminate ?? true) else { return }
        BGLocationManager.shared.startMonitoringSignificantChanges()
    }

    // MARK: - Activity handling

    private func handleActivity(_ activity: CMMotionActivity) {
        let threshold = config.activityConfidenceThreshold ?? 50
        let confidence = confidencePercent(activity.confidence)
        guard confidence >= threshold else { return }

        let motionType = motionType(from: activity)
        guard motionType != .unknown else { return }
        guard motionType != lastMotionType else { return }

        let previous = lastMotionType
        lastMotionType = motionType

        let bgActivity = BGActivity(type: motionType.rawValue, confidence: confidence)
        DispatchQueue.main.async { [weak self] in
            self?.delegate?.onActivityChanged(bgActivity)
        }

        if motionType != .still {
            if !isTracking {
                startTracking()
            }
        }
        // If previously tracking and now still, we let the next location update stop it
        _ = previous
    }

    // MARK: - Tracking control

    private func startTracking() {
        guard !isTracking else { return }
        isTracking = true
        let mgr = BGLocationManager.shared
        mgr.delegate = self
        mgr.distanceFilter = config.distanceFilter ?? kCLDistanceFilterNone
        mgr.desiredAccuracy = config.decodedDesiredAccuracy()
        mgr.activityType = config.decodedActivityType()
        if let showIndicator = config.showsBackgroundLocationIndicator {
            mgr.showsBackgroundLocationIndicator = showIndicator
        }
        mgr.start()
    }

    private func stopTracking() {
        guard isTracking else { return }
        isTracking = false
        BGLocationManager.shared.stop()
        BGLocationManager.shared.delegate = nil
    }

    // MARK: - CoreMotion conversions

    private func motionType(from activity: CMMotionActivity) -> MotionType {
        if activity.stationary { return .still }
        if activity.walking    { return .walking }
        if activity.running    { return .running }
        if activity.cycling    { return .cycling }
        if activity.automotive { return .driving }
        return .unknown
    }

    private func confidencePercent(_ confidence: CMMotionActivityConfidence) -> Int {
        switch confidence {
        case .low:    return 20
        case .medium: return 40
        case .high:   return 80
        @unknown default: return 0
        }
    }
}

// MARK: - LocationProviderDelegate (BGLocationManager callbacks)

extension ActivityLocationProvider: LocationProviderDelegate {

    public func onLocationChanged(_ location: BGLocation) {
        if lastMotionType == .still {
            stopTracking()
            var loc = location
            loc.locationProvider = BGLocationProvider.activity.rawValue
            loc.provider = "gps"
            delegate?.onStationaryChanged(loc)
        } else {
            var loc = location
            loc.locationProvider = BGLocationProvider.activity.rawValue
            loc.provider = "gps"
            delegate?.onLocationChanged(loc)
        }
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
