// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

final class BackgroundSync: NSObject, URLSessionDelegate, URLSessionTaskDelegate {

    static let shared = BackgroundSync()

    private var session: URLSession?

    private struct TaskMeta {
        let fileURL: URL
        let cutoffDate: Date
        let locationCount: Int
    }

    private var taskMeta = [Int: TaskMeta]()
    private let lock = NSLock()

    private override init() {
        super.init()
    }

    func start() {
        let config = URLSessionConfiguration.background(
            withIdentifier: "com.gachlab.geolocation.sync"
        )
        config.isDiscretionary = false
        config.sessionSendsLaunchEvents = true
        session = URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }

    func cancel() {
        session?.invalidateAndCancel()
        session = nil
    }

    func sync(locations: [BGLocation], config: BGConfig) {
        guard let session = session else { return }
        guard let syncUrl = config.syncUrl, !syncUrl.isEmpty else { return }

        let method = (config.syncHttpMethod ?? "POST").uppercased()
        // Parse the configured URL ONCE (it's invariant across the loop). A
        // malformed syncUrl previously force-unwrapped (`URL(string:)!`) inside
        // the loop and crashed the whole sync path on every flush. Matches the
        // per-location path (PostLocationTask uses `guard let`).
        guard let syncURL = URL(string: syncUrl) else { return }
        let cutoffDate = Date()

        for location in locations {
            guard let bodyObj = try? JSONSerialization.data(
                withJSONObject: location.toResult(from: config.resolvedTemplate),
                options: []
            ) else { continue }

            guard let docsDir = FileManager.default.urls(
                for: .documentDirectory, in: .userDomainMask
            ).first else { continue }
            let fileURL = docsDir.appendingPathComponent("locations_\(UUID().uuidString).json")

            do {
                try bodyObj.write(to: fileURL, options: .atomic)
            } catch {
                continue
            }

            var request = URLRequest(url: syncURL)
            request.httpMethod = method
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            if let headers = config.httpHeaders {
                for (k, v) in headers { request.setValue(v, forHTTPHeaderField: k) }
            }

            let task = session.uploadTask(with: request, fromFile: fileURL)

            lock.lock()
            taskMeta[task.taskIdentifier] = TaskMeta(
                fileURL: fileURL,
                cutoffDate: cutoffDate,
                locationCount: 1
            )
            lock.unlock()

            NotificationCenter.default.post(name: .BGBackgroundSyncDidStart, object: nil)
            task.resume()
        }
    }

    // MARK: - URLSessionTaskDelegate

    func urlSession(
        _ session: URLSession,
        task: URLSessionTask,
        didCompleteWithError error: Error?
    ) {
        lock.lock()
        let meta = taskMeta.removeValue(forKey: task.taskIdentifier)
        lock.unlock()

        if let fileURL = meta?.fileURL {
            try? FileManager.default.removeItem(at: fileURL)
        }

        if let error = error {
            LocationDAO.shared.restoreFailedSyncLocations()
            NotificationCenter.default.post(
                name: .BGBackgroundSyncDidFail,
                object: nil,
                userInfo: ["httpStatus": 0, "message": error.localizedDescription]
            )
            return
        }

        guard let httpResponse = task.response as? HTTPURLResponse else {
            LocationDAO.shared.restoreFailedSyncLocations()
            NotificationCenter.default.post(
                name: .BGBackgroundSyncDidFail,
                object: nil,
                userInfo: ["httpStatus": 0, "message": "No HTTP response"]
            )
            return
        }

        let status = httpResponse.statusCode
        let cutoff = meta?.cutoffDate ?? Date()
        let count  = meta?.locationCount ?? 0

        if status >= 200 && status < 300 {
            LocationDAO.shared.deleteSyncedLocationsBefore(cutoff)
            NotificationCenter.default.post(
                name: .BGBackgroundSyncDidSucceed,
                object: nil,
                userInfo: ["sent": count]
            )
        } else {
            LocationDAO.shared.restoreFailedSyncLocations()
            var userInfo: [String: Any] = ["httpStatus": status, "message": ""]
            if status == 285 {
                userInfo["requested_abort"] = true
            }
            NotificationCenter.default.post(
                name: .BGBackgroundSyncDidFail,
                object: nil,
                userInfo: userInfo
            )
        }
    }
}
