// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

/**
 * A geofence boundary transition — the domain modelling of a geofence event
 * (ARCHITECTURE.md domain set's "GeoEvent"), the web twin of the Android/iOS
 * `GeofenceTransition`.
 *
 * Each platform's event bus already carries the transition alongside the region id and
 * the platform location; what was loose was the *transition itself* — a magic
 * 'ENTER'/'EXIT'/'DWELL' string on web and iOS, a raw GMS constant on Android. Modelling
 * it as one typed value gives the three dies a shared vocabulary. The string values are
 * exactly the public `GeofenceEvent.action` contract, so the emitted payload is
 * unchanged.
 */
export enum GeofenceTransition {
  ENTER = 'ENTER',
  EXIT = 'EXIT',
  DWELL = 'DWELL',
}
