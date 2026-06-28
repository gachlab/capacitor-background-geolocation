// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import Foundation

/// Port (Roadmap Fase 3, hub → ports), the iOS twin of the Android
/// `ports/ConfigRepository`: how the hub loads and saves the persisted `BGConfig`,
/// decoupled from the SQLite-backed `Persistence/ConfigDAO` adapter.
///
/// Implemented by `ConfigDAO`.
protocol ConfigRepository: AnyObject {
    func retrieve() -> BGConfig?
    func persist(_ config: BGConfig)
}
