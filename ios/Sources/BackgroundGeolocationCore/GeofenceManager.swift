// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import CoreLocation

/**
 * Manages user-defined geofences using CLLocationManager region monitoring.
 *
 * iOS region monitoring is app-wide capped at 20 regions. One slot is reserved
 * for the stationary-detection region (`BGStationaryRegion`, monitored on the
 * provider's own manager), leaving **19** user-defined geofences. `add` enforces
 * this cap and surfaces a `BGGeofenceError` for any geofence that doesn't fit,
 * instead of silently failing.
 *
 * Parity notes vs. Android (GMS):
 *  - **Initial ENTER**: GMS supports `INITIAL_TRIGGER_ENTER`. iOS region monitoring
 *    only reports *transitions*, so we call `requestState(for:)` after registering
 *    and synthesise an ENTER from `didDetermineState(.inside)` when already inside.
 *  - **Dwell**: GMS dwell is OS-level and survives suspension. iOS has no native
 *    dwell, so we record the enter timestamp and fire DWELL from whichever comes
 *    first — a foreground Timer (fast path) or `evaluateDwell(now:location:)` driven
 *    by incoming location fixes (resilient to the Timer being suspended/coalesced).
 *
 * Geofences are persisted in UserDefaults and re-registered on startup.
 */
public final class GeofenceManager: NSObject, CLLocationManagerDelegate {

    public static let shared = GeofenceManager()

    /// 20 app-wide region slots minus one reserved for `BGStationaryRegion`.
    private static let maxUserGeofences = 19

    // Invoked with (geofenceId, transition, location?) on each transition.
    public var eventListener: ((String, GeofenceTransition, BGLocation?) -> Void)?

    private let locationManager = CLLocationManager()
    private var geofences: [String: BGGeofence] = [:]

    /// Geofences we have actually handed to CLLocationManager (synchronous mirror
    /// of `monitoredRegions`, which updates asynchronously). Used to enforce the cap.
    private var monitoredIds: Set<String> = []
    /// Regions the device is currently inside — dedups ENTER between
    /// `didEnterRegion` and `didDetermineState(.inside)`.
    private var insideRegions: Set<String> = []
    /// Enter timestamp per pending-dwell region; the single source of truth that a
    /// DWELL is still owed (cleared once fired, on exit, or on removal).
    private var dwellEnterAt: [String: Date] = [:]
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
        toRemove.forEach { id in
            geofences.removeValue(forKey: id)
            cancelDwellTimer(for: id)
            dwellEnterAt.removeValue(forKey: id)
            insideRegions.remove(id)
            monitoredIds.remove(id)
        }
        persistToDefaults()
        // stopMonitoring is async; derive the actual regions from monitoredRegions.
        locationManager.monitoredRegions
            .compactMap { $0 as? CLCircularRegion }
            .filter { toRemove.contains($0.identifier) }
            .forEach { locationManager.stopMonitoring(for: $0) }
    }

    public func getAll() -> [BGGeofence] { Array(geofences.values) }

    /// Resilient dwell check. Called with each incoming location fix so DWELL still
    /// fires when the app was suspended and the per-region Timer never ran. Fires at
    /// most once per dwell, coordinated with the Timer via `dwellEnterAt`.
    public func evaluateDwell(now: Date, location: BGLocation?) {
        runOnMain { [weak self] in
            guard let self = self else { return }
            // Snapshot the due ids BEFORE firing — fireDwellIfPending mutates
            // dwellEnterAt, which must not happen while iterating it.
            let due = self.dwellEnterAt.compactMap { (id, enterAt) -> String? in
                guard let gf = self.geofences[id] else { return nil }
                let elapsedMs = now.timeIntervalSince(enterAt) * 1000.0
                return elapsedMs >= Double(gf.loiteringDelay) ? id : nil
            }
            due.forEach { self.fireDwellIfPending($0, location: location) }
        }
    }

    // MARK: - CLLocationManagerDelegate

    public func locationManager(_ manager: CLLocationManager, didEnterRegion region: CLRegion) {
        guard let gf = geofences[region.identifier] else { return }
        handleEntered(gf)
    }

    public func locationManager(_ manager: CLLocationManager, didExitRegion region: CLRegion) {
        guard let gf = geofences[region.identifier] else { return }
        handleExited(gf)
    }

    /// Response to `requestState(for:)` — gives us the current state right after we
    /// start monitoring, so we can synthesise the initial ENTER GMS would deliver.
    public func locationManager(_ manager: CLLocationManager, didDetermineState state: CLRegionState, for region: CLRegion) {
        guard let gf = geofences[region.identifier] else { return }
        switch state {
        case .inside:  handleEntered(gf)
        case .outside: handleExited(gf)   // no-op unless we believed we were inside
        case .unknown: break
        @unknown default: break
        }
    }

    public func locationManager(_ manager: CLLocationManager, monitoringDidFailFor region: CLRegion?, withError error: Error) {
        if let region = region {
            NotificationCenter.default.post(
                name: .BGGeofenceError,
                object: nil,
                userInfo: ["id": region.identifier, "message": error.localizedDescription]
            )
        }
    }

    // MARK: - Transition handling (idempotent)

    private func handleEntered(_ gf: BGGeofence) {
        guard !insideRegions.contains(gf.id) else { return }
        insideRegions.insert(gf.id)
        if gf.notifyOnEntry { fire(.enter, id: gf.id, location: nil) }
        if gf.notifyOnDwell {
            dwellEnterAt[gf.id] = Date()
            startDwellTimer(for: gf)
        }
    }

    private func handleExited(_ gf: BGGeofence) {
        let wasInside = insideRegions.remove(gf.id) != nil
        cancelDwellTimer(for: gf.id)
        dwellEnterAt.removeValue(forKey: gf.id)
        // Only a real boundary exit (we were inside) emits EXIT — an initial
        // `.outside` determination must not.
        if wasInside && gf.notifyOnExit { fire(.exit, id: gf.id, location: nil) }
    }

    // MARK: - Private helpers

    private func startMonitoring(_ gf: BGGeofence) {
        // Enforce the app-wide cap (re-adding an already-monitored id just replaces).
        if !monitoredIds.contains(gf.id) && monitoredIds.count >= Self.maxUserGeofences {
            NotificationCenter.default.post(
                name: .BGGeofenceError,
                object: nil,
                userInfo: [
                    "id": gf.id,
                    "message": "iOS geofence limit reached (max \(Self.maxUserGeofences)); not monitoring \(gf.id)"
                ]
            )
            return
        }

        let coordinate = CLLocationCoordinate2D(latitude: gf.latitude, longitude: gf.longitude)
        let region = CLCircularRegion(center: coordinate, radius: gf.radius, identifier: gf.id)
        region.notifyOnEntry = gf.notifyOnEntry || gf.notifyOnDwell
        region.notifyOnExit  = gf.notifyOnExit
        locationManager.startMonitoring(for: region)
        monitoredIds.insert(gf.id)
        // Synthesise the initial ENTER if we're already inside (GMS INITIAL_TRIGGER_ENTER).
        locationManager.requestState(for: region)
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
                self?.fireDwellIfPending(id, location: nil)
            }
        }
    }

    private func cancelDwellTimer(for id: String) {
        DispatchQueue.main.async {
            self.dwellTimers[id]?.invalidate()
            self.dwellTimers.removeValue(forKey: id)
        }
    }

    /// Fire DWELL at most once: `dwellEnterAt[id]` being present is the guard that a
    /// dwell is still owed. Whichever path (Timer or evaluateDwell) arrives first wins.
    private func fireDwellIfPending(_ id: String, location: BGLocation?) {
        guard dwellEnterAt[id] != nil else { return }
        dwellEnterAt.removeValue(forKey: id)
        cancelDwellTimer(for: id)
        fire(.dwell, id: id, location: location)
    }

    private func fire(_ transition: GeofenceTransition, id: String, location: BGLocation?) {
        eventListener?(id, transition, location)
    }

    private func runOnMain(_ block: @escaping () -> Void) {
        if Thread.isMainThread { block() } else { DispatchQueue.main.async(execute: block) }
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
