// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.ports

import com.gachlab.geolocation.BGConfig

/**
 * Port (Roadmap Fase 3, hub → ports): how the hub loads and saves the persisted
 * [BGConfig], decoupled from the SQLite-backed `persistence/ConfigDAO` adapter. Lets the
 * hub depend on an abstraction (testable with a fake; storage swappable).
 *
 * Implemented by `persistence.ConfigDAO`.
 */
internal interface ConfigRepository {
    /** The persisted config, or null if none has been saved. */
    fun retrieveConfig(): BGConfig?

    /** Persist [config] as the current configuration. */
    fun persistConfig(config: BGConfig)
}
