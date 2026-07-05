// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 contract · native wire config (Fase 1).
// The FLAT config that actually crosses the Capacitor bridge, using the native wire
// vocabulary (desiredAccuracy, url, httpMode…). The facade's `toFlatConfig` resolves
// the composed cascade (BaseConfig ⊕ overrides) down to this shape. Kept deliberately
// close to what the Kotlin/Swift sides already parse, so the natives barely change.
//
// Consumers never see this — it is the internal contract between the facade and the
// native plugin. The public, clean, composed config lives in config.ts.

/** Numeric accuracy in meters, matching the native `AccuracyLevel`. */
export type WireAccuracy = 0 | 100 | 1000 | 10000;

/** Numeric provider id, matching the native provider constants. */
export type WireProvider = 0 | 1 | 2;

/**
 * Flat native configuration. Every field maps 1:1 to a key the native `configure`
 * already understands. This is the ONLY thing sent over the bridge; the facade
 * produces a fully-resolved instance so the native side does a plain apply
 * (no partial-merge ambiguity — one source of truth is the facade).
 */
export interface NativeConfig {
  // sampling
  desiredAccuracy?: WireAccuracy;
  distanceFilter?: number;
  locationProvider?: WireProvider;
  maxAcceptedAccuracy?: number;
  includeBattery?: boolean;
  mockLocationPolicy?: 'allow' | 'flag' | 'drop';
  activityType?: string;
  interval?: number;
  fastestInterval?: number;
  activitiesInterval?: number;
  activityConfidenceThreshold?: number;
  // stationary detection (Android)
  stationaryRadius?: number;
  stationaryTimeout?: number;
  stationaryPollInterval?: number;
  stationaryPollFast?: number;
  stationaryExitMode?: string;
  wakeLockMode?: 'none' | 'posting' | 'always';
  // survival
  stopOnTerminate?: boolean;
  startOnBoot?: boolean;
  restartOnKill?: boolean;
  heartbeatInterval?: number;
  enableWatchdog?: boolean;
  watchdogIntervalMs?: number;
  iosBackgroundFallback?: 'significantChanges' | 'regionMonitoring' | 'none';
  saveBatteryOnBackground?: boolean;
  pauseLocationUpdates?: boolean;
  showsBackgroundLocationIndicator?: boolean;
  // persistence
  maxLocations?: number;
  // transport
  url?: string;
  httpMethod?: 'POST' | 'GET' | 'PUT' | 'PATCH';
  httpMode?: 'batch' | 'single';
  headers?: Record<string, string>;
  queryParams?: Record<string, string | number>;
  postTemplate?: unknown;
  // sync queue
  syncUrl?: string;
  syncHttpMethod?: 'POST' | 'GET' | 'PUT' | 'PATCH';
  syncMode?: 'batch' | 'single';
  syncThreshold?: number;
  sync?: boolean;
  // priority sync
  prioritySyncEvents?: string[];
  prioritySyncUrl?: string;
  prioritySyncRetries?: number;
  prioritySyncRetryDelays?: number[];
  // notification
  notificationsEnabled?: boolean;
  notificationTitle?: string;
  notificationText?: string;
  notificationIconColor?: string;
  notificationIconLarge?: string;
  notificationIconSmall?: string;
  notificationSyncTitle?: string;
  notificationSyncText?: string;
  notificationSyncCompletedText?: string;
  notificationSyncFailedText?: string;
  showTime?: boolean;
  showDistance?: boolean;
  startForeground?: boolean;
  // driving events (X-ext) — sent as a nested blob the native already accepts
  drivingEvents?: Record<string, unknown>;
  // debug + raw escape hatch
  debug?: boolean;
  [nativeExtra: string]: unknown;
}
