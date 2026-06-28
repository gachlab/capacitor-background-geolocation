// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

/// Port (Roadmap Fase 3, hub → ports), the iOS twin of the Android
/// `ports/LocationPublisher`: how the hub drives the sync chiplet (the offline queue +
/// background uploader). `BGFacade` depends on this abstraction instead of the concrete
/// `Sync/PostLocationTask` singleton, so the sync block is swappable and the hub is
/// testable with a fake — the chiplet (future `capacitor-event-sink`) is a contract.
///
/// Implemented by `PostLocationTask`.
public protocol LocationPublisher: AnyObject {
    var delegate: PostLocationTaskDelegate? { get set }
    var attachBatterySnapshot: ((BGLocation) -> Void)? { get set }
    var config: BGConfig { get set }
    func start()
    func stop()
    func sync()
    func add(_ location: BGLocation)
}
