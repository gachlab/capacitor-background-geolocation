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
 * How the stationary machine gets out of stationary. The wire value is the
 * NATIVE spelling — `'polling'`, not the public config's `'poll'` — because
 * this type describes what crosses the bridge, and the mapper is what
 * translates. Writing the public spelling here is exactly the bug: it compiled,
 * it round-tripped, and the native never matched it.
 */
export type StationaryExitMode = 'polling' | 'geofence';

/**
 * iOS activity-type hint, in the NATIVE spelling (PascalCase) — the public
 * config uses camelCase and `ACTIVITY_TYPE` in the mapper translates.
 *
 * Worth stating because typing this field is what surfaced it: while it was a
 * bare `string`, nothing said which of the two vocabularies belonged here, and
 * the mapper's `?? loc.activityType` fallback quietly let the camelCase value
 * through whenever the table missed.
 */
export type ActivityTypeHint = 'AutomotiveNavigation' | 'OtherNavigation' | 'Fitness' | 'Other';

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
  /** Typed for the same reason as `stationaryExitMode`: a bare string here
   *  removes the compiler from the only place that could catch a divergence. */
  activityType?: ActivityTypeHint;
  interval?: number;
  fastestInterval?: number;
  activitiesInterval?: number;
  activityConfidenceThreshold?: number;
  // stationary detection (Android)
  stationaryRadius?: number;
  stationaryTimeout?: number;
  stationaryPollInterval?: number;
  stationaryPollFast?: number;
  /**
   * Typed, not `string`, because a bare string is what let this one diverge:
   * the public config declares `'poll' | 'geofence'` and the native constant is
   * `STATIONARY_EXIT_POLLING = "polling"`. `'poll'` fell into the else branch
   * and behaved as polling by luck — the same accident that hid the restart
   * reason bug — but `getConfig()` echoed `'polling'`, outside the declared
   * union, and the day someone adds a third mode or an explicit equality check
   * every config sent as `'poll'` takes the wrong branch.
   */
  stationaryExitMode?: StationaryExitMode;
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
  notificationChannel?: string;
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
