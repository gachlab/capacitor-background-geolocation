// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import XCTest

// End-to-end driving-events tests for iOS Simulator.
//
// GPS injection is done EXTERNALLY by e2e-ios-driving-events.sh via
// `xcrun simctl location set` running in the host shell process (not here).
// This test only handles UI interaction (Configure, Start) and event verification.
//
// The shell script must be started BEFORE this test runs. It continuously
// injects a moving GPS fix every 500 ms so that by the time the plugin is
// started, speed is already non-zero and driving events fire within seconds.

final class DrivingEventsE2ETests: XCTestCase {

    private let app = XCUIApplication()

    // SpringBoard — used to dismiss system permission dialogs directly.
    private let springboard = XCUIApplication(bundleIdentifier: "com.apple.springboard")

    override func setUpWithError() throws {
        continueAfterFailure = false
        app.launch()

        addUIInterruptionMonitor(withDescription: "System alert") { alert in
            for label in ["Allow While Using App", "Allow Once", "Allow", "OK", "Continue"] {
                let btn = alert.buttons[label]
                if btn.exists { btn.tap(); return true }
            }
            return false
        }
        let webView = app.webViews.firstMatch
        XCTAssert(webView.waitForExistence(timeout: 20), "WebView did not load in 20 s")
    }

    // Dismiss any system alert (location / notification / etc.) visible in SpringBoard.
    private func dismissSystemAlerts() {
        for label in ["Allow While Using App", "Allow Once", "Allow", "OK", "Continue", "Don't Allow"] {
            let btn = springboard.buttons[label]
            if btn.exists { btn.tap(); return }
        }
        // Trigger interruption monitor as a fallback
        app.tap()
    }

    override func tearDownWithError() throws {
        app.terminate()
    }

    // MARK: - Tests

    func testSpeedingEventFires() throws {
        tapConfigure()
        tapStart()
        // GPS is injected by the external shell script; allow up to 40 s for the
        // speeding event to appear in the WebView log.
        XCTAssert(waitForEvent("event:speeding", timeout: 40), "speeding event not fired within 40 s")
    }

    func testPossibleCrashEventFires() throws {
        tapConfigure()
        tapStart()
        // possibleCrash needs a full decel cycle (high-speed → near-stop → confirm),
        // which the injector reaches once per ~cycle. On a slow CI runner a single
        // cycle can eat most of a 40 s budget, leaving one fragile attempt. Allow
        // 120 s so several decel cycles land — turns a single chance into ~3+.
        XCTAssert(waitForEvent("event:possibleCrash", timeout: 120), "possibleCrash event not fired within 120 s")
    }

    // MARK: - Geofencing (#20)
    //
    // The external script injects a STATIONARY fix at the geofence center
    // (37.3349, -122.009 — matches GF_CENTER in www/main.js), so the device is
    // already inside the region when it registers.

    // Initial ENTER (GMS INITIAL_TRIGGER_ENTER parity): registering a geofence the
    // device is already inside must synthesise an ENTER from requestState(.inside).
    // The 4 s loiteringDelay then fires a foreground DWELL via the Timer fast-path.
    func testGeofenceInitialEnterAndDwell() throws {
        tapConfigure()
        tapStart()
        clearGeofences()
        tapGeofenceButton("GF: enter-here")
        XCTAssert(waitForEvent("event:geofenceEnter", timeout: 30),
                  "initial ENTER (already-inside) not synthesised")
        XCTAssert(waitForEvent("event:geofenceDwell", timeout: 30),
                  "DWELL not fired after loiteringDelay")
        clearGeofences()
    }

    // Region cap: iOS allows 19 user geofences (20 app-wide − 1 stationary). Registering
    // 21 must surface a geofence `error` (code 1005) for the overflow instead of failing
    // silently.
    func testGeofenceLimitEmitsError() throws {
        tapConfigure()
        tapStart()
        clearGeofences()
        tapGeofenceButton("GF: 21 geofences")
        XCTAssert(waitForEvent("event:geofenceError", timeout: 30),
                  "geofence limit (>19) did not surface a geofenceError")
        clearGeofences()
    }

    // Suspension-resilient DWELL: after ENTER, background the app; the per-region Timer
    // may be suspended, but evaluateDwell driven by incoming fixes still fires DWELL.
    // On return to foreground the WebView log shows it.
    func testGeofenceDwellSurvivesBackground() throws {
        tapConfigure()
        tapStart()
        clearGeofences()
        tapGeofenceButton("GF: enter-here")
        XCTAssert(waitForEvent("event:geofenceEnter", timeout: 30), "ENTER not fired before backgrounding")
        // Background before the 4 s Timer would fire, then let injected fixes drive dwell.
        XCUIDevice.shared.press(.home)
        Thread.sleep(forTimeInterval: 8)
        app.activate()
        XCTAssert(waitForEvent("event:geofenceDwell", timeout: 30),
                  "DWELL did not survive the background transition")
        clearGeofences()
    }

    // MARK: - Geofence helpers

    private func tapGeofenceButton(_ label: String) {
        let webView = app.webViews.firstMatch
        let btn = webView.buttons[label]
        XCTAssert(btn.waitForExistence(timeout: 10), "Geofence button '\(label)' not found")
        btn.tap()
        sleep(1)
    }

    private func clearGeofences() {
        let webView = app.webViews.firstMatch
        let btn = webView.buttons["GF: clear"]
        if btn.waitForExistence(timeout: 5) { btn.tap(); sleep(1) }
    }

    // MARK: - Helpers

    private func tapConfigure() {
        let webView = app.webViews.firstMatch
        let btn = webView.buttons["Configure"]
        XCTAssert(btn.waitForExistence(timeout: 30), "Configure button not found in WebView")
        btn.tap()
        sleep(1)
        dismissSystemAlerts()
        sleep(1)
    }

    private func tapStart() {
        let webView = app.webViews.firstMatch
        let btn = webView.buttons["Start"]
        XCTAssert(btn.waitForExistence(timeout: 5), "Start button not found")
        btn.tap()
        sleep(1)
        dismissSystemAlerts()
        sleep(1)
        dismissSystemAlerts()
        sleep(2)

        // Verify service actually started — the 'start' event sets service-status to "running"
        let statusPred = NSPredicate(format: "label == 'running'")
        let statusEl = webView.staticTexts.matching(statusPred).firstMatch
        if !statusEl.waitForExistence(timeout: 15) {
            // Dump accessibility tree to diagnose
            print("=== service-status 'running' not found; WebView texts ===")
            webView.staticTexts.allElementsBoundByIndex.forEach { print("  '\($0.label)'") }
            XCTFail("Service did not start within 15 s (service-status never 'running')")
        }
    }

    private func waitForEvent(_ label: String, timeout: TimeInterval) -> Bool {
        // Check the last-event span (updated with each event name, e.g. "event:speeding")
        // and also the full log div as a fallback.
        let webView = app.webViews.firstMatch
        let exactPred = NSPredicate(format: "label == %@", label)
        let containsPred = NSPredicate(format: "label CONTAINS %@", label)
        let lastEvent = webView.staticTexts.matching(exactPred).firstMatch
        let logText   = webView.staticTexts.matching(containsPred).firstMatch
        let deadline = Date().addingTimeInterval(timeout)
        while Date() < deadline {
            if lastEvent.exists || logText.exists { return true }
            Thread.sleep(forTimeInterval: 1)
        }
        return false
    }
}
