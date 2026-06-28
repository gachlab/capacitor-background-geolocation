// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.ports

import com.gachlab.geolocation.BGLocation

/**
 * Port (Roadmap Fase 3, hub → ports): how the hub hands accepted fixes to the sync
 * chiplet (the offline queue + background uploader). The hub depends on this abstraction
 * instead of the concrete `network/PostLocationTask`, so the sync block is swappable and
 * the hub is testable with a fake — and the chiplet (the future `capacitor-event-sink`)
 * is defined by a contract, not a class.
 *
 * Implemented by `network.PostLocationTask` (the sync adapter).
 */
internal interface LocationPublisher {
    /** Enqueue [location] for persistence + (batched) upload. */
    fun add(location: BGLocation)

    /** Release resources (e.g. on reconfigure / service stop). */
    fun shutdown()
}
