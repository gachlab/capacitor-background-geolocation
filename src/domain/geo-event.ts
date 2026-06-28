// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import { GeofenceTransition } from './geofence-transition';

/**
 * A geofence event — the domain "GeoEvent" of the ARCHITECTURE.md domain set: a
 * [transition] (enter/exit/dwell) at a region ([geofenceId]). Pure; the platform
 * location that accompanies the emitted event stays in the bus payload (it is a
 * platform type, not domain). The web/Android/iOS dies share this vocabulary.
 */
export class GeoEvent {
  constructor(
    readonly geofenceId: string,
    readonly transition: GeofenceTransition,
  ) {}
}
