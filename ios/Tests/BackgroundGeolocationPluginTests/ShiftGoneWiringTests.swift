// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import XCTest
@testable import BackgroundGeolocationCore

/// The wiring, which `ShiftGoneDetectorTests` cannot prove.
///
/// A detector nobody feeds is inert, and the rule tests stay green while the
/// sync path ignores it completely — that is exactly the shape of the original
/// defect, where iOS had a perfectly good `stop()` and nothing ever called it.
/// So this drives the real `PostLocationTask.add()` path end to end and asserts
/// the delegate is actually told.
///
/// The 404 comes from a stubbed `URLProtocol` rather than a mock HTTP client:
/// the code under test reads `HTTPURLResponse.statusCode` off a real
/// `URLSession.shared` task, and swapping the client would test the swap.
///
/// Time is not faked. The detector is primed with a 404 stamped a minute ago, so
/// the POST below is the second one and lands past the window — the same
/// arithmetic a real runaway does, without a minute of waiting.
final class ShiftGoneWiringTests: XCTestCase {

    // MARK: - Stub transport

    /// Answers every request with `Stub.status` and an empty body.
    final class Stub: URLProtocol {
        nonisolated(unsafe) static var status = 404
        nonisolated(unsafe) static var hits = 0

        override class func canInit(with request: URLRequest) -> Bool { true }
        override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

        override func startLoading() {
            Stub.hits += 1
            let response = HTTPURLResponse(
                url: request.url ?? URL(string: "http://127.0.0.1/shift")!,
                statusCode: Stub.status,
                httpVersion: "HTTP/1.1",
                headerFields: nil
            )!
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: Data())
            client?.urlProtocolDidFinishLoading(self)
        }

        override func stopLoading() {}
    }

    // MARK: - Delegate spy

    final class Spy: NSObject, PostLocationTaskDelegate {
        var shiftGoneCount = 0
        var abortCount = 0
        var authCount = 0
        let fired = XCTestExpectation(description: "delegate called")

        func postLocationTaskShiftGone(_ task: PostLocationTask) {
            shiftGoneCount += 1
            fired.fulfill()
        }
        func postLocationTaskRequestedAbortUpdates(_ task: PostLocationTask) { abortCount += 1 }
        func postLocationTaskHttpAuthorizationUpdates(_ task: PostLocationTask) { authCount += 1 }
    }

    private var spy: Spy!
    private var previousDelegate: PostLocationTaskDelegate?

    override func setUp() {
        super.setUp()
        URLProtocol.registerClass(Stub.self)
        Stub.status = 404
        Stub.hits = 0
        ShiftGoneDetector.shared.reset()

        spy = Spy()
        previousDelegate = PostLocationTask.shared.delegate
        PostLocationTask.shared.delegate = spy
        PostLocationTask.shared.hasConnectivity = true
        PostLocationTask.shared.config = configPostingTo("http://127.0.0.1:9/shift")
    }

    override func tearDown() {
        URLProtocol.unregisterClass(Stub.self)
        PostLocationTask.shared.delegate = previousDelegate
        ShiftGoneDetector.shared.reset()
        spy = nil
        super.tearDown()
    }

    private func configPostingTo(_ url: String) -> BGConfig {
        let cfg = BGConfig(defaults: ())
        cfg.url = url
        cfg.syncEnabled = false      // keep this test on the direct POST path only
        cfg.maxLocations = 1_000
        return cfg
    }

    private func fix() -> BGLocation {
        let loc = BGLocation()
        loc.latitude = 25.77
        loc.longitude = -80.19
        loc.time = Date()
        return loc
    }

    /// Stamp the run as having started a full window ago.
    private func primeSustainedRun(code: Int = 404) {
        ShiftGoneDetector.shared.observe(
            code,
            now: Date().addingTimeInterval(-ShiftGoneDetector.windowSeconds)
        )
    }

    // MARK: - It fires through the real path

    func testASustained404ThroughTheRealPostPathTellsTheDelegate() {
        primeSustainedRun()

        PostLocationTask.shared.add(fix())

        wait(for: [spy.fired], timeout: 10)
        XCTAssertGreaterThan(Stub.hits, 0, "the request must actually have been made")
        XCTAssertEqual(spy.shiftGoneCount, 1)
    }

    // MARK: - It does not fire

    func testAFirst404ChangesNothing() {
        // Without this, a fix that retired on every 404 would pass the test above
        // and still be wrong.
        let quiet = XCTestExpectation(description: "post completed")
        PostLocationTask.shared.add(fix())
        DispatchQueue.global().asyncAfter(deadline: .now() + 3) { quiet.fulfill() }
        wait(for: [quiet], timeout: 10)

        XCTAssertGreaterThan(Stub.hits, 0)
        XCTAssertEqual(spy.shiftGoneCount, 0, "one 404 may only start the clock")
    }

    func testASustained500NeverRetires() {
        // The regression that would trade a privacy bug for an outage: a backend
        // down for an hour must not switch tracking off.
        Stub.status = 500
        primeSustainedRun(code: 500)

        let quiet = XCTestExpectation(description: "post completed")
        PostLocationTask.shared.add(fix())
        DispatchQueue.global().asyncAfter(deadline: .now() + 3) { quiet.fulfill() }
        wait(for: [quiet], timeout: 10)

        XCTAssertGreaterThan(Stub.hits, 0)
        XCTAssertEqual(spy.shiftGoneCount, 0, "a bad backend is not a missing shift")
    }

    func testASuccessClearsARunThatWasAboutToExpire() {
        // Proves the 2xx branch feeds the detector too. If only the failure paths
        // called `observe`, a recovered shift would keep a stale clock and could
        // be retired later by a single unrelated 404.
        Stub.status = 200
        primeSustainedRun()

        let quiet = XCTestExpectation(description: "post completed")
        PostLocationTask.shared.add(fix())
        DispatchQueue.global().asyncAfter(deadline: .now() + 3) { quiet.fulfill() }
        wait(for: [quiet], timeout: 10)

        XCTAssertEqual(spy.shiftGoneCount, 0)
        XCTAssertNil(ShiftGoneDetector.shared.startedAt, "a successful POST must clear the run")
    }
}
