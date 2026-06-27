// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

/// A geofence boundary transition — the domain modelling of a geofence event
/// (ARCHITECTURE.md domain set's "GeoEvent"), the iOS twin of the web/Android
/// `GeofenceTransition`.
///
/// The geofence path lives in the Core module (GeofenceManager/BGFacade), so this base
/// vocabulary lives here rather than in the Plugin's Domain/ (which can't be seen from
/// Core). It replaces the stringly-typed `"ENTER"/"EXIT"/"DWELL"` action that flowed
/// through the geofence event listener. The raw values are exactly the public event
/// `action` contract, so the emitted notification payload is unchanged.
public enum GeofenceTransition: String {
    case enter = "ENTER"
    case exit = "EXIT"
    case dwell = "DWELL"
}
