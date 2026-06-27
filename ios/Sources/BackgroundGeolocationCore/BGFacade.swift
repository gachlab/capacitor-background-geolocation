// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import UIKit
import CoreLocation
import AudioToolbox
import UserNotifications

// MARK: - BGError

public enum BGError: Error {
    case timeout
    case permissionDenied
    case serviceError(BGErrorCode, String)
}

// MARK: - BGFacade

public final class BGFacade: NSObject {

    public weak var delegate: LocationProviderDelegate?
    public var drivingTripActive: Bool = false

    // MARK: - Private state

    private var _isStarted = false
    private var operationMode: BGOperationalMode = .foreground
    private var _config: BGConfig?
    private var stationaryLocation: BGLocation?
    private var lastReceivedLocation: BGLocation?
    private var heartbeatTimer: Timer?
    private var locationProvider: LocationProvider?
    private var sensorFusion: SensorFusionDetector?

    // Pending driving events (buffer when no simultaneous GPS fix)
    private let pendingDrivingEventsLock = NSLock()
    private var pendingDrivingEvents: [[String: Any]] = []
    static let kPendingDrivingEventsMax = 20
    static let kPendingDrivingEventsTTLMs: Double = 60_000

    // MARK: - Init

    public override init() {
        super.init()
        PostLocationTask.shared.delegate = self
        PostLocationTask.shared.attachBatterySnapshot = { [weak self] loc in
            self?.attachBatterySnapshotTo(loc)
        }
        GeofenceManager.shared.eventListener = { [weak self] id, transition, location in
            self?.onGeofenceTransition(id: id, transition: transition, location: location)
        }
    }

    // MARK: - Configure

    public func configure(_ config: BGConfig) throws {
        let existing = ConfigDAO.shared.retrieve()
        let previousHeartbeatInterval = _config?.heartbeatInterval
        let previousProviderType = _config?.locationProvider
        let previousDrivingEvents = _config?.drivingEvents

        let merged = BGConfig.merge(BGConfig.merge(BGConfig(defaults: ()), with: existing), with: config)
        _config = merged
        ConfigDAO.shared.persist(merged)
        PostLocationTask.shared.config = merged

        if merged.isDebugging {
            runOnMain {
                UNUserNotificationCenter.current().requestAuthorization(
                    options: [.alert, .sound]
                ) { _, _ in }
            }
        }

        guard _isStarted else { return }

        // Switch provider if locationProvider changed
        let newProviderType = merged.locationProvider ?? BGLocationProvider.distanceFilter.rawValue
        if newProviderType != (previousProviderType ?? BGLocationProvider.distanceFilter.rawValue),
           let oldProvider = locationProvider {
            runOnMain { try? oldProvider.onStop(); oldProvider.onDestroy() }
            let newProvider = try getProvider(newProviderType)
            newProvider.delegate = self
            newProvider.onCreate()
            locationProvider = newProvider
            runOnMain {
                try? newProvider.onConfigure(merged)
                try? newProvider.onStart()
            }
        } else if let provider = locationProvider {
            runOnMain { try? provider.onConfigure(merged) }
        }

        // Reschedule heartbeat if interval changed
        if merged.heartbeatInterval != previousHeartbeatInterval {
            cancelHeartbeat()
            scheduleHeartbeat()
        }

        // Reconfigure sensor fusion if drivingEvents changed
        let drivingEventsChanged: Bool = {
            switch (previousDrivingEvents, merged.drivingEvents) {
            case (nil, nil): return false
            case (nil, _), (_, nil): return true
            default:
                let old = NSDictionary(dictionary: previousDrivingEvents ?? [:])
                let new = NSDictionary(dictionary: merged.drivingEvents ?? [:])
                return !old.isEqual(to: new as! [AnyHashable: Any])
            }
        }()
        if drivingEventsChanged {
            configureSensorFusion()
        }
    }

    // MARK: - Start / Stop

    public func start() throws {
        guard !_isStarted else { return }

        let config = getConfig()
        _config = config
        PostLocationTask.shared.config = config
        PostLocationTask.shared.start()

        let providerType = config.locationProvider ?? BGLocationProvider.distanceFilter.rawValue
        let provider = try getProvider(providerType)
        provider.delegate = self
        provider.onCreate()
        locationProvider = provider

        runOnMain {
            try? provider.onConfigure(config)
            try? provider.onStart()
        }

        _isStarted = true
        scheduleHeartbeat()
        configureSensorFusion()
        sensorFusion?.start()
        BGLog.shared.i("Service started")
    }

    public func stop() throws {
        guard _isStarted else { return }

        cancelHeartbeat()
        sensorFusion?.tripActive = false
        sensorFusion?.stop()
        PostLocationTask.shared.stop()

        if let provider = locationProvider {
            runOnMain { try? provider.onStop() }
        }

        _isStarted = false
        BGLog.shared.i("Service stopped")
    }

    // MARK: - Location services status

    public func locationServicesEnabled() -> Bool {
        return CLLocationManager.locationServicesEnabled()
    }

    public func authorizationStatus() -> BGAuthorizationStatus {
        let status: CLAuthorizationStatus
        if #available(iOS 14.0, *) {
            status = CLLocationManager().authorizationStatus
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

    public func isStarted() -> Bool {
        return _isStarted
    }

    // MARK: - Settings UI

    public func showAppSettings() {
        runOnMain {
            guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        }
    }

    public func showLocationSettings() {
        // No-op: Apple rejected non-public URL schemes for location settings
    }

    // MARK: - Mode switching

    public func `switch`(_ mode: BGOperationalMode) {
        operationMode = mode
        if let provider = locationProvider {
            runOnMain { provider.onSwitchMode(mode) }
        }
    }

    // MARK: - Location accessors

    public func getStationaryLocation() -> BGLocation? {
        return stationaryLocation
    }

    public func getLocations() -> [BGLocation] {
        return LocationDAO.shared.getAllLocations()
    }

    public func getValidLocations() -> [BGLocation] {
        return LocationDAO.shared.getValidLocations()
    }

    /// Returns locations marked as SyncPending (for external sync consumers).
    public func getValidLocationsAndDelete() -> [BGLocation] {
        return LocationDAO.shared.getLocationsForSync()
    }

    public func deleteLocation(_ id: NSNumber) throws {
        try LocationDAO.shared.deleteLocation(id: Int64(id.int64Value))
    }

    public func deleteAllLocations() throws {
        try LocationDAO.shared.deleteAllLocations()
    }

    // MARK: - One-shot current location

    public func getCurrentLocation(
        timeout: Int32,
        maximumAge: Int,
        enableHighAccuracy: Bool
    ) throws -> BGLocation {
        // Check if the BGLocationManager already has a fresh enough fix
        if let clLoc = BGLocationManager.shared.currentLocation {
            let ageMs = Int(-clLoc.timestamp.timeIntervalSinceNow * 1000)
            if ageMs <= maximumAge {
                return BGLocation.from(clLocation: clLoc)
            }
        }

        // One-shot CLLocationManager via semaphore
        let semaphore = DispatchSemaphore(value: 0)
        var resultLocation: CLLocation?
        var resultError: Error?

        let helper = OneShotLocationHelper(
            enableHighAccuracy: enableHighAccuracy,
            onResult: { loc, err in
                resultLocation = loc
                resultError = err
                semaphore.signal()
            }
        )

        runOnMain { helper.start() }

        let timeoutSeconds = timeout > 0 ? DispatchTime.now() + .milliseconds(Int(timeout)) : DispatchTime.distantFuture
        let waitResult = semaphore.wait(timeout: timeoutSeconds)

        helper.cancel()

        if waitResult == .timedOut {
            throw BGError.timeout
        }
        if let err = resultError {
            throw err
        }
        guard let clLoc = resultLocation else {
            throw BGError.timeout
        }
        return BGLocation.from(clLocation: clLoc)
    }

    // MARK: - Config

    public func getConfig() -> BGConfig {
        if let c = _config { return c }
        let c = BGConfig.merge(BGConfig(defaults: ()), with: ConfigDAO.shared.retrieve())
        _config = c
        return c
    }

    // MARK: - Log entries

    public func getLogEntries(_ limit: Int, fromId: Int, minLevel: String) -> [Any] {
        return LogReader().getEntries(limit: limit, fromId: fromId, minLevel: minLevel)
    }

    // MARK: - Sync

    public func forceSync() {
        guard getConfig().isSyncEnabled else { return }
        PostLocationTask.shared.sync()
    }

    public func clearSync() {
        LocationDAO.shared.deletePendingSyncLocations()
    }

    public func getPendingSyncCount() -> Int {
        return LocationDAO.shared.getLocationsForSyncCount()
    }

    // MARK: - Session

    public func startSession() {
        SessionDAO.shared.startSession()
    }

    public func getSessionLocations() -> [BGLocation] {
        return SessionDAO.shared.getLocations()
    }

    public func clearSession() {
        SessionDAO.shared.clearSession()
    }

    public func getSessionLocationsCount() -> Int {
        return SessionDAO.shared.getCount()
    }

    // MARK: - SOS

    public func triggerSOS(_ payload: [String: Any]? = nil) {
        var userInfo: [String: Any] = [:]
        if let loc = lastReceivedLocation {
            userInfo["location"] = loc
        }
        if let payload = payload {
            for (k, v) in payload { userInfo[k] = v }
        }
        NotificationCenter.default.post(name: .BGSOS, object: self, userInfo: userInfo)
    }

    // MARK: - Geofencing

    public func addGeofences(_ geofences: [[String: Any]]) {
        let parsed = geofences.compactMap { BGGeofence.from(dictionary: $0) }
        GeofenceManager.shared.add(parsed)
    }

    public func removeGeofences(_ ids: [String]?) {
        GeofenceManager.shared.remove(ids)
    }

    public func getGeofences() -> [[String: Any]] {
        GeofenceManager.shared.getAll().map { $0.toDictionary() }
    }

    private func onGeofenceTransition(id: String, transition: GeofenceTransition, location: BGLocation?) {
        let notifName: Notification.Name
        switch transition {
        case .enter: notifName = .BGGeofenceEnter
        case .exit:  notifName = .BGGeofenceExit
        case .dwell: notifName = .BGGeofenceDwell
        }
        var info: [String: Any] = ["id": id, "action": transition.rawValue]
        if let loc = location { info["location"] = loc }
        NotificationCenter.default.post(name: notifName, object: self, userInfo: info)
    }

    // MARK: - App lifecycle

    public func onAppTerminate() {
        let config = getConfig()
        if config.stopOnTerminate == true {
            try? stop()
        } else {
            locationProvider?.onTerminate()
        }
    }

    // MARK: - Static transform passthrough

    public static func setLocationTransform(_ transform: BGLocationTransform?) {
        PostLocationTask.setLocationTransform(transform)
    }

    public static var locationTransform: BGLocationTransform? {
        return PostLocationTask.locationTransform
    }

    // MARK: - Private: Heartbeat

    private func scheduleHeartbeat() {
        cancelHeartbeat()
        guard let interval = _config?.heartbeatInterval, interval > 0 else { return }
        let intervalSeconds = TimeInterval(interval) / 1000.0
        runOnMain {
            self.heartbeatTimer = Timer.scheduledTimer(
                timeInterval: intervalSeconds,
                target: self,
                selector: #selector(self.onHeartbeatTick(_:)),
                userInfo: nil,
                repeats: true
            )
        }
    }

    private func cancelHeartbeat() {
        heartbeatTimer?.invalidate()
        heartbeatTimer = nil
    }

    @objc private func onHeartbeatTick(_ timer: Timer) {
        var userInfo: [String: Any] = [:]
        if let loc = lastReceivedLocation {
            userInfo["location"] = loc
        }
        NotificationCenter.default.post(name: .BGHeartbeat, object: self, userInfo: userInfo)
    }

    // MARK: - Private: Sensor fusion

    private func configureSensorFusion() {
        guard let drivingEventsConfig = _config?.drivingEvents else {
            sensorFusion?.stop()
            sensorFusion = nil
            return
        }
        let enabled = (drivingEventsConfig["enabled"] as? Bool) ?? false
        guard enabled else {
            sensorFusion?.stop()
            sensorFusion = nil
            return
        }

        if sensorFusion == nil {
            sensorFusion = SensorFusionDetector()
        }
        let detector = sensorFusion!

        detector.enabled = true
        detector.listener = self

        if let v = (drivingEventsConfig["crashImpactG"] as? NSNumber)?.doubleValue {
            detector.crashImpactG = v
        }
        // Contract key is `sensorCrashCooldownMs` (Android maps the same). The old
        // `crashCooldownMs` lookup never matched, so the cooldown was stuck at default.
        if let v = (drivingEventsConfig["sensorCrashCooldownMs"] as? NSNumber)?.doubleValue {
            detector.crashCooldownMs = v
        }
        detector.sensorFusion = (drivingEventsConfig["sensorFusion"] as? Bool) ?? false
        if let v = (drivingEventsConfig["phoneUsageWindowMs"] as? NSNumber)?.doubleValue {
            detector.phoneUsageWindowMs = v
        }
        if let v = (drivingEventsConfig["phoneUsageCooldownMs"] as? NSNumber)?.doubleValue {
            detector.phoneUsageCooldownMs = v
        }

        detector.tripActive = drivingTripActive
        detector.lastLocation = lastReceivedLocation
    }

    // MARK: - Private: Provider factory

    private func getProvider(_ id: Int) throws -> LocationProvider {
        switch id {
        case BGLocationProvider.distanceFilter.rawValue:
            return DistanceFilterLocationProvider()
        case BGLocationProvider.activity.rawValue:
            return ActivityLocationProvider()
        case BGLocationProvider.raw.rawValue:
            return RawLocationProvider()
        default:
            throw BGError.serviceError(.configureError, "Unknown locationProvider id: \(id)")
        }
    }

    // MARK: - Private: Battery snapshot

    func attachBatterySnapshotTo(_ loc: BGLocation) {
        runOnMain {
            UIDevice.current.isBatteryMonitoringEnabled = true
            let raw = UIDevice.current.batteryLevel
            if raw >= 0 {
                loc.batteryLevel = Int(raw * 100)
            }
            let state = UIDevice.current.batteryState
            loc.isCharging = (state == .charging || state == .full)
        }
    }

    // MARK: - Private: bufferPendingEvent

    func bufferPendingEvent(_ type: String, extra: [String: Any]? = nil) {
        pendingDrivingEventsLock.lock()
        defer { pendingDrivingEventsLock.unlock() }
        guard pendingDrivingEvents.count < BGFacade.kPendingDrivingEventsMax else { return }
        var entry: [String: Any] = [
            "type": type,
            "time": Int64(Date().timeIntervalSince1970 * 1000)
        ]
        if let extra = extra {
            for (k, v) in extra { entry[k] = v }
        }
        pendingDrivingEvents.append(entry)
    }

    // MARK: - Private: runOnMain

    private func runOnMain(_ block: @escaping () -> Void) {
        if Thread.isMainThread {
            block()
        } else {
            DispatchQueue.main.sync { block() }
        }
    }
}

// MARK: - LocationProviderDelegate

extension BGFacade: LocationProviderDelegate {

    public func onAuthorizationChanged(_ status: BGAuthorizationStatus) {
        delegate?.onAuthorizationChanged(status)
    }

    public func onLocationChanged(_ location: BGLocation) {
        if let max = _config?.maxAcceptedAccuracy, max > 0,
           let acc = location.accuracy, acc > max { return }
        stationaryLocation = nil
        lastReceivedLocation = location
        sensorFusion?.lastLocation = location
        PostLocationTask.shared.add(location)
        // Resilient geofence dwell: fire DWELL even if the per-region Timer was
        // suspended/coalesced while the app was backgrounded.
        GeofenceManager.shared.evaluateDwell(now: location.time ?? Date(), location: location)
        delegate?.onLocationChanged(location)
    }

    public func onStationaryChanged(_ location: BGLocation) {
        if let max = _config?.maxAcceptedAccuracy, max > 0,
           let acc = location.accuracy, acc > max { return }
        stationaryLocation = location
        PostLocationTask.shared.add(location)
        delegate?.onStationaryChanged(location)
    }

    public func onLocationPause() { delegate?.onLocationPause() }
    public func onLocationResume() { delegate?.onLocationResume() }
    public func onActivityChanged(_ activity: BGActivity) { delegate?.onActivityChanged(activity) }
    public func onAbortRequested() { delegate?.onAbortRequested() }
    public func onHttpAuthorization() { delegate?.onHttpAuthorization() }
    public func onError(_ error: Error) { delegate?.onError(error) }
}

// MARK: - SensorFusionListener

extension BGFacade: SensorFusionListener {

    public func onCrash(impactG: Double, location: BGLocation?) {
        bufferPendingEvent("possibleCrash", extra: ["value": impactG, "source": "sensor"])
        var info: [String: Any] = ["value": impactG, "source": "sensor"]
        if let loc = location { info["location"] = loc }
        NotificationCenter.default.post(name: .BGPossibleCrash, object: self, userInfo: info)
    }

    public func onPhoneUsageWhileDriving(location: BGLocation?) {
        bufferPendingEvent("phoneUsageWhileDriving", extra: nil)
        // Tag the source so the plugin feeds the score for the sensor path only — the
        // GPS path already records inside DrivingEventsDetector.feed (avoids double-count).
        var info: [String: Any] = ["source": "sensor"]
        if let loc = location { info["location"] = loc }
        NotificationCenter.default.post(name: .BGPhoneUsageWhileDriving, object: self, userInfo: info)
    }
}

// MARK: - PostLocationTaskDelegate

extension BGFacade: PostLocationTaskDelegate {

    public func postLocationTaskRequestedAbortUpdates(_ task: PostLocationTask) {
        if let d = delegate {
            d.onAbortRequested()
        } else {
            try? stop()
        }
    }

    public func postLocationTaskHttpAuthorizationUpdates(_ task: PostLocationTask) {
        delegate?.onHttpAuthorization()
    }
}

// MARK: - OneShotLocationHelper (private)

private final class OneShotLocationHelper: NSObject, CLLocationManagerDelegate {

    private let manager = CLLocationManager()
    private let onResult: (CLLocation?, Error?) -> Void
    private var done = false

    init(enableHighAccuracy: Bool, onResult: @escaping (CLLocation?, Error?) -> Void) {
        self.onResult = onResult
        super.init()
        manager.delegate = self
        manager.desiredAccuracy = enableHighAccuracy
            ? kCLLocationAccuracyBest
            : kCLLocationAccuracyHundredMeters
    }

    func start() {
        let status: CLAuthorizationStatus
        if #available(iOS 14.0, *) {
            status = manager.authorizationStatus
        } else {
            status = CLLocationManager.authorizationStatus()
        }
        switch status {
        case .denied, .restricted:
            finish(nil, BGError.permissionDenied)
        case .notDetermined:
            manager.requestWhenInUseAuthorization()
        default:
            manager.requestLocation()
        }
    }

    func cancel() {
        manager.stopUpdatingLocation()
    }

    private func finish(_ location: CLLocation?, _ error: Error?) {
        guard !done else { return }
        done = true
        manager.stopUpdatingLocation()
        onResult(location, error)
    }

    // MARK: CLLocationManagerDelegate

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        finish(locations.last, nil)
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        let clError = error as? CLError
        if clError?.code == .denied {
            finish(nil, BGError.permissionDenied)
        } else {
            finish(nil, error)
        }
    }

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        switch status {
        case .authorizedAlways, .authorizedWhenInUse:
            manager.requestLocation()
        case .denied, .restricted:
            finish(nil, BGError.permissionDenied)
        default:
            break
        }
    }

    @available(iOS 14.0, *)
    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        locationManager(manager, didChangeAuthorization: manager.authorizationStatus)
    }
}
