// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

public protocol LocationProvider: AnyObject {
    var delegate: LocationProviderDelegate? { get set }
    func onCreate()
    func onDestroy()
    func onConfigure(_ config: BGConfig) throws
    func onStart() throws
    func onStop() throws
    func onSwitchMode(_ mode: BGOperationalMode)
    func onTerminate()
}
