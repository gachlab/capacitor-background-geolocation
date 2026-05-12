// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM

import Foundation
import Capacitor
import CoreLocation
import CoreMotion
import UIKit
import UserNotifications
import MAURBackgroundGeolocation

@objc(BackgroundGeolocationPlugin)
public class BackgroundGeolocationPlugin: CAPPlugin, CAPBridgedPlugin, MAURProviderDelegate {
    public let identifier = "BackgroundGeolocationPlugin"
    public let jsName = "BackgroundGeolocation"
    public let pluginMethods: [CAPPluginMethod] = [
        CAPPluginMethod(name: "configure", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "start", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stop", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getCurrentLocation", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getStationaryLocation", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getLocations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getValidLocations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getValidLocationsAndDelete", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getConfig", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deleteLocation", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deleteAllLocations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isLocationEnabled", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "showAppSettings", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "showLocationSettings", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openSettings", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "watchLocationMode", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopWatchingLocationMode", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getLogEntries", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkStatus", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getDiagnostics", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPluginVersion", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "switchMode", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startTask", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "endTask", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "forceSync", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearSync", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getPendingSyncCount", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startSession", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "clearSession", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getSessionLocations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getSessionLocationsCount", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "triggerSOS", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isIgnoringBatteryOptimizations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestIgnoreBatteryOptimizations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openBatterySettings", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "openAutoStartSettings", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getManufacturerHelp", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestBackgroundLocationPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestActivityRecognitionPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestNotificationPermission", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "requestPermissions", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeAllListeners", returnType: CAPPluginReturnPromise)
    ]

    private static let pluginVersion = "1.0.0"

    private var facade: MAURBackgroundGeolocationFacade?
    private var currentConfig: MAURConfig?
    private var permissionHelper: PermissionRequestHelper?
    private var lastLocationAt: Date?

    override public func load() {
        let f = MAURBackgroundGeolocationFacade()
        f.delegate = self
        facade = f
        NotificationCenter.default.addObserver(self, selector: #selector(onAppForeground), name: UIApplication.willEnterForegroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(onAppBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)

        // v3.5 Phase 4 — sync + heartbeat notifications.
        let nc = NotificationCenter.default
        nc.addObserver(self, selector: #selector(onSyncStartN(_:)),    name: NSNotification.Name(rawValue: MAURBackgroundSyncDidStartNotification),    object: nil)
        nc.addObserver(self, selector: #selector(onSyncSuccessN(_:)),  name: NSNotification.Name(rawValue: MAURBackgroundSyncDidSucceedNotification),  object: nil)
        nc.addObserver(self, selector: #selector(onSyncErrorN(_:)),    name: NSNotification.Name(rawValue: MAURBackgroundSyncDidFailNotification),     object: nil)
        nc.addObserver(self, selector: #selector(onSyncProgressN(_:)), name: NSNotification.Name(rawValue: MAURBackgroundSyncDidProgressNotification), object: nil)
        nc.addObserver(self, selector: #selector(onHeartbeatN(_:)),    name: NSNotification.Name(rawValue: MAURHeartbeatNotification),                 object: nil)

        // v4.0 Phase 6 — driver-insight notifications.
        nc.addObserver(self, selector: #selector(onTripStartN(_:)),      name: NSNotification.Name(rawValue: MAURTripStartNotification),      object: nil)
        nc.addObserver(self, selector: #selector(onTripEndN(_:)),        name: NSNotification.Name(rawValue: MAURTripEndNotification),        object: nil)
        nc.addObserver(self, selector: #selector(onMovingN(_:)),         name: NSNotification.Name(rawValue: MAURMovingNotification),         object: nil)
        nc.addObserver(self, selector: #selector(onStoppedN(_:)),        name: NSNotification.Name(rawValue: MAURStoppedNotification),        object: nil)
        nc.addObserver(self, selector: #selector(onSpeedingN(_:)),       name: NSNotification.Name(rawValue: MAURSpeedingNotification),       object: nil)
        nc.addObserver(self, selector: #selector(onProviderChangeN(_:)), name: NSNotification.Name(rawValue: MAURProviderChangeNotification), object: nil)
        nc.addObserver(self, selector: #selector(onSOSN(_:)),            name: NSNotification.Name(rawValue: MAURSOSNotification),            object: nil)

        // v4.1 GPS-derived sensor-like events.
        nc.addObserver(self, selector: #selector(onHardBrakeN(_:)),         name: NSNotification.Name(rawValue: MAURHardBrakeNotification),         object: nil)
        nc.addObserver(self, selector: #selector(onRapidAccelerationN(_:)), name: NSNotification.Name(rawValue: MAURRapidAccelerationNotification), object: nil)
        nc.addObserver(self, selector: #selector(onSharpTurnN(_:)),         name: NSNotification.Name(rawValue: MAURSharpTurnNotification),         object: nil)
        nc.addObserver(self, selector: #selector(onPossibleCrashN(_:)),     name: NSNotification.Name(rawValue: MAURPossibleCrashNotification),     object: nil)

        // v4.2 sensor fusion.
        nc.addObserver(self, selector: #selector(onPhoneUsageWhileDrivingN(_:)), name: NSNotification.Name(rawValue: MAURPhoneUsageWhileDrivingNotification), object: nil)
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - App lifecycle bridge

    @objc private func onAppForeground() {
        facade?.switchMode(MAURForegroundMode)
        notifyListeners("foreground", data: [:])
    }

    @objc private func onAppBackground() {
        facade?.switchMode(MAURBackgroundMode)
        notifyListeners("background", data: [:])
    }

    // MARK: - Bridge methods

    @objc func configure(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let opts = call.options ?? [:]
        let cfg = MAURConfig.fromDictionary(opts)
        currentConfig = cfg
        do {
            try facade.configure(cfg)
            call.resolve()
        } catch {
            let nsErr = error as NSError
            call.reject(nsErr.localizedDescription, String(nsErr.code))
        }
    }

    @objc func start(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        do {
            try facade.start()
            // `start` event is emitted by MAURProviderDelegate.onLocationResume.
            call.resolve()
        } catch {
            let nsErr = error as NSError
            call.reject(nsErr.localizedDescription, String(nsErr.code))
        }
    }

    @objc func stop(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        do {
            try facade.stop()
            // `stop` event is emitted by MAURProviderDelegate.onLocationPause.
            call.resolve()
        } catch {
            let nsErr = error as NSError
            call.reject(nsErr.localizedDescription, String(nsErr.code))
        }
    }

    @objc func getCurrentLocation(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let timeout = call.getInt("timeout") ?? Int(Int32.max)
        let maximumAge = call.getInt("maximumAge").map { Int64($0) } ?? Int64.max
        let highAccuracy = call.getBool("enableHighAccuracy") ?? false
        do {
            let location = try facade.getCurrentLocation(Int32(timeout), maximumAge: maximumAge, enableHighAccuracy: highAccuracy)
            call.resolve(location.toDictionary() as? [String: Any] ?? [:])
        } catch {
            let nsErr = error as NSError
            call.reject(nsErr.localizedDescription, String(nsErr.code))
        }
    }

    @objc func getStationaryLocation(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        if let loc = facade.getStationaryLocation() {
            call.resolve(loc.toDictionary() as? [String: Any] ?? [:])
        } else {
            // TS contract is `Location | null` — resolve with no payload so the
            // JS bridge surfaces `null`, matching Android's `call.resolve()`.
            call.resolve()
        }
    }

    @objc func getLocations(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let locations = facade.getLocations() as? [MAURLocation] ?? []
        let arr = locations.compactMap { $0.toDictionaryWithId() as? [String: Any] }
        call.resolve(["locations": arr])
    }

    @objc func getValidLocations(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let locations = facade.getValidLocations() as? [MAURLocation] ?? []
        let arr = locations.compactMap { $0.toDictionaryWithId() as? [String: Any] }
        call.resolve(["locations": arr])
    }

    @objc func getValidLocationsAndDelete(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let locations = facade.getValidLocationsAndDelete() as? [MAURLocation] ?? []
        let arr = locations.compactMap { $0.toDictionaryWithId() as? [String: Any] }
        call.resolve(["locations": arr])
    }

    @objc func getConfig(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let cfg = facade.getConfig()
        let dict = cfg?.toDictionary() as? [String: Any] ?? [:]
        call.resolve(dict)
    }

    @objc func deleteLocation(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        guard let locationId = call.getInt("locationId") else {
            call.reject("locationId required"); return
        }
        do {
            try facade.delete(NSNumber(value: locationId))
            call.resolve()
        } catch {
            let nsErr = error as NSError
            call.reject(nsErr.localizedDescription, String(nsErr.code))
        }
    }

    @objc func deleteAllLocations(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        do {
            try facade.deleteAllLocations()
            call.resolve()
        } catch {
            let nsErr = error as NSError
            call.reject(nsErr.localizedDescription, String(nsErr.code))
        }
    }

    @objc func isLocationEnabled(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        call.resolve(["enabled": facade.locationServicesEnabled()])
    }

    @objc func showAppSettings(_ call: CAPPluginCall) {
        facade?.showAppSettings()
        call.resolve()
    }

    @objc func showLocationSettings(_ call: CAPPluginCall) {
        facade?.showLocationSettings()
        call.resolve()
    }

    @objc func openSettings(_ call: CAPPluginCall) {
        // Alias of showAppSettings — keeps parity with the cross-platform TS contract.
        facade?.showAppSettings()
        call.resolve()
    }

    @objc func watchLocationMode(_ call: CAPPluginCall) {
        // iOS has no separate "mode watcher". Permission/status changes flow through
        // onAuthorizationChanged -> "authorization" event. This is a no-op resolve.
        call.resolve()
    }

    @objc func stopWatchingLocationMode(_ call: CAPPluginCall) {
        call.resolve()
    }

    @objc func getLogEntries(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let limit = call.getInt("limit") ?? 0
        let fromId = call.getInt("fromId") ?? 0
        let minLevel = call.getString("minLevel") ?? "DEBUG"
        let logs = facade.getLogEntries(limit, fromLogEntryId: fromId, minLogLevelFromString: minLevel) as? [Any] ?? []
        call.resolve(["entries": logs])
    }

    @objc func checkStatus(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let isRunning = facade.isStarted()
        let enabled = facade.locationServicesEnabled()
        let auth = facade.authorizationStatus()
        call.resolve([
            "isRunning": isRunning,
            "locationServicesEnabled": enabled,
            "authorization": auth.rawValue
        ])
    }

    @objc func getDiagnostics(_ call: CAPPluginCall) {
        var d: [String: Any] = [:]
        d["isRunning"] = facade?.isStarted() ?? false
        d["locationServicesEnabled"] = facade?.locationServicesEnabled() ?? false
        // iOS has no boot-time auto-start equivalent. Expose for cross-platform shape.
        d["startOnBoot"] = false

        // Pending sync (best-effort).
        let pending = MAURSQLiteLocationDAO.sharedInstance().getLocationsForSyncCount()
        d["pendingSyncCount"] = pending?.intValue ?? 0

        if let last = lastLocationAt {
            d["lastLocationAt"] = Int64(last.timeIntervalSince1970 * 1000)
        } else {
            d["lastLocationAt"] = NSNull()
        }

        // Precise location (iOS 14+).
        if #available(iOS 14.0, *) {
            let lm = CLLocationManager()
            d["preciseLocationEnabled"] = (lm.accuracyAuthorization == .fullAccuracy)
        } else {
            d["preciseLocationEnabled"] = true
        }

        d["backgroundRefreshStatus"] = Self.backgroundRefreshText()
        d["lowPowerModeEnabled"] = ProcessInfo.processInfo.isLowPowerModeEnabled
        d["motionPermissionStatus"] = Self.motionPermissionText()
        d["authorizationStatusText"] = Self.authorizationStatusText(Self.currentAuthorizationStatus())

        call.resolve(d)
    }

    @objc func getPluginVersion(_ call: CAPPluginCall) {
        call.resolve(["version": Self.pluginVersion])
    }

    @objc func switchMode(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let raw = call.getInt("mode") ?? Int(MAURForegroundMode.rawValue)
        let mode: MAUROperationalMode = (raw == Int(MAURBackgroundMode.rawValue)) ? MAURBackgroundMode : MAURForegroundMode
        facade.switchMode(mode)
        call.resolve()
    }

    @objc func startTask(_ call: CAPPluginCall) {
        let key = MAURBackgroundTaskManager.sharedTasks().beginTask()
        call.resolve(["taskKey": key])
    }

    @objc func endTask(_ call: CAPPluginCall) {
        let key = call.getInt("taskKey") ?? 0
        MAURBackgroundTaskManager.sharedTasks().endTask(withKey: UInt(key))
        call.resolve()
    }

    @objc func forceSync(_ call: CAPPluginCall) {
        facade?.forceSync()
        call.resolve()
    }

    @objc func clearSync(_ call: CAPPluginCall) {
        facade?.clearSync()
        call.resolve()
    }

    @objc func getPendingSyncCount(_ call: CAPPluginCall) {
        let count = facade?.getPendingSyncCount() ?? 0
        call.resolve(["count": count])
    }

    @objc func startSession(_ call: CAPPluginCall) {
        facade?.startSession()
        call.resolve()
    }

    @objc func clearSession(_ call: CAPPluginCall) {
        facade?.clearSession()
        call.resolve()
    }

    @objc func getSessionLocations(_ call: CAPPluginCall) {
        let locations = facade?.getSessionLocations() as? [MAURLocation] ?? []
        let arr = locations.compactMap { $0.toDictionaryWithId() as? [String: Any] }
        call.resolve(["locations": arr])
    }

    @objc func getSessionLocationsCount(_ call: CAPPluginCall) {
        let count = facade?.getSessionLocationsCount() ?? 0
        call.resolve(["count": count])
    }

    @objc func triggerSOS(_ call: CAPPluginCall) {
        // Facade attaches the latest location and posts MAURSOSNotification, which we
        // forward via onSOSN(_:) below — keeps the payload-merge logic in a single place.
        let payload = call.getObject("payload")
        facade?.triggerSOS(payload)
        call.resolve()
    }

    // MARK: - Battery / OEM helpers (iOS no-ops)

    @objc func isIgnoringBatteryOptimizations(_ call: CAPPluginCall) {
        call.resolve(["whitelisted": true])
    }

    @objc func requestIgnoreBatteryOptimizations(_ call: CAPPluginCall) {
        call.resolve(["whitelisted": true])
    }

    @objc func openBatterySettings(_ call: CAPPluginCall) {
        // iOS has no Battery Settings deeplink. Best-effort: app settings.
        if let url = URL(string: UIApplication.openSettingsURLString), UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        }
        call.resolve()
    }

    @objc func openAutoStartSettings(_ call: CAPPluginCall) {
        // No per-OEM auto-start screen on iOS. Report opened=false to let JS render help.
        call.resolve([
            "opened": false,
            "manufacturer": "apple",
            "screen": ""
        ])
    }

    @objc func getManufacturerHelp(_ call: CAPPluginCall) {
        // Consumer apps will render their own copy. Return an empty list to keep the
        // cross-platform shape stable.
        call.resolve([
            "manufacturer": "apple",
            "steps": [] as [String]
        ])
    }

    // MARK: - Runtime permission helpers (cross-platform shims)

    @objc func requestBackgroundLocationPermission(_ call: CAPPluginCall) {
        // iOS folds background into "Always" — there's no separate gate.
        call.resolve(["granted": true, "notRequired": true])
    }

    @objc func requestActivityRecognitionPermission(_ call: CAPPluginCall) {
        // Probe motion availability so the consumer sees a faithful "granted" flag.
        var granted = true
        if #available(iOS 11.0, *) {
            if CMMotionActivityManager.isActivityAvailable() {
                switch CMMotionActivityManager.authorizationStatus() {
                case .authorized:    granted = true
                case .denied,
                     .restricted:    granted = false
                case .notDetermined: granted = true
                @unknown default:    granted = true
                }
            }
        }
        call.resolve(["granted": granted, "notRequired": true])
    }

    @objc func requestNotificationPermission(_ call: CAPPluginCall) {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound]) { granted, _ in
            call.resolve(["granted": granted, "notRequired": false])
        }
    }

    @objc override public func removeAllListeners(_ call: CAPPluginCall) {
        super.removeAllListeners(call)
    }

    // MARK: - Permissions (Capacitor standard)

    @objc override public func checkPermissions(_ call: CAPPluginCall) {
        let status = Self.currentAuthorizationStatus()
        call.resolve(["location": Self.permissionState(for: status)])
    }

    @objc override public func requestPermissions(_ call: CAPPluginCall) {
        let status = Self.currentAuthorizationStatus()
        if status != .notDetermined {
            call.resolve(["location": Self.permissionState(for: status)])
            return
        }
        let helper = PermissionRequestHelper { [weak self] result in
            call.resolve(["location": BackgroundGeolocationPlugin.permissionState(for: result)])
            self?.permissionHelper = nil
        }
        permissionHelper = helper
        helper.start()
    }

    private static func currentAuthorizationStatus() -> CLAuthorizationStatus {
        if #available(iOS 14.0, *) {
            return CLLocationManager().authorizationStatus
        }
        return CLLocationManager.authorizationStatus()
    }

    private static func permissionState(for status: CLAuthorizationStatus) -> String {
        switch status {
        case .notDetermined: return "prompt"
        case .denied, .restricted: return "denied"
        case .authorizedAlways, .authorizedWhenInUse: return "granted"
        @unknown default: return "prompt"
        }
    }

    private static func authorizationStatusText(_ status: CLAuthorizationStatus) -> String {
        switch status {
        case .notDetermined:       return "notDetermined"
        case .restricted:          return "restricted"
        case .denied:              return "denied"
        case .authorizedAlways:    return "authorizedAlways"
        case .authorizedWhenInUse: return "authorizedWhenInUse"
        @unknown default:          return "unknown"
        }
    }

    private static func backgroundRefreshText() -> String {
        switch UIApplication.shared.backgroundRefreshStatus {
        case .available:  return "available"
        case .denied:     return "denied"
        case .restricted: return "restricted"
        @unknown default: return "unknown"
        }
    }

    private static func motionPermissionText() -> String {
        if !CMMotionActivityManager.isActivityAvailable() { return "restricted" }
        if #available(iOS 11.0, *) {
            switch CMMotionActivityManager.authorizationStatus() {
            case .notDetermined: return "notDetermined"
            case .restricted:    return "restricted"
            case .denied:        return "denied"
            case .authorized:    return "authorized"
            @unknown default:    return "notDetermined"
            }
        }
        return "notDetermined"
    }

    // MARK: - MAURProviderDelegate

    public func onAuthorizationChanged(_ authStatus: MAURLocationAuthorizationStatus) {
        notifyListeners("authorization", data: ["status": authStatus.rawValue])
    }

    public func onLocationChanged(_ location: MAURLocation!) {
        lastLocationAt = Date()
        guard let dict = location.toDictionaryWithId() as? [String: Any] else { return }
        notifyListeners("location", data: dict)
    }

    public func onStationaryChanged(_ location: MAURLocation!) {
        guard let dict = location.toDictionaryWithId() as? [String: Any] else { return }
        notifyListeners("stationary", data: dict)
    }

    public func onLocationPause() {
        notifyListeners("stop", data: [:])
    }

    public func onLocationResume() {
        notifyListeners("start", data: [:])
    }

    public func onActivityChanged(_ activity: MAURActivity!) {
        guard let dict = activity.toDictionary() as? [String: Any] else { return }
        notifyListeners("activity", data: dict)
    }

    public func onAbortRequested() {
        notifyListeners("abort_requested", data: [:])
    }

    public func onHttpAuthorization() {
        notifyListeners("http_authorization", data: [:])
    }

    public func onError(_ error: Error!) {
        let nsErr = error as NSError?
        notifyListeners("error", data: [
            "code": nsErr?.code ?? -1,
            "message": nsErr?.localizedDescription ?? "unknown error"
        ])
    }

    // MARK: - Notification observers (sync / heartbeat / driving)

    @objc private func onSyncStartN(_ note: Notification) {
        notifyListeners("syncStart", data: [:])
    }

    @objc private func onSyncSuccessN(_ note: Notification) {
        let sent = (note.userInfo?["sent"] as? NSNumber)?.intValue ?? 0
        notifyListeners("syncSuccess", data: ["sent": sent])
    }

    @objc private func onSyncErrorN(_ note: Notification) {
        let status = (note.userInfo?["httpStatus"] as? NSNumber)?.intValue ?? 0
        let msg = (note.userInfo?["message"] as? String) ?? ""
        notifyListeners("syncError", data: ["httpStatus": status, "message": msg])
    }

    @objc private func onSyncProgressN(_ note: Notification) {
        let progress = (note.userInfo?["progress"] as? NSNumber)?.intValue ?? 0
        notifyListeners("syncProgress", data: ["progress": progress])
    }

    @objc private func onHeartbeatN(_ note: Notification) {
        if let loc = note.userInfo?["location"] as? MAURLocation,
           let dict = loc.toDictionaryWithId() as? [String: Any] {
            notifyListeners("heartbeat", data: dict)
        } else {
            notifyListeners("heartbeat", data: [:])
        }
    }

    @objc private func onTripStartN(_ note: Notification) {
        if let loc = note.userInfo?["location"] as? MAURLocation,
           let dict = loc.toDictionaryWithId() as? [String: Any] {
            notifyListeners("tripStart", data: dict)
        } else {
            notifyListeners("tripStart", data: [:])
        }
    }

    @objc private func onTripEndN(_ note: Notification) {
        var p: [String: Any] = [:]
        if let loc = note.userInfo?["location"] as? MAURLocation,
           let dict = loc.toDictionaryWithId() as? [String: Any] {
            p["location"] = dict
        } else {
            p["location"] = NSNull()
        }
        p["distance"]   = (note.userInfo?["distance"]   as? NSNumber)?.doubleValue ?? 0
        p["durationMs"] = (note.userInfo?["durationMs"] as? NSNumber)?.int64Value ?? 0
        notifyListeners("tripEnd", data: p)
    }

    @objc private func onMovingN(_ note: Notification) {
        if let loc = note.userInfo?["location"] as? MAURLocation,
           let dict = loc.toDictionaryWithId() as? [String: Any] {
            notifyListeners("moving", data: dict)
        } else {
            notifyListeners("moving", data: [:])
        }
    }

    @objc private func onStoppedN(_ note: Notification) {
        if let loc = note.userInfo?["location"] as? MAURLocation,
           let dict = loc.toDictionaryWithId() as? [String: Any] {
            notifyListeners("stopped", data: dict)
        } else {
            notifyListeners("stopped", data: [:])
        }
    }

    @objc private func onSpeedingN(_ note: Notification) {
        var p: [String: Any] = [:]
        if let loc = note.userInfo?["location"] as? MAURLocation,
           let dict = loc.toDictionaryWithId() as? [String: Any] {
            p["location"] = dict
        } else {
            p["location"] = NSNull()
        }
        p["speedKmh"] = (note.userInfo?["speedKmh"] as? NSNumber)?.doubleValue ?? 0
        p["limitKmh"] = (note.userInfo?["limitKmh"] as? NSNumber)?.doubleValue ?? 0
        notifyListeners("speeding", data: p)
    }

    @objc private func onProviderChangeN(_ note: Notification) {
        let provider = (note.userInfo?["provider"] as? String) ?? ""
        notifyListeners("providerChange", data: ["provider": provider])
    }

    @objc private func onSOSN(_ note: Notification) {
        var p: [String: Any] = [:]
        if let userPayload = note.userInfo?["payload"] as? [String: Any] {
            for (k, v) in userPayload { p[k] = v }
        }
        if let loc = note.userInfo?["location"] as? MAURLocation,
           let dict = loc.toDictionaryWithId() as? [String: Any] {
            p["location"] = dict
        } else if p["location"] == nil {
            p["location"] = NSNull()
        }
        notifyListeners("sos", data: p)
    }

    private func emitDrivingEvent(_ name: String, note: Notification) {
        var p: [String: Any] = [:]
        if let loc = note.userInfo?["location"] as? MAURLocation,
           let dict = loc.toDictionaryWithId() as? [String: Any] {
            p["location"] = dict
        } else {
            p["location"] = NSNull()
        }
        p["value"] = (note.userInfo?["value"] as? NSNumber)?.doubleValue ?? 0
        if let source = note.userInfo?["source"] as? String { p["source"] = source }
        notifyListeners(name, data: p)
    }

    @objc private func onHardBrakeN(_ note: Notification)         { emitDrivingEvent("hardBrake",         note: note) }
    @objc private func onRapidAccelerationN(_ note: Notification) { emitDrivingEvent("rapidAcceleration", note: note) }
    @objc private func onSharpTurnN(_ note: Notification)         { emitDrivingEvent("sharpTurn",         note: note) }
    @objc private func onPossibleCrashN(_ note: Notification)     { emitDrivingEvent("possibleCrash",     note: note) }

    @objc private func onPhoneUsageWhileDrivingN(_ note: Notification) {
        if let loc = note.userInfo?["location"] as? MAURLocation,
           let dict = loc.toDictionaryWithId() as? [String: Any] {
            notifyListeners("phoneUsageWhileDriving", data: dict)
        } else {
            notifyListeners("phoneUsageWhileDriving", data: [:])
        }
    }
}

// MARK: - Permission helper

/// Wraps `CLLocationManager` for a single permission prompt.
private final class PermissionRequestHelper: NSObject, CLLocationManagerDelegate {
    private let manager = CLLocationManager()
    private let completion: (CLAuthorizationStatus) -> Void
    private var done = false

    init(completion: @escaping (CLAuthorizationStatus) -> Void) {
        self.completion = completion
        super.init()
        manager.delegate = self
    }

    func start() {
        manager.requestWhenInUseAuthorization()
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        finish(with: manager.authorizationStatus)
    }

    // iOS 13 fallback (kept for SDK completeness; deployment target is 14+).
    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        finish(with: status)
    }

    private func finish(with status: CLAuthorizationStatus) {
        if status == .notDetermined || done { return }
        done = true
        completion(status)
    }
}
