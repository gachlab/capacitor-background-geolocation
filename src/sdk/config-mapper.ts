// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · config resolution + translation (Fase 2) — THE risk piece (review #8).
//
// Two jobs, kept small and centralized so they can't drift silently:
//   mergeConfig()  — resolves the cascade: base ⊕ override. Maps deep-merge, scalars
//                    replace, `null` unsets.
//   toFlatConfig() — translates the composed clean config to the flat native wire.
//
// Every native key is written in exactly ONE place here. Fase 4 adds a guard test that
// fails if a native wire key has no mapping, so adding a native field can't rot.

import type { BaseConfig, DrivingConfig, StartOverride } from '../definitions/config';
import type { Accuracy, LocationProvider } from '../definitions/values';
import type { NativeConfig, WireAccuracy, WireProvider } from '../definitions/wire';

const ACCURACY_METERS: Record<Accuracy, WireAccuracy> = { high: 0, medium: 100, low: 1000, passive: 10000 };
const PROVIDER_ID: Record<LocationProvider, WireProvider> = { distanceFilter: 0, activity: 1, raw: 2 };
const ACTIVITY_TYPE: Record<string, string> = {
  automotiveNavigation: 'AutomotiveNavigation',
  otherNavigation: 'OtherNavigation',
  fitness: 'Fitness',
  other: 'Other',
};

// ── Cascade merge ────────────────────────────────────────────────────────────

/** Resolve one cascade level: `base ⊕ override`. Deep for maps, replace for scalars, `null` = unset. */
export function mergeConfig(base: BaseConfig, override?: StartOverride | Partial<BaseConfig>): BaseConfig {
  if (override === undefined) return base;
  return deepMerge(base as Record<string, unknown>, override as Record<string, unknown>) as BaseConfig;
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function deepMerge(base: Record<string, unknown>, patch: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = { ...base };
  for (const [key, value] of Object.entries(patch)) {
    if (value === null) {
      delete out[key]; // explicit unset
    } else if (value === undefined) {
      continue; // not provided → keep base
    } else if (isPlainObject(value) && isPlainObject(out[key])) {
      out[key] = deepMerge(out[key] as Record<string, unknown>, value);
    } else if (isPlainObject(value) || Array.isArray(value)) {
      // Copy — never alias the caller's nested object/array into our stored state, or a
      // later external mutation of the passed patch would silently rewrite the config.
      out[key] = safeClone(value);
    } else {
      out[key] = value; // scalar → replace
    }
  }
  return out;
}

/**
 * Structured deep clone that degrades gracefully instead of throwing on a non-cloneable value
 * (e.g. a function slipped into the `native` escape hatch). Used wherever we must NOT alias
 * caller state (deepMerge) or must snapshot for subscribers (config-api) without a stray
 * non-serializable value crashing configure()/emit() after the native side already applied.
 */
export function safeClone<T>(value: T): T {
  try {
    return structuredClone(value);
  } catch {
    try {
      return JSON.parse(JSON.stringify(value)) as T; // drops functions/undefined, but never throws on cycles-free config
    } catch {
      return value; // last resort: keep the reference rather than throw
    }
  }
}

// ── Translation to native wire ───────────────────────────────────────────────

/** Translate a fully-resolved composed config to the flat native wire config. */
export function toFlatConfig(cfg: BaseConfig): NativeConfig {
  const out: NativeConfig = {};

  const loc = cfg.location;
  if (loc) {
    if (loc.accuracy !== undefined) out.desiredAccuracy = ACCURACY_METERS[loc.accuracy];
    if (loc.distanceFilter !== undefined) out.distanceFilter = loc.distanceFilter;
    if (loc.provider !== undefined) out.locationProvider = PROVIDER_ID[loc.provider];
    if (loc.maxAcceptedAccuracy !== undefined) out.maxAcceptedAccuracy = loc.maxAcceptedAccuracy;
    if (loc.includeBattery !== undefined) out.includeBattery = loc.includeBattery;
    if (loc.mockPolicy !== undefined) out.mockLocationPolicy = loc.mockPolicy;
    if (loc.activityType !== undefined) out.activityType = ACTIVITY_TYPE[loc.activityType] ?? loc.activityType;
    if (loc.interval !== undefined) out.interval = loc.interval;
    if (loc.fastestInterval !== undefined) out.fastestInterval = loc.fastestInterval;
    if (loc.activityInterval !== undefined) out.activitiesInterval = loc.activityInterval;
    if (loc.activityConfidenceThreshold !== undefined)
      out.activityConfidenceThreshold = loc.activityConfidenceThreshold;
  }

  const st = cfg.stationary;
  if (st) {
    if (st.radius !== undefined) out.stationaryRadius = st.radius;
    if (st.timeout !== undefined) out.stationaryTimeout = st.timeout;
    if (st.pollInterval !== undefined) out.stationaryPollInterval = st.pollInterval;
    if (st.pollFast !== undefined) out.stationaryPollFast = st.pollFast;
    if (st.exitMode !== undefined) out.stationaryExitMode = st.exitMode;
  }

  const t = cfg.transport;
  if (t) {
    if (t.baseUrl !== undefined) out.url = t.baseUrl;
    if (t.headers !== undefined) out.headers = t.headers;
    if (t.method !== undefined) out.httpMethod = t.method;
    if (t.mode !== undefined) out.httpMode = t.mode;
    if (t.queryParams !== undefined) out.queryParams = t.queryParams;
    if (t.bodyTemplate !== undefined) out.postTemplate = t.bodyTemplate;
  }

  const sync = cfg.sync;
  if (sync) {
    if (sync.path !== undefined) out.syncUrl = joinUrl(t?.baseUrl, sync.path);
    if (sync.mode !== undefined) out.syncMode = sync.mode;
    if (sync.threshold !== undefined) out.syncThreshold = sync.threshold;
    if (sync.auto !== undefined) out.sync = sync.auto;
    // Sync method is independent of the live-transport method, falling back to it when unset.
    const syncMethod = sync.method ?? t?.method;
    if (syncMethod !== undefined) out.syncHttpMethod = syncMethod;
    const p = sync.priority;
    if (p) {
      if (p.events !== undefined) out.prioritySyncEvents = p.events;
      if (p.path !== undefined) out.prioritySyncUrl = joinUrl(t?.baseUrl, p.path);
      if (p.retries !== undefined) out.prioritySyncRetries = p.retries;
      if (p.retryDelaysMs !== undefined) out.prioritySyncRetryDelays = p.retryDelaysMs;
    }
  }

  const n = cfg.notification;
  if (n) {
    if (n.enabled !== undefined) out.notificationsEnabled = n.enabled;
    if (n.channel !== undefined) out.notificationChannel = n.channel;
    if (n.title !== undefined) out.notificationTitle = n.title;
    if (n.text !== undefined) out.notificationText = n.text;
    if (n.color !== undefined) out.notificationIconColor = n.color;
    if (n.icon?.large !== undefined) out.notificationIconLarge = n.icon.large;
    if (n.icon?.small !== undefined) out.notificationIconSmall = n.icon.small;
    if (n.foreground !== undefined) out.startForeground = n.foreground;
    if (n.showTime !== undefined) out.showTime = n.showTime;
    if (n.showDistance !== undefined) out.showDistance = n.showDistance;
    if (n.sync?.title !== undefined) out.notificationSyncTitle = n.sync.title;
    if (n.sync?.text !== undefined) out.notificationSyncText = n.sync.text;
    if (n.sync?.completedText !== undefined) out.notificationSyncCompletedText = n.sync.completedText;
    if (n.sync?.failedText !== undefined) out.notificationSyncFailedText = n.sync.failedText;
  }

  const s = cfg.survival;
  if (s) {
    if (s.stopOnTerminate !== undefined) out.stopOnTerminate = s.stopOnTerminate;
    if (s.startOnBoot !== undefined) out.startOnBoot = s.startOnBoot;
    if (s.restartOnKill !== undefined) out.restartOnKill = s.restartOnKill;
    if (s.heartbeatInterval !== undefined) out.heartbeatInterval = s.heartbeatInterval;
    if (s.iosBackgroundFallback !== undefined) out.iosBackgroundFallback = s.iosBackgroundFallback;
    if (s.saveBatteryOnBackground !== undefined) out.saveBatteryOnBackground = s.saveBatteryOnBackground;
    if (s.watchdog?.enabled !== undefined) out.enableWatchdog = s.watchdog.enabled;
    if (s.watchdog?.intervalMs !== undefined) out.watchdogIntervalMs = s.watchdog.intervalMs;
    if (s.pauseLocationUpdates !== undefined) out.pauseLocationUpdates = s.pauseLocationUpdates;
    if (s.showsBackgroundLocationIndicator !== undefined)
      out.showsBackgroundLocationIndicator = s.showsBackgroundLocationIndicator;
    if (s.wakeLockMode !== undefined) out.wakeLockMode = s.wakeLockMode;
  }

  if (cfg.persistence?.maxLocations !== undefined) out.maxLocations = cfg.persistence.maxLocations;
  if (cfg.driving !== undefined) out.drivingEvents = toDrivingEvents(cfg.driving);
  if (cfg.debug !== undefined) out.debug = cfg.debug;

  // Explicit, typed escape hatch — raw native flags passed straight through.
  if (cfg.native) Object.assign(out, cfg.native);

  return out;
}

/** The clean DrivingConfig → the native `drivingEvents` blob (a few keys are renamed). */
function toDrivingEvents(d: DrivingConfig): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  const copy = (key: keyof DrivingConfig, to: string = key): void => {
    if (d[key] !== undefined) out[to] = d[key];
  };
  copy('enabled');
  copy('speedLimit');
  copy('minMovingSpeed');
  copy('stoppedDurationMs', 'stoppedDuration');
  copy('minTripSpeed');
  copy('minTripDurationMs', 'minTripDuration');
  copy('hardBrakeMps2');
  copy('rapidAccelMps2');
  copy('sharpTurnDegPerSec');
  copy('crashImpactKmh');
  copy('crashWindowMs');
  copy('crashConfirmWindowMs');
  copy('sensorFusion');
  copy('crashImpactG');
  copy('sensorCrashCooldownMs');
  copy('phoneUsageWindowMs');
  copy('phoneUsageCooldownMs');
  copy('idleThresholdMs');
  copy('idleEndThresholdMs');
  if (d.scoring) {
    out.scoring = {
      speedingWeight: d.scoring.speeding,
      hardBrakingWeight: d.scoring.hardBraking,
      rapidAccelWeight: d.scoring.rapidAcceleration,
      sharpTurnWeight: d.scoring.sharpTurn,
      phoneUsageWeight: d.scoring.phoneUsage,
    };
  }
  return out;
}

function joinUrl(base: string | undefined, path: string): string {
  if (base === undefined || /^https?:\/\//.test(path)) return path;
  return `${base.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`;
}
