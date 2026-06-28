// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import XCTest
import BackgroundGeolocationCore

// Mirrors the Android PositionBufferTest — the L2/UMA shared last-fix cache.
final class PositionBufferTests: XCTestCase {

    func testEmptyByDefault() {
        let b = PositionBuffer()
        XCTAssertNil(b.lastFix)
        XCTAssertEqual(b.lastFixAtMs, 0)
    }

    func testRecordsZeroCopy() {
        let b = PositionBuffer()
        let loc = BGLocation()
        b.record(loc, at: 1_716_000_000_000) // ms
        XCTAssertTrue(b.lastFix === loc) // same reference — no re-serialisation
        XCTAssertEqual(b.lastFixAtMs, 1_716_000_000_000)
    }

    func testOverwrites() {
        let b = PositionBuffer()
        b.record(BGLocation(), at: 1)
        let newer = BGLocation()
        b.record(newer, at: 2)
        XCTAssertTrue(b.lastFix === newer)
        XCTAssertEqual(b.lastFixAtMs, 2)
    }

    func testClearResets() {
        let b = PositionBuffer()
        b.record(BGLocation(), at: 5)
        b.clear()
        XCTAssertNil(b.lastFix)
        XCTAssertEqual(b.lastFixAtMs, 0)
    }
}
