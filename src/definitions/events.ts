// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 contract · event map (Fase 1).
// One typed map replaces the 38 `addListener` overloads. The native contract gets a
// single generic `addListener` (GeolocationEvents); the facade exposes grouped,
// disposable `.on()` per sub-API on top. Events stay callbacks — the honest primitive
// for a hot source (no async-iterable backpressure lie).

import type { PluginListenerHandle } from '@capacitor/core';

import type {
  Activity,
  AuthorizationStatus,
  GeofenceErrorEvent,
  GeofenceEvent,
  Location,
  LocationError,
  StationaryLocation,
  TripScore,
} from './values';

/** A GPS-derived driving event anchored to a fix. */
export interface DrivingEventPayload {
  location: Location;
  value: number;
}

/** Payload of `possibleCrash` (adds the detection source). */
export interface CrashEventPayload extends DrivingEventPayload {
  source: 'gps' | 'sensor';
}

/** Payload of `speeding`. */
export interface SpeedingEventPayload {
  location: Location;
  speedKmh: number;
  limitKmh: number;
}

/** Payload of `tripEnd`. */
export interface TripEndPayload {
  location: Location;
  distanceKm: number;
  durationMs: number;
  score?: TripScore;
}

/** Payload of `idleStart`. */
export interface IdleStartPayload {
  location: Location;
  startedAt: number;
}

/** Payload of `idleEnd`. */
export interface IdleEndPayload {
  location: Location;
  durationMs: number;
  startedAt: number;
}

/** Payload of `serviceRestarted`. */
/**
 * Why the native service came back. Exported on its own because
 * `diagnostics.killReason()` answers the same question through a different door
 * and has to answer it with the same words — the two used to disagree, and the
 * one that was wrong was the one nobody could see in a type.
 */
export type ServiceRestartReason = 'watchdog' | 'systemKill' | 'boot' | 'appRemoved';

export interface ServiceRestartedPayload {
  reason: ServiceRestartReason;
}

/** Payload of `sos` — the flattened user payload plus the latest known location. */
export interface SosPayload {
  location?: Location;
  [key: string]: unknown;
}

/**
 * The full event catalog: name → payload. Adding an event here is the single place
 * it needs to be declared; the generic `addListener` and every sub-API `.on()`
 * derive their types from this map.
 */
export interface GeolocationEventMap {
  // core location
  location: Location;
  stationary: StationaryLocation;
  activity: Activity;
  /**
   * Periodic keep-alive. Carries the last known Location — but BEFORE the first fix exists
   * (cold start) both natives emit an EMPTY payload, so narrow with `'latitude' in event`
   * before reading location fields.
   */
  heartbeat: Location | Record<string, never>;
  providerChange: { provider: string };
  // lifecycle & system
  start: void;
  stop: void;
  error: LocationError;
  authorization: { status: AuthorizationStatus };
  foreground: void;
  background: void;
  abortRequested: void;
  httpAuthorization: void;
  /**
   * The persisted base config changed — the native is the source of truth. Carries the clean
   * base as a JSON blob so any facade instance (or the raw proxy path) can adopt it. Consumed by
   * `bg.config` to keep `current()`/`on()` coherent across writers; rarely subscribed directly.
   */
  configChanged: { baseConfigJson: string };
  serviceRestarted: ServiceRestartedPayload;
  iosFallbackActivated: { strategy: 'significantChanges' | 'regionMonitoring' };
  // sync
  syncStart: void;
  syncProgress: { progress: number };
  syncSuccess: { sent: number };
  syncError: { httpStatus: number; message: string };
  prioritySyncSuccess: { eventType: string; attemptNumber: number };
  prioritySyncFailed: { eventType: string; httpStatus: number; attempts: number };
  // geofence
  geofenceEnter: GeofenceEvent;
  geofenceExit: GeofenceEvent;
  geofenceDwell: GeofenceEvent;
  geofenceError: GeofenceErrorEvent;
  // driver intelligence & safety (X-ext)
  tripStart: Location;
  tripEnd: TripEndPayload;
  moving: Location;
  stopped: Location;
  speeding: SpeedingEventPayload;
  hardBrake: DrivingEventPayload;
  rapidAcceleration: DrivingEventPayload;
  sharpTurn: DrivingEventPayload;
  possibleCrash: CrashEventPayload;
  phoneUsageWhileDriving: { location?: Location };
  idleStart: IdleStartPayload;
  idleEnd: IdleEndPayload;
  sos: SosPayload;
}

/** Every event name known to the plugin. */
export type GeolocationEventName = keyof GeolocationEventMap;

/** Listener callback for a given event, correctly typed by its payload. */
export type GeolocationEventListener<E extends GeolocationEventName> = GeolocationEventMap[E] extends void
  ? () => void
  : (event: GeolocationEventMap[E]) => void;

/**
 * The native event surface — a single generic `addListener` replacing 38 overloads.
 * The facade wraps this into disposable, grouped `.on()` per sub-API.
 */
export interface GeolocationEvents {
  addListener<E extends GeolocationEventName>(
    eventName: E,
    listener: GeolocationEventListener<E>,
  ): Promise<PluginListenerHandle>;
  removeAllListeners(): Promise<void>;
}

/** Discriminated union yielded by `bg.driver.on()` / a merged driving-event stream. */
export type DrivingEvent =
  | ({ type: 'hardBrake' } & DrivingEventPayload)
  | ({ type: 'rapidAcceleration' } & DrivingEventPayload)
  | ({ type: 'sharpTurn' } & DrivingEventPayload)
  | ({ type: 'possibleCrash' } & CrashEventPayload)
  | ({ type: 'speeding' } & SpeedingEventPayload)
  | { type: 'phoneUsageWhileDriving'; location?: Location };
