// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 entry point. Registers the flat native contract and exposes the composed facade.

import { registerPlugin } from '@capacitor/core';

import type { BackgroundGeolocationNative } from './definitions/roles';
import { BackgroundGeolocation as BackgroundGeolocationClient } from './sdk/background-geolocation';

/**
 * The raw flat native proxy (Promise + addListener). Registered by Capacitor; the web
 * implementation lives in web.ts. Most apps use the `BackgroundGeolocation` facade
 * below — this is the low-level escape hatch.
 */
const native = registerPlugin<BackgroundGeolocationNative>('BackgroundGeolocation', {
  web: () => import('./web').then((m) => new m.BackgroundGeolocationWeb()),
});

/** The v3 facade — composed sub-APIs, clean two-tier config cascade, typed events. */
export const BackgroundGeolocation = new BackgroundGeolocationClient(native);

/** The raw native proxy, for advanced/low-level use. */
export const NativeBackgroundGeolocation = native;

// ── Public types & values ────────────────────────────────────────────────────
export * from './definitions/values';
export * from './definitions/config';
export * from './definitions/events';
export * from './definitions/roles';
export type { NativeConfig } from './definitions/wire';

// The facade class (for testing / manual composition) and SDK types.
export { BackgroundGeolocationClient };
export { CapabilityError } from './sdk/errors';
export type { Subscription } from './sdk/stream';
export type { TrackingSession } from './sdk/tracking';
export type { PendingQuery } from './sdk/locations';
export type { SyncEvents } from './sdk/sync';
export type { GeofenceEvents } from './sdk/geofences';
export type { DriverEvents } from './sdk/driver';
export type { LogPageOptions } from './sdk/logs';
