// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 entry point. Registers the flat native contract and exposes the composed facade FACTORY.

import { registerPlugin } from '@capacitor/core';

import type { BackgroundGeolocationNative } from './definitions/roles';
import { BackgroundGeolocation as composeFacade } from './sdk/background-geolocation';

/**
 * The raw flat native proxy (Promise + addListener). Registered by Capacitor; the web
 * implementation lives in web.ts. Most apps use the `BackgroundGeolocation` facade — this
 * is the low-level escape hatch, and the default dependency injected below.
 */
const nativeProxy = registerPlugin<BackgroundGeolocationNative>('BackgroundGeolocation', {
  web: () => import('./web').then((m) => new m.BackgroundGeolocationWeb()),
});

/** The composed v3 facade type (merges with the factory function of the same name below). */
export type BackgroundGeolocation = ReturnType<typeof composeFacade>;

let defaultInstance: BackgroundGeolocation | undefined;

/**
 * Build the v3 facade — composed sub-APIs, reactive config, typed events.
 *
 *   const bg = BackgroundGeolocation();                     // zero-config (shared singleton)
 *   const bg = BackgroundGeolocation({ native: fakeBridge }); // inject a fake for testing
 *
 * With no `native`, the registered Capacitor bridge is used and the instance is memoized, so
 * `BackgroundGeolocation()` from anywhere returns the same object. Passing `native` always
 * builds a fresh, isolated instance (the point of the DI: mock the bridge in a test).
 */
export function BackgroundGeolocation(deps: { native?: BackgroundGeolocationNative } = {}): BackgroundGeolocation {
  if (deps.native !== undefined) return composeFacade({ native: deps.native });
  return (defaultInstance ??= composeFacade({ native: nativeProxy }));
}

/** The raw native proxy, for advanced/low-level use. */
export const NativeBackgroundGeolocation = nativeProxy;

// ── Public types & values ────────────────────────────────────────────────────
export * from './definitions/values';
export * from './definitions/config';
export * from './definitions/events';
export * from './definitions/roles';
export type { NativeConfig } from './definitions/wire';

// SDK sub-API factories + types (for manual composition / testing).
export { ConfigApi } from './sdk/config-api';
export { TrackingApi } from './sdk/tracking';
export { LocationsApi } from './sdk/locations';
export { GeofencesApi } from './sdk/geofences';
export { SyncApi } from './sdk/sync';
export { RecordingsApi } from './sdk/recordings';
export { PermissionsApi } from './sdk/permissions';
export { DiagnosticsApi } from './sdk/diagnostics';
export { DriverApi } from './sdk/driver';
export { LogsApi } from './sdk/logs';
export { PlatformApi } from './sdk/platform';
export { CapabilityError } from './sdk/errors';
export type { Subscription } from './sdk/stream';
export type { TrackingSession } from './sdk/tracking';
export type { PendingQuery } from './sdk/locations';
export type { SyncEvents } from './sdk/sync';
export type { GeofenceEvents } from './sdk/geofences';
export type { DriverEvents } from './sdk/driver';
export type { LogPageOptions, LogStreamOptions } from './sdk/logs';
