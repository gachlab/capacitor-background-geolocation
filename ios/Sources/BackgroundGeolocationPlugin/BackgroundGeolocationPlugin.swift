// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM

import Foundation
import Capacitor
import CoreLocation
import CoreMotion
import UIKit
#if canImport(MAURBackgroundGeolocation)
import MAURBackgroundGeolocation
#endif

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
        CAPPluginMethod(name: "getValidLocations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getConfig", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deleteLocation", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "deleteAllLocations", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "isLocationEnabled", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "showAppSettings", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "showLocationSettings", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "watchLocationMode", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "stopWatchingLocationMode", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "getLogEntries", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "checkStatus", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "startTask", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "endTask", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "forceSync", returnType: CAPPluginReturnPromise),
        CAPPluginMethod(name: "removeAllListeners", returnType: CAPPluginReturnPromise)
    ]

    private var facade: MAURBackgroundGeolocationFacade?
    private var currentConfig: MAURConfig?

    override public func load() {
        let f = MAURBackgroundGeolocationFacade()
        f.delegate = self
        facade = f
        NotificationCenter.default.addObserver(self, selector: #selector(onAppForeground), name: UIApplication.willEnterForegroundNotification, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(onAppBackground), name: UIApplication.didEnterBackgroundNotification, object: nil)
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
            notifyListeners("start", data: [:])
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
            notifyListeners("stop", data: [:])
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
            call.resolve([:])
        }
    }

    @objc func getValidLocations(_ call: CAPPluginCall) {
        guard let facade = facade else { call.reject("facade not initialized"); return }
        let locations = facade.getValidLocations() as? [MAURLocation] ?? []
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

    @objc override public func removeAllListeners(_ call: CAPPluginCall) {
        super.removeAllListeners(call)
    }

    // MARK: - MAURProviderDelegate

    public func onAuthorizationChanged(_ authStatus: MAURLocationAuthorizationStatus) {
        notifyListeners("authorization", data: ["status": authStatus.rawValue])
    }

    public func onLocationChanged(_ location: MAURLocation!) {
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
}
