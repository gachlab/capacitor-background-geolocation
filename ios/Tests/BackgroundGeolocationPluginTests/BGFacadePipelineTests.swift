// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import XCTest
import BackgroundGeolocationCore
@testable import BackgroundGeolocationPlugin

// Fase 0 hub integration — the iOS twin of the Android LocationService test.
// BGFacade is the orchestration hub; onLocationChanged/onStationaryChanged is the
// provider-delegate seam (the same one a real CLLocationManager-backed provider
// calls). Driving fixes through it pins the glue the Apple-Silicon refactor will
// move to domain/, without a real CoreLocation provider.

// Captures the hub's delegate emissions.
private final class RecordingLocationDelegate: LocationProviderDelegate {
    var locations: [BGLocation] = []
    var stationaries: [BGLocation] = []

    func onAuthorizationChanged(_ status: BGAuthorizationStatus) {}
    func onLocationChanged(_ location: BGLocation) { locations.append(location) }
    func onStationaryChanged(_ location: BGLocation) { stationaries.append(location) }
    func onLocationPause() {}
    func onLocationResume() {}
    func onActivityChanged(_ activity: BGActivity) {}
    func onAbortRequested() {}
    func onHttpAuthorization() {}
    func onError(_ error: Error) {}
}

final class BGFacadePipelineTests: XCTestCase {

    private var facade: BGFacade!
    private var recorder: RecordingLocationDelegate!

    override func setUp() {
        super.setUp()
        facade = BGFacade()
        recorder = RecordingLocationDelegate()
        facade.delegate = recorder
    }

    override func tearDown() {
        facade = nil
        recorder = nil
        super.tearDown()
    }

    private func makeConfig(maxAccuracy: Double) -> BGConfig {
        let c = BGConfig()
        c.maxAcceptedAccuracy = maxAccuracy
        return c
    }

    private func fix(lat: Double, lon: Double, accuracy: Double) -> BGLocation {
        let l = BGLocation()
        l.latitude = lat
        l.longitude = lon
        l.accuracy = accuracy
        l.time = Date(timeIntervalSince1970: 1_716_000_000)
        return l
    }

    func testForwardsAcceptedFixToDelegate() throws {
        try facade.configure(makeConfig(maxAccuracy: 50))

        facade.onLocationChanged(fix(lat: 19.4326, lon: -99.1332, accuracy: 10))

        XCTAssertEqual(recorder.locations.count, 1, "an accepted fix must reach the delegate")
        XCTAssertEqual(recorder.locations.first?.latitude, 19.4326)
    }

    func testDropsFixWorseThanMaxAcceptedAccuracy() throws {
        try facade.configure(makeConfig(maxAccuracy: 50))

        facade.onLocationChanged(fix(lat: 19.4326, lon: -99.1332, accuracy: 999))

        XCTAssertTrue(
            recorder.locations.isEmpty,
            "a fix worse than maxAcceptedAccuracy must be gated out before the delegate")
    }

    func testStationaryTransitionForwardsToDelegate() throws {
        try facade.configure(makeConfig(maxAccuracy: 50))

        facade.onStationaryChanged(fix(lat: 19.4326, lon: -99.1332, accuracy: 20))

        XCTAssertEqual(recorder.stationaries.count, 1, "a stationary transition must reach the delegate")
        XCTAssertEqual(recorder.stationaries.first?.latitude, 19.4326)
    }
}
