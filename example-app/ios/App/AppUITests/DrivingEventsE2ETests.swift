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
        XCTAssert(waitForEvent("event:possibleCrash", timeout: 40), "possibleCrash event not fired within 40 s")
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
