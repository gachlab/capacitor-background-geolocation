// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import XCTest
@testable import BackgroundGeolocationPlugin

// RecordingDelegate captures each callback as a string token (same pattern as the Java tests).
private final class RecordingDelegate: DrivingEventsDetectorDelegate {
    var events: [String] = []
    var lastScore: TripScore?

    func detectorOnMoving(_ l: DLLocation)              { events.append("moving") }
    func detectorOnStopped(_ l: DLLocation)             { events.append("stopped") }
    func detectorOnTripStart(_ l: DLLocation)           { events.append("tripStart") }
    func detectorOnTripEnd(_ l: DLLocation, journey: Journey) {
        events.append("tripEnd(dist=\(Int(journey.distanceMeters))m,dur=\(journey.durationMs)ms)")
        lastScore = journey.score
    }
    func detectorOnSpeeding(_ l: DLLocation, speedKmh: Double, limitKmh: Double) {
        events.append("speeding(\(Int(speedKmh))kmh)")
    }
    func detectorOnProviderChange(provider: String)     { events.append("provider:\(provider)") }
    func detectorOnHardBrake(_ l: DLLocation, decelMps2: Double) { events.append("hardBrake") }
    func detectorOnRapidAcceleration(_ l: DLLocation, accelMps2: Double) { events.append("rapidAccel") }
    func detectorOnSharpTurn(_ l: DLLocation, degPerSec: Double) { events.append("sharpTurn") }
    func detectorOnPossibleCrash(_ l: DLLocation, dropKmh: Double) { events.append("crash") }
    func detectorOnIdleStart(_ l: DLLocation, startedAt: Double) { events.append("idleStart") }
    func detectorOnIdleEnd(_ l: DLLocation, durationMs: Int64, startedAt: Double) { events.append("idleEnd") }
    func detectorOnPhoneUsageWhileDriving(_ l: DLLocation) { events.append("phoneUsage") }
}

// Checks that `sub` appears in `arr` in order (not necessarily contiguous).
private func assertSubsequence(_ arr: [String], contains sub: [String], file: StaticString = #file, line: UInt = #line) {
    var idx = 0
    for item in arr {
        if idx < sub.count && item == sub[idx] { idx += 1 }
    }
    XCTAssertEqual(idx, sub.count, "Expected subsequence \(sub) in \(arr)", file: file, line: line)
}

final class DrivingEventsDetectorTests: XCTestCase {

    // Short stopped-duration so tests don't need to wait 60 seconds.
    private static let stoppedSec = 0.01

    private func makeDetector(speedLimitKmh: Double = 0) -> (DrivingEventsDetector, RecordingDelegate) {
        let d = DrivingEventsDetector()
        let rec = RecordingDelegate()
        d.delegate = rec
        d.config.enabled = true
        d.config.stoppedDurationSec = Self.stoppedSec
        d.config.minTripDurationSec = 0
        if speedLimitKmh > 0 { d.config.speedLimitKmh = speedLimitKmh }
        return (d, rec)
    }

    private func loc(lat: Double = 0, lon: Double = 0, speedMps: Double = 0, bearing: Double? = nil) -> DLLocation {
        DLLocation(latitude: lat, longitude: lon, speed: speedMps, bearing: bearing, provider: nil)
    }

    // MARK: - happyPath: stationary → fast → hard brake → stopped

    func testHappyPath() throws {
        let (det, rec) = makeDetector(speedLimitKmh: 50)

        // Stationary fix — no events.
        det.feed(loc(speedMps: 0))
        XCTAssertFalse(det.tripActive)

        // Fast fix — moving + tripStart + speeding (25 m/s = 90 km/h > 50 km/h limit).
        det.feed(loc(lat: 0.001, speedMps: 25))
        XCTAssertTrue(det.tripActive)

        // Small sleep so the next feed has dt > 0 (needed for hardBrake check).
        Thread.sleep(forTimeInterval: 0.005)

        // Full stop — hardBrake fires; then after stoppedDuration, stopped + tripEnd.
        det.feed(loc(lat: 0.002, speedMps: 0))

        Thread.sleep(forTimeInterval: Self.stoppedSec * 1.5)
        det.feed(loc(lat: 0.002, speedMps: 0))      // trigger stopped

        assertSubsequence(rec.events, contains: ["moving", "tripStart", "hardBrake", "stopped"])
        XCTAssertTrue(rec.events.contains { $0.hasPrefix("speeding") }, "speeding not fired: \(rec.events)")
        XCTAssertTrue(rec.events.contains { $0.hasPrefix("tripEnd") }, "tripEnd not fired: \(rec.events)")
    }

    // MARK: - Speeding fires once per crossing

    func testSpeedingFiresOncePerCrossing() {
        let (det, rec) = makeDetector(speedLimitKmh: 30)

        det.feed(loc(speedMps: 9))    // 32.4 km/h — first crossing
        det.feed(loc(speedMps: 10))   // still over — no second event
        det.feed(loc(speedMps: 7))    // back under
        det.feed(loc(speedMps: 9))    // second crossing
        det.feed(loc(speedMps: 10))   // still over — no event

        let speedingCount = rec.events.filter { $0.hasPrefix("speeding") }.count
        XCTAssertEqual(speedingCount, 2, "speeding should fire exactly once per upward crossing, got: \(rec.events)")
    }

    // MARK: - Hard brake detected

    func testHardBrakeDetected() throws {
        let (det, _) = makeDetector()
        let rec = RecordingDelegate()
        det.delegate = rec
        det.config.hardBrakeMps2 = 3.5

        // Put detector into tripActive state.
        det.feed(loc(speedMps: 20))   // moving + tripStart (minTripDurationSec = 0)
        XCTAssertTrue(det.tripActive)

        // Sleep so dt > 0 on next feed.
        Thread.sleep(forTimeInterval: 0.005)

        // 20 → 0 m/s is a decel >> 3.5 m/s².
        det.feed(loc(speedMps: 0))

        XCTAssertTrue(rec.events.contains("hardBrake"), "hardBrake not in \(rec.events)")
    }

    // MARK: - TripEnd metrics

    func testTripEndMetrics() throws {
        let (det, rec) = makeDetector()
        det.config.stoppedDurationSec = 0   // fire stopped immediately on speed == 0

        // Fixes roughly 1 km apart north.
        det.feed(loc(lat: 0.000, lon: 0, speedMps: 15))
        det.feed(loc(lat: 0.009, lon: 0, speedMps: 15))   // ≈ 1 km north

        Thread.sleep(forTimeInterval: 0.005)

        det.feed(loc(lat: 0.009, lon: 0, speedMps: 0))    // stop

        let tripEnd = rec.events.first { $0.hasPrefix("tripEnd") }
        XCTAssertNotNil(tripEnd, "tripEnd not fired")

        // Check that distance > 0 and duration > 0 inside the token.
        if let te = tripEnd {
            XCTAssertFalse(te.contains("dist=0m"), "distance should be > 0 in \(te)")
            XCTAssertFalse(te.contains("dur=0ms"), "durationMs should be > 0 in \(te)")
        }
    }

    // MARK: - Stationary never fires tripStart

    func testStationaryNeverFiresTripStart() {
        let (det, rec) = makeDetector()
        det.config.minMovingSpeedMps = 1.0

        for _ in 0..<10 {
            det.feed(loc(speedMps: 0))
        }

        XCTAssertFalse(rec.events.contains("tripStart"), "tripStart should not fire for stationary fixes")
        XCTAssertFalse(det.tripActive)
    }

    // MARK: - Disabled detector fires no events

    func testDisabledDetectorFiresNoEvents() {
        let det = DrivingEventsDetector()
        let rec = RecordingDelegate()
        det.delegate = rec
        det.config.enabled = false    // explicitly disabled

        det.feed(loc(speedMps: 30))
        det.feed(loc(speedMps: 30))

        XCTAssertTrue(rec.events.isEmpty, "disabled detector should fire no events, got: \(rec.events)")
    }

    // MARK: - crashConfirmWindowSec

    func testCrashConfirmWindowFiresAfterWindow() {
        let (det, rec) = makeDetector()
        det.config.crashImpactKmh = 10
        det.config.crashWindowSec = 4.0
        det.config.crashConfirmWindowSec = 0.1   // 100 ms for fast test

        det.feed(loc(speedMps: 10))       // fast (36 km/h)
        Thread.sleep(forTimeInterval: 0.005)
        det.feed(loc(speedMps: 0))        // sudden stop — crash candidate

        // Before confirm window: no crash yet
        XCTAssertFalse(rec.events.contains("crash"), "crash should not fire before confirm window: \(rec.events)")

        Thread.sleep(forTimeInterval: 0.15) // wait > confirmWindowSec
        det.feed(loc(speedMps: 0))        // still stopped — fires confirmation

        XCTAssertTrue(rec.events.contains("crash"), "crash should fire after confirm window: \(rec.events)")
    }

    func testCrashConfirmWindowCancelledOnRecovery() {
        let (det, rec) = makeDetector()
        det.config.crashImpactKmh = 10
        det.config.crashWindowSec = 4.0
        det.config.crashConfirmWindowSec = 0.5

        det.feed(loc(speedMps: 10))       // fast
        Thread.sleep(forTimeInterval: 0.005)
        det.feed(loc(speedMps: 0))        // crash candidate

        Thread.sleep(forTimeInterval: 0.02)
        det.feed(loc(speedMps: 8))        // speed recovered — cancel

        Thread.sleep(forTimeInterval: 0.6)
        det.feed(loc(speedMps: 0))        // stopped again, but no pending crash

        XCTAssertFalse(rec.events.contains("crash"), "crash should be cancelled on speed recovery: \(rec.events)")
    }

    // MARK: - phoneUsageWhileDriving GPS heuristic

    func testPhoneUsageWhileDrivingFiresOnJitter() {
        let (det, rec) = makeDetector()
        det.config.sensorFusion = false
        det.config.phoneUsageWindowSec = 0.4    // 400 ms for fast test
        det.config.phoneUsageCooldownSec = 60.0

        // Enter trip-active state at ~20 km/h
        det.feed(loc(speedMps: 6))

        // Send jitter fixes: bearing oscillates ±10° at ~5.5 m/s (20 km/h)
        var bearing = 90.0
        for _ in 0..<6 {
            Thread.sleep(forTimeInterval: 0.07)
            bearing = bearing == 90.0 ? 100.0 : 90.0
            det.feed(loc(speedMps: 5.5, bearing: bearing))
        }

        Thread.sleep(forTimeInterval: 0.5)
        det.feed(loc(speedMps: 5.5, bearing: bearing))

        XCTAssertTrue(rec.events.contains("phoneUsage"), "phoneUsageWhileDriving should fire on jitter: \(rec.events)")
    }

    // #21 — phone usage must reach the aggregate trip score, not just fire the event.
    func testPhoneUsageReachesScore() {
        let (det, rec) = makeDetector()
        det.config.sensorFusion = false
        det.config.phoneUsageWindowSec = 0.4
        det.config.phoneUsageCooldownSec = 0.0
        det.config.sharpTurnDegPerSec = 100_000.0   // isolate: jitter must read as phone usage, not sharp turn
        det.config.crashImpactKmh = 100_000.0

        // Enter an active trip.
        det.feed(loc(speedMps: 6))

        // ~±10° bearing oscillation at ~5.5 m/s → phone-usage jitter.
        var bearing = 90.0
        for _ in 0..<6 {
            Thread.sleep(forTimeInterval: 0.07)
            bearing = bearing == 90.0 ? 100.0 : 90.0
            det.feed(loc(speedMps: 5.5, bearing: bearing))
        }
        Thread.sleep(forTimeInterval: 0.5)
        det.feed(loc(speedMps: 5.5, bearing: bearing))
        XCTAssertTrue(rec.events.contains("phoneUsage"), "phoneUsage should fire: \(rec.events)")

        // Stop to end the trip and deliver the score.
        det.feed(loc(speedMps: 0))
        Thread.sleep(forTimeInterval: Self.stoppedSec * 2)
        det.feed(loc(speedMps: 0))

        let score = rec.lastScore
        XCTAssertNotNil(score, "tripEnd should deliver a score: \(rec.events)")
        XCTAssertLessThan(score!.breakdown.phoneUsage, 100,
                          "phoneUsage category should be penalised, got \(score!.breakdown.phoneUsage)")
        XCTAssertTrue(score!.events.contains { $0.type == "phoneUsage" },
                      "phoneUsage should be among scored events: \(score!.events.map { $0.type })")
    }

    func testExternalPhoneUsageReachesScore() {
        let (det, rec) = makeDetector()
        det.config.sensorFusion = true   // GPS jitter path gated off; the sensor path feeds the score

        // Enter an active trip (minTripDurationSec == 0 → first fast fix starts it).
        det.feed(loc(speedMps: 6))
        XCTAssertTrue(rec.events.contains("tripStart"), "expected tripStart: \(rec.events)")

        // Sensor-fusion path (SensorFusionDetector → BGFacade → plugin) records here.
        det.recordExternalPhoneUsage(loc(speedMps: 6))

        // Stop to end the trip and deliver the score.
        det.feed(loc(speedMps: 0))
        Thread.sleep(forTimeInterval: Self.stoppedSec * 2)
        det.feed(loc(speedMps: 0))

        let score = rec.lastScore
        XCTAssertNotNil(score, "tripEnd should deliver a score: \(rec.events)")
        XCTAssertLessThan(score!.breakdown.phoneUsage, 100,
                          "sensor phoneUsage should penalise the category, got \(score!.breakdown.phoneUsage)")
        XCTAssertTrue(score!.events.contains { $0.type == "phoneUsage" },
                      "phoneUsage should be among scored events: \(score!.events.map { $0.type })")
        // GPS jitter path stayed gated off — the detector itself fired no phoneUsage event.
        XCTAssertFalse(rec.events.contains("phoneUsage"), "GPS phoneUsage must stay suppressed: \(rec.events)")
    }

    func testPhoneUsageWhileDrivingSuppressedWhenSensorFusion() {
        let (det, rec) = makeDetector()
        det.config.sensorFusion = true           // GPS path disabled
        det.config.phoneUsageWindowSec = 0.3
        det.config.phoneUsageCooldownSec = 60.0

        det.feed(loc(speedMps: 6))

        var bearing = 90.0
        for _ in 0..<8 {
            Thread.sleep(forTimeInterval: 0.05)
            bearing = bearing == 90.0 ? 100.0 : 90.0
            det.feed(loc(speedMps: 5.5, bearing: bearing))
        }

        Thread.sleep(forTimeInterval: 0.4)
        det.feed(loc(speedMps: 5.5, bearing: bearing))

        XCTAssertFalse(rec.events.contains("phoneUsage"), "phoneUsage GPS path should be suppressed when sensorFusion=true: \(rec.events)")
    }

    // MARK: - reset clears state

    func testResetClearsState() {
        let (det, rec) = makeDetector()

        det.feed(loc(speedMps: 20))   // tripStart
        XCTAssertTrue(det.tripActive)

        det.reset()
        XCTAssertFalse(det.tripActive)

        // After reset, speeding should fire again on first crossing.
        let (det2, rec2) = makeDetector(speedLimitKmh: 30)
        det2.feed(loc(speedMps: 10))   // 36 km/h — speeding
        det2.reset()
        det2.feed(loc(speedMps: 10))   // 36 km/h — should fire again after reset

        let speedingCount = rec2.events.filter { $0.hasPrefix("speeding") }.count
        XCTAssertEqual(speedingCount, 2, "speeding should fire again after reset, got: \(rec2.events)")
        _ = rec   // silence unused-variable warning
    }
}
