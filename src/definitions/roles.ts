// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 contract · native contract, segregated into role interfaces (Fase 1).
//
// This is the flat, Promise-based surface that crosses the Capacitor bridge. It is
// composed by INTERSECTION of small role interfaces (Interface Segregation — the
// SOLID form of "composition over inheritance" applicable to a flat bridge). The
// public facade (Fase 2) builds clean, disposable sub-APIs on top and translates the
// composed BaseConfig to NativeConfig.
//
// Payload shapes are the clean ones from values.ts; the native side emits them (or
// the facade coerces raw → clean at the boundary). Config INPUT uses the native wire
// (NativeConfig); config vocabulary rename happens facade-side only.

import type { GeolocationEvents } from './events';
import type {
  Capabilities,
  Diagnostics,
  Geofence,
  Location,
  LogEntry,
  LogLevel,
  PermissionRequestResult,
  ServiceStatus,
  StationaryLocation,
  TripScore,
} from './values';
import type { NativeConfig } from './wire';

/** Native one-shot options (facade maps CurrentLocationOptions → this). */
export interface NativeCurrentOptions {
  timeout?: number;
  maximumAge?: number;
  enableHighAccuracy?: boolean;
}

/** Consolidated location query — replaces the 5 overlapping getters. */
export interface LocationQuery {
  /** Which store to read. @default 'all' */
  scope?: 'all' | 'valid' | 'stationary' | 'session';
  /** Delete the returned rows after reading (replaces the `…AndDelete` variant). */
  consume?: boolean;
}

/** Capacitor-style permission state. */
export type PermissionState = 'granted' | 'denied' | 'prompt' | 'prompt-with-rationale';

// ── Roles ──────────────────────────────────────────────────────────────────

export interface Trackable {
  /** Apply the fully-resolved flat config (plain replace — the facade resolved the cascade). */
  configure(config: NativeConfig): Promise<void>;
  start(): Promise<void>;
  stop(): Promise<void>;
  checkStatus(): Promise<ServiceStatus>;
}

export interface LocationStore {
  getCurrentLocation(options?: NativeCurrentOptions): Promise<Location>;
  queryLocations(query: LocationQuery): Promise<{ locations: Location[] }>;
  getStationaryLocation(): Promise<StationaryLocation | null>;
  deleteLocation(options: { locationId: number }): Promise<void>;
  deleteAllLocations(): Promise<void>;
}

/** Recording sessions — a distinct store from live tracking. */
export interface RecordingStore {
  startSession(): Promise<void>;
  clearSession(): Promise<void>;
  getSessionLocations(): Promise<{ locations: Location[] }>;
  getSessionLocationsCount(): Promise<{ count: number }>;
}

export interface SyncQueue {
  forceSync(): Promise<void>;
  clearSync(): Promise<void>;
  getPendingSyncCount(): Promise<{ count: number }>;
}

export interface Geofencing {
  addGeofences(options: { geofences: Geofence[] }): Promise<void>;
  removeGeofences(options?: { ids?: string[] }): Promise<void>;
  getGeofences(): Promise<{ geofences: Geofence[] }>;
}

export interface PermissionController {
  checkPermissions(): Promise<{ location: PermissionState }>;
  requestPermissions(): Promise<{ location: PermissionState }>;
  requestBackgroundLocationPermission(): Promise<PermissionRequestResult>;
  requestActivityRecognitionPermission(): Promise<PermissionRequestResult>;
  requestNotificationPermission(): Promise<PermissionRequestResult>;
  openAppSettings(): Promise<void>;
  openLocationSettings(): Promise<void>;
}

export interface DiagnosticsApi {
  getDiagnostics(): Promise<Diagnostics>;
  getPluginVersion(): Promise<{ version: string }>;
  /** Static platform capabilities (the `misa` register). Permission state is in getDiagnostics. */
  getCapabilities(): Promise<Capabilities>;
  getConfig(): Promise<NativeConfig>;
  getLogEntries(options: { limit: number; fromId?: number; minLevel?: LogLevel }): Promise<{ entries: LogEntry[] }>;
  getBackgroundKillReason(): Promise<{ reason: string | null; timestamp: number | null }>;
}

/** Driver-intelligence (X-ext). Gated by capabilities.driverIntelligence. */
export interface DriverIntelligence {
  getTripScore(): Promise<TripScore | null>;
}

/** OEM battery / auto-start settings — Android only (gated by capabilities.oemSettings). */
export interface OemSettings {
  isIgnoringBatteryOptimizations(): Promise<{ whitelisted: boolean }>;
  requestIgnoreBatteryOptimizations(): Promise<{ whitelisted: boolean }>;
  openBatterySettings(): Promise<void>;
  openAutoStartSettings(): Promise<{ opened: boolean; manufacturer: string; screen: string }>;
  getManufacturerHelp(): Promise<{ manufacturer: string; steps: string[] }>;
}

/** iOS platform tasks — gated by platform. */
export interface PlatformTasks {
  startTask(): Promise<{ taskKey: number }>;
  endTask(options: { taskKey: number }): Promise<void>;
  switchMode(options: { mode: 'background' | 'foreground' }): Promise<void>;
}

/** Safety — SOS. Payload is passed as the bare call args (flattened top-level). */
export interface Safety {
  triggerSOS(payload?: Record<string, unknown>): Promise<void>;
}

/**
 * The complete native contract every platform (`hart`) implements — composed by
 * intersection of the role interfaces. This is what `registerPlugin` is typed with.
 */
export interface BackgroundGeolocationNative
  extends Trackable,
    LocationStore,
    RecordingStore,
    SyncQueue,
    Geofencing,
    PermissionController,
    DiagnosticsApi,
    DriverIntelligence,
    OemSettings,
    PlatformTasks,
    Safety,
    GeolocationEvents {}
