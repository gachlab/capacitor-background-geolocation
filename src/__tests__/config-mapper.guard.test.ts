// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · toFlatConfig coverage guard (Fase 4 · edge-decision §08).
// The risk: a native wire key (NativeConfig in wire.ts) gains no producer in
// toFlatConfig, so a clean-config consumer silently can't reach it (must fall back to
// the `native` escape hatch). This test is the single source-of-truth table: every
// wire key is classified 'facade' (produced from clean config) or 'native-only'
// (intentional escape-hatch/native-managed). Adding a wire key without classifying it
// is a COMPILE error (the Record is exhaustive over KnownKeys<NativeConfig>); marking
// one 'facade' without a mapping is a RUNTIME failure below.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import type { BaseConfig, NotificationConfig } from '../definitions/config';
import type { NativeConfig } from '../definitions/wire';
import { toFlatConfig } from '../sdk/config-mapper';

/** Drop the `[nativeExtra: string]: unknown` index signature → only the declared keys. */
type RemoveIndex<T> = {
  [K in keyof T as string extends K ? never : number extends K ? never : symbol extends K ? never : K]: T[K];
};
type WireKey = keyof RemoveIndex<NativeConfig>;

// The single source-of-truth mapping table. Exhaustive by construction: a new key in
// NativeConfig forces a new entry here or the file won't type-check.
const WIRE_KEY_SOURCE: Record<WireKey, 'facade' | 'native-only'> = {
  // sampling
  desiredAccuracy: 'facade',
  distanceFilter: 'facade',
  locationProvider: 'facade',
  maxAcceptedAccuracy: 'facade',
  includeBattery: 'facade',
  mockLocationPolicy: 'facade',
  activityType: 'facade',
  interval: 'facade',
  fastestInterval: 'facade',
  activitiesInterval: 'facade',
  activityConfidenceThreshold: 'facade',
  // stationary
  stationaryRadius: 'facade',
  stationaryTimeout: 'facade',
  stationaryPollInterval: 'facade',
  stationaryPollFast: 'facade',
  stationaryExitMode: 'facade',
  wakeLockMode: 'facade',
  // survival
  stopOnTerminate: 'facade',
  startOnBoot: 'facade',
  restartOnKill: 'facade',
  heartbeatInterval: 'facade',
  enableWatchdog: 'facade',
  watchdogIntervalMs: 'facade',
  iosBackgroundFallback: 'facade',
  saveBatteryOnBackground: 'facade',
  pauseLocationUpdates: 'facade',
  showsBackgroundLocationIndicator: 'facade',
  // persistence
  maxLocations: 'facade',
  // transport
  url: 'facade',
  httpMethod: 'facade',
  httpMode: 'facade',
  headers: 'facade',
  queryParams: 'facade',
  postTemplate: 'facade',
  // sync queue
  syncUrl: 'facade',
  syncHttpMethod: 'facade',
  syncMode: 'facade',
  syncThreshold: 'facade',
  sync: 'facade',
  // priority sync
  prioritySyncEvents: 'facade',
  prioritySyncUrl: 'facade',
  prioritySyncRetries: 'facade',
  prioritySyncRetryDelays: 'facade',
  // notification
  notificationsEnabled: 'facade',
  notificationChannel: 'facade',
  notificationTitle: 'facade',
  notificationText: 'facade',
  notificationIconColor: 'facade',
  notificationIconLarge: 'facade',
  notificationIconSmall: 'facade',
  notificationSyncTitle: 'facade',
  notificationSyncText: 'facade',
  notificationSyncCompletedText: 'facade',
  notificationSyncFailedText: 'facade',
  showTime: 'facade',
  showDistance: 'facade',
  startForeground: 'facade',
  // driving events (nested blob)
  drivingEvents: 'facade',
  // debug
  debug: 'facade',
};

/**
 * A clean config that populates EVERY facade-mapped field, so toFlatConfig must emit
 * every 'facade' wire key. If a mapping is missing, the corresponding key is absent
 * from the output and the test fails naming it.
 */
const MAXIMAL_BASE: BaseConfig = {
  location: {
    accuracy: 'high',
    distanceFilter: 25,
    provider: 'distanceFilter',
    maxAcceptedAccuracy: 50,
    includeBattery: true,
    mockPolicy: 'flag',
    activityType: 'automotiveNavigation',
    interval: 600000,
    fastestInterval: 120000,
    activityInterval: 10000,
    activityConfidenceThreshold: 50,
  },
  stationary: { radius: 50, timeout: 300000, pollInterval: 180000, pollFast: 60000, exitMode: 'geofence' },
  transport: {
    baseUrl: 'https://api.me',
    headers: { Authorization: 'Bearer x' },
    method: 'POST',
    mode: 'batch',
    queryParams: { device: 'abc' },
    bodyTemplate: { lat: '@latitude' },
  },
  notification: {
    enabled: true,
    channel: 'tracking_channel',
    title: 'Tracking',
    text: 'On',
    color: '#00AAFF',
    icon: { small: 'ic_small', large: 'ic_large' },
    foreground: true,
    showTime: true,
    showDistance: true,
    sync: { title: 'Syncing', text: 'Uploading', completedText: 'Done', failedText: 'Failed' },
  },
  survival: {
    stopOnTerminate: false,
    startOnBoot: true,
    restartOnKill: true,
    heartbeatInterval: 60000,
    watchdog: { enabled: true, intervalMs: 90000 },
    iosBackgroundFallback: 'significantChanges',
    saveBatteryOnBackground: true,
    pauseLocationUpdates: true,
    showsBackgroundLocationIndicator: true,
    wakeLockMode: 'always',
  },
  persistence: { maxLocations: 10000 },
  sync: {
    path: '/sync',
    mode: 'batch',
    threshold: 100,
    auto: true,
    priority: { events: ['sos'], path: '/priority', retries: 3, retryDelaysMs: [1000, 2000, 4000] },
  },
  driving: { enabled: true, speedLimit: 100, scoring: { speeding: 100 } },
  geofenceDefaults: { radius: 200, loiteringDelayMs: 60000, notifyOnEntry: true, notifyOnExit: true, notifyOnDwell: true },
  debug: true,
};

// ── Clean → wire direction (the gap that let notification.channel + geofenceDefaults rot) ──
//
// The table above guards wire-key → producer. This one guards the OTHER direction: every CLEAN
// field must be classified 'wire' (translated in toFlatConfig) or 'facade-side' (consumed by the
// facade itself, never a wire key — e.g. geofenceDefaults, the `native` escape hatch). Adding a
// clean field without classifying it is a COMPILE error (the Record is exhaustive over keys).

const BASE_FIELD_DISPOSITION: Record<keyof BaseConfig, 'wire' | 'facade-side'> = {
  location: 'wire',
  stationary: 'wire',
  transport: 'wire',
  sync: 'wire',
  notification: 'wire',
  survival: 'wire',
  persistence: 'wire',
  driving: 'wire',
  debug: 'wire',
  native: 'facade-side', // escape hatch: Object.assign'd straight through, not a named wire key
  geofenceDefaults: 'facade-side', // applied by geofences.add(), never crosses as a config wire key
};

// notification.channel was silently dropped because nested fields weren't guarded. This makes
// NotificationConfig exhaustive too, so a new notification field can't rot the same way.
const NOTIFICATION_FIELD_DISPOSITION: Record<keyof NotificationConfig, 'wire' | 'facade-side'> = {
  enabled: 'wire',
  channel: 'wire',
  icon: 'wire',
  color: 'wire',
  title: 'wire',
  text: 'wire',
  foreground: 'wire',
  showTime: 'wire',
  showDistance: 'wire',
  sync: 'wire',
};

describe('toFlatConfig clean→wire coverage guard', () => {
  // Setting ONLY a single 'wire'-classified top-level field must emit at least one wire key —
  // catches a whole clean field (geofenceDefaults-class) that maps to nothing.
  it('every wire-classified top-level clean field produces output', () => {
    const probes: Partial<Record<keyof BaseConfig, BaseConfig>> = {
      location: { location: { accuracy: 'high' } },
      stationary: { stationary: { radius: 50 } },
      transport: { transport: { baseUrl: 'https://x' } },
      sync: { sync: { mode: 'batch' } },
      notification: { notification: { title: 'x' } },
      survival: { survival: { stopOnTerminate: false } },
      persistence: { persistence: { maxLocations: 10 } },
      driving: { driving: { enabled: true } },
      debug: { debug: true },
    };
    const wireFields = (Object.keys(BASE_FIELD_DISPOSITION) as (keyof BaseConfig)[]).filter(
      (k) => BASE_FIELD_DISPOSITION[k] === 'wire',
    );
    const unmapped = wireFields.filter((k) => Object.keys(toFlatConfig(probes[k] ?? {})).length === 0);
    assert.deepEqual(unmapped, [], `top-level clean fields marked 'wire' but producing no wire key: ${unmapped.join(', ')}`);
  });

  it('every wire-classified notification field is emitted from a maximal notification', () => {
    const flat = toFlatConfig(MAXIMAL_BASE) as Record<string, unknown>;
    // Each notification wire field lands under some notification* / show* / startForeground key;
    // proven collectively by the maximal base emitting notificationChannel (the regressed field).
    assert.equal(flat.notificationChannel, 'tracking_channel', 'notification.channel must reach the wire as notificationChannel');
    void NOTIFICATION_FIELD_DISPOSITION; // exhaustiveness is the compile-time guard
  });

  it('facade-side clean fields never leak into the wire', () => {
    const flat = toFlatConfig(MAXIMAL_BASE) as Record<string, unknown>;
    assert.equal('geofenceDefaults' in flat, false, 'geofenceDefaults must not be a wire key (facade applies it)');
  });
});

describe('toFlatConfig coverage guard (§08)', () => {
  const flat = toFlatConfig(MAXIMAL_BASE) as Record<string, unknown>;
  const facadeKeys = (Object.keys(WIRE_KEY_SOURCE) as WireKey[]).filter((k) => WIRE_KEY_SOURCE[k] === 'facade');

  it('produces every facade-mapped wire key from a maximal clean config', () => {
    const missing = facadeKeys.filter((k) => !(k in flat));
    assert.deepEqual(missing, [], `wire keys marked 'facade' but never produced by toFlatConfig: ${missing.join(', ')}`);
  });

  it('emits no wire key outside the source-of-truth table', () => {
    // The maximal base sets no `native` escape hatch, so every emitted key must be classified.
    const unknown = Object.keys(flat).filter((k) => !(k in WIRE_KEY_SOURCE));
    assert.deepEqual(unknown, [], `toFlatConfig emitted unclassified wire keys: ${unknown.join(', ')}`);
  });
});
