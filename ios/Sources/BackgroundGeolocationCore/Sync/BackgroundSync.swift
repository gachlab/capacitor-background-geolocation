// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

final class BackgroundSync: NSObject, URLSessionDelegate, URLSessionTaskDelegate {

    static let shared = BackgroundSync()

    private var session: URLSession?

    private struct TaskMeta {
        let fileURL: URL
        let cutoffDate: Date
        let batch: BatchProgress
        /// Locations carried by this task: 1 in `single` mode, N in `batch` mode.
        let locationsInTask: Int
    }

    /// Shared, mutable progress accumulator for one sync() call. Aggregates the
    /// per-task completions into a single start/success and granular `syncProgress`.
    /// `sentLocations` (not `succeeded`) is reported as `sent` so `batch` mode — where
    /// one task carries N locations — reports N, not 1.
    private final class BatchProgress {
        let total: Int
        var completed = 0
        var succeeded = 0
        var sentLocations = 0
        var firstFailure: (status: Int, message: String)?
        init(total: Int) { self.total = total }
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
        let mode = (config.syncMode ?? "batch").lowercased()
        // Shared batch id so the BE can correlate one flush (parity with Android).
        let batchId = String(Int64(cutoffDate.timeIntervalSince1970 * 1000))

        // Builds one upload-from-file request; returns nil if serialisation/IO fails.
        func makeUpload(_ jsonObject: Any, locationsInTask: Int) -> (request: URLRequest, fileURL: URL, count: Int)? {
            guard let bodyData = try? JSONSerialization.data(withJSONObject: jsonObject, options: []) else { return nil }
            guard let docsDir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first else { return nil }
            let fileURL = docsDir.appendingPathComponent("locations_\(UUID().uuidString).json")
            do { try bodyData.write(to: fileURL, options: .atomic) } catch { return nil }

            var request = URLRequest(url: syncURL)
            request.httpMethod = method
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.setValue(batchId, forHTTPHeaderField: "x-batch-id")
            if let headers = config.httpHeaders {
                for (k, v) in headers { request.setValue(v, forHTTPHeaderField: k) }
            }
            return (request, fileURL, locationsInTask)
        }

        // `single` → one request per location (single JSON object, what the BE
        // expects for real-time). `batch` → one request carrying a JSON array.
        var uploads = [(request: URLRequest, fileURL: URL, count: Int)]()
        if mode == "single" {
            for location in locations {
                if let u = makeUpload(location.toResult(from: config.resolvedTemplate), locationsInTask: 1) {
                    uploads.append(u)
                }
            }
        } else {
            let array = locations.map { $0.toResult(from: config.resolvedTemplate) }
            if let u = makeUpload(array, locationsInTask: locations.count) {
                uploads.append(u)
            }
        }

        guard !uploads.isEmpty else { return }

        let batch = BatchProgress(total: uploads.count)
        // One start per flush (was per-location), matching Android.
        NotificationCenter.default.post(name: .BGBackgroundSyncDidStart, object: nil)
        BGLog.shared.i("Sync start: \(locations.count) locations (mode=\(mode))")

        for item in uploads {
            let task = session.uploadTask(with: item.request, fromFile: item.fileURL)
            lock.lock()
            taskMeta[task.taskIdentifier] = TaskMeta(
                fileURL: item.fileURL,
                cutoffDate: cutoffDate,
                batch: batch,
                locationsInTask: item.count
            )
            lock.unlock()
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

        // Per-task outcome.
        let cutoff = meta?.cutoffDate ?? Date()
        var success = false
        var status  = 0
        var message = ""
        if let error = error {
            message = error.localizedDescription
        } else if let httpResponse = task.response as? HTTPURLResponse {
            status = httpResponse.statusCode
            success = (status >= 200 && status < 300)
            if !success { message = "" }
        } else {
            message = "No HTTP response"
        }

        // Per-task DB side effects (unchanged): clear sent rows / restore failed.
        if success {
            LocationDAO.shared.deleteSyncedLocationsBefore(cutoff)
        } else {
            LocationDAO.shared.restoreFailedSyncLocations()
        }

        guard let batch = meta?.batch else { return }

        // Aggregate the batch under the lock: granular progress on every task,
        // and a single success/fail once the whole batch has settled.
        lock.lock()
        batch.completed += 1
        if success {
            batch.succeeded += 1
            batch.sentLocations += (meta?.locationsInTask ?? 0)
        } else if batch.firstFailure == nil {
            batch.firstFailure = (status, message)
        }
        let progress = Int((Double(batch.completed) / Double(batch.total)) * 100)
        let finished = batch.completed >= batch.total
        let sentLocations = batch.sentLocations
        let failure = batch.firstFailure
        lock.unlock()

        NotificationCenter.default.post(
            name: .BGBackgroundSyncDidProgress,
            object: nil,
            userInfo: ["progress": progress]
        )

        guard finished else { return }

        if sentLocations > 0 {
            NotificationCenter.default.post(
                name: .BGBackgroundSyncDidSucceed,
                object: nil,
                userInfo: ["sent": sentLocations]
            )
            BGLog.shared.i("Sync success: \(sentLocations) sent")
        }
        if let f = failure {
            var userInfo: [String: Any] = ["httpStatus": f.status, "message": f.message]
            if f.status == 285 {
                userInfo["requested_abort"] = true
            }
            NotificationCenter.default.post(
                name: .BGBackgroundSyncDidFail,
                object: nil,
                userInfo: userInfo
            )
            BGLog.shared.w("Sync failed HTTP \(f.status): \(f.message)")
        }
    }
}
