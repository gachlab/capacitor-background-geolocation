// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

/**
 * A geofence boundary transition — the domain modelling of a geofence event
 * (ARCHITECTURE.md domain set's "GeoEvent"), the Android twin of the web/iOS
 * `GeofenceTransition`.
 *
 * Pure — no GMS import. The GMS `Geofence.GEOFENCE_TRANSITION_*` ints are mapped to this
 * enum at the platform boundary (`GeofenceBroadcastReceiver`), keeping the domain free of
 * Play-services types. The hub bus (`ServiceEvent.Geofence{Enter,Exit,Dwell}`) already
 * carried the transition; this names it in the shared vocabulary so the three dies agree.
 */
enum class GeofenceTransition { ENTER, EXIT, DWELL }
