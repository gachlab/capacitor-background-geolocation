// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import Network

public protocol PostLocationTaskDelegate: AnyObject {
    func postLocationTaskRequestedAbortUpdates(_ task: PostLocationTask)
    func postLocationTaskHttpAuthorizationUpdates(_ task: PostLocationTask)
}

public final class PostLocationTask {

    public static let shared = PostLocationTask()

    private let executor = DispatchQueue(
        label: "com.gachlab.geolocation.post",
        qos: .utility
    )

    public var config: BGConfig = BGConfig(defaults: ())

    private var _pendingDrivingEvents: [[String: Any]] = []
    private let pendingEventsLock = NSLock()
    public var pendingDrivingEvents: [[String: Any]] {
        get {
            pendingEventsLock.lock()
            defer { pendingEventsLock.unlock() }
            return _pendingDrivingEvents
        }
        set {
            pendingEventsLock.lock()
            _pendingDrivingEvents = newValue
            pendingEventsLock.unlock()
        }
    }

    public var attachBatterySnapshot: ((BGLocation) -> Void)?
    public weak var delegate: PostLocationTaskDelegate?

    private static var s_transform: BGLocationTransform?

    public static func setLocationTransform(_ t: BGLocationTransform?) {
        s_transform = t
    }

    public static var locationTransform: BGLocationTransform? {
        return s_transform
    }

    public var hasConnectivity = true
    private var pathMonitor: NWPathMonitor?
    private let monitorQueue = DispatchQueue(
        label: "com.gachlab.geolocation.network",
        qos: .utility
    )

    private init() {}

    public func start() {
        let monitor = NWPathMonitor()
        pathMonitor = monitor
        monitor.pathUpdateHandler = { [weak self] path in
            self?.hasConnectivity = path.status == .satisfied
        }
        monitor.start(queue: monitorQueue)
    }

    public func stop() {
        pathMonitor?.cancel()
        pathMonitor = nil
    }

    public func add(_ location: BGLocation) {
        executor.async { [weak self] in
            guard let self = self else { return }
            self.processLocation(location)
        }
    }

    private func processLocation(_ location: BGLocation) {
        var loc = location

        // 1. Apply transform
        if let transform = PostLocationTask.s_transform {
            guard let transformed = transform(loc) else { return }
            if transformed !== loc {
                let originalEvents = loc.drivingEvents
                loc = transformed
                if loc.drivingEvents == nil || loc.drivingEvents!.isEmpty,
                   let orig = originalEvents, !orig.isEmpty {
                    loc.drivingEvents = orig
                }
            }
        }

        // 2. Flush pending driving events (age <= 60s)
        pendingEventsLock.lock()
        let now = Date().timeIntervalSince1970 * 1000
        let cutoff = now - 60_000
        var flushed = [[String: Any]]()
        var remaining = [[String: Any]]()
        for event in _pendingDrivingEvents {
            if let t = event["time"] as? Int64, Double(t) >= cutoff {
                flushed.append(event)
            } else {
                remaining.append(event)
            }
        }
        _pendingDrivingEvents = remaining
        pendingEventsLock.unlock()

        if !flushed.isEmpty {
            var merged = loc.drivingEvents ?? []
            merged.append(contentsOf: flushed)
            loc.drivingEvents = merged
        }

        // 3. Attach battery snapshot
        attachBatterySnapshot?(loc)

        // 4. Mock policy
        if let policy = config.mockLocationPolicy, loc.simulated == true {
            if policy == "drop" { return }
            // "flag" falls through with simulated already set
        }

        // 5. Persist
        LocationDAO.shared.persistLocation(loc, maxRows: config.maxLocations ?? 10_000)

        // 6. Session
        if SessionDAO.shared.isSessionActive {
            SessionDAO.shared.persistLocation(loc)
        }

        // 7. Post
        if hasConnectivity && config.hasValidUrl {
            let success = post(loc)
            if success, let lid = loc.locationId {
                try? LocationDAO.shared.deleteLocation(id: lid)
            }
        }

        // 8. Sync threshold check
        if config.isSyncEnabled {
            let count = LocationDAO.shared.getLocationsForSyncCount()
            if count >= (config.syncThreshold ?? 100) {
                sync()
            }
        }
    }

    public func sync() {
        guard config.isSyncEnabled, config.hasValidSyncUrl else { return }
        let locations = LocationDAO.shared.getLocationsForSync()
        guard !locations.isEmpty else { return }
        NotificationCenter.default.post(name: .BGBackgroundSyncDidStart, object: nil)
        BackgroundSync.shared.sync(locations: locations, config: config)
    }

    @discardableResult
    private func post(_ location: BGLocation) -> Bool {
        guard let urlString = config.url else { return false }
        let resolvedUrl = UrlTemplateResolver.resolve(
            urlString,
            location: location,
            queryParams: config.queryParams
        )

        guard let url = URL(string: resolvedUrl) else { return false }

        let method = (config.httpMethod ?? "POST").uppercased()

        guard let bodyObj = location.toResult(from: config.resolvedTemplate) as? [String: Any],
              let bodyData = try? JSONSerialization.data(withJSONObject: bodyObj, options: [])
        else { return false }

        var request: URLRequest

        if method == "GET" {
            var components = URLComponents(url: url, resolvingAgainstBaseURL: false) ?? URLComponents()
            if let jsonStr = String(data: bodyData, encoding: .utf8) {
                var items = components.queryItems ?? []
                items.append(URLQueryItem(name: "location", value: jsonStr))
                components.queryItems = items
            }
            guard let finalUrl = components.url else { return false }
            request = URLRequest(url: finalUrl)
        } else {
            request = URLRequest(url: url)
            request.httpBody = bodyData
        }

        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")

        if let headers = config.httpHeaders {
            for (k, v) in headers { request.setValue(v, forHTTPHeaderField: k) }
        }

        var resultStatus = 0
        let semaphore = DispatchSemaphore(value: 0)

        URLSession.shared.dataTask(with: request) { _, response, _ in
            if let http = response as? HTTPURLResponse {
                resultStatus = http.statusCode
            }
            semaphore.signal()
        }.resume()

        semaphore.wait()

        switch resultStatus {
        case 285:
            delegate?.postLocationTaskRequestedAbortUpdates(self)
            return false
        case 401:
            delegate?.postLocationTaskHttpAuthorizationUpdates(self)
            return false
        case 200...299:
            return true
        default:
            return false
        }
    }

    public func bufferPendingEvent(_ type: String, extra: [String: Any]? = nil) {
        pendingEventsLock.lock()
        defer { pendingEventsLock.unlock() }
        guard _pendingDrivingEvents.count < 20 else { return }
        var entry: [String: Any] = [
            "type": type,
            "time": Int64(Date().timeIntervalSince1970 * 1000)
        ]
        if let extra = extra {
            for (k, v) in extra { entry[k] = v }
        }
        _pendingDrivingEvents.append(entry)
    }
}
