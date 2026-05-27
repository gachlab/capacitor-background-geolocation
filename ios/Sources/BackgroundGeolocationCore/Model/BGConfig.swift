// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import CoreLocation

public enum BGLocationProvider: Int {
    case distanceFilter = 0
    case activity       = 1
    case raw            = 2
}

public final class BGConfig: NSObject, NSCopying {

    // MARK: – Core positioning
    public var stationaryRadius:  Double?
    public var distanceFilter:    Double?
    public var desiredAccuracy:   Double?
    public var locationProvider:  Int?
    public var activityType:      String?
    public var activitiesInterval: Double?
    public var pauseLocationUpdates: Bool?
    public var showsBackgroundLocationIndicator: Bool?
    public var maxAcceptedAccuracy: Double?
    public var activityConfidenceThreshold: Int?

    // MARK: – App lifecycle
    public var stopOnTerminate:         Bool?
    public var saveBatteryOnBackground: Bool?
    public var maxLocations:            Int?
    /**
     * iOS-only background survival fallback strategy.
     * - `"significantChanges"` (default when saveBatteryOnBackground=true): uses
     *   `startMonitoringSignificantLocationChanges()` which survives app kill.
     * - `"regionMonitoring"`: uses `startMonitoring(for:)` on a geofence around
     *   the last known position to wake the app when the user moves significantly.
     * - `"none"`: no fallback; rely on `startOnBoot` / BootReceiver equivalents.
     * Fires a `iosFallbackActivated` plugin event when the fallback activates.
     */
    public var iosBackgroundFallback:   String?

    // MARK: – HTTP sync
    public var url:            String?
    public var syncUrl:        String?
    public var syncThreshold:  Int?
    public var syncEnabled:    Bool?
    public var httpHeaders:    [String: String]?
    public var httpMethod:     String?
    public var syncHttpMethod: String?
    public var httpMode:       String?
    public var syncMode:       String?
    public var queryParams:    [String: String]?
    public var template:       Any?   // [String: String] or [[String: String]]

    // MARK: – Debug / diagnostics
    public var debug:               Bool?
    public var heartbeatInterval:   Int?
    public var mockLocationPolicy:  String?

    // MARK: – Driver insights
    public var drivingEvents: [String: Any]?

    // MARK: – Battery
    public var includeBattery: Bool?

    // MARK: - Defaults

    public override init() { super.init() }

    public convenience init(defaults: Void) {
        self.init()
        stationaryRadius           = 50
        distanceFilter             = 500
        desiredAccuracy            = 100
        debug                      = false
        activityType               = "OtherNavigation"
        activitiesInterval         = 10_000
        stopOnTerminate            = true
        saveBatteryOnBackground    = false
        maxLocations               = 10_000
        syncThreshold              = 100
        syncEnabled                = true
        pauseLocationUpdates       = false
        locationProvider           = BGLocationProvider.distanceFilter.rawValue
        httpMethod                 = "POST"
        syncHttpMethod             = "POST"
        httpMode                   = "batch"
        syncMode                   = "batch"
        heartbeatInterval          = 0
        mockLocationPolicy         = "allow"
        activityConfidenceThreshold = 50
        template                   = BGConfig.defaultTemplate()
    }

    // MARK: - Factory

    public static func from(dictionary d: [String: Any]) -> BGConfig {
        let c = BGConfig()
        func set<T>(_ kp: WritableKeyPath<BGConfig, T?>, _ key: String, as: T.Type = T.self) {
            if let v = d[key] as? T { c[keyPath: kp] = v }
        }
        func setNum<T: BinaryFloatingPoint>(_ kp: WritableKeyPath<BGConfig, T?>, _ key: String) {
            if let v = (d[key] as? NSNumber)?.doubleValue { c[keyPath: kp] = T(v) }
        }
        func setInt(_ kp: WritableKeyPath<BGConfig, Int?>, _ key: String) {
            if let v = (d[key] as? NSNumber)?.intValue { c[keyPath: kp] = v }
        }
        func setBool(_ kp: WritableKeyPath<BGConfig, Bool?>, _ key: String) {
            if let v = (d[key] as? NSNumber)?.boolValue { c[keyPath: kp] = v }
        }
        setNum(\.stationaryRadius,   "stationaryRadius")
        setNum(\.distanceFilter,     "distanceFilter")
        setNum(\.desiredAccuracy,    "desiredAccuracy")
        setInt(\.locationProvider,   "locationProvider")
        set(\.activityType,          "activityType")
        setNum(\.activitiesInterval, "activitiesInterval")
        setBool(\.pauseLocationUpdates, "pauseLocationUpdates")
        setBool(\.showsBackgroundLocationIndicator, "showsBackgroundLocationIndicator")
        setNum(\.maxAcceptedAccuracy, "maxAcceptedAccuracy")
        setInt(\.activityConfidenceThreshold, "activityConfidenceThreshold")
        setBool(\.stopOnTerminate,         "stopOnTerminate")
        setBool(\.saveBatteryOnBackground, "saveBatteryOnBackground")
        setInt(\.maxLocations, "maxLocations")
        if let v = d["url"]     as? String { c.url     = v }
        if let v = d["syncUrl"] as? String { c.syncUrl = v }
        setInt(\.syncThreshold, "syncThreshold")
        setBool(\.syncEnabled, "sync")
        if let v = d["httpHeaders"] as? [String: String] { c.httpHeaders = v }
        else if let v = d["headers"] as? [String: String] { c.httpHeaders = v }
        if let v = (d["httpMethod"]     as? String)?.uppercased() { c.httpMethod     = v }
        if let v = (d["syncHttpMethod"] as? String)?.uppercased() { c.syncHttpMethod = v }
        if let v = (d["httpMode"]  as? String)?.lowercased() { c.httpMode  = v }
        if let v = (d["syncMode"]  as? String)?.lowercased() { c.syncMode  = v }
        if let v = d["queryParams"] as? [String: String] { c.queryParams = v }
        setBool(\.debug, "debug")
        setInt(\.heartbeatInterval, "heartbeatInterval")
        if let v = (d["mockLocationPolicy"] as? String)?.lowercased() { c.mockLocationPolicy = v }
        if let v = d["drivingEvents"] as? [String: Any] { c.drivingEvents = v }
        setBool(\.includeBattery, "includeBattery")
        if let v = d["postTemplate"]  { c.template = v }
        else if let v = d["bodyTemplate"] { c.template = v }
        if let v = (d["iosBackgroundFallback"] as? String)?.lowercased() { c.iosBackgroundFallback = v }
        return c
    }

    public static func merge(_ base: BGConfig?, with override: BGConfig?) -> BGConfig {
        guard let base = base else { return override ?? BGConfig(defaults: ()) }
        guard let override = override else { return base }
        let m = base.copy() as! BGConfig
        func apply<T>(_ kp: WritableKeyPath<BGConfig, T?>) {
            if let v = override[keyPath: kp] { m[keyPath: kp] = v }
        }
        apply(\.stationaryRadius); apply(\.distanceFilter); apply(\.desiredAccuracy)
        apply(\.locationProvider); apply(\.activityType); apply(\.activitiesInterval)
        apply(\.pauseLocationUpdates); apply(\.showsBackgroundLocationIndicator)
        apply(\.maxAcceptedAccuracy); apply(\.activityConfidenceThreshold)
        apply(\.stopOnTerminate); apply(\.saveBatteryOnBackground); apply(\.maxLocations)
        apply(\.url); apply(\.syncUrl); apply(\.syncThreshold); apply(\.syncEnabled)
        apply(\.httpHeaders); apply(\.httpMethod); apply(\.syncHttpMethod)
        apply(\.httpMode); apply(\.syncMode); apply(\.queryParams)
        apply(\.debug); apply(\.heartbeatInterval); apply(\.mockLocationPolicy)
        apply(\.drivingEvents); apply(\.includeBattery); apply(\.template)
        apply(\.iosBackgroundFallback)
        return m
    }

    public static func defaultTemplate() -> [String: String] {
        return [
            "time": "@time", "accuracy": "@accuracy", "altitudeAccuracy": "@altitudeAccuracy",
            "speed": "@speed", "bearing": "@bearing", "altitude": "@altitude",
            "latitude": "@latitude", "longitude": "@longitude",
            "provider": "@provider", "locationProvider": "@locationProvider",
            "radius": "@radius", "events": "@events",
            "battery": "@battery", "isCharging": "@isCharging"
        ]
    }

    // MARK: - Computed helpers

    public var isDebugging: Bool            { debug ?? false }
    public var isSyncEnabled: Bool          { syncEnabled ?? true }
    public var hasValidUrl: Bool            { !(url?.isEmpty ?? true) }
    public var hasValidSyncUrl: Bool        { !(syncUrl?.isEmpty ?? true) }
    public var resolvedTemplate: Any        { template ?? BGConfig.defaultTemplate() }

    public func decodedActivityType() -> CLActivityType {
        switch (activityType ?? "").lowercased() {
        case "automotivenavigation": return .automotiveNavigation
        case "othernavigation":      return .otherNavigation
        case "fitness":              return .fitness
        default:                     return .other
        }
    }

    public func decodedDesiredAccuracy() -> CLLocationAccuracy {
        let acc = desiredAccuracy ?? 100
        if acc >= 1000 { return kCLLocationAccuracyKilometer }
        if acc >= 100  { return kCLLocationAccuracyHundredMeters }
        if acc >= 10   { return kCLLocationAccuracyNearestTenMeters }
        if acc >= 0    { return kCLLocationAccuracyBest }
        return kCLLocationAccuracyHundredMeters
    }

    public func httpHeadersJSON() throws -> String? {
        guard let h = httpHeaders, !h.isEmpty else { return nil }
        let data = try JSONSerialization.data(withJSONObject: h)
        return String(data: data, encoding: .utf8)
    }

    public func templateJSON() throws -> String? {
        let tmpl = resolvedTemplate
        let data = try JSONSerialization.data(withJSONObject: tmpl)
        return String(data: data, encoding: .utf8)
    }

    // MARK: - Serialization

    public func toDictionary() -> [String: Any] {
        var d = [String: Any]()
        if let v = activityType      { d["activityType"]      = v }
        if let v = activitiesInterval { d["activitiesInterval"] = v }
        if let v = url               { d["url"]               = v }
        if let v = syncUrl           { d["syncUrl"]           = v }
        if let v = httpHeaders       { d["httpHeaders"]       = v }
        if let v = httpMethod        { d["httpMethod"]        = v }
        if let v = syncHttpMethod    { d["syncHttpMethod"]    = v }
        if let v = httpMode          { d["httpMode"]          = v }
        if let v = syncMode          { d["syncMode"]          = v }
        if let v = queryParams       { d["queryParams"]       = v }
        if let v = showsBackgroundLocationIndicator { d["showsBackgroundLocationIndicator"] = v }
        if let v = heartbeatInterval { d["heartbeatInterval"] = v }
        if let v = mockLocationPolicy { d["mockLocationPolicy"] = v }
        if let v = drivingEvents     { d["drivingEvents"]     = v }
        if let v = includeBattery    { d["includeBattery"]    = v }
        if let v = activityConfidenceThreshold { d["activityConfidenceThreshold"] = v }
        if let v = maxAcceptedAccuracy { d["maxAcceptedAccuracy"] = v }
        if let v = stationaryRadius  { d["stationaryRadius"]  = v }
        if let v = distanceFilter    { d["distanceFilter"]    = v }
        if let v = desiredAccuracy   { d["desiredAccuracy"]   = v }
        if let v = debug             { d["debug"]             = v }
        if let v = stopOnTerminate   { d["stopOnTerminate"]   = v }
        if let v = syncThreshold     { d["syncThreshold"]     = v }
        if let v = syncEnabled       { d["sync"]              = v }
        if let v = saveBatteryOnBackground { d["saveBatteryOnBackground"] = v }
        if let v = maxLocations      { d["maxLocations"]      = v }
        if let v = pauseLocationUpdates { d["pauseLocationUpdates"] = v }
        if let v = locationProvider  { d["locationProvider"]  = v }
        if let v = iosBackgroundFallback { d["iosBackgroundFallback"] = v }
        d["postTemplate"] = resolvedTemplate
        return d
    }

    // MARK: - NSCopying

    public func copy(with zone: NSZone? = nil) -> Any {
        let c = BGConfig()
        c.stationaryRadius          = stationaryRadius
        c.distanceFilter            = distanceFilter
        c.desiredAccuracy           = desiredAccuracy
        c.locationProvider          = locationProvider
        c.activityType              = activityType
        c.activitiesInterval        = activitiesInterval
        c.pauseLocationUpdates      = pauseLocationUpdates
        c.showsBackgroundLocationIndicator = showsBackgroundLocationIndicator
        c.maxAcceptedAccuracy       = maxAcceptedAccuracy
        c.activityConfidenceThreshold = activityConfidenceThreshold
        c.stopOnTerminate           = stopOnTerminate
        c.saveBatteryOnBackground   = saveBatteryOnBackground
        c.maxLocations              = maxLocations
        c.url                       = url
        c.syncUrl                   = syncUrl
        c.syncThreshold             = syncThreshold
        c.syncEnabled               = syncEnabled
        c.httpHeaders               = httpHeaders
        c.httpMethod                = httpMethod
        c.syncHttpMethod            = syncHttpMethod
        c.httpMode                  = httpMode
        c.syncMode                  = syncMode
        c.queryParams               = queryParams
        c.debug                     = debug
        c.heartbeatInterval         = heartbeatInterval
        c.mockLocationPolicy        = mockLocationPolicy
        c.drivingEvents             = drivingEvents
        c.includeBattery            = includeBattery
        c.template                  = template
        c.iosBackgroundFallback     = iosBackgroundFallback
        return c
    }
}
