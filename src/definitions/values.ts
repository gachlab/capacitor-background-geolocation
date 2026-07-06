// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 contract · shared value types (Fase 1).
// Data/payload types delivered by the native provider. These are passed through
// the facade largely untouched (they are *data*, not config), so their shape stays
// close to what the bridge emits. The clean-vocabulary rename applies to CONFIG
// inputs (see config.ts), not to these payloads.

// ---------------------------------------------------------------------------
// Config-input vocabulary (clean names — the facade maps these to the native wire)
// ---------------------------------------------------------------------------

/** Desired accuracy tier. Facade maps to the native meters value. */
export type Accuracy = 'high' | 'medium' | 'low' | 'passive';

/** Location provider strategy. Facade maps to the native provider id. */
export type LocationProvider = 'distanceFilter' | 'activity' | 'raw';

/** HTTP method for transport requests. */
export type HttpMethod = 'POST' | 'GET' | 'PUT' | 'PATCH';

/** Delivery mode for outbound locations. */
export type DeliveryMode = 'batch' | 'single';

/** Policy for samples flagged as mock locations. */
export type MockPolicy = 'allow' | 'flag' | 'drop';

// ---------------------------------------------------------------------------
// Enums / unions reported by the OS
// ---------------------------------------------------------------------------

/** Native location source reported by the OS. */
export type NativeProvider = 'gps' | 'network' | 'passive' | 'fused';

/** Service authorization state (clean string union; facade maps the native int). */
export type AuthorizationStatus = 'notAuthorized' | 'authorized' | 'authorizedForeground';

/** Recognised motion-activity type. */
export type ActivityType =
  | 'inVehicle'
  | 'onBicycle'
  | 'onFoot'
  | 'running'
  | 'still'
  | 'tilting'
  | 'unknown'
  | 'walking';

/** Severity for persisted log entries. */
export type LogLevel = 'trace' | 'debug' | 'info' | 'warn' | 'error';

/** Reason a geolocation request failed. */
export type LocationErrorCode = 'permissionDenied' | 'unavailable' | 'timeout';

// ---------------------------------------------------------------------------
// Location payloads
// ---------------------------------------------------------------------------

/** A single location fix delivered by the native provider. */
export interface Location {
  /** DB id (absent for synthetic fixes). */
  id?: number;
  /** Native provider that produced the fix. */
  provider: NativeProvider;
  /** UTC timestamp in ms. */
  time: number;
  latitude: number;
  longitude: number;
  /** Horizontal accuracy in meters. */
  accuracy: number;
  /** Ground speed in m/s. */
  speed: number;
  altitude: number;
  /** Bearing in degrees. */
  bearing: number;
  /** Fix came from a mock provider (Android). */
  isFromMockProvider?: boolean;
  /** Developer-options "mock locations" is enabled (Android). */
  mockLocationsEnabled?: boolean;
  /** Simulator-generated fix (iOS 15+). */
  simulated?: boolean;
  /** Battery percentage (0–100) when battery stamping is on. */
  battery?: number;
  /** Charging state when battery stamping is on. */
  isCharging?: boolean;
}

/** Stationary location adds a `radius` (meters). */
export interface StationaryLocation extends Location {
  radius: number;
}

/** Geolocation error shape. */
export interface LocationError {
  code: LocationErrorCode;
  message: string;
}

/** Motion-activity recognition payload. */
export interface Activity {
  type: ActivityType;
  /** Confidence percentage (0–100). */
  confidence: number;
}

// ---------------------------------------------------------------------------
// Status / diagnostics / capabilities
// ---------------------------------------------------------------------------

/** Service status snapshot. */
export interface ServiceStatus {
  isRunning: boolean;
  locationServicesEnabled: boolean;
  authorization: AuthorizationStatus;
}

/**
 * Static capability descriptor — what the platform *supports* (the `misa` register).
 * These are platform facts, NOT the current permission state. Live permission /
 * runtime state lives in {@link Diagnostics} and {@link ServiceStatus}.
 */
export interface Capabilities {
  platform: 'ios' | 'android' | 'web';
  /**
   * Native wire-contract version. The facade warns once if the native binary is OLDER than
   * the JS expects (a stale `cap sync`), turning silent event/method skew into a diagnosable
   * signal. Bump on any breaking wire change.
   */
  contractVersion: number;
  backgroundTracking: boolean;
  activityRecognition: boolean;
  geofencing: boolean;
  /** Max simultaneous geofences. iOS 19 · Android 100 · web -1 (unbounded). */
  maxGeofences: number;
  sensorFusion: boolean;
  /** On-device trip scoring + driving-event inference (the X-extension). */
  driverIntelligence: boolean;
  /** OEM battery / auto-start settings screens can be opened (Android only). */
  oemSettings: boolean;
}

/** Extended diagnostics (permissions, battery, OEM, iOS flags). */
export interface Diagnostics {
  isRunning: boolean;
  locationServicesEnabled: boolean;
  startOnBoot?: boolean;
  pendingSyncCount?: number;
  lastLocationAt?: number | null;
  // Android
  fineLocationGranted?: boolean;
  coarseLocationGranted?: boolean;
  backgroundLocationGranted?: boolean;
  notificationPermissionGranted?: boolean;
  activityRecognitionGranted?: boolean;
  batteryOptimizationIgnored?: boolean;
  manufacturer?: string;
  foregroundServiceType?: number;
  // iOS
  preciseLocationEnabled?: boolean;
  backgroundRefreshStatus?: 'available' | 'denied' | 'restricted';
  lowPowerModeEnabled?: boolean;
  motionPermissionStatus?: 'authorized' | 'denied' | 'restricted' | 'notDetermined';
  authorizationStatusText?: string;
}

/** Persisted log entry returned by the logs stream. */
export interface LogEntry {
  id: number;
  /** UTC timestamp (ms). */
  timestamp: number;
  level: LogLevel;
  message: string;
  /** Stack trace (Android only — iOS folds it into `message`). */
  stackTrace: string;
}

/** Result of an Android runtime permission request. */
export interface PermissionRequestResult {
  granted: boolean;
  denied?: string[];
  /** TRUE when the OS made the request a no-op (iOS, old Android). */
  notRequired?: boolean;
}

// ---------------------------------------------------------------------------
// Geofencing
// ---------------------------------------------------------------------------

/** A user-defined geofence region. */
export interface Geofence {
  id: string;
  latitude: number;
  longitude: number;
  /** Radius in meters. @default 200 */
  radius?: number;
  notifyOnEntry?: boolean;
  notifyOnExit?: boolean;
  notifyOnDwell?: boolean;
  /** Dwell threshold (ms). @default 30000 */
  loiteringDelayMs?: number;
}

/** Payload emitted on a geofence transition. */
export interface GeofenceEvent {
  id: string;
  action: 'enter' | 'exit' | 'dwell';
  location?: Location;
}

/** Payload emitted when a geofence fails to register or monitor. */
export interface GeofenceErrorEvent {
  id?: string;
  message: string;
}

// ---------------------------------------------------------------------------
// Driver intelligence (X-extension)
// ---------------------------------------------------------------------------

/** Driving-event category keys. */
export type DrivingEventType =
  | 'hardBrake'
  | 'rapidAcceleration'
  | 'sharpTurn'
  | 'possibleCrash'
  | 'speeding'
  | 'phoneUsageWhileDriving';

/** Per-category score breakdown (0–100 each). */
export interface TripScoreBreakdown {
  speeding: number;
  hardBraking: number;
  rapidAcceleration: number;
  sharpTurns: number;
  phoneUsage: number;
}

/** A single scored event recorded during a trip. */
export interface TripScoreEvent {
  type: string;
  timestamp: number;
  penalty: number;
  location: { latitude: number; longitude: number };
}

/** Driver behaviour score for a completed trip. */
export interface TripScore {
  overall: number;
  breakdown: TripScoreBreakdown;
  events: TripScoreEvent[];
  tripId: string;
  startedAt: number;
  endedAt: number;
  distanceKm: number;
  totalIdleMs: number;
  idleCount: number;
}
