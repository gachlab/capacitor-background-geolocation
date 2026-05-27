// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import CoreLocation

/**
 * Manages user-defined geofences using CLLocationManager region monitoring.
 *
 * iOS CLLocationManager can monitor at most 20 simultaneous regions. That budget
 * is shared with the stationary-detection region used by
 * `DistanceFilterLocationProvider` ("BGStationaryRegion"). In practice this means
 * ~19 user-defined geofences are available.
 *
 * Geofences are persisted in UserDefaults and re-registered on startup.
 * Dwell detection is implemented with a per-region Timer (requires foreground or
 * background-active app state — timers do not fire when the app is suspended).
 */
public final class GeofenceManager: NSObject, CLLocationManagerDelegate {

    public static let shared = GeofenceManager()

    // Invoked with (geofenceId, action, location?) on each transition.
    // "action" is "ENTER", "EXIT", or "DWELL".
    public var eventListener: ((String, String, BGLocation?) -> Void)?

    private let locationManager = CLLocationManager()
    private var geofences: [String: BGGeofence] = [:]
    private var dwellTimers: [String: Timer] = [:]

    private static let userDefaultsKey = "gachlab_geofences"

    private override init() {
        super.init()
        locationManager.delegate = self
        loadFromDefaults()
        reRegisterMonitored()
    }

    // MARK: - Public API

    public func add(_ incoming: [BGGeofence]) {
        incoming.forEach { geofences[$0.id] = $0 }
        persistToDefaults()
        incoming.forEach { startMonitoring($0) }
    }

    public func remove(_ ids: [String]?) {
        let toRemove = ids ?? Array(geofences.keys)
        toRemove.forEach {
            geofences.removeValue(forKey: $0)
            cancelDwellTimer(for: $0)
        }
        persistToDefaults()
        let monitoredIds = locationManager.monitoredRegions
            .compactMap { ($0 as? CLCircularRegion)?.identifier }
        toRemove.filter { monitoredIds.contains($0) }.forEach {
            if let region = locationManager.monitoredRegions
                .first(where: { ($0 as? CLCircularRegion)?.identifier == $0 }) {
                locationManager.stopMonitoring(for: region)
            }
        }
        // Re-derive actual regions from monitoredRegions since stopMonitoring is async
        locationManager.monitoredRegions
            .compactMap { $0 as? CLCircularRegion }
            .filter { toRemove.contains($0.identifier) }
            .forEach { locationManager.stopMonitoring(for: $0) }
    }

    public func getAll() -> [BGGeofence] { Array(geofences.values) }

    // MARK: - CLLocationManagerDelegate

    public func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        guard let gf = geofences[region.identifier] else { return }
        if gf.notifyOnEntry { fire("ENTER", id: gf.id, location: nil) }
        if gf.notifyOnDwell { startDwellTimer(for: gf) }
    }

    public func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        guard let gf = geofences[region.identifier] else { return }
        cancelDwellTimer(for: gf.id)
        if gf.notifyOnExit { fire("EXIT", id: gf.id, location: nil) }
    }

    public func locationManager(_ manager: CLLocationManager, monitoringDidFailFor region: CLRegion?, withError error: Error) {
        if let region = region {
            // Post BGFallbackActivated so the plugin can surface the error
            NotificationCenter.default.post(
                name: .BGGeofenceError,
                object: nil,
                userInfo: ["id": region.identifier, "message": error.localizedDescription]
            )
        }
    }

    // MARK: - Private helpers

    private func startMonitoring(_ gf: BGGeofence) {
        let coordinate = CLLocationCoordinate2D(latitude: gf.latitude, longitude: gf.longitude)
        let region = CLCircularRegion(center: coordinate, radius: gf.radius, identifier: gf.id)
        region.notifyOnEntry = gf.notifyOnEntry || gf.notifyOnDwell
        region.notifyOnExit  = gf.notifyOnExit
        locationManager.startMonitoring(for: region)
    }

    private func reRegisterMonitored() {
        geofences.values.forEach { startMonitoring($0) }
    }

    private func startDwellTimer(for gf: BGGeofence) {
        cancelDwellTimer(for: gf.id)
        let delay = TimeInterval(gf.loiteringDelay) / 1000.0
        let id = gf.id
        DispatchQueue.main.async {
            self.dwellTimers[id] = Timer.scheduledTimer(
                withTimeInterval: delay,
                repeats: false
            ) { [weak self] _ in
                self?.fire("DWELL", id: id, location: nil)
                self?.dwellTimers.removeValue(forKey: id)
            }
        }
    }

    private func cancelDwellTimer(for id: String) {
        DispatchQueue.main.async {
            self.dwellTimers[id]?.invalidate()
            self.dwellTimers.removeValue(forKey: id)
        }
    }

    private func fire(_ action: String, id: String, location: BGLocation?) {
        eventListener?(id, action, location)
    }

    private func persistToDefaults() {
        let data = geofences.values.compactMap { try? JSONEncoder().encode($0) }
        UserDefaults.standard.set(data, forKey: Self.userDefaultsKey)
    }

    private func loadFromDefaults() {
        guard let dataArray = UserDefaults.standard.array(forKey: Self.userDefaultsKey) as? [Data] else { return }
        dataArray.forEach {
            if let gf = try? JSONDecoder().decode(BGGeofence.self, from: $0) {
                geofences[gf.id] = gf
            }
        }
    }
}
