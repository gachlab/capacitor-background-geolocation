// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import UIKit

public final class BGBackgroundTaskManager {

    public static let shared = BGBackgroundTaskManager()

    private var taskCounter: UInt = 0
    private var identifiers: [UInt: UIBackgroundTaskIdentifier] = [:]
    private let lock = NSLock()

    private init() {}

    public func beginTask() -> UInt {
        lock.lock()
        let key = taskCounter
        taskCounter &+= 1
        lock.unlock()

        var bgId: UIBackgroundTaskIdentifier = .invalid
        if Thread.isMainThread {
            bgId = UIApplication.shared.beginBackgroundTask(expirationHandler: { [weak self] in
                self?.endTask(key: key)
            })
        } else {
            DispatchQueue.main.sync {
                bgId = UIApplication.shared.beginBackgroundTask(expirationHandler: { [weak self] in
                    self?.endTask(key: key)
                })
            }
        }

        lock.lock()
        identifiers[key] = bgId
        lock.unlock()

        return key
    }

    public func endTask(key: UInt) {
        lock.lock()
        let bgId = identifiers.removeValue(forKey: key)
        lock.unlock()

        guard let bgId = bgId, bgId != .invalid else { return }

        if Thread.isMainThread {
            UIApplication.shared.endBackgroundTask(bgId)
        } else {
            DispatchQueue.main.async {
                UIApplication.shared.endBackgroundTask(bgId)
            }
        }
    }
}
