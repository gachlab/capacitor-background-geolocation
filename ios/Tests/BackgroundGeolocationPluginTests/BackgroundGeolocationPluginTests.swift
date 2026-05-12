// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM

import XCTest
@testable import BackgroundGeolocationPlugin

final class BackgroundGeolocationPluginTests: XCTestCase {
    func testPluginExists() {
        let plugin = BackgroundGeolocationPlugin()
        XCTAssertNotNil(plugin)
    }
}
