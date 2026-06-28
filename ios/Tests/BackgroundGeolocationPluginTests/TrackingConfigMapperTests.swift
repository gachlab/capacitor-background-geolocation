// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import XCTest
import BackgroundGeolocationCore

// Mirrors the Android TrackingConfigMapperTest: BGConfig.toTrackingConfig() resolves the
// nullable tracking overrides into the pure domain value object.
final class TrackingConfigMapperTests: XCTestCase {

    func testResolvesDefaults() {
        let tc = BGConfig().toTrackingConfig()
        XCTAssertEqual(tc.stationaryRadius, 50)
        XCTAssertEqual(tc.distanceFilter, 500)
        XCTAssertEqual(tc.desiredAccuracy, 100)
        XCTAssertEqual(tc.activitiesInterval, 10_000)
        XCTAssertEqual(tc.activityConfidenceThreshold, 50)
        XCTAssertEqual(tc.locationProvider, BGConfig(defaults: ()).locationProvider ?? -1)
        XCTAssertNil(tc.maxAcceptedAccuracy) // nil = no accuracy gate
    }

    func testPassesOverridesThrough() {
        let c = BGConfig()
        c.distanceFilter = 25
        c.desiredAccuracy = 10
        c.maxAcceptedAccuracy = 50
        let tc = c.toTrackingConfig()
        XCTAssertEqual(tc.distanceFilter, 25)
        XCTAssertEqual(tc.desiredAccuracy, 10)
        XCTAssertEqual(tc.maxAcceptedAccuracy, 50)
    }
}
