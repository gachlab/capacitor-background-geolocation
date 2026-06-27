// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import XCTest
@testable import BackgroundGeolocationPlugin

/// Integration test mirroring the Android `DrivingEventsIntegrationTest`: drives a
/// full trip through one detector instance and asserts the events compose into a
/// single aggregate `TripScore`. Runs on macOS via `swift test` or in Xcode — no
/// device needed (the detector is pure Swift, keyed off wall-clock time).
private final class ScoreCapturingDelegate: DrivingEventsDetectorDelegate {
    var events: [String] = []
    var finalScore: TripScore?

    func detectorOnMoving(_ l: DLLocation)    { events.append("moving") }
    func detectorOnStopped(_ l: DLLocation)   { events.append("stopped") }
    func detectorOnTripStart(_ l: DLLocation) { events.append("tripStart") }
    func detectorOnTripEnd(_ l: DLLocation, distanceMeters: Double, durationMs: Int64, score: TripScore) {
        events.append("tripEnd")
        finalScore = score
    }
    func detectorOnSpeeding(_ l: DLLocation, speedKmh: Double, limitKmh: Double) { events.append("speeding") }
    func detectorOnProviderChange(provider: String) {}
    func detectorOnHardBrake(_ l: DLLocation, decelMps2: Double) { events.append("hardBrake") }
    func detectorOnRapidAcceleration(_ l: DLLocation, accelMps2: Double) { events.append("rapidAccel") }
    func detectorOnSharpTurn(_ l: DLLocation, degPerSec: Double) { events.append("sharpTurn") }
    func detectorOnPossibleCrash(_ l: DLLocation, dropKmh: Double) { events.append("crash") }
    func detectorOnIdleStart(_ l: DLLocation, startedAt: Double) { events.append("idleStart") }
    func detectorOnIdleEnd(_ l: DLLocation, durationMs: Int64, startedAt: Double) { events.append("idleEnd") }
    func detectorOnPhoneUsageWhileDriving(_ l: DLLocation) { events.append("phoneUsage") }
}

final class TripLifecycleTests: XCTestCase {

    private var lat = 19.4326
    private func fix(_ speedMps: Double) -> DLLocation {
        lat += 0.001 // ~111 m between fixes so the trip accrues real distance
        return DLLocation(latitude: lat, longitude: -99.1332, speed: speedMps, bearing: nil, provider: "gps")
    }

    func testFullTripLifecycleProducesAggregateScore() throws {
        let det = DrivingEventsDetector()
        let rec = ScoreCapturingDelegate()
        det.delegate = rec
        det.config.enabled = true
        det.config.speedLimitKmh = 120
        det.config.minTripDurationSec = 0
        det.config.crashImpactKmh = 1_000        // disable crash so the brake reads as hardBrake only
        // idleThreshold must be << stoppedDuration so idle fires DURING the trip.
        det.config.stoppedDurationSec = 0.4
        det.config.idleThresholdSec = 0.08
        det.config.idleEndThresholdSec = 0.08

        // 1) Start moving fast → moving + tripStart
        det.feed(fix(20))                 // 72 km/h
        usleep(20_000)
        // 2) Exceed the speed limit → speeding
        det.feed(fix(40))                 // 144 km/h > 120
        usleep(20_000)
        // 3) Slam to a stop → hardBrake (begins stopped/idle clock)
        det.feed(fix(0))
        // 4) Stay stopped past idleThreshold (under stoppedDuration) → idleStart
        usleep(140_000)
        det.feed(fix(0))
        // 5) Resume: idleEnd needs a moving fix to arm the clock, then a second after idleEndThreshold
        usleep(20_000)
        det.feed(fix(20))
        usleep(120_000)
        det.feed(fix(20))
        // 6) Final stop, wait past stoppedDuration → tripEnd
        det.feed(fix(0))
        usleep(480_000)
        det.feed(fix(0))

        XCTAssertTrue(rec.events.contains("tripStart"), "events=\(rec.events)")
        XCTAssertTrue(rec.events.contains("speeding"), "events=\(rec.events)")
        XCTAssertTrue(rec.events.contains("hardBrake"), "events=\(rec.events)")
        XCTAssertTrue(rec.events.contains("idleStart"), "events=\(rec.events)")
        XCTAssertTrue(rec.events.contains("idleEnd"), "events=\(rec.events)")
        XCTAssertTrue(rec.events.contains("tripEnd"), "events=\(rec.events)")
        XCTAssertFalse(rec.events.contains("crash"), "crash must not fire: \(rec.events)")

        let score = try XCTUnwrap(rec.finalScore, "tripEnd should deliver a TripScore")
        XCTAssertTrue((0...99).contains(score.overall), "expected < 100, got \(score.overall)")
        // breakdown holds per-category scores (100 - penalty); a penalised category drops below 100.
        XCTAssertLessThan(score.breakdown.speeding, 100)
        XCTAssertLessThan(score.breakdown.hardBraking, 100)
        XCTAssertTrue(score.events.contains { $0.type == "speeding" }, "scored=\(score.events.map { $0.type })")
        XCTAssertTrue(score.events.contains { $0.type == "hardBrake" }, "scored=\(score.events.map { $0.type })")
        XCTAssertGreaterThanOrEqual(score.idleCount, 1)
        XCTAssertGreaterThan(score.distanceKm, 0.0)
        XCTAssertGreaterThanOrEqual(score.endedAt, score.startedAt)
    }
}
