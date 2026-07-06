// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Geofences sub-API (Fase 2).

import type { GeolocationEventMap, GeolocationEventName } from '../definitions/events';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { Geofence } from '../definitions/values';
import type { ConfigApi } from './config-api';
import { makeAliasedOn, type Subscription } from './stream';

/** A geofence on the native wire: the clean `loiteringDelayMs` is carried as `loiteringDelay`. */
type WireGeofence = Omit<Geofence, 'loiteringDelayMs'> & { loiteringDelay?: number };

/** Short geofence event names → their payloads. */
export interface GeofenceEvents {
  enter: GeolocationEventMap['geofenceEnter'];
  exit: GeolocationEventMap['geofenceExit'];
  dwell: GeolocationEventMap['geofenceDwell'];
  error: GeolocationEventMap['geofenceError'];
}

/** Real renames only (all four are prefixed); identity names would pass through. */
const GEOFENCE_EVENT: Partial<Record<keyof GeofenceEvents, GeolocationEventName>> = {
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

export function GeofencesApi(deps: { native: BackgroundGeolocationNative; config: ConfigApi }): GeofencesApi {
  const { native, config } = deps;
  return {
    async add(geofences) {
      // Fill any field the registration omits from the plugin-level geofenceDefaults, then
      // translate the clean field name (loiteringDelayMs) to the native wire key (loiteringDelay).
      const defaults = (await config.current()).geofenceDefaults ?? {};
      const wire: WireGeofence[] = geofences.map((gf) => {
        const merged = {
          ...gf,
          radius: gf.radius ?? defaults.radius,
          notifyOnEntry: gf.notifyOnEntry ?? defaults.notifyOnEntry,
          notifyOnExit: gf.notifyOnExit ?? defaults.notifyOnExit,
          notifyOnDwell: gf.notifyOnDwell ?? defaults.notifyOnDwell,
          loiteringDelayMs: gf.loiteringDelayMs ?? defaults.loiteringDelayMs,
        };
        const { loiteringDelayMs, ...rest } = merged;
        return loiteringDelayMs === undefined ? rest : { ...rest, loiteringDelay: loiteringDelayMs };
      });
      return native.addGeofences({ geofences: wire as unknown as Geofence[] });
    },
    remove(ids) {
      return native.removeGeofences(ids === undefined ? undefined : { ids });
    },
    async list() {
      // Translate the native wire key (loiteringDelay) back to the clean field (loiteringDelayMs).
      const wire = (await native.getGeofences()).geofences as unknown as WireGeofence[];
      return wire.map(({ loiteringDelay, ...rest }) =>
        loiteringDelay === undefined ? (rest as Geofence) : ({ ...rest, loiteringDelayMs: loiteringDelay } as Geofence),
      );
    },
    on: makeAliasedOn<GeofenceEvents>(native, GEOFENCE_EVENT),
  };
}
