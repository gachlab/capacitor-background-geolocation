// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

/**
 * A geofence event — the domain "GeoEvent" of the ARCHITECTURE.md domain set: a
 * [transition] (enter/exit/dwell) at a region ([geofenceId]). Pure — no GMS, no platform
 * location (the triggering location is a platform type and rides the hub bus). The
 * receiver decodes the GMS transition into a GeoEvent at the boundary, then adapts it to
 * the `ServiceEvent` bus.
 */
data class GeoEvent(
    val geofenceId: String,
    val transition: GeofenceTransition,
)
