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

export interface GeofencesApi {
  /** Register geofences. Existing ones with the same id are replaced. */
  add(geofences: Geofence[]): Promise<void>;
  /** Remove geofences by id. Omit `ids` to remove all. */
  remove(ids?: string[]): Promise<void>;
  /** The current list of registered geofences. */
  list(): Promise<Geofence[]>;
  /** Subscribe to a geofence transition (or error). */
  on<E extends keyof GeofenceEvents>(
    event: E,
    listener: (payload: GeofenceEvents[E]) => void,
  ): Subscription<GeofenceEvents[E]>;
}

export function GeofencesApi(deps: { native: BackgroundGeolocationNative }): GeofencesApi {
  const { native } = deps;
  return {
    add(geofences) {
      // Translate the clean field to the native wire key (loiteringDelayMs → loiteringDelay).
      const wire = geofences.map(({ loiteringDelayMs, ...g }) =>
        loiteringDelayMs === undefined ? g : { ...g, loiteringDelay: loiteringDelayMs },
      );
      return native.addGeofences({ geofences: wire as unknown as Geofence[] });
    },
    remove(ids) {
      return native.removeGeofences(ids === undefined ? undefined : { ids });
    },
    async list() {
      return (await native.getGeofences()).geofences;
    },
    on<E extends keyof GeofenceEvents>(
      event: E,
      listener: (payload: GeofenceEvents[E]) => void,
    ): Subscription<GeofenceEvents[E]> {
      return listen<GeofenceEvents[E]>(native, GEOFENCE_EVENT[event], listener);
    },
  };
}
