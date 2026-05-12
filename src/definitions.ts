// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM
//
// Capacitor plugin definitions for @josuelmm/capacitor-background-geolocation.
// Translated from the Cordova plugin's www/BackgroundGeolocation.d.ts.
// Cordova-style success/fail callbacks have been collapsed into Promises and
// `on(eventName, cb)` has been replaced by Capacitor's `addListener` overloads.

import type { PermissionState, PluginListenerHandle } from '@capacitor/core';

// ---------------------------------------------------------------------------
// Enums / unions
// ---------------------------------------------------------------------------

/**
 * Location provider name. Strings are preferred in the Capacitor API; numeric
 * values are accepted via {@link LocationProviderValue} for back-compat with
 * the Cordova plugin.
 *
 * @since 0.1.0
 */
export type LocationProvider =
  | 'DISTANCE_FILTER'
  | 'ACTIVITY_PROVIDER'
  | 'RAW_PROVIDER';

/**
 * Numeric mapping of {@link LocationProvider} matching the Cordova plugin.
 *
 * @since 0.1.0
 */
export const LocationProviderValue = {
  DISTANCE_FILTER: 0,
  ACTIVITY_PROVIDER: 1,
  RAW_PROVIDER: 2,
} as const;

/**
 * Desired accuracy. Strings are preferred; the corresponding meters value is
 * available via {@link AccuracyValue}.
 *
 * @since 0.1.0
 */
export type Accuracy = 'HIGH' | 'MEDIUM' | 'LOW' | 'PASSIVE';

/**
 * Meters mapping of {@link Accuracy}.
 *
 * @since 0.1.0
 */
export const AccuracyValue = {
  HIGH: 0,
  MEDIUM: 10,
  LOW: 100,
  PASSIVE: 1000,
} as const;

/**
 * Hex string (e.g. `'#4CAF50'`) used for the Android notification accent color.
 *
 * @since 0.1.0
 */
export type NotificationIconColor = string;

/**
 * Service authorization state, mirroring the Cordova plugin constants.
 *
 * @since 0.1.0
 */
export enum AuthorizationStatus {
  NOT_AUTHORIZED = 0,
  AUTHORIZED = 1,
  AUTHORIZED_FOREGROUND = 2,
}

/**
 * Log levels accepted by {@link BackgroundGeolocationPlugin.getLogEntries}.
 *
 * @since 0.1.0
 */
export type LogLevel = 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'FATAL';

/**
 * Native location source reported by the OS.
 *
 * @since 0.1.0
 */
export type NativeProvider = 'gps' | 'network' | 'passive' | 'fused';

/**
 * iOS `CLActivityType` mapping.
 *
 * @since 0.1.0
 */
export type IOSActivityType =
  | 'AutomotiveNavigation'
  | 'OtherNavigation'
  | 'Fitness'
  | 'Other';

/**
 * Service mode used by {@link BackgroundGeolocationPlugin.switchMode}.
 *
 * @since 0.1.0
 */
export type ServiceMode = 0 | 1;

// ---------------------------------------------------------------------------
// LocationOptions (a.k.a. ConfigureOptions in the Cordova plugin)
// ---------------------------------------------------------------------------

/**
 * Plugin configuration options. Fields mirror the Cordova plugin's
 * `ConfigureOptions` 1:1. Every field is optional; the native side uses safe
 * defaults documented inline.
 *
 * @since 0.1.0
 */
export interface LocationOptions {
  /** Location provider strategy. @default 'DISTANCE_FILTER' */
  locationProvider?: LocationProvider | 0 | 1 | 2;
  /** Desired accuracy in meters. @default 'MEDIUM' */
  desiredAccuracy?: Accuracy | number;
  /** Stationary radius in meters. @default 50 */
  stationaryRadius?: number;
  /** Minimum horizontal distance (meters) between location updates. @default 500 */
  distanceFilter?: number;
  /** Emit debugging sounds for life-cycle events. @default false */
  debug?: boolean;
  /** Minimum time interval between location updates (ms). Android. @default 600000 */
  interval?: number;
  /** Fastest rate (ms) at which updates may be delivered. Android. @default 120000 */
  fastestInterval?: number;
  /** Activity recognition cadence (ms). Android ACTIVITY provider. @default 10000 */
  activitiesInterval?: number;
  /** Stop tracking when the app is terminated. @default true */
  stopOnTerminate?: boolean;
  /** Start tracking on device boot. Android. @default false */
  startOnBoot?: boolean;
  /** @deprecated Stop on STILL activity. */
  stopOnStillActivity?: boolean;
  /** Restart the provider if no update is received for ~60s. Android. @default false */
  enableWatchdog?: boolean;
  /** Watchdog timeout in ms when {@link enableWatchdog} is `true`. */
  watchdogTimeout?: number;
  /** Show local notifications during tracking/sync. Android. @default true */
  notificationsEnabled?: boolean;
  /** Run the sync service in foreground (Android requires a notification). @default false */
  startForeground?: boolean;
  /** Foreground notification title. @default 'Background tracking' */
  notificationTitle?: string;
  /** Foreground notification body. @default 'ENABLED' */
  notificationText?: string;
  /** Show elapsed time in the notification. Android. @default false */
  showTime?: boolean;
  /** Show accumulated distance in the notification. Android. @default false */
  showDistance?: boolean;
  /** Sync notification title. Android. @default 'Syncing locations' */
  notificationSyncTitle?: string;
  /** Sync notification body. @default 'Sync in progress' */
  notificationSyncText?: string;
  /** Sync notification body on success. @default 'Sync completed' */
  notificationSyncCompletedText?: string;
  /** Sync notification body on failure. @default 'Sync failed' */
  notificationSyncFailedText?: string;
  /** Notification accent color (`#RRGGBB`). Android. */
  notificationIconColor?: NotificationIconColor;
  /** Custom large notification icon (drawable name). Android. */
  notificationIconLarge?: string;
  /** Custom small notification icon (drawable name). Android. */
  notificationIconSmall?: string;
  /** Notification channel name. Android 8+. */
  notificationChannelName?: string;
  /** iOS activity type hint. @default 'OtherNavigation' */
  activityType?: IOSActivityType;
  /** Allow iOS to pause location updates. @default false */
  pauseLocationUpdates?: boolean;
  /** iOS: switch to significant changes in background. @default false */
  saveBatteryOnBackground?: boolean;
  /** Endpoint where each location is POSTed. */
  url?: string;
  /** Endpoint used by the sync queue for failed locations. */
  syncUrl?: string;
  /** Sync batch size. @default 100 */
  syncThreshold?: number;
  /** Whether automatic sync to {@link syncUrl} is enabled. @default true */
  sync?: boolean;
  /** HTTP headers added to every request. */
  httpHeaders?: { [key: string]: string };
  /** Alias of {@link httpHeaders} (v3.3 backend-agnostic transport). */
  headers?: { [key: string]: string };
  /** HTTP method for `url`. @default 'POST' */
  httpMethod?: 'POST' | 'GET' | 'PUT' | 'PATCH';
  /** HTTP method for `syncUrl`. @default 'POST' */
  syncHttpMethod?: 'POST' | 'GET' | 'PUT' | 'PATCH';
  /** Delivery mode for real-time locations. @default 'batch' */
  httpMode?: 'batch' | 'single';
  /** Delivery mode for sync-queue locations. @default 'batch' */
  syncMode?: 'batch' | 'single';
  /** Placeholder values used by URL/body templating. */
  queryParams?: { [key: string]: string | number };
  /** Maximum locations stored in the local DB. @default 10000 */
  maxLocations?: number;
  /** Body template applied to each location. */
  postTemplate?: unknown;
  /** Alias of {@link postTemplate}. */
  bodyTemplate?: unknown;
  /** iOS 11+: show the blue background-location indicator. @default false */
  showsBackgroundLocationIndicator?: boolean;
  /** Heartbeat tick interval (ms). 0 disables. @default 0 */
  heartbeatInterval?: number;
  /** Policy for samples flagged as mock locations. @default 'allow' */
  mockLocationPolicy?: 'allow' | 'flag' | 'drop';
  /** Compatibility alias for {@link mockLocationPolicy}. */
  mockPolicy?: 'allow' | 'flag' | 'drop';
  /** Stamp battery level / charging state on every fix. @default true */
  includeBattery?: boolean;
  /** Android WakeLock policy. @default 'posting' */
  wakeLockMode?: 'none' | 'posting' | 'always';
  /** Stationary detection: no-movement time (ms). Android. @default 300000 */
  stationaryTimeout?: number;
  /** Stationary detection: lazy poll (ms). Android. @default 180000 */
  stationaryPollInterval?: number;
  /** Stationary detection: aggressive poll (ms). Android. @default 60000 */
  stationaryPollFast?: number;
  /** Activity confidence threshold (0–100). @default 50 */
  activityConfidenceThreshold?: number;
  /** Drop fixes whose reported accuracy is worse than this (meters). */
  maxAcceptedAccuracy?: number;
  /** Hint that the host registered a headless task (Android only). */
  headlessMode?: boolean;
  /** Convenience flag mirroring `activityConfidenceThreshold` usage. */
  useActivityDetection?: boolean;
  /** Driver-insights state machine configuration. */
  drivingEvents?: {
    enabled?: boolean;
    speedLimit?: number;
    minMovingSpeed?: number;
    stoppedDuration?: number;
    minTripSpeed?: number;
    minTripDuration?: number;
    hardBrakeMps2?: number;
    rapidAccelMps2?: number;
    sharpTurnDegPerSec?: number;
    crashImpactKmh?: number;
    crashWindowMs?: number;
    sensorFusion?: boolean;
    crashImpactG?: number;
    sensorCrashCooldownMs?: number;
    phoneUsageWindowMs?: number;
    phoneUsageCooldownMs?: number;
  };
  /** Forward-compatible escape hatch for new native options. */
  [extra: string]: unknown;
}

/** Options for {@link BackgroundGeolocationPlugin.getCurrentLocation}. @since 0.1.0 */
export interface CurrentLocationOptions {
  /** Max time the device will wait for a fix (ms). */
  timeout?: number;
  /** Max age of a cached fix that is acceptable (ms). */
  maximumAge?: number;
  /** Request the most accurate position available. */
  enableHighAccuracy?: boolean;
}

// ---------------------------------------------------------------------------
// Location / payload types
// ---------------------------------------------------------------------------

/**
 * A single location fix delivered by the native provider.
 *
 * @since 0.1.0
 */
export interface Location {
  /** DB id (`null`/`undefined` for synthetic fixes). */
  id: number;
  /** Native provider. */
  provider: NativeProvider;
  /** Configured location provider id. */
  locationProvider: number;
  /** Service provider (`'gps' | 'fused'` etc.) when known. */
  serviceProvider?: string;
  /** UTC timestamp in ms. */
  time: number;
  /** Latitude in degrees. */
  latitude: number;
  /** Longitude in degrees. */
  longitude: number;
  /** Horizontal accuracy in meters. */
  accuracy: number;
  /** Ground speed in m/s. */
  speed: number;
  /** Altitude in meters. */
  altitude: number;
  /** Bearing in degrees. */
  bearing: number;
  /** Android: fix came from a mock provider. */
  isFromMockProvider?: boolean;
  /** Android: developer-options "mock locations" is enabled. */
  mockLocationsEnabled?: boolean;
  /** iOS 15+: simulator-generated fix. */
  simulated?: boolean;
  /** Stationary radius (only on stationary events). */
  radius?: number;
  /** Battery percentage (0–100) when {@link LocationOptions.includeBattery} is on. */
  battery?: number;
  /** Charging state when {@link LocationOptions.includeBattery} is on. */
  isCharging?: boolean;
  /** Driving events anchored to this fix. */
  events?: Array<{ type: string; time: number; [key: string]: unknown }>;
}

/** Persisted log entry returned by {@link BackgroundGeolocationPlugin.getLogEntries}. @since 0.1.0 */
export interface LogEntry {
  /** DB id. */
  id: number;
  /** Free-form context tag. */
  context?: string;
  /** Severity. */
  level: LogLevel;
  /** Message body. */
  message: string;
  /** UTC timestamp (ms). */
  timestamp: number;
  /** Logger name. */
  logger?: string;
  /** Stack trace (Android only — iOS folds it into `message`). */
  stackTrace?: string;
}

/** Service status returned by {@link BackgroundGeolocationPlugin.checkStatus}. @since 0.1.0 */
export interface Status {
  /** Service is currently running. */
  isRunning: boolean;
  /** OS-level location services are enabled. */
  locationServicesEnabled: boolean;
  /** Current authorization state. */
  authorization: AuthorizationStatus;
}

/** iOS background task handle returned by {@link BackgroundGeolocationPlugin.startTask}. @since 0.1.0 */
export interface Task {
  /** Native task identifier — pass back to `endTask`. */
  taskKey: number;
}

// ---------------------------------------------------------------------------
// Event payload types
// ---------------------------------------------------------------------------

/** Location event payload. @since 0.1.0 */
export type LocationEvent = Location;

/** Stationary event payload (location with radius). @since 0.1.0 */
export type StationaryEvent = Location;

/** Activity recognition event. @since 0.1.0 */
export interface ActivityEvent {
  /** Recognised activity type (`IN_VEHICLE`, `ON_FOOT`, etc.). */
  type: string;
  /** Confidence percentage (0–100). */
  confidence: number;
}

/** Error event payload. @since 0.1.0 */
export interface ErrorEvent {
  /** Numeric error code (1 PERMISSION_DENIED, 2 LOCATION_UNAVAILABLE, 3 TIMEOUT, …). */
  code: number;
  /** Human-readable description. */
  message: string;
}

/** Authorization state change. @since 0.1.0 */
export interface AuthorizationEvent {
  /** New authorization state. */
  status: AuthorizationStatus;
}

/** HTTP 401 received from sync endpoint. @since 0.1.0 */
export type HttpAuthorizationEvent = Record<string, never>;

// ---------------------------------------------------------------------------
// Main plugin interface
// ---------------------------------------------------------------------------

/**
 * Public contract of the `BackgroundGeolocation` plugin.
 *
 * @since 0.1.0
 */
export interface BackgroundGeolocationPlugin {
  /**
   * Configure the native plugin. Must be called at least once before `start`.
   *
   * @since 0.1.0
   */
  configure(options: LocationOptions): Promise<void>;

  /**
   * Start the native background location service.
   *
   * @since 0.1.0
   */
  start(): Promise<void>;

  /**
   * Stop the native background location service.
   *
   * @since 0.1.0
   */
  stop(): Promise<void>;

  /**
   * One-shot location request. Resolves with the latest acceptable fix.
   *
   * @since 0.1.0
   */
  getCurrentLocation(options?: CurrentLocationOptions): Promise<Location>;

  /**
   * Returns the last stationary location if any, otherwise `null`.
   *
   * @since 0.1.0
   */
  getStationaryLocation(): Promise<Location | null>;

  /**
   * Locations stored locally that have not yet been delivered.
   *
   * @since 0.1.0
   */
  getValidLocations(): Promise<{ locations: Location[] }>;

  /**
   * Retrieve the persisted configuration.
   *
   * @since 0.1.0
   */
  getConfig(): Promise<LocationOptions>;

  /**
   * Delete a single stored location by DB id.
   *
   * @since 0.1.0
   */
  deleteLocation(options: { locationId: number }): Promise<void>;

  /**
   * Delete every stored location.
   *
   * @since 0.1.0
   */
  deleteAllLocations(): Promise<void>;

  /**
   * Whether OS-level location services are enabled.
   *
   * @since 0.1.0
   */
  isLocationEnabled(): Promise<{ enabled: boolean }>;

  /**
   * Open this app's settings screen so the user can change permissions.
   *
   * @since 0.1.0
   */
  showAppSettings(): Promise<void>;

  /**
   * Open the system Location settings screen.
   *
   * @since 0.1.0
   */
  showLocationSettings(): Promise<void>;

  /**
   * Subscribe to provider-state changes (location services toggled, etc.).
   *
   * @since 0.1.0
   */
  watchLocationMode(): Promise<void>;

  /**
   * Stop watching provider-state changes.
   *
   * @since 0.1.0
   */
  stopWatchingLocationMode(): Promise<void>;

  /**
   * Return logged plugin events (useful for diagnostics).
   *
   * @since 0.1.0
   */
  getLogEntries(options: {
    /** Maximum entries to return. */
    limit: number;
    /** Only entries with `id > fromId`. */
    fromId?: number;
    /** Minimum severity to include. */
    minLevel?: LogLevel;
  }): Promise<{ entries: LogEntry[] }>;

  /**
   * Service status snapshot.
   *
   * @since 0.1.0
   */
  checkStatus(): Promise<Status>;

  /**
   * iOS: begin a background task. Pair every call with {@link endTask}.
   *
   * @since 0.1.0
   */
  startTask(): Promise<Task>;

  /**
   * iOS: end a background task started by {@link startTask}.
   *
   * @since 0.1.0
   */
  endTask(options: { taskKey: number }): Promise<void>;

  /**
   * Force an immediate sync of pending locations to `syncUrl`.
   *
   * @since 0.1.0
   */
  forceSync(): Promise<void>;

  /**
   * Capacitor permission check. Resolves to the current location permission.
   *
   * @since 0.1.0
   */
  checkPermissions(): Promise<{ location: PermissionState }>;

  /**
   * Capacitor permission request. Prompts the user if needed.
   *
   * @since 0.1.0
   */
  requestPermissions(): Promise<{ location: PermissionState }>;

  /**
   * Remove every registered listener attached to this plugin instance.
   *
   * @since 0.1.0
   */
  removeAllListeners(): Promise<void>;

  // ---------------- Event listeners ----------------

  /**
   * New location fix.
   *
   * @since 0.1.0
   */
  addListener(
    eventName: 'location',
    listener: (event: LocationEvent) => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Stationary state entered (Android distance-filter provider).
   *
   * @since 0.1.0
   */
  addListener(
    eventName: 'stationary',
    listener: (event: StationaryEvent) => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Activity recognition update.
   *
   * @since 0.1.0
   */
  addListener(
    eventName: 'activity',
    listener: (event: ActivityEvent) => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Recoverable or fatal native error.
   *
   * @since 0.1.0
   */
  addListener(
    eventName: 'error',
    listener: (event: ErrorEvent) => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * User changed authorization or toggled location services.
   *
   * @since 0.1.0
   */
  addListener(
    eventName: 'authorization',
    listener: (event: AuthorizationEvent) => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Service started successfully.
   *
   * @since 0.1.0
   */
  addListener(
    eventName: 'start',
    listener: () => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Service stopped successfully.
   *
   * @since 0.1.0
   */
  addListener(
    eventName: 'stop',
    listener: () => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * App entered the foreground.
   *
   * @since 0.1.0
   */
  addListener(
    eventName: 'foreground',
    listener: () => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * App entered the background.
   *
   * @since 0.1.0
   */
  addListener(
    eventName: 'background',
    listener: () => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Server responded with `285 Updates Not Required`.
   *
   * @since 0.1.0
   */
  addListener(
    eventName: 'abort_requested',
    listener: () => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  /**
   * Server responded with `401 Unauthorized`.
   *
   * @since 0.1.0
   */
  addListener(
    eventName: 'http_authorization',
    listener: () => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
}
