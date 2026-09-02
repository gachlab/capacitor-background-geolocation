// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import XCTest
@testable import BackgroundGeolocationCore

/// The iOS half of the rule that decides whether a shift is dead (#67).
///
/// #63 shipped this on Android only, so the same runaway lived on here: a 404
/// fell into the same bucket as a 500 or a timeout, the rows went back to
/// pending, and a shift deleted server-side was retried indefinitely.
///
/// These cases are a deliberate mirror of `ShiftGoneDetectorTest.kt`. The rule
/// is a contract between the two platforms, and two implementations that
/// disagree about what "the shift is gone" means would be worse than one
/// platform not having it at all — so if a case is added there, it belongs here.
///
/// Most of this file guards against FIRING. A rule that retires a shift too
/// eagerly is an outage with extra steps: on a moving vehicle, "no network" is
/// the normal case, not the exception.
final class ShiftGoneDetectorTests: XCTestCase {

    private let t0 = Date(timeIntervalSince1970: 1_000_000)
    private var window: TimeInterval { ShiftGoneDetector.windowSeconds }
    private var detector: ShiftGoneDetector { ShiftGoneDetector.shared }

    override func setUp() {
        super.setUp()
        // Process-wide singleton: without this a leftover run from another test
        // decides this one's outcome.
        detector.reset()
    }

    override func tearDown() {
        detector.reset()
        super.tearDown()
    }

    // MARK: - It fires

    func testASustainedMinuteOf404RetiresTheShift() {
        XCTAssertFalse(detector.observe(404, now: t0), "the first 404 only starts the clock")
        XCTAssertTrue(detector.observe(404, now: t0.addingTimeInterval(window)))
    }

    func testTwoRejectionsAMinuteApartAreEnough() {
        // The weakest case that should count, and why this is a clock and not a
        // counter: with a slow posting interval there is no third attempt to wait
        // for, and the evidence is already a minute old.
        XCTAssertFalse(detector.observe(404, now: t0))
        XCTAssertTrue(detector.observe(404, now: t0.addingTimeInterval(window + 1)))
    }

    func testTheBoundaryIsInclusive() {
        XCTAssertFalse(detector.observe(404, now: t0))
        XCTAssertFalse(
            detector.observe(404, now: t0.addingTimeInterval(window - 0.001)),
            "a millisecond short must not fire"
        )
        XCTAssertTrue(
            detector.observe(404, now: t0.addingTimeInterval(window)),
            "exactly the window must fire"
        )
    }

    // MARK: - It does not fire

    func testASingle404NeverRetires() {
        // One 404 can race a legitimate shift change.
        XCTAssertFalse(detector.observe(404, now: t0))
    }

    func testABurstOf404sInsideTheMinuteDoesNot() {
        // Volume must not decide — only elapsed time. The Android incident
        // measured 276 POSTs in 90 seconds.
        XCTAssertFalse(detector.observe(404, now: t0))
        for i in 0..<300 {
            let t = t0.addingTimeInterval(Double(i % 59))
            XCTAssertFalse(
                detector.observe(404, now: t),
                "no volume of 404s inside the window may retire a shift"
            )
        }
    }

    func testASuccessClearsTheRun() {
        // The only thing that can prove the shift exists.
        XCTAssertFalse(detector.observe(404, now: t0))
        XCTAssertFalse(detector.observe(200, now: t0.addingTimeInterval(1)))
        XCTAssertFalse(
            detector.observe(404, now: t0.addingTimeInterval(window + 10)),
            "a 404 after a success starts a fresh minute"
        )
    }

    func testABadBackendNeverRetiresAShift() {
        // 5xx means the backend is having a bad time, not that the shift is gone.
        // Retiring here would switch off tracking during an outage.
        for code in [500, 502, 503] {
            detector.reset()
            XCTAssertFalse(detector.observe(code, now: t0))
            XCTAssertFalse(
                detector.observe(code, now: t0.addingTimeInterval(window * 10)),
                "HTTP \(code) must never retire a shift"
            )
        }
    }

    func testNoNetworkNeverRetiresAShift() {
        // `0` is what this code leaves in `resultStatus` when the request never
        // produced an HTTP response — a tunnel, the commonest failure on a moving
        // vehicle. `-1` is the Android spelling of the same thing; both must be inert.
        for code in [0, -1] {
            detector.reset()
            XCTAssertFalse(detector.observe(code, now: t0))
            XCTAssertFalse(detector.observe(code, now: t0.addingTimeInterval(window * 10)))
        }
    }

    func testAnExpiredTokenNeverRetiresAShift() {
        // 401/403 are a token to refresh, not a missing shift.
        for code in [401, 403] {
            detector.reset()
            XCTAssertFalse(detector.observe(code, now: t0))
            XCTAssertFalse(
                detector.observe(code, now: t0.addingTimeInterval(window * 10)),
                "HTTP \(code) must never retire a shift"
            )
        }
    }

    // MARK: - The inconclusive middle

    func testANetworkDropMidRunNeitherCountsNorDestroysTheEvidence() {
        // The case worth being deliberate about. A backend that 404s, drops off
        // the network for ten minutes, and 404s again has not changed its answer,
        // so the clock must keep running. Resetting here would mean a driver
        // moving in and out of coverage never accumulates a full minute, and the
        // runaway never stops.
        XCTAssertFalse(detector.observe(404, now: t0))
        XCTAssertFalse(detector.observe(0, now: t0.addingTimeInterval(1)))
        XCTAssertFalse(detector.observe(503, now: t0.addingTimeInterval(2)))
        XCTAssertTrue(
            detector.observe(404, now: t0.addingTimeInterval(window)),
            "the 404 clock survives failures that say nothing about the shift"
        )
    }

    func testANewShiftDoesNotInheritThePreviousRun() {
        // What `reset()` is for, called from `BGFacade.start()`. The detector is
        // in-memory and process-wide, so without it a fresh shift could be retired
        // by evidence belonging to the last one.
        XCTAssertFalse(detector.observe(404, now: t0))
        XCTAssertNotNil(detector.startedAt)

        detector.reset()

        XCTAssertNil(detector.startedAt)
        XCTAssertFalse(
            detector.observe(404, now: t0.addingTimeInterval(window * 10)),
            "the clock must start again with the new shift"
        )
    }
}
