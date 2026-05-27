// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// Capacitor plugin definitions for @josuelmm/capacitor-background-geolocation.
// Mirrors @josuelmm/cordova-background-geolocation's www/BackgroundGeolocation.d.ts
// 1:1 for v1.0.0 parity. Cordova success/fail callbacks are collapsed into
// Promises; `on(eventName, cb)` becomes Capacitor's `addListener` overloads.

import type { PermissionState, PluginListenerHandle } from '@capacitor/core';

// ---------------------------------------------------------------------------
// Enums / unions
// ---------------------------------------------------------------------------

/**
 * Location provider strategy. Strings are preferred in the Capacitor API.
 * Numeric ids `0 | 1 | 2` are accepted for back-compat with the Cordova plugin.
 *
 * @since 1.0.0
 */
export type LocationProvider = 'DISTANCE_FILTER' | 'ACTIVITY_PROVIDER' | 'RAW_PROVIDER' | 0 | 1 | 2;

/**
 * Numeric mapping of {@link LocationProvider} matching the Cordova plugin.
 *
 * @since 1.0.0
 */
export const LocationProviderValue = {
  DISTANCE_FILTER: 0,
  ACTIVITY_PROVIDER: 1,
  RAW_PROVIDER: 2,
} as const;

/**
 * Desired accuracy. Strings or the corresponding meters value (`0 | 100 | 1000 | 10000`).
 *
 * @since 1.0.0
 */
export type Accuracy = 'HIGH' | 'MEDIUM' | 'LOW' | 'PASSIVE' | number;

/**
 * Meters mapping of {@link Accuracy} (matches the Cordova plugin's `AccuracyLevel`).
 *
 * @since 1.0.0
 */
export const AccuracyValue = {
  HIGH: 0,
  MEDIUM: 100,
  LOW: 1000,
  PASSIVE: 10000,
} as const;

/**
 * Hex string (e.g. `'#4CAF50'`) used for the Android notification accent color.
 *
 * @since 1.0.0
 */
export type NotificationIconColor = string;

/**
 * Service authorization state, mirroring the Cordova plugin constants.
 *
 * @since 1.0.0
 */
export enum AuthorizationStatus {
  NOT_AUTHORIZED = 0,
  AUTHORIZED = 1,
  AUTHORIZED_FOREGROUND = 2,
}

/**
 * Log levels accepted by {@link BackgroundGeolocationPlugin.getLogEntries}.
 *
 * @since 1.0.0
 */
export type LogLevel = 'TRACE' | 'DEBUG' | 'INFO' | 'WARN' | 'ERROR';

/**
 * Native location source reported by the OS.
 *
 * @since 1.0.0
 */
export type NativeProvider = 'gps' | 'network' | 'passive' | 'fused';

/**
 * iOS `CLActivityType` mapping.
 *
 * @since 1.0.0
 */
export type IOSActivityType = 'AutomotiveNavigation' | 'OtherNavigation' | 'Fitness' | 'Other';

/**
 * Service mode used by {@link BackgroundGeolocationPlugin.switchMode}.
 * `0 = BACKGROUND`, `1 = FOREGROUND`.
 *
 * @since 1.0.0
 */
export type ServiceMode = 0 | 1;

/**
 * Activity type reported by the activity-recognition provider.
 *
 * @since 1.0.0
 */
export type ActivityType =
  | 'IN_VEHICLE'
  | 'ON_BICYCLE'
  | 'ON_FOOT'
  | 'RUNNING'
  | 'STILL'
  | 'TILTING'
  | 'UNKNOWN'
  | 'WALKING';

/**
 * Numeric error code emitted with `LocationError`.
 *  - `1` PERMISSION_DENIED
 *  - `2` LOCATION_UNAVAILABLE
 *  - `3` TIMEOUT
 *
 * @since 1.0.0
 */
export type LocationErrorCode = 1 | 2 | 3;

/**
 * Headless task event name. Reserved for a future `BackgroundFetch`-style hook.
 *
 * @since 1.0.0
 */
export type HeadlessTaskEventName = 'location' | 'stationary' | 'activity';

// ---------------------------------------------------------------------------
// ConfigureOptions (a.k.a. LocationOptions in <1.0)
// ---------------------------------------------------------------------------

/**
 * Plugin configuration options. Fields mirror the Cordova plugin's
 * `ConfigureOptions` 1:1. Every field is optional; the native side uses safe
 * defaults documented inline.
 *
 * @since 1.0.0
 */
export interface ConfigureOptions {
  /** Location provider strategy. @default 'DISTANCE_FILTER' */
  locationProvider?: LocationProvider;
  /** Desired accuracy in meters. @default 'MEDIUM' */
  desiredAccuracy?: Accuracy;
  /** Stationary radius in meters. @default 50 */
  stationaryRadius?: number;
  /** Emit debugging sounds for life-cycle events. @default false */
  debug?: boolean;
  /** Minimum horizontal distance (meters) between location updates. @default 500 */
  distanceFilter?: number;
  /** Stop tracking when the app is terminated. @default true */
  stopOnTerminate?: boolean;
  /** Start tracking on device boot. Android. @default false */
  startOnBoot?: boolean;
  /** Minimum time interval between location updates (ms). Android. @default 600000 */
  interval?: number;
  /** Fastest rate (ms) at which updates may be delivered. Android. @default 120000 */
  fastestInterval?: number;
  /** Activity recognition cadence (ms). Android ACTIVITY provider. @default 10000 */
  activitiesInterval?: number;
  /** @deprecated Stop on STILL activity. */
  stopOnStillActivity?: boolean;
  /** Restart the provider if no update is received for the watchdog window. Android. @default false */
  enableWatchdog?: boolean;
  /** Watchdog check interval (ms). @default 60000 */
  watchdogIntervalMs?: number;
  /**
   * When `true` (default) the service uses `START_STICKY` and is restarted by the OS
   * after a kill. Set to `false` to opt out — useful for battery-conscious apps that
   * prefer not to auto-restart after the OS reclaims memory.
   * Android only. @default true
   */
  restartOnKill?: boolean;
  /**
   * Periodic interval (ms) for the WorkManager headless sync job registered by
   * `registerHeadlessTask()`. WorkManager enforces a minimum of 15 minutes.
   * Android only. @default 900000 (15 min)
   * @since 1.2.0
   */
  headlessTaskTimeoutMs?: number;
  /**
   * iOS-only background survival strategy activated when the app enters the background
   * and `saveBatteryOnBackground` is `true`.
   *
   * - `'significantChanges'` (default): calls
   *   `startMonitoringSignificantLocationChanges()` — survives app kill, low battery
   *   drain, ~500 m accuracy.
   * - `'regionMonitoring'`: sets a geofence around the last known position so iOS
   *   wakes the app when the user moves out of the region.
   * - `'none'`: no fallback — rely on push-to-restart or `startOnBoot` equivalents.
   *
   * Emits `iosFallbackActivated` when the fallback mode activates.
   * iOS only. @since 1.2.0
   */
  iosBackgroundFallback?: 'significantChanges' | 'regionMonitoring' | 'none';
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
  /** Driver-insights state machine configuration. */
  drivingEvents?: {
    /** Master switch. When `false` (default) no driver-insight events are emitted. */
    enabled?: boolean;
    /** Speed limit (km/h) for the `speeding` event. `0` disables. */
    speedLimit?: number;
    /** m/s threshold below which the user is considered stopped. */
    minMovingSpeed?: number;
    /** ms of continuous below-threshold speed needed to confirm `stopped`. */
    stoppedDuration?: number;
    /** m/s threshold to start counting a trip. */
    minTripSpeed?: number;
    /** ms of continuous above-threshold speed needed to confirm `tripStart`. */
    minTripDuration?: number;
    /** Deceleration threshold (m/s²) for `hardBrake`. */
    hardBrakeMps2?: number;
    /** Acceleration threshold (m/s²) for `rapidAcceleration`. */
    rapidAccelMps2?: number;
    /** Bearing change rate (deg/s) for `sharpTurn`. */
    sharpTurnDegPerSec?: number;
    /** Velocity drop (km/h) within `crashWindowMs` to trigger `possibleCrash`. */
    crashImpactKmh?: number;
    /** Window (ms) used to evaluate the crash impact. */
    crashWindowMs?: number;
    /** Enable accelerometer/gyroscope sensor fusion. @default false */
    sensorFusion?: boolean;
    /** Crash impact threshold in g for the sensor pipeline. */
    crashImpactG?: number;
    /** Cooldown (ms) between sensor-driven crash detections. */
    sensorCrashCooldownMs?: number;
    /** Sustained jitter window (ms) for `phoneUsageWhileDriving`. */
    phoneUsageWindowMs?: number;
    /** Cooldown (ms) between `phoneUsageWhileDriving` events. */
    phoneUsageCooldownMs?: number;
    /**
     * Duration (ms) the vehicle must be stationary during an active trip before
     * `idleStart` fires. @default 300000 (5 min) @since 1.4.0
     */
    idleThresholdMs?: number;
    /**
     * Duration (ms) the vehicle must be moving continuously after an idle before
     * `idleEnd` fires. @default 30000 @since 1.4.0
     */
    idleEndThresholdMs?: number;
    /**
     * Per-category weights for the driver behavior score.
     * Values must sum to exactly 100. @since 1.4.0
     */
    scoring?: {
      speedingWeight?: number;
      hardBrakingWeight?: number;
      rapidAccelWeight?: number;
      sharpTurnWeight?: number;
      phoneUsageWeight?: number;
    };
  };
  /** Forward-compatible escape hatch for new native options. */
  [extra: string]: unknown;
}

/**
 * Backwards-compatible alias for {@link ConfigureOptions} (v0.x name).
 *
 * @since 1.0.0
 */
export type LocationOptions = ConfigureOptions;

/** Options for {@link BackgroundGeolocationPlugin.getCurrentLocation}. @since 1.0.0 */
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
 * @since 1.0.0
 */
export interface Location {
  /** DB id (`null`/`undefined` for synthetic fixes). */
  id: number;
  /** Native provider. */
  provider: NativeProvider;
  /** Configured location provider id. */
  locationProvider: number;
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
  /** Driving events anchored to this fix. */
  events?: { type: string; time: number; [key: string]: unknown }[];
  /** Battery percentage (0–100) when {@link ConfigureOptions.includeBattery} is on. */
  battery?: number;
  /** Charging state when {@link ConfigureOptions.includeBattery} is on. */
  isCharging?: boolean;
}

/** Stationary location adds a `radius` (meters) to {@link Location}. @since 1.0.0 */
export interface StationaryLocation extends Location {
  /** Stationary radius in meters. */
  radius: number;
}

/** Geolocation error shape. @since 1.0.0 */
export interface LocationError {
  /** Reason code. */
  code: LocationErrorCode;
  /** Human-readable description. */
  message: string;
}

/** Generic native plugin error. @since 1.0.0 */
export interface BackgroundGeolocationError {
  /** Numeric error code. */
  code: number;
  /** Human-readable description. */
  message: string;
}

/** Activity recognition payload. @since 1.0.0 */
export interface Activity {
  /** Recognised activity type. */
  type: ActivityType;
  /** Confidence percentage (0–100). */
  confidence: number;
}

/** Service status returned by {@link BackgroundGeolocationPlugin.checkStatus}. @since 1.0.0 */
export interface ServiceStatus {
  /** Service is currently running. */
  isRunning: boolean;
  /** OS-level location services are enabled. */
  locationServicesEnabled: boolean;
  /** Current authorization state. */
  authorization: AuthorizationStatus;
}

/** Backwards-compatible alias for {@link ServiceStatus}. @since 1.0.0 */
export type Status = ServiceStatus;

/**
 * Extended diagnostics returned by {@link BackgroundGeolocationPlugin.getDiagnostics}.
 *
 * @since 1.0.0
 */
export interface Diagnostics {
  // ---- common ----
  /** TRUE if the native service is currently running. */
  isRunning: boolean;
  /** TRUE if the OS-level location services are enabled. */
  locationServicesEnabled: boolean;
  /** Configured `startOnBoot` flag. */
  startOnBoot?: boolean;
  /** Number of locations queued for sync. */
  pendingSyncCount?: number;
  /** UTC ms of the last received location, or `null` if none yet. */
  lastLocationAt?: number | null;

  // ---- Android ----
  /** Android: TRUE if `ACCESS_FINE_LOCATION` is granted. */
  fineLocationGranted?: boolean;
  /** Android: TRUE if `ACCESS_COARSE_LOCATION` is granted. */
  coarseLocationGranted?: boolean;
  /** Android 10+: TRUE if `ACCESS_BACKGROUND_LOCATION` is granted. */
  backgroundLocationGranted?: boolean;
  /** Android 13+: TRUE if `POST_NOTIFICATIONS` is granted. */
  notificationPermissionGranted?: boolean;
  /** Android 10+: TRUE if `ACTIVITY_RECOGNITION` is granted. */
  activityRecognitionGranted?: boolean;
  /** Android: TRUE if the app is on the battery optimisation whitelist. */
  batteryOptimizationIgnored?: boolean;
  /** Android: device manufacturer (`Build.MANUFACTURER`). */
  manufacturer?: string;
  /** Android: declared `foregroundServiceType` (numeric). */
  foregroundServiceType?: number;

  // ---- iOS ----
  /** iOS 14+: TRUE if the user granted Precise Location. */
  preciseLocationEnabled?: boolean;
  /** iOS: status of system-wide Background App Refresh. */
  backgroundRefreshStatus?: 'available' | 'denied' | 'restricted';
  /** iOS: TRUE if Low Power Mode is currently enabled. */
  lowPowerModeEnabled?: boolean;
  /** iOS: status of the Motion & Fitness permission. */
  motionPermissionStatus?: 'authorized' | 'denied' | 'restricted' | 'notDetermined';
  /** iOS: human-readable label of the current `CLAuthorizationStatus`. */
  authorizationStatusText?: string;
}

/** Persisted log entry returned by {@link BackgroundGeolocationPlugin.getLogEntries}. @since 1.0.0 */
export interface LogEntry {
  /** DB id. */
  id: number;
  /** UTC timestamp (ms). */
  timestamp: number;
  /** Severity. */
  level: LogLevel;
  /** Message body. */
  message: string;
  /** Stack trace (Android only — iOS folds it into `message`). */
  stackTrace: string;
}

/**
 * Headless task event payload (reserved for a future v1.1 hook).
 *
 * @since 1.0.0
 */
export interface HeadlessTaskEvent {
  /** Event name. */
  name: HeadlessTaskEventName;
  /** Event parameters. */
  params: unknown;
}

/**
 * A user-defined geofence region for entry/exit/dwell detection.
 *
 * - **Android**: backed by Google Play Services `GeofencingClient`. Up to 100 simultaneous geofences.
 * - **iOS**: backed by `CLLocationManager.startMonitoring(for:)`. Up to ~19 user-defined geofences
 *   (one slot is used by the internal stationary region). Dwell detection uses a per-region Timer,
 *   which requires the app to be foreground or background-active.
 *
 * @since 1.3.0
 */
export interface Geofence {
  /** Unique identifier for this geofence. */
  id: string;
  /** Center latitude. */
  latitude: number;
  /** Center longitude. */
  longitude: number;
  /** Radius in meters. @default 200 */
  radius?: number;
  /** Emit `geofenceEnter` when the device enters the region. @default true */
  notifyOnEntry?: boolean;
  /** Emit `geofenceExit` when the device exits the region. @default false */
  notifyOnExit?: boolean;
  /**
   * Emit `geofenceDwell` after the device has been inside the region for
   * `loiteringDelay` milliseconds without exiting.
   * @default false
   */
  notifyOnDwell?: boolean;
  /**
   * Dwell threshold in milliseconds. Android uses the native GMS loitering
   * delay; iOS uses a Timer (app must be active).
   * @default 30000
   */
  loiteringDelay?: number;
}

/**
 * Payload emitted by `geofenceEnter`, `geofenceExit`, and `geofenceDwell`. @since 1.3.0
 */
export interface GeofenceEvent {
  /** Identifier of the geofence that triggered the event. */
  id: string;
  /** Transition type. */
  action: 'ENTER' | 'EXIT' | 'DWELL';
  /** Location fix at the moment of the transition (may be absent). */
  location?: Location;
}

/**
 * Per-category score breakdown (0–100 each). @since 1.4.0
 */
export interface TripScoreBreakdown {
  speeding: number;
  hardBraking: number;
  rapidAcceleration: number;
  sharpTurns: number;
  phoneUsage: number;
}

/**
 * A single scored event recorded during a trip. @since 1.4.0
 */
export interface TripScoreEvent {
  /** Category key (e.g. `'speeding'`, `'hardBrake'`). */
  type: string;
  /** UTC timestamp (ms) when the event occurred. */
  timestamp: number;
  /** Points deducted for this event. */
  penalty: number;
  /** Location at the time of the event. */
  location: { latitude: number; longitude: number };
}

/**
 * Driver behaviour score for a completed trip. @since 1.4.0
 */
export interface TripScore {
  /** Overall score (0–100). */
  overall: number;
  /** Per-category breakdown. */
  breakdown: TripScoreBreakdown;
  /** Individual scored events. */
  events: TripScoreEvent[];
  /** Unique trip identifier. */
  tripId: string;
  /** UTC ms when the trip started. */
  startedAt: number;
  /** UTC ms when the trip ended. */
  endedAt: number;
  /** Total trip distance in kilometers. */
  distanceKm: number;
  /** Total idle time in milliseconds. */
  totalIdleMs: number;
  /** Number of idle episodes during the trip. */
  idleCount: number;
}

/**
 * Payload of the `idleStart` event. @since 1.4.0
 */
export interface IdleStartEvent {
  /** Location at which idling began. */
  location: Location;
  /** UTC ms when idling started. */
  startedAt: number;
}

/**
 * Payload of the `idleEnd` event. @since 1.4.0
 */
export interface IdleEndEvent {
  /** Location at which the vehicle resumed moving. */
  location: Location;
  /** Total idle duration in milliseconds. */
  durationMs: number;
  /** UTC ms when idling started. */
  startedAt: number;
}

/**
 * Payload of the `serviceRestarted` event. @since 1.1.0
 */
export interface ServiceRestartedEvent {
  /**
   * Why the service was restarted.
   * - `'watchdog'`    — no GPS fix received within `watchdogIntervalMs`; provider restarted.
   * - `'system_kill'` — OS killed the service (OOM or battery saver); restarted via `START_STICKY`.
   * - `'boot'`        — device rebooted and `startOnBoot` is `true`.
   * - `'app_removed'` — user removed the task from recents; service survived (`stopOnTerminate: false`).
   */
  reason: 'watchdog' | 'system_kill' | 'boot' | 'app_removed';
}

/**
 * Payload of the `iosFallbackActivated` event. @since 1.2.0
 */
export interface IosFallbackActivatedEvent {
  /** Which fallback strategy was activated. */
  strategy: 'significantchanges' | 'regionmonitoring';
}

/** iOS background task handle returned by {@link BackgroundGeolocationPlugin.startTask}. @since 1.0.0 */
export interface Task {
  /** Native task identifier — pass back to `endTask`. */
  taskKey: number;
}

// ---------------------------------------------------------------------------
// Permission-request result
// ---------------------------------------------------------------------------

/** Result of an Android runtime permission request. @since 1.0.0 */
export interface PermissionRequestResult {
  /** TRUE if all requested permissions were granted. */
  granted: boolean;
  /** Names of any permissions that were denied. */
  denied?: string[];
  /** TRUE when the OS version made the request a no-op (e.g. iOS, old Android). */
  notRequired?: boolean;
}

// ---------------------------------------------------------------------------
// Main plugin interface
// ---------------------------------------------------------------------------

/**
 * Public contract of the `BackgroundGeolocation` plugin.
 *
 * @since 1.0.0
 */
export interface BackgroundGeolocationPlugin {
  // ---------------- Tracking control ----------------

  /**
   * Configure the native plugin. Must be called at least once before `start`.
   *
   * @since 1.0.0
   */
  configure(options: ConfigureOptions): Promise<void>;

  /**
   * Start the native background location service.
   *
   * @since 1.0.0
   */
  start(): Promise<void>;

  /**
   * Stop the native background location service.
   *
   * @since 1.0.0
   */
  stop(): Promise<void>;

  /**
   * Switch plugin operation mode (iOS).
   * `0` = BACKGROUND, `1` = FOREGROUND.
   *
   * @since 1.0.0
   */
  switchMode(options: { mode: ServiceMode }): Promise<void>;

  /**
   * Service status snapshot.
   *
   * @since 1.0.0
   */
  checkStatus(): Promise<ServiceStatus>;

  // ---------------- Locations ----------------

  /**
   * One-shot location request.
   *
   * @since 1.0.0
   */
  getCurrentLocation(options?: CurrentLocationOptions): Promise<Location>;

  /**
   * Returns the last stationary location if any, otherwise `null`.
   *
   * @since 1.0.0
   */
  getStationaryLocation(): Promise<StationaryLocation | null>;

  /**
   * Return all stored locations.
   *
   * @since 1.0.0
   */
  getLocations(): Promise<{ locations: Location[] }>;

  /**
   * Locations stored locally that have not yet been delivered.
   *
   * @since 1.0.0
   */
  getValidLocations(): Promise<{ locations: Location[] }>;

  /**
   * Like {@link getValidLocations} but also deletes the rows returned.
   *
   * @since 1.0.0
   */
  getValidLocationsAndDelete(): Promise<{ locations: Location[] }>;

  /**
   * Delete a single stored location by DB id.
   *
   * @since 1.0.0
   */
  deleteLocation(options: { locationId: number }): Promise<void>;

  /**
   * Delete every stored location.
   *
   * @since 1.0.0
   */
  deleteAllLocations(): Promise<void>;

  // ---------------- Sync queue ----------------

  /**
   * Force an immediate sync of pending locations to `syncUrl`.
   *
   * @since 1.0.0
   */
  forceSync(): Promise<void>;

  /**
   * Discard every pending sync-queue location.
   *
   * @since 1.0.0
   */
  clearSync(): Promise<void>;

  /**
   * Number of locations pending to be synced.
   *
   * @since 1.0.0
   */
  getPendingSyncCount(): Promise<{ count: number }>;

  // ---------------- Sessions ----------------

  /**
   * Begin a recording session (clears the session table and starts collecting).
   *
   * @since 1.0.0
   */
  startSession(): Promise<void>;

  /**
   * Return every location stored in the current session.
   *
   * @since 1.0.0
   */
  getSessionLocations(): Promise<{ locations: Location[] }>;

  /**
   * Clear the session table and stop collecting.
   *
   * @since 1.0.0
   */
  clearSession(): Promise<void>;

  /**
   * Count of locations in the current session.
   *
   * @since 1.0.0
   */
  getSessionLocationsCount(): Promise<{ count: number }>;

  // ---------------- Diagnostics & OEMs ----------------

  /**
   * Extended diagnostics (permissions, battery optimisation, OEM, iOS flags).
   *
   * @since 1.0.0
   */
  getDiagnostics(): Promise<Diagnostics>;

  /**
   * Returns the reason and timestamp of the last time the native service was killed
   * and restarted automatically. Useful for post-mortem debugging of tracking gaps.
   * Both fields are `null` if the service has never been killed since last install.
   *
   * Android only — iOS resolves `{ reason: null, timestamp: null }`.
   *
   * @since 1.1.0
   */
  getBackgroundKillReason(): Promise<{ reason: string | null; timestamp: number | null }>;

  /**
   * Android: TRUE if the app is on the battery optimisation whitelist.
   * iOS: resolves `{ whitelisted: true }`.
   *
   * @since 1.0.0
   */
  isIgnoringBatteryOptimizations(): Promise<{ whitelisted: boolean }>;

  /**
   * Android: prompt the user to add the app to the battery optimisation whitelist.
   * iOS: resolves `{ whitelisted: true }`.
   *
   * @since 1.0.0
   */
  requestIgnoreBatteryOptimizations(): Promise<{ whitelisted: boolean }>;

  /**
   * Android: open the battery-related settings screen.
   * iOS: no-op.
   *
   * @since 1.0.0
   */
  openBatterySettings(): Promise<void>;

  /**
   * Android: open the OEM-specific auto-start / background-activity screen.
   *
   * @since 1.0.0
   */
  openAutoStartSettings(): Promise<{
    opened: boolean;
    manufacturer: string;
    screen: string;
  }>;

  /**
   * Return OEM-specific guidance steps to surface in the UI.
   *
   * @since 1.0.0
   */
  getManufacturerHelp(): Promise<{ manufacturer: string; steps: string[] }>;

  /**
   * Get the plugin version from native code.
   *
   * @since 1.0.0
   */
  getPluginVersion(): Promise<{ version: string }>;

  // ---------------- Permissions ----------------

  /**
   * Capacitor-style permission check. Resolves to the current location permission.
   *
   * @since 1.0.0
   */
  checkPermissions(): Promise<{ location: PermissionState }>;

  /**
   * Capacitor-style permission request. Prompts the user if needed.
   *
   * @since 1.0.0
   */
  requestPermissions(): Promise<{ location: PermissionState }>;

  /**
   * Request `ACCESS_BACKGROUND_LOCATION` (Android 10+).
   *
   * @since 1.0.0
   */
  requestBackgroundLocationPermission(): Promise<PermissionRequestResult>;

  /**
   * Request `ACTIVITY_RECOGNITION` (Android 10+).
   *
   * @since 1.0.0
   */
  requestActivityRecognitionPermission(): Promise<PermissionRequestResult>;

  /**
   * Request `POST_NOTIFICATIONS` (Android 13+).
   *
   * @since 1.0.0
   */
  requestNotificationPermission(): Promise<PermissionRequestResult>;

  /**
   * Open this app's settings screen so the user can change permissions.
   *
   * @since 1.0.0
   */
  showAppSettings(): Promise<void>;

  /**
   * Convenience alias for {@link showAppSettings}.
   *
   * @since 1.0.0
   */
  openSettings(): Promise<void>;

  /**
   * Open the system Location settings screen (Android).
   *
   * @since 1.0.0
   */
  showLocationSettings(): Promise<void>;

  // ---------------- Tasks ----------------

  /**
   * iOS: begin a background task. Pair every call with {@link endTask}.
   *
   * @since 1.0.0
   */
  startTask(): Promise<{ taskKey: number }>;

  /**
   * iOS: end a background task started by {@link startTask}.
   *
   * @since 1.0.0
   */
  endTask(options: { taskKey: number }): Promise<void>;

  /**
   * Trigger an SOS event from JS. The plugin emits an `sos` event carrying
   * the latest known location plus the user-supplied payload.
   *
   * @since 1.0.0
   */
  triggerSOS(payload?: Record<string, unknown>): Promise<void>;

  // ---------------- Config & logs ----------------

  /**
   * Retrieve the persisted configuration.
   *
   * @since 1.0.0
   */
  getConfig(): Promise<ConfigureOptions>;

  /**
   * Return logged plugin events (useful for diagnostics).
   *
   * @since 1.0.0
   */
  getLogEntries(options: {
    /** Maximum entries to return. */
    limit: number;
    /** Only entries with `id > fromId`. */
    fromId?: number;
    /** Minimum severity to include. */
    minLevel?: LogLevel;
  }): Promise<{ entries: LogEntry[] }>;

  // ---------------- Headless task (Android) ----------------

  /**
   * **Android only.** Register a JS callback that runs on `location`,
   * `stationary`, and `activity` events even when the host activity has
   * been killed by the system — as long as `stopOnTerminate: false` and
   * the foreground service is still alive.
   *
   * The function body is serialised with `task.toString()` and evaluated
   * inside a hidden Android WebView via the upstream `JsEvaluator`
   * pipeline. Variables from the outer scope CANNOT be referenced — the
   * callback runs in an isolated context. Plain `XMLHttpRequest`,
   * `fetch`, and `JSON` are available.
   *
   * On iOS this call resolves immediately as a no-op (Apple does not
   * allow running JS in a killed-app scenario). On Web it throws
   * `unimplemented`. Prefer the regular `addListener` flow whenever
   * possible — `headlessTask` is only useful when the app has been
   * killed by the OS but the service must still react to GPS events.
   *
   * @since 1.0.0
   */
  headlessTask(task: (event: HeadlessTaskEvent) => unknown): Promise<void>;

  // ---------------- Geofencing ----------------

  /**
   * Register one or more geofences. Existing geofences with the same `id` are replaced.
   * Geofences are persisted and survive app restarts. Android GMS clears geofences on
   * device reboot or app update — they are automatically re-registered on next startup.
   *
   * @since 1.3.0
   */
  addGeofences(options: { geofences: Geofence[] }): Promise<void>;

  /**
   * Remove geofences by id. If `ids` is omitted, **all** geofences are removed.
   *
   * @since 1.3.0
   */
  removeGeofences(options?: { ids?: string[] }): Promise<void>;

  /**
   * Return the current list of registered geofences.
   *
   * @since 1.3.0
   */
  getGeofences(): Promise<{ geofences: Geofence[] }>;

  // ---------------- Driver intelligence ----------------

  /**
   * Return the {@link TripScore} for the most recently completed trip, or `null`
   * if no trip has ended since the service started.
   *
   * @since 1.4.0
   */
  getTripScore(): Promise<TripScore | null>;

  // ---------------- Lifecycle ----------------

  /**
   * Remove every registered listener attached to this plugin instance.
   *
   * @since 1.0.0
   */
  removeAllListeners(): Promise<void>;

  // ---------------- Event listeners ----------------

  /**
   * New location fix.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'location', listener: (event: Location) => void): Promise<PluginListenerHandle>;

  /**
   * Stationary state entered.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'stationary', listener: (event: StationaryLocation) => void): Promise<PluginListenerHandle>;

  /**
   * Activity recognition update.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'activity', listener: (event: Activity) => void): Promise<PluginListenerHandle>;

  /**
   * Service started.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'start', listener: () => void): Promise<PluginListenerHandle>;

  /**
   * Service stopped.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'stop', listener: () => void): Promise<PluginListenerHandle>;

  /**
   * Recoverable or fatal native error.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'error', listener: (event: BackgroundGeolocationError) => void): Promise<PluginListenerHandle>;

  /**
   * User changed authorization or toggled location services.
   *
   * @since 1.0.0
   */
  addListener(
    eventName: 'authorization',
    listener: (event: { status: AuthorizationStatus }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * App entered the foreground.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'foreground', listener: () => void): Promise<PluginListenerHandle>;

  /**
   * App entered the background.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'background', listener: () => void): Promise<PluginListenerHandle>;

  /**
   * Server returned `285 Updates Not Required`.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'abort_requested', listener: () => void): Promise<PluginListenerHandle>;

  /**
   * Server returned `401 Unauthorized`.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'http_authorization', listener: () => void): Promise<PluginListenerHandle>;

  /**
   * Periodic tick with the latest known location. Receives a {@link Location}
   * once the native service has at least one fix. Early ticks (before any GPS
   * fix) deliver an empty object — guard with `if ('latitude' in event)`.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'heartbeat', listener: (event: Location) => void): Promise<PluginListenerHandle>;

  /**
   * A batch upload to `syncUrl` started.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'syncStart', listener: () => void): Promise<PluginListenerHandle>;

  /**
   * Sync upload progress (0..100).
   *
   * @since 1.0.0
   */
  addListener(
    eventName: 'syncProgress',
    listener: (event: { progress: number }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Sync upload completed successfully.
   *
   * @since 1.0.0
   */
  addListener(eventName: 'syncSuccess', listener: (event: { sent: number }) => void): Promise<PluginListenerHandle>;

  /**
   * Sync upload failed.
   *
   * @since 1.0.0
   */
  addListener(
    eventName: 'syncError',
    listener: (event: { httpStatus: number; message: string }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * A trip started (driver insights).
   *
   * @since 1.0.0
   */
  addListener(eventName: 'tripStart', listener: (event: Location) => void): Promise<PluginListenerHandle>;

  /**
   * A trip ended (driver insights). Includes a {@link TripScore} when
   * `drivingEvents.enabled` is `true`.
   *
   * @since 1.0.0
   */
  addListener(
    eventName: 'tripEnd',
    listener: (event: { location: Location; distance: number; durationMs: number; score?: TripScore }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * User started moving (driver insights).
   *
   * @since 1.0.0
   */
  addListener(eventName: 'moving', listener: (event: Location) => void): Promise<PluginListenerHandle>;

  /**
   * User stopped (driver insights).
   *
   * @since 1.0.0
   */
  addListener(eventName: 'stopped', listener: (event: Location) => void): Promise<PluginListenerHandle>;

  /**
   * Speed crossed above `drivingEvents.speedLimit`.
   *
   * @since 1.0.0
   */
  addListener(
    eventName: 'speeding',
    listener: (event: { location: Location; speedKmh: number; limitKmh: number }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Native location provider changed (gps ↔ network ↔ fused).
   *
   * @since 1.0.0
   */
  addListener(
    eventName: 'providerChange',
    listener: (event: { provider: string }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * `triggerSOS()` was invoked.
   *
   * @since 1.0.0
   */
  addListener(
    eventName: 'sos',
    listener: (event: { location?: Location; [key: string]: unknown }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * GPS-derived hard brake.
   *
   * @since 1.0.0
   */
  addListener(
    eventName: 'hardBrake',
    listener: (event: { location: Location; value: number }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * GPS-derived rapid acceleration.
   *
   * @since 1.0.0
   */
  addListener(
    eventName: 'rapidAcceleration',
    listener: (event: { location: Location; value: number }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * GPS-derived sharp turn.
   *
   * @since 1.0.0
   */
  addListener(
    eventName: 'sharpTurn',
    listener: (event: { location: Location; value: number }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Heuristic possible-crash detection (GPS or sensor pipeline).
   *
   * @since 1.0.0
   */
  addListener(
    eventName: 'possibleCrash',
    listener: (event: { location: Location; value: number; source: 'gps' | 'sensor' }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Sustained phone interaction during an active trip.
   *
   * @since 1.0.0
   */
  addListener(
    eventName: 'phoneUsageWhileDriving',
    listener: (event: { location?: Location }) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Android location service was restarted by the OS or watchdog.
   * `reason` is `'watchdog'` (no GPS fix in the configured window),
   * `'system_kill'` (OS killed and restarted via START_STICKY), or
   * `'boot'` (device boot/package-replace via BootCompletedReceiver).
   *
   * @since 1.1.0
   */
  addListener(
    eventName: 'serviceRestarted',
    listener: (event: ServiceRestartedEvent) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * iOS only. Fired when the location provider switches to a background fallback
   * strategy (significant-location changes or region monitoring). Useful to inform
   * the UI that accuracy has been reduced to save battery.
   *
   * `strategy` is `'significantchanges'` or `'regionmonitoring'`.
   *
   * @since 1.2.0
   */
  addListener(
    eventName: 'iosFallbackActivated',
    listener: (event: IosFallbackActivatedEvent) => void,
  ): Promise<PluginListenerHandle>;

  /**
   * Device entered a registered geofence.
   *
   * @since 1.3.0
   */
  addListener(eventName: 'geofenceEnter', listener: (event: GeofenceEvent) => void): Promise<PluginListenerHandle>;

  /**
   * Device exited a registered geofence.
   *
   * @since 1.3.0
   */
  addListener(eventName: 'geofenceExit', listener: (event: GeofenceEvent) => void): Promise<PluginListenerHandle>;

  /**
   * Device has been inside a geofence for `loiteringDelay` milliseconds.
   *
   * @since 1.3.0
   */
  addListener(eventName: 'geofenceDwell', listener: (event: GeofenceEvent) => void): Promise<PluginListenerHandle>;

  /**
   * Vehicle has been stationary during an active trip for at least
   * `drivingEvents.idleThresholdMs` (default 5 min).
   *
   * @since 1.4.0
   */
  addListener(eventName: 'idleStart', listener: (event: IdleStartEvent) => void): Promise<PluginListenerHandle>;

  /**
   * Vehicle resumed continuous movement after an idle episode.
   *
   * @since 1.4.0
   */
  addListener(eventName: 'idleEnd', listener: (event: IdleEndEvent) => void): Promise<PluginListenerHandle>;
}

// ---------------------------------------------------------------------------
// @awesome-cordova-plugins compatibility re-exports
// ---------------------------------------------------------------------------

/** Event name strings (compatibility with `@awesome-cordova-plugins` style). @since 1.0.0 */
export enum BackgroundGeolocationEvents {
  http_authorization = 'http_authorization',
  abort_requested = 'abort_requested',
  background = 'background',
  foreground = 'foreground',
  authorization = 'authorization',
  error = 'error',
  stop = 'stop',
  start = 'start',
  activity = 'activity',
  stationary = 'stationary',
  location = 'location',
  heartbeat = 'heartbeat',
  syncStart = 'syncStart',
  syncProgress = 'syncProgress',
  syncSuccess = 'syncSuccess',
  syncError = 'syncError',
  tripStart = 'tripStart',
  tripEnd = 'tripEnd',
  moving = 'moving',
  stopped = 'stopped',
  speeding = 'speeding',
  providerChange = 'providerChange',
  sos = 'sos',
  hardBrake = 'hardBrake',
  rapidAcceleration = 'rapidAcceleration',
  sharpTurn = 'sharpTurn',
  possibleCrash = 'possibleCrash',
  phoneUsageWhileDriving = 'phoneUsageWhileDriving',
  serviceRestarted = 'serviceRestarted',
  iosFallbackActivated = 'iosFallbackActivated',
  geofenceEnter = 'geofenceEnter',
  geofenceExit = 'geofenceExit',
  geofenceDwell = 'geofenceDwell',
  idleStart = 'idleStart',
  idleEnd = 'idleEnd',
}

/** Location error codes. @since 1.0.0 */
export enum BackgroundGeolocationLocationCode {
  PERMISSION_DENIED = 1,
  LOCATION_UNAVAILABLE = 2,
  TIMEOUT = 3,
}

/** Native provider strings. @since 1.0.0 */
export enum BackgroundGeolocationNativeProvider {
  gps = 'gps',
  network = 'network',
  passive = 'passive',
  fused = 'fused',
}

/** Location provider IDs. @since 1.0.0 */
export enum BackgroundGeolocationLocationProvider {
  DISTANCE_FILTER_PROVIDER = 0,
  ACTIVITY_PROVIDER = 1,
  RAW_PROVIDER = 2,
}

/** Authorization status. @since 1.0.0 */
export enum BackgroundGeolocationAuthorizationStatus {
  NOT_AUTHORIZED = 0,
  AUTHORIZED = 1,
  AUTHORIZED_FOREGROUND = 2,
}

/** Log levels. @since 1.0.0 */
export enum BackgroundGeolocationLogLevel {
  TRACE = 'TRACE',
  DEBUG = 'DEBUG',
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR',
}

/** Provider enum (compatibility with `@awesome-cordova-plugins` style). @since 1.0.0 */
export enum BackgroundGeolocationProvider {
  ANDROID_DISTANCE_FILTER_PROVIDER = 0,
  ANDROID_ACTIVITY_PROVIDER = 1,
  RAW_PROVIDER = 2,
}

/** Desired accuracy in meters. @since 1.0.0 */
export enum BackgroundGeolocationAccuracy {
  HIGH = 0,
  MEDIUM = 100,
  LOW = 1000,
  PASSIVE = 10000,
}

/** Mode for `switchMode`. @since 1.0.0 */
export enum BackgroundGeolocationMode {
  BACKGROUND = 0,
  FOREGROUND = 1,
}

/** iOS activity type. @since 1.0.0 */
export enum BackgroundGeolocationIOSActivity {
  AutomotiveNavigation = 'AutomotiveNavigation',
  OtherNavigation = 'OtherNavigation',
  Fitness = 'Fitness',
  Other = 'Other',
}

/** Alias for {@link ConfigureOptions}. @since 1.0.0 */
export type BackgroundGeolocationConfig = ConfigureOptions;

/** Alias for {@link Location}. @since 1.0.0 */
export type BackgroundGeolocationResponse = Location;

/** Alias for {@link CurrentLocationOptions}. @since 1.0.0 */
export type BackgroundGeolocationCurrentPositionConfig = CurrentLocationOptions;

/** Alias for {@link LogEntry}. @since 1.0.0 */
export type BackgroundGeolocationLogEntry = LogEntry;
