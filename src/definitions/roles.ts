// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 contract · native contract, segregated into role interfaces (Fase 1).
//
// This mirrors the FLAT method surface the natives expose TODAY (getLocations,
// getValidLocations, showAppSettings…), composed by INTERSECTION of small role
// interfaces (Interface Segregation — the SOLID form of "composition over
// inheritance" for a flat bridge). Method names stay as-is so the natives barely
// change; the public facade (Fase 2) CONSOLIDATES and RENAMES on top (5 location
// getters → one query(), etc.) by calling these real methods.
//
// Payloads use the clean shapes from values.ts: in v3 the natives emit clean OUTPUT
// (authorization string, lowercase geofence action, camelCase reason) — a small,
// contained emit-point change per platform, cheaper than maintaining duplicate
// raw/clean type sets. Config INPUT stays the native wire (NativeConfig); the facade
// translates the composed cascade to it (toFlatConfig).

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
  /** Correlates this one-shot with a targeted cancelCurrentLocation({ requestId }). */
  requestId?: string;
}

/** Capacitor-style permission state. */
export type PermissionState = 'granted' | 'denied' | 'prompt' | 'prompt-with-rationale';

// ── Roles ──────────────────────────────────────────────────────────────────
// Real native method names. The facade renames/consolidates on top.

export interface Trackable {
  /** Apply the fully-resolved flat config (plain replace — the facade resolved the cascade). */
  configure(config: NativeConfig): Promise<void>;
  start(): Promise<void>;
  stop(): Promise<void>;
  checkStatus(): Promise<ServiceStatus>;
}

/** The native location surface. The facade renames these into all()/pending()/stationary(). */
export interface LocationStore {
  getCurrentLocation(options?: NativeCurrentOptions): Promise<Location>;
  /**
   * Cancel an in-flight one-shot `getCurrentLocation` — stops the native GPS work so an
   * AbortSignal is honest, not just caller-side. With `{ requestId }` it cancels exactly
   * that request (so concurrent one-shots aren't cross-cancelled) and pre-empts it even if
   * the cancel wins the register race; with no id it cancels all *currently-registered* ones.
   * The facade always passes a requestId (via `locations.current({ signal })`); the id-less
   * form is a raw-proxy escape hatch and does NOT pre-empt a not-yet-registered request.
   */
  cancelCurrentLocation(options?: { requestId?: string }): Promise<void>;
  getStationaryLocation(): Promise<StationaryLocation | null>;
  /** The last known fix (from the native PositionBuffer), or null — used for sticky replay on subscribe. */
  getLastLocation(): Promise<Location | null>;
  getLocations(): Promise<{ locations: Location[] }>;
  getValidLocations(): Promise<{ locations: Location[] }>;
  getValidLocationsAndDelete(): Promise<{ locations: Location[] }>;
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
  showAppSettings(): Promise<void>;
  openSettings(): Promise<void>;
  showLocationSettings(): Promise<void>;
}

export interface DiagnosticsProvider {
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

/** iOS platform tasks — gated by platform. `switchMode` keeps the native 0|1 wire. */
export interface PlatformTasks {
  startTask(): Promise<{ taskKey: number }>;
  endTask(options: { taskKey: number }): Promise<void>;
  switchMode(options: { mode: 0 | 1 }): Promise<void>;
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
    DiagnosticsProvider,
    DriverIntelligence,
    OemSettings,
    PlatformTasks,
    Safety,
    GeolocationEvents {}
