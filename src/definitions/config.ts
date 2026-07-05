// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 contract · composed configuration (Fase 1).
//
// Two-tier, cascading config with CLEAN vocabulary:
//
//   base (BG.configure, once)  ⊕  session (tracking.start)  ⊕  per-call  =  effective
//
// Shared config (transport, notification identity, sampling) lives on the plugin
// and is set ONCE. Features inherit and override only their delta. The facade
// resolves the cascade and translates to the flat native wire (NativeConfig in
// wire.ts). Merge rule: deep-merge for maps (headers, queryParams), replace for
// scalars, `null` = explicit unset. Modern names here; wire names stay native-side.

import type {
  Accuracy,
  DeliveryMode,
  DrivingEventType,
  HttpMethod,
  LocationProvider,
  MockPolicy,
} from './values';

// ---------------------------------------------------------------------------
// Shared groups (base) — configured once, reused across features
// ---------------------------------------------------------------------------

/** HTTP transport shared by live posting, sync queue and priority sync. Set once. */
export interface TransportConfig {
  /** Base URL. Feature endpoints (sync, priority) append their own `path`. */
  baseUrl?: string;
  /** Headers merged into every request (deep-merged across the cascade). */
  headers?: Record<string, string>;
  /** Default HTTP method. @default 'POST' */
  method?: HttpMethod;
  /** Placeholder values for URL/body templating. */
  queryParams?: Record<string, string | number>;
  /** Body template applied to each location. */
  bodyTemplate?: unknown;
}

/** Foreground / sync notification identity. Shared across purposes. */
export interface NotificationConfig {
  /** Enable local notifications during tracking/sync (Android). @default true */
  enabled?: boolean;
  channel?: string;
  icon?: { small?: string; large?: string };
  /** Accent color (`#RRGGBB`). */
  color?: string;
  title?: string;
  text?: string;
}

/** Base sampling — the default location quality every feature inherits. */
export interface SamplingConfig {
  /** @default 'medium' */
  accuracy?: Accuracy;
  /** Minimum horizontal distance (m) between updates. @default 500 */
  distanceFilter?: number;
  /** @default 'distanceFilter' */
  provider?: LocationProvider;
  /** Drop fixes whose accuracy is worse than this (m). */
  maxAcceptedAccuracy?: number;
  /** Stamp battery level / charging state on every fix. @default true */
  includeBattery?: boolean;
  /** Policy for samples flagged as mock. @default 'allow' */
  mockPolicy?: MockPolicy;
  /** iOS activity type hint. */
  activityType?: 'automotiveNavigation' | 'otherNavigation' | 'fitness' | 'other';
}

/** Background-survival strategy (global). */
export interface SurvivalConfig {
  /** Stop tracking when the app is terminated. @default true */
  stopOnTerminate?: boolean;
  /** Start tracking on device boot (Android). @default false */
  startOnBoot?: boolean;
  /** Use START_STICKY so the OS restarts the service (Android). @default true */
  restartOnKill?: boolean;
  /** Heartbeat tick interval (ms). 0 disables. @default 0 */
  heartbeatInterval?: number;
  /** Restart the provider if no fix arrives within the window (Android). */
  watchdog?: { enabled?: boolean; intervalMs?: number };
  /** iOS background fallback when battery-saving in background. */
  iosBackgroundFallback?: 'significantChanges' | 'regionMonitoring' | 'none';
  /** iOS: switch to significant changes in background. @default false */
  saveBatteryOnBackground?: boolean;
}

/** Local persistence limits (global). */
export interface PersistenceConfig {
  /** Max locations stored in the local DB. @default 10000 */
  maxLocations?: number;
}

// ---------------------------------------------------------------------------
// Feature groups — compose on top of the base, override only their delta
// ---------------------------------------------------------------------------

/** Priority-sync: immediate POST for high-value events, bypassing the queue. */
export interface PrioritySyncConfig {
  events?: (DrivingEventType | 'sos')[];
  /** Endpoint appended to `transport.baseUrl`. Falls back to the sync path. */
  path?: string;
  /** Retry attempts before `prioritySyncFailed`. @default 3 */
  retries?: number;
  /** Delay (ms) between attempts. Length must match `retries`. */
  retryDelaysMs?: number[];
}

/** Sync-queue behavior. Transport (baseUrl/headers) comes from the base `transport`. */
export interface SyncConfig {
  /** Endpoint appended to `transport.baseUrl` for queued locations. */
  path?: string;
  /** Delivery mode for queued locations. @default 'batch' */
  mode?: DeliveryMode;
  /** Batch size. @default 100 */
  threshold?: number;
  /** Whether automatic sync is enabled. @default true */
  auto?: boolean;
  priority?: PrioritySyncConfig;
}

/** Per-category weights for the driver-behavior score. Must sum to 100. */
export interface DrivingScoring {
  speeding?: number;
  hardBraking?: number;
  rapidAcceleration?: number;
  sharpTurn?: number;
  phoneUsage?: number;
}

/**
 * Driver-intelligence config (the X-extension). Gated at runtime by
 * `capabilities.driverIntelligence`; applied via `bg.driver.enable()`.
 */
export interface DrivingConfig {
  enabled?: boolean;
  /** Speed limit (km/h) for the `speeding` event. 0 disables. */
  speedLimit?: number;
  minMovingSpeed?: number;
  stoppedDurationMs?: number;
  minTripSpeed?: number;
  minTripDurationMs?: number;
  hardBrakeMps2?: number;
  rapidAccelMps2?: number;
  sharpTurnDegPerSec?: number;
  crashImpactKmh?: number;
  crashWindowMs?: number;
  /** Delay firing `possibleCrash` until stopped this long after impact. @default 0 */
  crashConfirmWindowMs?: number;
  /** Enable accelerometer/gyroscope sensor fusion. @default false */
  sensorFusion?: boolean;
  crashImpactG?: number;
  sensorCrashCooldownMs?: number;
  phoneUsageWindowMs?: number;
  phoneUsageCooldownMs?: number;
  /** Stationary time during a trip before `idleStart`. @default 300000 */
  idleThresholdMs?: number;
  /** Continuous movement after idle before `idleEnd`. @default 30000 */
  idleEndThresholdMs?: number;
  scoring?: DrivingScoring;
}

/** Default geofence behavior applied to registrations that omit their own. */
export interface GeofenceDefaults {
  radius?: number;
  loiteringDelayMs?: number;
  notifyOnEntry?: boolean;
  notifyOnExit?: boolean;
  notifyOnDwell?: boolean;
}

// ---------------------------------------------------------------------------
// The three cascade levels
// ---------------------------------------------------------------------------

/**
 * Plugin-level base config — set once via `BG.configure()`. Everything shared
 * lives here so it is written a single time; features inherit from it.
 */
export interface BaseConfig {
  location?: SamplingConfig;
  transport?: TransportConfig;
  notification?: NotificationConfig;
  survival?: SurvivalConfig;
  persistence?: PersistenceConfig;
  sync?: SyncConfig;
  driving?: DrivingConfig;
  geofenceDefaults?: GeofenceDefaults;
  /** Emit debugging sounds for life-cycle events. @default false */
  debug?: boolean;
  /**
   * Explicit, typed escape hatch for raw native flags — replaces the old
   * `[extra: string]: unknown` index signature that silently ate typos.
   */
  native?: Record<string, unknown>;
}

/**
 * Session-level override passed to `tracking.start()`. Overrides the base for the
 * lifetime of this tracking session (scope: session — changes the running provider).
 */
export interface StartOverride {
  location?: SamplingConfig;
  notification?: Pick<NotificationConfig, 'title' | 'text'>;
  sync?: SyncConfig;
  driving?: DrivingConfig;
}

/**
 * Per-call one-shot options (scope: request — applies only to this operation, e.g.
 * a single `locations.current()` with a cheaper accuracy than the running session).
 */
export interface CurrentLocationOptions {
  accuracy?: Accuracy;
  /** Max time to wait for a fix (ms). */
  timeout?: number;
  /** Max age of an acceptable cached fix (ms). */
  maximumAge?: number;
  /** Cancels the caller's wait (does not stop the native GPS work). */
  signal?: AbortSignal;
}
