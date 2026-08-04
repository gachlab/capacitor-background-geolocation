// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import XCTest
import BackgroundGeolocationCore

/// The iOS half of "one spelling per fact across the bridge".
///
/// These were claimed to be untestable. They are not: this target exists, CI
/// runs it on every PR, and the assertions below are the same shape as
/// `TrackingConfigMapperTests`. The claim came from not looking.
///
/// Each case is a defect that shipped:
///
///  · `getConfig()` echoed the internal lowercase spelling while the
///    `iosFallbackActivated` event normalised, so
///    `cfg.survival.iosBackgroundFallback === 'regionMonitoring'` was never true.
///  · one numeric value made the whole `[String: String]` cast fail and dropped
///    every query param, leaving every `{placeholder}` unresolved.
///  · `getLogEntries()` returned UPPERCASE against a lowercase `LogLevel` union,
///    so filtering by `=== 'error'` matched nothing, ever.
final class VocabularyTests: XCTestCase {

    // MARK: - iosBackgroundFallback

    func testPublicFallbackTranslatesToTheDeclaredUnion() {
        XCTAssertEqual(BGConfig.publicFallback("regionmonitoring"), "regionMonitoring")
        XCTAssertEqual(BGConfig.publicFallback("significantchanges"), "significantChanges")
        XCTAssertEqual(BGConfig.publicFallback("none"), "none")
    }

    func testConfigEchoesTheFallbackInThePublicSpelling() {
        // The round trip that was broken: ingest lower-cases for internal
        // comparison, and `toDictionary()` goes straight to JavaScript.
        let config = BGConfig.from(dictionary: ["iosBackgroundFallback": "regionMonitoring"])
        XCTAssertEqual(config.iosBackgroundFallback, "regionmonitoring",
                       "internally it stays lower-cased, which is what the providers compare")
        XCTAssertEqual(config.toDictionary()["iosBackgroundFallback"] as? String, "regionMonitoring",
                       "but what leaves the plugin must match the published union")
    }

    func testFallbackSurvivesARoundTrip() {
        let once = BGConfig.from(dictionary: ["iosBackgroundFallback": "regionMonitoring"])
        let twice = BGConfig.from(dictionary: once.toDictionary())
        XCTAssertEqual(twice.iosBackgroundFallback, once.iosBackgroundFallback,
                       "re-ingesting our own output must not drift")
    }

    // MARK: - queryParams

    func testNumericQueryParamDoesNotDropTheWholeMap() {
        // A heterogeneous cast is all-or-nothing in Swift: before, one number lost
        // every key — not just its own.
        let config = BGConfig.from(dictionary: [
            "queryParams": ["deviceId": "abc", "propertyId": 42]
        ])
        XCTAssertEqual(config.queryParams?["deviceId"], "abc")
        XCTAssertEqual(config.queryParams?["propertyId"], "42",
                       "a number is coerced, the way Android has always done it")
    }

    func testStringOnlyQueryParamsStillWork() {
        let config = BGConfig.from(dictionary: ["queryParams": ["a": "1", "b": "2"]])
        XCTAssertEqual(config.queryParams?.count, 2)
    }
}
