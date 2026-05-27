// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import CoreLocation

public typealias BGLocationTransform = (BGLocation) -> BGLocation?

public enum BGLocationStatus: Int {
    case deleted     = 0
    case postPending = 1
    case syncPending = 2
}

public final class BGLocation: NSObject, NSCopying {
    public var locationId: Int64?
    public var time: Date?
    public var accuracy: Double?
    public var altitudeAccuracy: Double?
    public var speed: Double?
    public var heading: Double?
    public var altitude: Double?
    public var latitude: Double?
    public var longitude: Double?
    public var provider: String?
    public var locationProvider: Int?
    public var radius: Double?
    public var isValid: Bool = true
    public var recordedAt: Date?
    public var simulated: Bool?
    public var drivingEvents: [[String: Any]]?
    public var batteryLevel: Int?
    public var isCharging: Bool?

    public override init() { super.init() }

    public static func from(clLocation: CLLocation) -> BGLocation {
        let loc = BGLocation()
        loc.time              = clLocation.timestamp
        loc.accuracy          = clLocation.horizontalAccuracy
        loc.altitudeAccuracy  = clLocation.verticalAccuracy
        loc.speed             = clLocation.speed
        loc.heading           = clLocation.course
        loc.altitude          = clLocation.altitude
        loc.latitude          = clLocation.coordinate.latitude
        loc.longitude         = clLocation.coordinate.longitude
        if #available(iOS 15.0, *) {
            loc.simulated = clLocation.sourceInformation?.isSimulatedBySoftware ?? false
        }
        return loc
    }

    public func toDictionary() -> [String: Any] {
        var d = [String: Any]()
        if let t = time { d["time"] = Int64(t.timeIntervalSince1970 * 1000) }
        if let v = accuracy          { d["accuracy"]          = v }
        if let v = altitudeAccuracy  { d["altitudeAccuracy"]  = v }
        if let v = speed             { d["speed"]             = v }
        if let v = heading           { d["heading"]           = v; d["bearing"] = v }
        if let v = altitude          { d["altitude"]          = v }
        if let v = latitude          { d["latitude"]          = v }
        if let v = longitude         { d["longitude"]         = v }
        if let v = provider          { d["provider"]          = v }
        if let v = locationProvider  { d["locationProvider"]  = v }
        if let v = radius            { d["radius"]            = v }
        if let t = recordedAt        { d["recordedAt"]        = Int64(t.timeIntervalSince1970 * 1000) }
        if let v = simulated         { d["simulated"]         = v }
        if let ev = drivingEvents, !ev.isEmpty { d["events"] = ev }
        if let v = batteryLevel      { d["battery"]           = v }
        if let v = isCharging        { d["isCharging"]        = v }
        return d
    }

    public func toDictionaryWithId() -> [String: Any] {
        var d = toDictionary()
        if let id = locationId { d["id"] = id }
        return d
    }

    public func toResult(from template: Any) -> Any {
        if let arr = template as? [[String: Any]] {
            return arr.map { mapValue($0) }
        } else if let dict = template as? [String: Any] {
            return mapValue(dict)
        }
        return toDictionary()
    }

    private func mapValue(_ value: Any) -> Any {
        if let s = value as? String {
            let resolved = self.value(forKey: s)
            if s.hasPrefix("@") { return resolved ?? NSNull() }
            return resolved ?? value
        } else if let dict = value as? [String: Any] {
            return mapValue(dict)
        } else if let arr = value as? [Any] {
            return arr.map { mapValue($0) }
        }
        return value
    }

    private func mapValue(_ dict: [String: Any]) -> [String: Any] {
        var out = [String: Any]()
        for (k, v) in dict { out[k] = mapValue(v) }
        return out
    }

    override public func value(forKey key: String) -> Any? {
        switch key {
        case "@id":              return locationId
        case "@time":            return time.map { Int64($0.timeIntervalSince1970 * 1000) }
        case "@accuracy":        return accuracy
        case "@altitudeAccuracy":return altitudeAccuracy
        case "@speed":           return speed
        case "@heading", "@bearing": return heading
        case "@altitude":        return altitude
        case "@latitude":        return latitude
        case "@longitude":       return longitude
        case "@provider":        return provider
        case "@locationProvider":return locationProvider
        case "@radius":          return radius
        case "@recordedAt":      return recordedAt.map { Int64($0.timeIntervalSince1970 * 1000) }
        case "@simulated":       return simulated
        case "@events":
            guard let ev = drivingEvents, !ev.isEmpty else { return nil }
            return ev
        case "@battery":         return batteryLevel
        case "@isCharging":      return isCharging
        default:                 return nil
        }
    }

    public func distance(from other: BGLocation) -> Double {
        guard let aLat = latitude, let aLon = longitude,
              let bLat = other.latitude, let bLon = other.longitude else { return 0 }
        let earthRadius = 6_378_137.0
        let dTheta  = (aLat - bLat) * (.pi / 180.0)
        let dLambda = (aLon - bLon) * (.pi / 180.0)
        let meanT   = (aLat + bLat) * (.pi / 180.0) / 2.0
        let cosT    = cos(meanT)
        return sqrt(earthRadius * earthRadius * (dTheta * dTheta + cosT * cosT * dLambda * dLambda))
    }

    public func isBetter(than other: BGLocation?) -> Bool {
        guard let other = other else { return false }
        guard let selfTime = time, let otherTime = other.time else { return false }
        let timeDelta = selfTime.timeIntervalSince(otherTime)
        if timeDelta > 120  { return true  }
        if timeDelta < -120 { return false }
        let accDelta = (accuracy ?? .infinity) - (other.accuracy ?? .infinity)
        if accDelta < 0   { return true }
        if timeDelta > 0 && accDelta <= 0  { return true }
        if timeDelta > 0 && accDelta <= 200 { return true }
        return false
    }

    public func isBeyond(_ other: BGLocation, radius: Double) -> Bool {
        return (distance(from: other) - (accuracy ?? 0) - (other.accuracy ?? 0)) > radius
    }

    public func copy(with zone: NSZone? = nil) -> Any {
        let copy = BGLocation()
        copy.locationId      = locationId
        copy.time            = time
        copy.accuracy        = accuracy
        copy.altitudeAccuracy = altitudeAccuracy
        copy.speed           = speed
        copy.heading         = heading
        copy.altitude        = altitude
        copy.latitude        = latitude
        copy.longitude       = longitude
        copy.provider        = provider
        copy.locationProvider = locationProvider
        copy.radius          = radius
        copy.isValid         = isValid
        copy.recordedAt      = recordedAt
        copy.simulated       = simulated
        copy.drivingEvents   = drivingEvents
        copy.batteryLevel    = batteryLevel
        copy.isCharging      = isCharging
        return copy
    }

    public override var description: String {
        return "BGLocation id=\(locationId as Any) lat=\(latitude as Any) lon=\(longitude as Any) acc=\(accuracy as Any)"
    }
}
