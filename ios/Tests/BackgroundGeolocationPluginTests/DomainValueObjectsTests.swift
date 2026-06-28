// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import XCTest
@testable import BackgroundGeolocationPlugin

// Mirrors the Android domain/ unit tests (TripTest / GeoPointTest / PositionTest /
// HeadingTest) so the shared vocabulary stays in parity across the two natives.
final class DomainValueObjectsTests: XCTestCase {

    // ── Trip ────────────────────────────────────────────────────────────────
    func testTripStartedAtUsesMillisIdAndZeroDistance() {
        let t = Trip.startedAt(1_716_000_000)        // seconds
        XCTAssertEqual(t.id, "1716000000000")        // millis as string
        XCTAssertEqual(t.startedAt, 1_716_000_000)
        XCTAssertEqual(t.distanceMeters, 0)
    }

    func testTripPlusDistanceIsImmutable() {
        let a = Trip.startedAt(1000)
        let b = a.plusDistance(50).plusDistance(25)
        XCTAssertEqual(a.distanceMeters, 0)
        XCTAssertEqual(b.distanceMeters, 75)
    }

    func testTripDurationMs() {
        XCTAssertEqual(Trip.startedAt(1000).durationMs(1006), 6000) // 6 s → 6000 ms
    }

    // ── GeoPoint ──────────────────────────────────────────────────────────────
    func testGeoPointZeroDistanceToItself() {
        let p = GeoPoint(latitude: -25.2637, longitude: -57.5759)
        XCTAssertEqual(p.distanceTo(p), 0, accuracy: 1e-6)
    }

    func testGeoPointDistanceIsSymmetric() {
        let a = GeoPoint(latitude: -25.2637, longitude: -57.5759)
        let b = GeoPoint(latitude: -25.30, longitude: -57.60)
        XCTAssertEqual(a.distanceTo(b), b.distanceTo(a), accuracy: 1e-9)
    }

    func testGeoPointOneDegreeLatitudeIsAbout111Km() {
        let d = GeoPoint(latitude: 0, longitude: 0).distanceTo(GeoPoint(latitude: 1, longitude: 0))
        XCTAssertEqual(d, 111_194.9, accuracy: 1.0)
    }

    // ── Position ────────────────────────────────────────────────────────────────
    func testPositionAbsentKinematicsAreNil() {
        let p = Position(point: GeoPoint(latitude: 0, longitude: 0),
                         speedMps: nil, bearingDeg: nil, provider: nil)
        XCTAssertNil(p.speedMps)
        XCTAssertNil(p.bearingDeg)
        XCTAssertNil(p.provider)
        XCTAssertEqual(p.speedMpsOrZero, 0)
    }

    func testPositionSpeedOrZeroReturnsReadingWhenPresent() {
        let p = Position(point: GeoPoint(latitude: 0, longitude: 0),
                         speedMps: 12.5, bearingDeg: 270, provider: "fused")
        XCTAssertEqual(p.speedMpsOrZero, 12.5)
        XCTAssertEqual(p.bearingDeg, 270)
        XCTAssertEqual(p.provider, "fused")
    }

    // ── Heading ────────────────────────────────────────────────────────────────
    func testHeadingZeroDelta() {
        XCTAssertEqual(Heading(degrees: 123.4).deltaTo(Heading(degrees: 123.4)), 0, accuracy: 1e-9)
    }

    func testHeadingDirectDelta() {
        XCTAssertEqual(Heading(degrees: 40).deltaTo(Heading(degrees: 10)), 30, accuracy: 1e-9)
    }

    func testHeadingWrapAroundIsSymmetric() {
        XCTAssertEqual(Heading(degrees: 350).deltaTo(Heading(degrees: 10)), 20, accuracy: 1e-9)
        XCTAssertEqual(Heading(degrees: 10).deltaTo(Heading(degrees: 350)), 20, accuracy: 1e-9)
    }

    func testHeadingOppositeIs180() {
        XCTAssertEqual(Heading(degrees: 0).deltaTo(Heading(degrees: 180)), 180, accuracy: 1e-9)
    }
}
