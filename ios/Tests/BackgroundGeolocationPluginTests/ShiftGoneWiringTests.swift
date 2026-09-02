// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Network
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

    // MARK: - Local HTTP server

    /// A real loopback server, not a `URLProtocol` stub.
    ///
    /// The first version of this file stubbed `URLProtocol`. It passed on a real
    /// Mac and failed on CI with zero hits in every case, which left two
    /// suspects — `registerClass` not applying to `URLSession.shared` on that
    /// runner, or the connectivity flag below — and no way to tell them apart
    /// from the failure. A real socket removes the first suspect entirely and
    /// matches what the Android wiring test does with the JDK's own HttpServer.
    final class LocalHTTPServer {
        private var listener: NWListener?
        private let lock = NSLock()
        private var _hits = 0
        private var _status = 404

        private(set) var port: UInt16 = 0

        var hits: Int { lock.lock(); defer { lock.unlock() }; return _hits }
        var status: Int {
            get { lock.lock(); defer { lock.unlock() }; return _status }
            set { lock.lock(); _status = newValue; lock.unlock() }
        }

        func start() throws {
            let params = NWParameters.tcp
            params.allowLocalEndpointReuse = true
            let listener = try NWListener(using: params, on: .any)
            let ready = DispatchSemaphore(value: 0)
            listener.stateUpdateHandler = { if case .ready = $0 { ready.signal() } }
            listener.newConnectionHandler = { [weak self] conn in
                conn.start(queue: .global())
                self?.receive(conn, accumulated: Data())
            }
            listener.start(queue: .global())
            guard ready.wait(timeout: .now() + 10) == .success, let p = listener.port else {
                throw NSError(domain: "LocalHTTPServer", code: 1)
            }
            self.listener = listener
            self.port = p.rawValue
        }

        func stop() {
            listener?.cancel()
            listener = nil
        }

        /// Answer as soon as the request headers are complete. The body is not
        /// needed to decide a status, and waiting for it would deadlock against a
        /// client that expects the response first.
        private func receive(_ conn: NWConnection, accumulated: Data) {
            conn.receive(minimumIncompleteLength: 1, maximumLength: 65_536) { [weak self] data, _, isComplete, error in
                guard let self = self else { return }
                var buffer = accumulated
                if let data = data { buffer.append(data) }
                if String(decoding: buffer, as: UTF8.self).contains("\r\n\r\n") {
                    self.respond(conn)
                } else if isComplete || error != nil {
                    conn.cancel()
                } else {
                    self.receive(conn, accumulated: buffer)
                }
            }
        }

        private func respond(_ conn: NWConnection) {
            lock.lock()
            _hits += 1
            let code = _status
            lock.unlock()

            let body = "{}"
            let head = "HTTP/1.1 \(code) \(Self.reason(code))\r\n"
                + "Content-Type: application/json\r\n"
                + "Content-Length: \(body.utf8.count)\r\n"
                + "Connection: close\r\n\r\n"
            conn.send(
                content: Data((head + body).utf8),
                completion: .contentProcessed { _ in conn.cancel() }
            )
        }

        private static func reason(_ code: Int) -> String {
            switch code {
            case 200: return "OK"
            case 404: return "Not Found"
            case 500: return "Internal Server Error"
            default:  return "Status"
            }
        }
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
    private var server: LocalHTTPServer!
    private var previousDelegate: PostLocationTaskDelegate?

    override func setUpWithError() throws {
        try super.setUpWithError()
        server = LocalHTTPServer()
        try server.start()
        server.status = 404
        ShiftGoneDetector.shared.reset()

        spy = Spy()
        previousDelegate = PostLocationTask.shared.delegate
        PostLocationTask.shared.delegate = spy

        // Cancel the NWPathMonitor before pinning the flag it writes.
        //
        // The second suspect behind the CI-only failure, and a real race either
        // way: another test may have called `start()`, and the monitor's handler
        // fires asynchronously, so setting `hasConnectivity = true` here can be
        // overwritten a moment later. On a CI VM the monitor can report
        // `unsatisfied` even with a working network, which would skip the POST
        // entirely and make every assertion below fail with zero hits.
        PostLocationTask.shared.stop()
        PostLocationTask.shared.hasConnectivity = true
        PostLocationTask.shared.config = configPostingTo("http://127.0.0.1:\(server.port)/shift")
    }

    override func tearDown() {
        PostLocationTask.shared.delegate = previousDelegate
        ShiftGoneDetector.shared.reset()
        server?.stop()
        server = nil
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

    /// Post one fix and wait until the server has actually answered it.
    ///
    /// Polling the server rather than sleeping a fixed interval: a hard-coded
    /// wait is the other way this file could go green on a fast Mac and red on a
    /// loaded runner.
    private func postOneFixAndWaitForTheServer(
        file: StaticString = #filePath, line: UInt = #line
    ) {
        PostLocationTask.shared.add(fix())
        let deadline = Date().addingTimeInterval(15)
        while server.hits == 0 && Date() < deadline {
            RunLoop.current.run(until: Date().addingTimeInterval(0.05))
        }
        XCTAssertGreaterThan(
            server.hits, 0,
            "the POST never reached the server — the wiring cannot be judged",
            file: file, line: line
        )
    }

    // MARK: - It fires through the real path

    func testASustained404ThroughTheRealPostPathTellsTheDelegate() {
        primeSustainedRun()

        postOneFixAndWaitForTheServer()

        wait(for: [spy.fired], timeout: 10)
        XCTAssertEqual(spy.shiftGoneCount, 1)
    }

    // MARK: - It does not fire

    func testAFirst404ChangesNothing() {
        // Without this, a fix that retired on every 404 would pass the test above
        // and still be wrong.
        postOneFixAndWaitForTheServer()

        XCTAssertEqual(spy.shiftGoneCount, 0, "one 404 may only start the clock")
    }

    func testASustained500NeverRetires() {
        // The regression that would trade a privacy bug for an outage: a backend
        // down for an hour must not switch tracking off.
        server.status = 500
        primeSustainedRun(code: 500)

        postOneFixAndWaitForTheServer()

        XCTAssertEqual(spy.shiftGoneCount, 0, "a bad backend is not a missing shift")
    }

    func testASuccessClearsARunThatWasAboutToExpire() {
        // Proves the 2xx branch feeds the detector too. If only the failure paths
        // called `observe`, a recovered shift would keep a stale clock and could
        // be retired later by a single unrelated 404.
        server.status = 200
        primeSustainedRun()

        postOneFixAndWaitForTheServer()

        XCTAssertEqual(spy.shiftGoneCount, 0)
        XCTAssertNil(ShiftGoneDetector.shared.startedAt, "a successful POST must clear the run")
    }
}
