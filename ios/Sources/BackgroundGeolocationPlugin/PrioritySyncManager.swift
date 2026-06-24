// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import Network
import BackgroundGeolocationCore

/// Immediately POSTs safety-critical events to a priority endpoint, with
/// retry and offline queuing via NWPathMonitor.
final class PrioritySyncManager {

    // MARK: – Constants

    static let defaultRetries = 3
    static let defaultRetryDelays: [TimeInterval] = [10, 30, 60]
    static let defaultEvents = ["possibleCrash", "sos"]

    // MARK: – Config

    private let url: URL?
    private let headers: [String: String]?
    private let maxRetries: Int
    private let retryDelays: [TimeInterval]

    // MARK: – State

    private let queue = DispatchQueue(label: "gachlab.priority-sync", qos: .utility)
    private var sentTimestamps = Set<Int64>()
    private var sentOrder = [Int64]()          // insertion order so overflow drops the OLDEST
    private var offlineQueue: [(String, [String: Any])] = []
    private var monitor: NWPathMonitor?
    // Cached connectivity from the persistent monitor — avoids a per-submit probe race.
    private var isNetworkAvailable = true

    // MARK: – Init

    init(config: BGConfig) {
        let urlString = config.prioritySyncUrl?.isEmpty == false
            ? config.prioritySyncUrl!
            : (config.url ?? "")
        self.url = URL(string: urlString)
        self.headers = config.httpHeaders
        self.maxRetries = config.prioritySyncRetries ?? Self.defaultRetries
        self.retryDelays = config.prioritySyncRetryDelays?.map { TimeInterval($0) }
            ?? Self.defaultRetryDelays
        if self.url != nil { startMonitoring() }
    }

    deinit { monitor?.cancel() }

    // MARK: – Public API

    /// Submit an event for immediate delivery. No-op when no URL is configured
    /// or when the event timestamp was already submitted (dedup).
    func submit(eventType: String, payload: [String: Any]) {
        guard url != nil else { return }
        // Accept any numeric timestamp (Int, Double, NSNumber bridged from JS), matching
        // Android's optLong — a strict `as? Int64` would miss most bridged numbers and break dedup.
        let ts = (payload["timestamp"] as? NSNumber)?.int64Value
            ?? Int64(Date().timeIntervalSince1970 * 1000)
        queue.async { [weak self] in
            guard let self else { return }
            guard self.sentTimestamps.insert(ts).inserted else { return }
            self.sentOrder.append(ts)
            if self.sentTimestamps.count > 200 {
                // Drop the oldest 100 deterministically (mirrors Android's LinkedHashSet trim).
                let drop = self.sentOrder.prefix(100)
                self.sentOrder.removeFirst(min(100, self.sentOrder.count))
                for old in drop { self.sentTimestamps.remove(old) }
            }
            if !self.isConnected() {
                self.offlineQueue.append((eventType, payload))
                return
            }
            self.postWithRetry(eventType: eventType, payload: payload, attempt: 1)
        }
    }

    // MARK: – Private

    private func postWithRetry(eventType: String, payload: [String: Any], attempt: Int) {
        guard let url else { return }
        let code = httpPost(url: url, headers: headers, body: payload)
        if (200...299).contains(code) {
            NotificationCenter.default.post(
                name: .BGPrioritySyncSuccess,
                object: nil,
                userInfo: ["eventType": eventType, "attemptNumber": attempt]
            )
        } else if attempt < maxRetries {
            let delay = retryDelays.indices.contains(attempt - 1)
                ? retryDelays[attempt - 1]
                : (retryDelays.last ?? 60)
            queue.asyncAfter(deadline: .now() + delay) { [weak self] in
                self?.postWithRetry(eventType: eventType, payload: payload, attempt: attempt + 1)
            }
        } else {
            NotificationCenter.default.post(
                name: .BGPrioritySyncFailed,
                object: nil,
                userInfo: ["eventType": eventType, "httpStatus": code, "attempts": attempt]
            )
        }
    }

    // Reads the cached state kept current by the persistent monitor. Called on `queue`,
    // where the flag is also written, so no probe and no race.
    private func isConnected() -> Bool { isNetworkAvailable }

    private func startMonitoring() {
        let m = NWPathMonitor()
        m.pathUpdateHandler = { [weak self] path in
            guard let self else { return }
            let available = path.status == .satisfied
            self.queue.async {
                self.isNetworkAvailable = available
                if available { self.flushOfflineQueue() }
            }
        }
        m.start(queue: DispatchQueue(label: "gachlab.psm.monitor"))
        monitor = m
    }

    private func flushOfflineQueue() {
        guard !offlineQueue.isEmpty else { return }
        let pending = offlineQueue
        offlineQueue.removeAll()
        pending.forEach { (type, payload) in
            postWithRetry(eventType: type, payload: payload, attempt: 1)
        }
    }

    private func httpPost(url: URL, headers: [String: String]?, body: [String: Any]) -> Int {
        guard let data = try? JSONSerialization.data(withJSONObject: body) else { return -1 }
        var request = URLRequest(url: url, timeoutInterval: 30)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        headers?.forEach { request.setValue($1, forHTTPHeaderField: $0) }
        request.httpBody = data

        var responseCode = -1
        let semaphore = DispatchSemaphore(value: 0)
        URLSession.shared.dataTask(with: request) { _, response, _ in
            responseCode = (response as? HTTPURLResponse)?.statusCode ?? -1
            semaphore.signal()
        }.resume()
        _ = semaphore.wait(timeout: .now() + 35)
        return responseCode
    }
}
