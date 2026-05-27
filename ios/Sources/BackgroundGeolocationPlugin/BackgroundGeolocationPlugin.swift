// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import Capacitor
import CoreLocation
import CoreMotion
import UIKit
import UserNotifications
import BackgroundGeolocationCore

@objc(BackgroundGeolocationPlugin)
public class BackgroundGeolocationPlugin: CAPPlugin, CAPBridgedPlugin, LocationProviderDelegate, DrivingEventsDetectorDelegate {
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
        CAPPluginMethod(name: "getBackgroundKillReason", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "addGeofences", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeGeofences", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getGeofences", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeAllListeners", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getTripScore", returnType: CAPPluginReturnPromise)
    ]

    private static let pluginVersion = "1.5.0"

    private var facade: BGFacade?
    private var currentConfig: BGConfig?
    private var permissionHelper: PermissionRequestHelper?
    private var lastLocationAt: Date?
    private let drivingDetector = DrivingEventsDetector()
    private var lastBGLocation: BGLocation?
    private var prevDLLoc: (lat: Double, lon: Double, time: Date)?
    private var lastTripScore: TripScore?
    private var prioritySyncManager: PrioritySyncManager?

    override public func load() {
        let f = BGFacade()
        f.delegate = self
        facade = f
        drivingDetector.delegate = self
        NotificationCenter.default.addObserver(self, selector: #selector(onAppForeground), name: UIApplication.willEnterForegroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(onAppBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)

        let nc = NotificationCenter.default
        nc.addObserver(self, selector: #selector(onSyncStartN(_:)),    name: .BGBackgroundSyncDidStart,    object: nil)
        nc.addObserver(self, selector: #selector(onSyncSuccessN(_:)),  name: .BGBackgroundSyncDidSucceed,  object: nil)
        nc.addObserver(self, selector: #selector(onSyncErrorN(_:)),    name: .BGBackgroundSyncDidFail,     object: nil)
        nc.addObserver(self, selector: #selector(onSyncProgressN(_:)), name: .BGBackgroundSyncDidProgress, object: nil)
        nc.addObserver(self, selector: #selector(onHeartbeatN(_:)),    name: .BGHeartbeat,                 object: nil)

        nc.addObserver(self, selector: #selector(onTripStartN(_:)),      name: .BGTripStart,      object: nil)
        nc.addObserver(self, selector: #selector(onTripEndN(_:)),        name: .BGTripEnd,        object: nil)
        nc.addObserver(self, selector: #selector(onMovingN(_:)),         name: .BGMoving,         object: nil)
        nc.addObserver(self, selector: #selector(onStoppedN(_:)),        name: .BGStopped,        object: nil)
        nc.addObserver(self, selector: #selector(onSpeedingN(_:)),       name: .BGSpeeding,       object: nil)
        nc.addObserver(self, selector: #selector(onProviderChangeN(_:)), name: .BGProviderChange, object: nil)
        nc.addObserver(self, selector: #selector(onSOSN(_:)),            name: .BGSOS,            object: nil)

        nc.addObserver(self, selector: #selector(onHardBrakeN(_:)),         name: .BGHardBrake,         object: nil)
        nc.addObserver(self, selector: #selector(onRapidAccelerationN(_:)), name: .BGRapidAcceleration, object: nil)
        nc.addObserver(self, selector: #selector(onSharpTurnN(_:)),         name: .BGSharpTurn,         object: nil)
        nc.addObserver(self, selector: #selector(onPossibleCrashN(_:)),     name: .BGPossibleCrash,     object: nil)

        nc.addObserver(self, selector: #selector(onPhoneUsageWhileDrivingN(_:)), name: .BGPhoneUsageWhileDriving, object: nil)
        nc.addObserver(self, selector: #selector(onFallbackActivatedN(_:)), name: .BGFallbackActivated, object: nil)
        nc.addObserver(self, selector: #selector(onIdleStartN(_:)), name: .BGIdleStart, object: nil)
        nc.addObserver(self, selector: #selector(onIdleEndN(_:)),   name: .BGIdleEnd,   object: nil)
        nc.addObserver(self, selector: #selector(onGeofenceN(_:)), name: .BGGeofenceEnter, object: nil)
        nc.addObserver(self, selector: #selector(onGeofenceN(_:)), name: .BGGeofenceExit,  object: nil)
        nc.addObserver(self, selector: #selector(onGeofenceN(_:)), name: .BGGeofenceDwell, object: nil)

        nc.addObserver(self, selector: #selector(onPrioritySyncSuccessN(_:)), name: .BGPrioritySyncSuccess, object: nil)
        nc.addObserver(self, selector: #selector(onPrioritySyncFailedN(_:)),  name: .BGPrioritySyncFailed,  object: nil)
    }

    deinit {
        NotificationCenter.default.removeObserver(self)
    }

    // MARK: - App lifecycle bridge

    @objc private func onAppForeground() {
        facade?.`switch`(.foreground)
        notifyListeners("foreground", data: [:])
    }

    @objc private func onAppBackground() {
        facade?.`switch`(.background)
        notifyListeners("background", data: [:])
    }

    // MARK: - Bridge methods

    @objc func configure(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let opts: [String: Any] = call.options as? [String: Any] ?? [:]
        let cfg = BGConfig.from(dictionary: opts)
        currentConfig = cfg
        configureDrivingDetector(from: opts)
        prioritySyncManager = PrioritySyncManager(config: cfg)
        do {
            try facade.configure(cfg)
            call.resolve()
        } catch {
            let nsErr = error as NSError
            call.reject(nsErr.localizedDescription, String(nsErr.code))
        }
    }

    private func configureDrivingDetector(from opts: [String: Any]) {
        guard let de = opts["drivingEvents"] as? [String: Any] else {
            drivingDetector.enabled = false
            drivingDetector.reset()
            return
        }
        drivingDetector.enabled = (de["enabled"] as? Bool) ?? false
        if let v = (de["speedLimit"]         as? NSNumber)?.doubleValue { drivingDetector.speedLimitKmh      = v }
        if let v = (de["minMovingSpeed"]     as? NSNumber)?.doubleValue { drivingDetector.minMovingSpeedMps  = v }
        if let v = (de["stoppedDuration"]    as? NSNumber)?.doubleValue { drivingDetector.stoppedDurationSec = v }
        if let v = (de["minTripSpeed"]       as? NSNumber)?.doubleValue { drivingDetector.minTripSpeedMps    = v }
        if let v = (de["minTripDuration"]    as? NSNumber)?.doubleValue { drivingDetector.minTripDurationSec = v }
        if let v = (de["hardBrakeMps2"]      as? NSNumber)?.doubleValue { drivingDetector.hardBrakeMps2      = v }
        if let v = (de["rapidAccelMps2"]     as? NSNumber)?.doubleValue { drivingDetector.rapidAccelMps2     = v }
        if let v = (de["sharpTurnDegPerSec"] as? NSNumber)?.doubleValue { drivingDetector.sharpTurnDegPerSec = v }
        if let v = (de["crashImpactKmh"]     as? NSNumber)?.doubleValue { drivingDetector.crashImpactKmh     = v }
        if let v = (de["crashWindowSec"]     as? NSNumber)?.doubleValue { drivingDetector.crashWindowSec     = v }
        if let v = (de["crashConfirmWindowMs"] as? NSNumber)?.doubleValue { drivingDetector.crashConfirmWindowSec = v / 1000.0 }
        if let v = de["sensorFusion"]        as? Bool                    { drivingDetector.sensorFusion          = v }
        if let v = (de["phoneUsageWindowMs"]  as? NSNumber)?.doubleValue { drivingDetector.phoneUsageWindowSec   = v / 1000.0 }
        if let v = (de["phoneUsageCooldownMs"] as? NSNumber)?.doubleValue { drivingDetector.phoneUsageCooldownSec = v / 1000.0 }
        // v1.4 idle + scoring
        if let v = (de["idleThresholdMs"]    as? NSNumber)?.doubleValue { drivingDetector.idleThresholdSec    = v / 1000.0 }
        if let v = (de["idleEndThresholdMs"] as? NSNumber)?.doubleValue { drivingDetector.idleEndThresholdSec = v / 1000.0 }
        if let sw = de["scoring"] as? [String: Any] {
            var weights = ScoringWeights()
            if let v = (sw["speedingWeight"]    as? NSNumber)?.intValue { weights.speeding    = v }
            if let v = (sw["hardBrakingWeight"] as? NSNumber)?.intValue { weights.hardBraking = v }
            if let v = (sw["rapidAccelWeight"]  as? NSNumber)?.intValue { weights.rapidAccel  = v }
            if let v = (sw["sharpTurnWeight"]   as? NSNumber)?.intValue { weights.sharpTurn   = v }
            if let v = (sw["phoneUsageWeight"]  as? NSNumber)?.intValue { weights.phoneUsage  = v }
            drivingDetector.scoringWeights = weights.isValid ? weights : nil
        } else {
            drivingDetector.scoringWeights = nil
        }
        drivingDetector.reset()
    }

    @objc func start(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        do {
            try facade.start()
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
            call.resolve()
        } catch {
            let nsErr = error as NSError
            call.reject(nsErr.localizedDescription, String(nsErr.code))
        }
    }

    @objc func getCurrentLocation(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let timeout = call.getInt("timeout") ?? Int(Int32.max)
        let maximumAge = call.getInt("maximumAge") ?? Int.max
        let highAccuracy = call.getBool("enableHighAccuracy") ?? false
        do {
            let location = try facade.getCurrentLocation(
                timeout: Int32(timeout), maximumAge: maximumAge, enableHighAccuracy: highAccuracy)
            call.resolve(location.toDictionary())
        } catch {
            let nsErr = error as NSError
            call.reject(nsErr.localizedDescription, String(nsErr.code))
        }
    }

    @objc func getStationaryLocation(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        if let loc = facade.getStationaryLocation() {
            call.resolve(loc.toDictionary())
        } else {
            call.resolve()
        }
    }

    @objc func getLocations(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let arr = facade.getLocations().map { $0.toDictionaryWithId() }
        call.resolve(["locations": arr])
    }

    @objc func getValidLocations(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let arr = facade.getValidLocations().map { $0.toDictionaryWithId() }
        call.resolve(["locations": arr])
    }

    @objc func getValidLocationsAndDelete(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let arr = facade.getValidLocationsAndDelete().map { $0.toDictionaryWithId() }
        call.resolve(["locations": arr])
    }

    @objc func getConfig(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        call.resolve(facade.getConfig().toDictionary())
    }

    @objc func deleteLocation(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        guard let locationId = call.getInt("locationId") else {
            call.reject("locationId required"); return
        }
        do {
            try facade.deleteLocation(NSNumber(value: locationId))
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
        facade?.showAppSettings()
        call.resolve()
    }

    @objc func watchLocationMode(_ call: CAPPluginCall) {
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
        let logs = facade.getLogEntries(limit, fromId: fromId, minLevel: minLevel)
        call.resolve(["entries": logs])
    }

    @objc func checkStatus(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        call.resolve([
            "isRunning": facade.isStarted(),
            "locationServicesEnabled": facade.locationServicesEnabled(),
            "authorization": facade.authorizationStatus().rawValue
        ])
    }

    @objc func getDiagnostics(_ call: CAPPluginCall) {
        var d: [String: Any] = [:]
        d["isRunning"] = facade?.isStarted() ?? false
        d["locationServicesEnabled"] = facade?.locationServicesEnabled() ?? false
        d["startOnBoot"] = false
        d["pendingSyncCount"] = facade?.getPendingSyncCount() ?? 0

        if let last = lastLocationAt {
            d["lastLocationAt"] = Int64(last.timeIntervalSince1970 * 1000)
        } else {
            d["lastLocationAt"] = NSNull()
        }

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
        let raw = call.getInt("mode") ?? 1
        let mode: BGOperationalMode = raw == 0 ? .background : .foreground
        facade.`switch`(mode)
        call.resolve()
    }

    @objc func startTask(_ call: CAPPluginCall) {
        let key = BGBackgroundTaskManager.shared.beginTask()
        call.resolve(["taskKey": key])
    }

    @objc func endTask(_ call: CAPPluginCall) {
        let key = UInt(call.getInt("taskKey") ?? 0)
        BGBackgroundTaskManager.shared.endTask(key: key)
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
        let arr = (facade?.getSessionLocations() ?? []).map { $0.toDictionaryWithId() }
        call.resolve(["locations": arr])
    }

    @objc func getSessionLocationsCount(_ call: CAPPluginCall) {
        let count = facade?.getSessionLocationsCount() ?? 0
        call.resolve(["count": count])
    }

    @objc func triggerSOS(_ call: CAPPluginCall) {
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
        if let url = URL(string: UIApplication.openSettingsURLString), UIApplication.shared.canOpenURL(url) {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        }
        call.resolve()
    }

    @objc func openAutoStartSettings(_ call: CAPPluginCall) {
        call.resolve(["opened": false, "manufacturer": "apple", "screen": ""])
    }

    @objc func getManufacturerHelp(_ call: CAPPluginCall) {
        call.resolve(["manufacturer": "apple", "steps": [] as [String]])
    }

    // MARK: - Runtime permission helpers

    @objc func requestBackgroundLocationPermission(_ call: CAPPluginCall) {
        call.resolve(["granted": true, "notRequired": true])
    }

    @objc func requestActivityRecognitionPermission(_ call: CAPPluginCall) {
        var granted = true
        if #available(iOS 11.0, *) {
            if CMMotionActivityManager.isActivityAvailable() {
                switch CMMotionActivityManager.authorizationStatus() {
                case .authorized:    granted = true
                case .denied, .restricted: granted = false
                case .notDetermined: granted = true
                @unknown default:   granted = true
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

    @objc func getBackgroundKillReason(_ call: CAPPluginCall) {
        // iOS does not expose a kill reason; return null fields for API parity.
        call.resolve(["reason": NSNull(), "timestamp": NSNull()])
    }

    // MARK: - Geofencing

    @objc func addGeofences(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let arr = call.getArray("geofences") as? [[String: Any]] ?? []
        facade.addGeofences(arr)
        call.resolve()
    }

    @objc func removeGeofences(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let ids = call.getArray("ids") as? [String]
        facade.removeGeofences(ids)
        call.resolve()
    }

    @objc func getGeofences(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        call.resolve(["geofences": facade.getGeofences()])
    }

    @objc func getTripScore(_ call: CAPPluginCall) {
        if let score = lastTripScore {
            call.resolve(scoreToDict(score))
        } else {
            call.resolve([
                "overall": 100,
                "breakdown": [
                    "speeding": 100, "hardBraking": 100, "rapidAcceleration": 100,
                    "sharpTurns": 100, "phoneUsage": 100
                ],
                "events": [] as [[String: Any]],
                "tripId": "", "startedAt": 0, "endedAt": 0,
                "distanceKm": 0.0, "totalIdleMs": 0, "idleCount": 0
            ])
        }
    }

    @objc private func onIdleStartN(_ note: Notification) {
        var p: [String: Any] = [:]
        if let loc = note.userInfo?["location"] as? BGLocation {
            p["location"] = loc.toDictionaryWithId()
        } else {
            p["location"] = NSNull()
        }
        p["startedAt"] = (note.userInfo?["startedAt"] as? NSNumber)?.int64Value ?? 0
        notifyListeners("idleStart", data: p)
    }

    @objc private func onIdleEndN(_ note: Notification) {
        var p: [String: Any] = [:]
        if let loc = note.userInfo?["location"] as? BGLocation {
            p["location"] = loc.toDictionaryWithId()
        } else {
            p["location"] = NSNull()
        }
        p["durationMs"] = (note.userInfo?["durationMs"] as? NSNumber)?.int64Value ?? 0
        p["startedAt"]  = (note.userInfo?["startedAt"]  as? NSNumber)?.int64Value ?? 0
        notifyListeners("idleEnd", data: p)
    }

    private func scoreToDict(_ score: TripScore) -> [String: Any] {
        let evArr: [[String: Any]] = score.events.map { e in
            ["type": e.type, "timestamp": e.timestamp, "penalty": e.penalty,
             "location": ["latitude": e.latitude, "longitude": e.longitude]]
        }
        return [
            "overall": score.overall,
            "breakdown": [
                "speeding":          score.breakdown.speeding,
                "hardBraking":       score.breakdown.hardBraking,
                "rapidAcceleration": score.breakdown.rapidAcceleration,
                "sharpTurns":        score.breakdown.sharpTurns,
                "phoneUsage":        score.breakdown.phoneUsage
            ],
            "events":      evArr,
            "tripId":      score.tripId,
            "startedAt":   score.startedAt,
            "endedAt":     score.endedAt,
            "distanceKm":  score.distanceKm,
            "totalIdleMs": score.totalIdleMs,
            "idleCount":   score.idleCount
        ]
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

    // MARK: - LocationProviderDelegate

    public func onAuthorizationChanged(_ status: BGAuthorizationStatus) {
        notifyListeners("authorization", data: ["status": status.rawValue])
    }

    public func onLocationChanged(_ location: BGLocation) {
        lastLocationAt = Date()
        lastBGLocation = location
        if let lat = location.latitude, let lon = location.longitude {
            let now = location.time ?? Date()
            var speed = location.speed ?? -1
            // Compute speed from consecutive fixes when sensor speed is unavailable
            // (e.g. CLLocation injected by xcrun simctl provides speed = -1).
            if speed < 0, let prev = prevDLLoc {
                let dt = now.timeIntervalSince(prev.time)
                if dt > 0 && dt < 10 {
                    let dLat = (lat - prev.lat) * .pi / 180
                    let dLon = (lon - prev.lon) * .pi / 180
                    let a = sin(dLat/2) * sin(dLat/2)
                        + cos(prev.lat * .pi / 180) * cos(lat * .pi / 180) * sin(dLon/2) * sin(dLon/2)
                    let dist = 2 * 6_371_000.0 * asin(sqrt(a))
                    speed = dist / dt
                }
            }
            prevDLLoc = (lat: lat, lon: lon, time: now)
            let dl = DLLocation(
                latitude:  lat,
                longitude: lon,
                speed:     speed,
                bearing:   location.heading,
                provider:  location.provider
            )
            drivingDetector.feed(dl)
        }
        notifyListeners("location", data: location.toDictionaryWithId())
    }

    public func onStationaryChanged(_ location: BGLocation) {
        notifyListeners("stationary", data: location.toDictionaryWithId())
    }

    public func onLocationPause() {
        drivingDetector.reset()
        prevDLLoc = nil
        notifyListeners("stop", data: [:])
    }

    public func onLocationResume() {
        notifyListeners("start", data: [:])
    }

    public func onActivityChanged(_ activity: BGActivity) {
        notifyListeners("activity", data: activity.toDictionary())
    }

    public func onAbortRequested() {
        notifyListeners("abort_requested", data: [:])
    }

    public func onHttpAuthorization() {
        notifyListeners("http_authorization", data: [:])
    }

    public func onError(_ error: Error) {
        let nsErr = error as NSError
        notifyListeners("error", data: [
            "code": nsErr.code,
            "message": nsErr.localizedDescription
        ])
    }

    // MARK: - Notification observers (sync / heartbeat / driving)

    @objc private func onSyncStartN(_: Notification) {
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
        if let loc = note.userInfo?["location"] as? BGLocation {
            notifyListeners("heartbeat", data: loc.toDictionaryWithId())
        } else {
            notifyListeners("heartbeat", data: [:])
        }
    }

    @objc private func onTripStartN(_ note: Notification) {
        if let loc = note.userInfo?["location"] as? BGLocation {
            notifyListeners("tripStart", data: loc.toDictionaryWithId())
        } else {
            notifyListeners("tripStart", data: [:])
        }
    }

    @objc private func onTripEndN(_ note: Notification) {
        var p: [String: Any] = [:]
        if let loc = note.userInfo?["location"] as? BGLocation {
            p["location"] = loc.toDictionaryWithId()
        } else {
            p["location"] = NSNull()
        }
        p["distance"]   = (note.userInfo?["distance"]   as? NSNumber)?.doubleValue ?? 0
        p["durationMs"] = (note.userInfo?["durationMs"] as? NSNumber)?.int64Value  ?? 0
        if let score = note.userInfo?["score"] as? TripScore {
            p["score"] = scoreToDict(score)
        }
        notifyListeners("tripEnd", data: p)
    }

    @objc private func onMovingN(_ note: Notification) {
        if let loc = note.userInfo?["location"] as? BGLocation {
            notifyListeners("moving", data: loc.toDictionaryWithId())
        } else {
            notifyListeners("moving", data: [:])
        }
    }

    @objc private func onStoppedN(_ note: Notification) {
        if let loc = note.userInfo?["location"] as? BGLocation {
            notifyListeners("stopped", data: loc.toDictionaryWithId())
        } else {
            notifyListeners("stopped", data: [:])
        }
    }

    @objc private func onSpeedingN(_ note: Notification) {
        var p: [String: Any] = [:]
        if let loc = note.userInfo?["location"] as? BGLocation {
            p["location"] = loc.toDictionaryWithId()
        } else {
            p["location"] = NSNull()
        }
        let speedKmh = (note.userInfo?["speedKmh"] as? NSNumber)?.doubleValue ?? 0
        let limitKmh = (note.userInfo?["limitKmh"] as? NSNumber)?.doubleValue ?? 0
        p["speedKmh"] = speedKmh
        p["limitKmh"] = limitKmh
        notifyListeners("speeding", data: p)

        let allowed = currentConfig?.prioritySyncEvents ?? PrioritySyncManager.defaultEvents
        if allowed.contains("speeding"), let loc = note.userInfo?["location"] as? BGLocation {
            prioritySyncManager?.submit(eventType: "speeding", payload: [
                "type": "speeding", "timestamp": loc.time.map { Int64($0.timeIntervalSince1970 * 1000) } as Any,
                "location": ["latitude": loc.latitude as Any, "longitude": loc.longitude as Any],
                "speedKmh": speedKmh, "limitKmh": limitKmh, "source": "gps"
            ])
        }
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
        if let loc = note.userInfo?["location"] as? BGLocation {
            p["location"] = loc.toDictionaryWithId()
        } else if p["location"] == nil {
            p["location"] = NSNull()
        }
        notifyListeners("sos", data: p)

        let allowed = currentConfig?.prioritySyncEvents ?? PrioritySyncManager.defaultEvents
        if allowed.contains("sos") {
            var payload: [String: Any] = ["type": "sos", "timestamp": Int64(Date().timeIntervalSince1970 * 1000)]
            if let loc = note.userInfo?["location"] as? BGLocation {
                payload["location"] = ["latitude": loc.latitude, "longitude": loc.longitude]
            }
            prioritySyncManager?.submit(eventType: "sos", payload: payload)
        }
    }

    private func emitDrivingEvent(_ name: String, note: Notification) {
        var p: [String: Any] = [:]
        if let loc = note.userInfo?["location"] as? BGLocation {
            p["location"] = loc.toDictionaryWithId()
        } else {
            p["location"] = NSNull()
        }
        p["value"] = (note.userInfo?["value"] as? NSNumber)?.doubleValue ?? 0
        if let source = note.userInfo?["source"] as? String { p["source"] = source }
        notifyListeners(name, data: p)

        let allowed = currentConfig?.prioritySyncEvents ?? PrioritySyncManager.defaultEvents
        guard allowed.contains(name), let loc = note.userInfo?["location"] as? BGLocation else { return }
        prioritySyncManager?.submit(eventType: name, payload: [
            "type": name,
            "timestamp": loc.time.map { Int64($0.timeIntervalSince1970 * 1000) } as Any,
            "location": ["latitude": loc.latitude as Any, "longitude": loc.longitude as Any],
            "source": (note.userInfo?["source"] as? String) ?? "gps"
        ])
    }

    @objc private func onHardBrakeN(_ note: Notification)         { emitDrivingEvent("hardBrake",         note: note) }
    @objc private func onRapidAccelerationN(_ note: Notification) { emitDrivingEvent("rapidAcceleration", note: note) }
    @objc private func onSharpTurnN(_ note: Notification)         { emitDrivingEvent("sharpTurn",         note: note) }
    @objc private func onPossibleCrashN(_ note: Notification)     { emitDrivingEvent("possibleCrash",     note: note) }

    @objc private func onPrioritySyncSuccessN(_ note: Notification) {
        let eventType = (note.userInfo?["eventType"] as? String) ?? ""
        let attempt   = (note.userInfo?["attemptNumber"] as? Int) ?? 1
        notifyListeners("prioritySyncSuccess", data: ["eventType": eventType, "attemptNumber": attempt])
    }

    @objc private func onPrioritySyncFailedN(_ note: Notification) {
        let eventType  = (note.userInfo?["eventType"] as? String) ?? ""
        let httpStatus = (note.userInfo?["httpStatus"] as? Int) ?? -1
        let attempts   = (note.userInfo?["attempts"]   as? Int) ?? 1
        notifyListeners("prioritySyncFailed", data: ["eventType": eventType, "httpStatus": httpStatus, "attempts": attempts])
    }

    @objc private func onPhoneUsageWhileDrivingN(_ note: Notification) {
        if let loc = note.userInfo?["location"] as? BGLocation {
            notifyListeners("phoneUsageWhileDriving", data: loc.toDictionaryWithId())
        } else {
            notifyListeners("phoneUsageWhileDriving", data: [:])
        }
    }

    @objc private func onFallbackActivatedN(_ note: Notification) {
        let strategy = (note.userInfo?["strategy"] as? String) ?? "significantchanges"
        notifyListeners("iosFallbackActivated", data: ["strategy": strategy])
    }

    @objc private func onGeofenceN(_ note: Notification) {
        guard let id = note.userInfo?["id"] as? String,
              let action = note.userInfo?["action"] as? String else { return }
        var p: [String: Any] = ["id": id, "action": action]
        if let loc = note.userInfo?["location"] as? BGLocation {
            p["location"] = loc.toDictionaryWithId()
        }
        let eventName: String
        switch action {
        case "ENTER": eventName = "geofenceEnter"
        case "EXIT":  eventName = "geofenceExit"
        default:      eventName = "geofenceDwell"
        }
        notifyListeners(eventName, data: p)
    }
}

// MARK: - DrivingEventsDetectorDelegate

extension BackgroundGeolocationPlugin {

    func detectorOnMoving(_ location: DLLocation) {
        postDrivingNote(.BGMoving)
    }

    func detectorOnStopped(_ location: DLLocation) {
        postDrivingNote(.BGStopped)
    }

    func detectorOnTripStart(_ location: DLLocation) {
        facade?.drivingTripActive = true
        postDrivingNote(.BGTripStart)
    }

    func detectorOnTripEnd(_ location: DLLocation, distanceMeters: Double, durationMs: Int64, score: TripScore) {
        facade?.drivingTripActive = false
        lastTripScore = score
        var info: [String: Any] = [:]
        if let loc = lastBGLocation { info["location"] = loc }
        info["distance"]   = distanceMeters
        info["durationMs"] = NSNumber(value: durationMs)
        info["score"]      = score
        NotificationCenter.default.post(name: .BGTripEnd, object: nil, userInfo: info)
    }

    func detectorOnIdleStart(_ location: DLLocation, startedAt: Double) {
        var info: [String: Any] = [:]
        if let loc = lastBGLocation { info["location"] = loc }
        info["startedAt"] = NSNumber(value: Int64(startedAt * 1000))
        NotificationCenter.default.post(name: .BGIdleStart, object: nil, userInfo: info)
    }

    func detectorOnIdleEnd(_ location: DLLocation, durationMs: Int64, startedAt: Double) {
        var info: [String: Any] = [:]
        if let loc = lastBGLocation { info["location"] = loc }
        info["durationMs"] = NSNumber(value: durationMs)
        info["startedAt"]  = NSNumber(value: Int64(startedAt * 1000))
        NotificationCenter.default.post(name: .BGIdleEnd, object: nil, userInfo: info)
    }

    func detectorOnSpeeding(_ location: DLLocation, speedKmh: Double, limitKmh: Double) {
        var info: [String: Any] = [:]
        if let loc = lastBGLocation { info["location"] = loc }
        info["speedKmh"] = speedKmh
        info["limitKmh"] = limitKmh
        NotificationCenter.default.post(name: .BGSpeeding, object: nil, userInfo: info)
    }

    func detectorOnProviderChange(provider: String) {
        NotificationCenter.default.post(name: .BGProviderChange, object: nil, userInfo: ["provider": provider])
    }

    func detectorOnHardBrake(_ location: DLLocation, decelMps2: Double) {
        postSensorNote(.BGHardBrake, value: decelMps2)
    }

    func detectorOnRapidAcceleration(_ location: DLLocation, accelMps2: Double) {
        postSensorNote(.BGRapidAcceleration, value: accelMps2)
    }

    func detectorOnSharpTurn(_ location: DLLocation, degPerSec: Double) {
        postSensorNote(.BGSharpTurn, value: degPerSec)
    }

    func detectorOnPossibleCrash(_ location: DLLocation, dropKmh: Double) {
        postSensorNote(.BGPossibleCrash, value: dropKmh)
    }

    func detectorOnPhoneUsageWhileDriving(_ location: DLLocation) {
        postDrivingNote(.BGPhoneUsageWhileDriving)
    }

    private func postDrivingNote(_ name: Notification.Name) {
        var info: [String: Any] = [:]
        if let loc = lastBGLocation { info["location"] = loc }
        NotificationCenter.default.post(name: name, object: nil, userInfo: info)
    }

    private func postSensorNote(_ name: Notification.Name, value: Double) {
        var info: [String: Any] = ["value": value, "source": "gps"]
        if let loc = lastBGLocation { info["location"] = loc }
        NotificationCenter.default.post(name: name, object: nil, userInfo: info)
    }
}

// MARK: - Permission helper

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

    func locationManager(_ manager: CLLocationManager, didChangeAuthorization status: CLAuthorizationStatus) {
        finish(with: status)
    }

    private func finish(with status: CLAuthorizationStatus) {
        if status == .notDetermined || done { return }
        done = true
        completion(status)
    }
}
