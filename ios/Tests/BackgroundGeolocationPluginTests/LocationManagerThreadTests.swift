// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import CoreLocation
import XCTest
@testable import BackgroundGeolocationCore
@testable import BackgroundGeolocationPlugin

/// Every manager with a delegate is built on the main thread.
///
/// Core Location answers on the run loop of the thread that created the manager.
/// Built on Capacitor's plugin queue, it never answers: a fresh iOS install of the
/// Pickup driver app spun forever after the driver granted location, because
/// `requestPermissions` waited for an authorization callback that had nowhere to
/// land. Nothing else noticed — the request DID reach iOS and the prompt DID show.
///
/// Each test builds its subject from a background queue, the way a plugin call
/// does, and records which thread the manager was really constructed on.
final class LocationManagerThreadTests: XCTestCase {

    private var original: (() -> CLLocationManager)!
    private var builtOnMain: [Bool] = []
    private let lock = NSLock()

    override func setUp() {
        super.setUp()
        original = LocationManagerFactory.construct
        LocationManagerFactory.construct = { [unowned self] in
            self.lock.lock(); self.builtOnMain.append(Thread.isMainThread); self.lock.unlock()
            return CLLocationManager()
        }
    }

    override func tearDown() {
        LocationManagerFactory.construct = original
        super.tearDown()
    }

    /// Runs `body` on a background queue and waits for it, keeping main free to
    /// serve the factory's `main.sync`.
    private func fromBackground(_ body: @escaping () -> Void) {
        let done = expectation(description: "built off main")
        DispatchQueue.global(qos: .userInitiated).async {
            body()
            done.fulfill()
        }
        wait(for: [done], timeout: 5)
    }

    private func assertBuiltOnMain(_ what: String, file: StaticString = #filePath, line: UInt = #line) {
        lock.lock(); let seen = builtOnMain; lock.unlock()
        XCTAssertFalse(seen.isEmpty, "\(what) did not build its manager through LocationManagerFactory", file: file, line: line)
        XCTAssertTrue(seen.allSatisfy { $0 }, "\(what) built its manager off the main thread", file: file, line: line)
    }

    // MARK: - The factory itself

    func testFactoryBuildsOnMainWhenCalledFromBackground() {
        fromBackground { _ = LocationManagerFactory.make() }
        assertBuiltOnMain("LocationManagerFactory.make()")
    }

    // MARK: - Each manager that has a delegate

    /// The one that hung: `requestPermissions` on a fresh install.
    func testPermissionRequestHelperBuildsItsManagerOnMain() {
        var helper: PermissionRequestHelper?
        fromBackground { helper = PermissionRequestHelper { _ in } }
        XCTAssertNotNil(helper)
        assertBuiltOnMain("PermissionRequestHelper")
    }

    /// Same shape: `start()` was moved to main, the construction was not.
    func testOneShotLocationHelperBuildsItsManagerOnMain() {
        var helper: OneShotLocationHelper?
        fromBackground { helper = OneShotLocationHelper(enableHighAccuracy: false) { _, _ in } }
        XCTAssertNotNil(helper)
        assertBuiltOnMain("OneShotLocationHelper")
    }

    /// Built by `BGFacade.getProvider`, which a plugin call reaches off main.
    func testDistanceFilterProviderBuildsItsManagerOnMain() {
        var provider: DistanceFilterLocationProvider?
        fromBackground { provider = DistanceFilterLocationProvider() }
        XCTAssertNotNil(provider)
        assertBuiltOnMain("DistanceFilterLocationProvider")
    }

    // MARK: - The class, not the instances

    /// A new `CLLocationManager()` with a delegate, anywhere, reopens the defect.
    ///
    /// The shared managers (`BGLocationManager`, `GeofenceManager`) cannot be
    /// rebuilt from a test — their `init` is private and runs once — so this is
    /// what holds them. The only direct constructions allowed are reads that need
    /// no delegate, chained on the same line.
    func testNoManagerWithADelegateIsBuiltOutsideTheFactory() throws {
        let sources = URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()   // BackgroundGeolocationPluginTests
            .deletingLastPathComponent()   // Tests
            .deletingLastPathComponent()   // ios
            .appendingPathComponent("Sources")
        let files = FileManager.default.enumerator(at: sources, includingPropertiesForKeys: nil)?
            .compactMap { $0 as? URL }
            .filter { $0.pathExtension == "swift" && $0.lastPathComponent != "LocationManagerFactory.swift" } ?? []
        XCTAssertFalse(files.isEmpty, "found no Swift sources under \(sources.path)")

        var offenders: [String] = []
        for file in files {
            let text = try String(contentsOf: file, encoding: .utf8)
            for (n, raw) in text.components(separatedBy: .newlines).enumerated() {
                let line = raw.trimmingCharacters(in: .whitespaces)
                guard !line.hasPrefix("//"), line.contains("CLLocationManager()") else { continue }
                let readOnly = line.contains("CLLocationManager().authorizationStatus")
                    || line.contains("CLLocationManager().accuracyAuthorization")
                if !readOnly {
                    offenders.append("\(file.lastPathComponent):\(n + 1): \(line)")
                }
            }
        }
        XCTAssertEqual(offenders, [], "build these through LocationManagerFactory.make()")
    }
}
