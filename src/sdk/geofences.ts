// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Geofences sub-API (Fase 2).

import type { GeolocationEventMap, GeolocationEventName } from '../definitions/events';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { Geofence } from '../definitions/values';
import { listen, type Subscription } from './stream';

/** Short geofence event names → their payloads. */
export interface GeofenceEvents {
  enter: GeolocationEventMap['geofenceEnter'];
  exit: GeolocationEventMap['geofenceExit'];
  dwell: GeolocationEventMap['geofenceDwell'];
  error: GeolocationEventMap['geofenceError'];
}

const GEOFENCE_EVENT: Record<keyof GeofenceEvents, GeolocationEventName> = {
  enter: 'geofenceEnter',
  exit: 'geofenceExit',
  dwell: 'geofenceDwell',
  error: 'geofenceError',
};

export class GeofencesApi {
  constructor(private readonly native: BackgroundGeolocationNative) {}

  /** Register geofences. Existing ones with the same id are replaced. */
  add(geofences: Geofence[]): Promise<void> {
    // Translate the clean field to the native wire key (loiteringDelayMs → loiteringDelay).
    const wire = geofences.map(({ loiteringDelayMs, ...g }) =>
      loiteringDelayMs === undefined ? g : { ...g, loiteringDelay: loiteringDelayMs },
    );
    return this.native.addGeofences({ geofences: wire as unknown as Geofence[] });
  }

  /** Remove geofences by id. Omit `ids` to remove all. */
  remove(ids?: string[]): Promise<void> {
    return this.native.removeGeofences(ids === undefined ? undefined : { ids });
  }

  /** The current list of registered geofences. */
  async list(): Promise<Geofence[]> {
    return (await this.native.getGeofences()).geofences;
  }

  /** Subscribe to a geofence transition (or error). */
  on<E extends keyof GeofenceEvents>(
    event: E,
    listener: (payload: GeofenceEvents[E]) => void,
  ): Subscription<GeofenceEvents[E]> {
    return listen<GeofenceEvents[E]>(this.native, GEOFENCE_EVENT[event], listener);
  }
}
