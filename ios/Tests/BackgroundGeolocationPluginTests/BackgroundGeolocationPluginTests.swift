// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import XCTest
import CoreLocation
import Capacitor
@testable import BackgroundGeolocationPlugin

final class BackgroundGeolocationPluginTests: XCTestCase {

    // ── Instantiation ─────────────────────────────────────────────────────────

    func testPluginExists() {
        let plugin = BackgroundGeolocationPlugin()
        XCTAssertNotNil(plugin)
    }

    // ── Static metadata ───────────────────────────────────────────────────────

    func testPluginIdentifier() {
        let plugin = BackgroundGeolocationPlugin()
        XCTAssertEqual(plugin.identifier, "BackgroundGeolocationPlugin")
    }

    func testPluginJsName() {
        let plugin = BackgroundGeolocationPlugin()
        XCTAssertEqual(plugin.jsName, "BackgroundGeolocation")
    }

    // ── Plugin method registry ────────────────────────────────────────────────
    // Verifies that all methods declared in definitions.ts are registered in
    // the Swift bridge. Missing entries cause Capacitor to silently ignore calls.

    func testPluginMethodsContainsAllTrackingMethods() {
        let plugin = BackgroundGeolocationPlugin()
        let names = Set(plugin.pluginMethods.map { $0.name })

        let required = [
            "configure", "start", "stop", "switchMode", "checkStatus",
            "getCurrentLocation", "getStationaryLocation",
            "getLocations", "getValidLocations", "getValidLocationsAndDelete",
            "deleteLocation", "deleteAllLocations",
        ]
        for method in required {
            XCTAssertTrue(names.contains(method), "pluginMethods missing: \(method)")
        }
    }

    func testPluginMethodsContainsAllSyncMethods() {
        let plugin = BackgroundGeolocationPlugin()
        let names = Set(plugin.pluginMethods.map { $0.name })

        let required = ["forceSync", "clearSync", "getPendingSyncCount"]
        for method in required {
            XCTAssertTrue(names.contains(method), "pluginMethods missing: \(method)")
        }
    }

    func testPluginMethodsContainsAllSessionMethods() {
        let plugin = BackgroundGeolocationPlugin()
        let names = Set(plugin.pluginMethods.map { $0.name })

        let required = [
            "startSession", "clearSession",
            "getSessionLocations", "getSessionLocationsCount",
        ]
        for method in required {
            XCTAssertTrue(names.contains(method), "pluginMethods missing: \(method)")
        }
    }

    func testPluginMethodsContainsAllPermissionMethods() {
        let plugin = BackgroundGeolocationPlugin()
        let names = Set(plugin.pluginMethods.map { $0.name })

        let required = [
            "checkPermissions", "requestPermissions",
            "requestBackgroundLocationPermission",
            "requestActivityRecognitionPermission",
            "requestNotificationPermission",
            "showAppSettings", "openSettings", "showLocationSettings",
        ]
        for method in required {
            XCTAssertTrue(names.contains(method), "pluginMethods missing: \(method)")
        }
    }

    func testPluginMethodsContainsAllDiagnosticMethods() {
        let plugin = BackgroundGeolocationPlugin()
        let names = Set(plugin.pluginMethods.map { $0.name })

        let required = [
            "getDiagnostics", "getPluginVersion",
            "isIgnoringBatteryOptimizations", "requestIgnoreBatteryOptimizations",
            "openBatterySettings", "openAutoStartSettings", "getManufacturerHelp",
            "getLogEntries", "getConfig",
        ]
        for method in required {
            XCTAssertTrue(names.contains(method), "pluginMethods missing: \(method)")
        }
    }

    func testPluginMethodsNoDuplicates() {
        let plugin = BackgroundGeolocationPlugin()
        let names = plugin.pluginMethods.map { $0.name }
        let unique = Set(names)
        XCTAssertEqual(names.count, unique.count, "pluginMethods contains duplicate entries")
    }

    func testAllMethodsUsePromiseReturnType() {
        let plugin = BackgroundGeolocationPlugin()
        for method in plugin.pluginMethods {
            XCTAssertEqual(
                method.returnType, CAPPluginReturnPromise,
                "\(method.name ?? "?") should use CAPPluginReturnPromise"
            )
        }
    }

    // ── iOS-only no-ops (battery / OEM helpers) ───────────────────────────────
    // These methods must resolve — not reject — on iOS.

    func testIsIgnoringBatteryOptimizationsResolves() {
        let plugin = BackgroundGeolocationPlugin()
        let mock = MockCAPPluginCall()
        plugin.isIgnoringBatteryOptimizations(mock.call)
        XCTAssertTrue(mock.resolved, "isIgnoringBatteryOptimizations should resolve")
        XCTAssertEqual(mock.resolvedData?["whitelisted"] as? Bool, true)
    }

    func testRequestIgnoreBatteryOptimizationsResolves() {
        let plugin = BackgroundGeolocationPlugin()
        let mock = MockCAPPluginCall()
        plugin.requestIgnoreBatteryOptimizations(mock.call)
        XCTAssertTrue(mock.resolved)
        XCTAssertEqual(mock.resolvedData?["whitelisted"] as? Bool, true)
    }

    func testOpenAutoStartSettingsResolvesWithOpenedFalse() {
        let plugin = BackgroundGeolocationPlugin()
        let mock = MockCAPPluginCall()
        plugin.openAutoStartSettings(mock.call)
        XCTAssertTrue(mock.resolved)
        XCTAssertEqual(mock.resolvedData?["opened"] as? Bool, false)
        XCTAssertEqual(mock.resolvedData?["manufacturer"] as? String, "apple")
    }

    func testGetManufacturerHelpResolvesWithEmptySteps() {
        let plugin = BackgroundGeolocationPlugin()
        let mock = MockCAPPluginCall()
        plugin.getManufacturerHelp(mock.call)
        XCTAssertTrue(mock.resolved)
        XCTAssertEqual(mock.resolvedData?["manufacturer"] as? String, "apple")
        let steps = mock.resolvedData?["steps"] as? [String]
        XCTAssertEqual(steps?.count, 0)
    }

    func testRequestBackgroundLocationPermissionReturnsNotRequired() {
        let plugin = BackgroundGeolocationPlugin()
        let mock = MockCAPPluginCall()
        plugin.requestBackgroundLocationPermission(mock.call)
        XCTAssertTrue(mock.resolved)
        XCTAssertEqual(mock.resolvedData?["notRequired"] as? Bool, true)
    }

    // ── Methods that require facade reject when facade is nil ─────────────────
    // facade is set in load(), which is not called in unit tests.
    // Every method guarded by `guard let facade = facade else { ... }` must reject
    // rather than crash.

    func testConfigureRejectsWhenFacadeIsNil() {
        let plugin = BackgroundGeolocationPlugin()
        let mock = MockCAPPluginCall()
        plugin.configure(mock.call)
        XCTAssertTrue(mock.rejected, "configure() must reject when facade is nil")
        XCTAssertNotNil(mock.rejectedMessage)
    }

    func testStartRejectsWhenFacadeIsNil() {
        let plugin = BackgroundGeolocationPlugin()
        let mock = MockCAPPluginCall()
        plugin.start(mock.call)
        XCTAssertTrue(mock.rejected, "start() must reject when facade is nil")
    }

    func testStopRejectsWhenFacadeIsNil() {
        let plugin = BackgroundGeolocationPlugin()
        let mock = MockCAPPluginCall()
        plugin.stop(mock.call)
        XCTAssertTrue(mock.rejected, "stop() must reject when facade is nil")
    }

    func testCheckStatusRejectsWhenFacadeIsNil() {
        let plugin = BackgroundGeolocationPlugin()
        let mock = MockCAPPluginCall()
        plugin.checkStatus(mock.call)
        XCTAssertTrue(mock.rejected, "checkStatus() must reject when facade is nil")
    }
}

// ── CallRecorder ──────────────────────────────────────────────────────────────

private final class CallRecorder {
    var resolved = false
    var rejected = false
    var resolvedData: PluginCallResultData?
    var rejectedMessage: String?
}

// ── MockCAPPluginCall ─────────────────────────────────────────────────────────
// Wraps a real CAPPluginCall with success/error callbacks to capture outcomes.
// Subclassing is not viable because resolve/reject are @objc dynamic public
// (not open) in Capacitor's binary xcframework — Swift blocks the override.
// The ObjC-bridged successHandler/errorHandler closures fire when the plugin
// calls call.resolve(_:) / call.reject(_:...) via the ObjC runtime.

final class MockCAPPluginCall {
    private let recorder: CallRecorder
    let call: CAPPluginCall

    var resolved: Bool { recorder.resolved }
    var rejected: Bool { recorder.rejected }
    var resolvedData: PluginCallResultData? { recorder.resolvedData }
    var rejectedMessage: String? { recorder.rejectedMessage }

    init() {
        let rec = CallRecorder()
        self.recorder = rec
        self.call = CAPPluginCall(
            callbackId: "test-\(UUID().uuidString)",
            methodName: "test",
            options: [:],
            success: { result, _ in
                rec.resolved = true
                rec.resolvedData = result?.data
            },
            error: { error in
                rec.rejected = true
                rec.rejectedMessage = error?.message
            }
        )
    }
}
