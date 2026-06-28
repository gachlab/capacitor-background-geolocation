// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

/// A geofence event — the domain "GeoEvent" of the ARCHITECTURE.md domain set: a
/// `transition` (enter/exit/dwell) at a region (`geofenceId`). Pure — the triggering
/// location is a platform type and rides the event listener separately. Lives in Core
/// alongside `GeofenceTransition` (the geofence path is in Core). GeofenceManager emits a
/// GeoEvent; BGFacade adapts it to the NotificationCenter bus.
public struct GeoEvent {
    public let geofenceId: String
    public let transition: GeofenceTransition

    public init(geofenceId: String, transition: GeofenceTransition) {
        self.geofenceId = geofenceId
        self.transition = transition
    }
}
