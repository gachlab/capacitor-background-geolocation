// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation
import UserNotifications

open class AbstractLocationProvider: NSObject, LocationProvider {

    // MARK: - LocationProvider

    open weak var delegate: LocationProviderDelegate?

    // MARK: - Config

    private(set) public var config: BGConfig = BGConfig(defaults: ())

    // MARK: - Lifecycle

    open func onCreate() {}

    open func onDestroy() {}

    open func onConfigure(_ config: BGConfig) throws {
        self.config = config
    }

    open func onStart() throws {}

    open func onStop() throws {}

    open func onSwitchMode(_ mode: BGOperationalMode) {}

    open func onTerminate() {}

    // MARK: - Helpers

    public func sendDebugNotification(_ message: String) {
        guard config.isDebugging else { return }
        let content = UNMutableNotificationContent()
        content.title = "BackgroundGeolocation"
        content.body = message
        content.sound = .default
        let request = UNNotificationRequest(
            identifier: UUID().uuidString,
            content: content,
            trigger: nil
        )
        UNUserNotificationCenter.current().add(request, withCompletionHandler: nil)
    }

    public func runOnMain(_ block: @escaping () -> Void) {
        if Thread.isMainThread {
            block()
        } else {
            DispatchQueue.main.async(execute: block)
        }
    }
}
