// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import CoreMotion
import UIKit

public protocol SensorFusionListener: AnyObject {
    func onCrash(impactG: Double, location: BGLocation?)
    func onPhoneUsageWhileDriving(location: BGLocation?)
}

public final class SensorFusionDetector {

    private var motionManager = CMMotionManager()
    private var queue = OperationQueue()
    private var started = false

    public var enabled = false
    public var crashImpactG = 3.0
    public var crashCooldownMs = 10_000.0
    // Phone usage by sensor runs only under sensor fusion; otherwise the GPS bearing-jitter
    // path in DrivingEventsDetector owns it. Gating here avoids double-counting the same
    // distraction (matches the Android SensorFusionDetector).
    public var sensorFusion = false
    public var phoneUsageWindowMs = 4_000.0
    public var phoneUsageCooldownMs = 60_000.0

    public var tripActive = false
    public var lastLocation: BGLocation?

    public weak var listener: SensorFusionListener?

    private var lastCrashAt: TimeInterval = 0
    private var lastPhoneUsageAt: TimeInterval = 0
    private var jitterAboveSince: TimeInterval = 0

    public init() {
        queue.name = "com.gachlab.geolocation.motion"
        queue.maxConcurrentOperationCount = 1
    }

    public var isAvailable: Bool {
        CMMotionManager().isDeviceMotionAvailable
    }

    public func start() {
        guard !started, CMMotionManager().isDeviceMotionAvailable else { return }
        started = true
        motionManager.deviceMotionUpdateInterval = 1.0 / 50.0
        motionManager.startDeviceMotionUpdates(to: queue) { [weak self] data, _ in
            guard let self = self, let data = data else { return }
            self.processMotion(data)
        }
    }

    public func stop() {
        motionManager.stopDeviceMotionUpdates()
        started = false
    }

    private func processMotion(_ data: CMDeviceMotion) {
        let ax = data.userAcceleration.x
        let ay = data.userAcceleration.y
        let az = data.userAcceleration.z
        let accelMag = sqrt(ax * ax + ay * ay + az * az)

        let rx = data.rotationRate.x
        let ry = data.rotationRate.y
        let rz = data.rotationRate.z
        let gyroMag = sqrt(rx * rx + ry * ry + rz * rz)

        let now = Date().timeIntervalSinceReferenceDate

        // Crash detection
        if tripActive && accelMag >= crashImpactG {
            let elapsedMs = (now - lastCrashAt) * 1000.0
            if elapsedMs >= crashCooldownMs {
                lastCrashAt = now
                let loc = lastLocation
                DispatchQueue.main.async { [weak self] in
                    self?.listener?.onCrash(impactG: accelMag, location: loc)
                }
            }
        }

        // Phone usage detection (sensor-fusion only; GPS path handles it otherwise)
        guard sensorFusion, tripActive else { return }

        var appIsActive = false
        if Thread.isMainThread {
            appIsActive = UIApplication.shared.applicationState == .active
        } else {
            DispatchQueue.main.sync {
                appIsActive = UIApplication.shared.applicationState == .active
            }
        }

        guard appIsActive else {
            jitterAboveSince = 0
            return
        }

        if accelMag >= 0.5 || gyroMag >= 0.7 {
            if jitterAboveSince == 0 {
                jitterAboveSince = now
            } else {
                let windowElapsedMs = (now - jitterAboveSince) * 1000.0
                let cooldownElapsedMs = (now - lastPhoneUsageAt) * 1000.0
                if windowElapsedMs >= phoneUsageWindowMs && cooldownElapsedMs >= phoneUsageCooldownMs {
                    lastPhoneUsageAt = now
                    jitterAboveSince = 0
                    let loc = lastLocation
                    DispatchQueue.main.async { [weak self] in
                        self?.listener?.onPhoneUsageWhileDriving(location: loc)
                    }
                }
            }
        } else {
            jitterAboveSince = 0
        }
    }
}
