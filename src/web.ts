// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// Browser fallback for @gachlab/capacitor-background-geolocation.
// Uses navigator.geolocation. Tracking runs only while the page is alive.
// Locations, sessions, and the sync queue are kept in memory for the lifetime
// of the page — no IndexedDB persistence across reloads.

import { WebPlugin } from '@capacitor/core';
import type { PermissionState } from '@capacitor/core';

import type {
  BackgroundGeolocationError,
  BackgroundGeolocationPlugin,
  Capabilities,
  ConfigureOptions,
  CurrentLocationOptions,
  Diagnostics,
  Geofence,
  Location,
  LogEntry,
  PermissionRequestResult,
  ServiceStatus,
  StationaryLocation,
  TripScore,
} from './definitions';
import { AuthorizationStatus } from './definitions';
import { GeoPoint } from './domain/geo-point';
import { GeoEvent } from './domain/geo-event';
import { GeofenceTransition } from './domain/geofence-transition';

interface BrowserPermissions {
  query?: (descriptor: { name: string }) => Promise<{ state: PermissionState }>;
}

export class BackgroundGeolocationWeb extends WebPlugin implements BackgroundGeolocationPlugin {
  private watchId: number | null = null;
  private config: ConfigureOptions = {};
  private lastLocation: Location | null = null;

  // In-memory stores (cleared on page reload — foreground only)
  private locations: Location[] = [];
  private sessionLocations: Location[] = [];
  private sessionActive = false;
  private syncQueue: Location[] = [];

  // ---------------- Tracking control ----------------

  async configure(options: ConfigureOptions): Promise<void> {
    this.config = { ...this.config, ...options };
  }

  async start(): Promise<void> {
    if (!('geolocation' in navigator)) {
      throw this.unavailable('Geolocation API is not available in this browser.');
    }
    if (this.watchId !== null) return;
    this.watchId = navigator.geolocation.watchPosition(
      (pos) => {
        const loc = this.toLocation(pos);
        this.lastLocation = loc;
        this.storeLocation(loc);
        this.notifyListeners('location', loc);
        this.evaluateGeofences(loc);
        if (this.config.url) void this.postLocation(loc);
      },
      (err) => {
        this.notifyListeners('error', this.toError(err));
      },
      {
        enableHighAccuracy: this.config.desiredAccuracy !== 'LOW',
        maximumAge: 0,
        timeout: 30_000,
      },
    );
    this.notifyListeners('start', {});
  }

  async stop(): Promise<void> {
    if (this.watchId !== null) {
      navigator.geolocation.clearWatch(this.watchId);
      this.watchId = null;
      this.notifyListeners('stop', {});
    }
  }

  async switchMode(_options: { mode: 0 | 1 }): Promise<void> {
    /* no-op on web — always foreground */
  }

  async checkStatus(): Promise<ServiceStatus> {
    return {
      isRunning: this.watchId !== null,
      locationServicesEnabled: 'geolocation' in navigator,
      authorization: AuthorizationStatus.AUTHORIZED_FOREGROUND,
    };
  }

  // ---------------- Locations ----------------

  getCurrentLocation(options?: CurrentLocationOptions): Promise<Location> {
    if (!('geolocation' in navigator)) {
      throw this.unavailable('Geolocation API is not available in this browser.');
    }
    return new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(
        (pos) => resolve(this.toLocation(pos)),
        (err) => reject(this.toError(err)),
        {
          enableHighAccuracy: options?.enableHighAccuracy ?? true,
          maximumAge: options?.maximumAge ?? 0,
          timeout: options?.timeout ?? 30_000,
        },
      );
    });
  }

  async getStationaryLocation(): Promise<StationaryLocation | null> {
    return null;
  }

  async getLocations(): Promise<{ locations: Location[] }> {
    return { locations: [...this.locations] };
  }

  async getValidLocations(): Promise<{ locations: Location[] }> {
    return { locations: [...this.locations] };
  }

  async getValidLocationsAndDelete(): Promise<{ locations: Location[] }> {
    const locations = [...this.locations];
    this.locations = [];
    return { locations };
  }

  async deleteLocation(options: { locationId: number }): Promise<void> {
    this.locations = this.locations.filter((l) => l.id !== options.locationId);
  }

  async deleteAllLocations(): Promise<void> {
    this.locations = [];
  }

  // ---------------- Sync queue ----------------

  async forceSync(): Promise<void> {
    const url = this.config.syncUrl ?? this.config.url;
    if (!url || this.syncQueue.length === 0) return;
    const batch = this.syncQueue.splice(0);
    try {
      const resp = await fetch(url, {
        method: (this.config.syncHttpMethod ?? this.config.httpMethod ?? 'POST').toUpperCase(),
        headers: { 'Content-Type': 'application/json', ...(this.config.httpHeaders ?? {}) },
        body: JSON.stringify(batch),
      });
      if (!resp.ok) this.syncQueue.unshift(...batch);
      else this.notifyListeners('syncSuccess', { sent: batch.length });
    } catch {
      this.syncQueue.unshift(...batch);
    }
  }

  async clearSync(): Promise<void> {
    this.syncQueue = [];
  }

  async getPendingSyncCount(): Promise<{ count: number }> {
    return { count: this.syncQueue.length };
  }

  // ---------------- Sessions ----------------

  async startSession(): Promise<void> {
    this.sessionLocations = [];
    this.sessionActive = true;
  }

  async getSessionLocations(): Promise<{ locations: Location[] }> {
    return { locations: [...this.sessionLocations] };
  }

  async clearSession(): Promise<void> {
    this.sessionLocations = [];
    this.sessionActive = false;
  }

  async getSessionLocationsCount(): Promise<{ count: number }> {
    return { count: this.sessionLocations.length };
  }

  // ---------------- Diagnostics & OEMs ----------------

  async getBackgroundKillReason(): Promise<{ reason: string | null; timestamp: number | null }> {
    return { reason: null, timestamp: null };
  }

  async getDiagnostics(): Promise<Diagnostics> {
    return {
      isRunning: this.watchId !== null,
      locationServicesEnabled: 'geolocation' in navigator,
      lastLocationAt: this.lastLocation?.time ?? null,
      manufacturer: 'web',
    };
  }

  async isIgnoringBatteryOptimizations(): Promise<{ whitelisted: boolean }> {
    return { whitelisted: true };
  }

  async requestIgnoreBatteryOptimizations(): Promise<{ whitelisted: boolean }> {
    return { whitelisted: true };
  }

  async openBatterySettings(): Promise<void> {
    /* no-op */
  }

  async openAutoStartSettings(): Promise<{ opened: boolean; manufacturer: string; screen: string }> {
    return { opened: false, manufacturer: 'web', screen: '' };
  }

  async getManufacturerHelp(): Promise<{ manufacturer: string; steps: string[] }> {
    return { manufacturer: 'web', steps: [] };
  }

  async getPluginVersion(): Promise<{ version: string }> {
    // Keep in sync with package.json `version`. Enforced by version-sync.test.ts.
    return { version: '2.0.0' };
  }

  /**
   * The web die's `misa`: it implements the base ISA (location, geofencing in JS)
   * but **not** the always-on background extension or native driver-intelligence —
   * a browser tab has no AOP island. Report that honestly so consumers degrade
   * gracefully instead of expecting background tracking that cannot exist.
   */
  async getCapabilities(): Promise<Capabilities> {
    return {
      platform: 'web',
      backgroundTracking: false,
      activityRecognition: false,
      geofencing: true,
      maxGeofences: -1,
      sensorFusion: false,
      driverIntelligence: false,
      oemSettings: false,
    };
  }

  // ---------------- Permissions ----------------

  async checkPermissions(): Promise<{ location: PermissionState }> {
    const perms = (navigator as unknown as { permissions?: BrowserPermissions }).permissions;
    if (perms?.query) {
      try {
        const result = await perms.query({ name: 'geolocation' });
        return { location: result.state };
      } catch {
        /* fall through */
      }
    }
    return { location: 'prompt' };
  }

  async requestPermissions(): Promise<{ location: PermissionState }> {
    if (!('geolocation' in navigator)) return { location: 'denied' };
    return new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        () => resolve({ location: 'granted' }),
        (err) => resolve({ location: err.code === err.PERMISSION_DENIED ? 'denied' : 'prompt' }),
        { enableHighAccuracy: false, maximumAge: 60_000, timeout: 5_000 },
      );
    });
  }

  async requestBackgroundLocationPermission(): Promise<PermissionRequestResult> {
    return { granted: true, notRequired: true };
  }

  async requestActivityRecognitionPermission(): Promise<PermissionRequestResult> {
    return { granted: true, notRequired: true };
  }

  async requestNotificationPermission(): Promise<PermissionRequestResult> {
    if (typeof Notification === 'undefined') return { granted: true, notRequired: true };
    if (Notification.permission === 'granted') return { granted: true };
    try {
      const result = await Notification.requestPermission();
      return result === 'granted' ? { granted: true } : { granted: false, denied: ['notifications'] };
    } catch {
      return { granted: false, denied: ['notifications'] };
    }
  }

  async showAppSettings(): Promise<void> {
    /* no-op */
  }

  async openSettings(): Promise<void> {
    /* no-op */
  }

  async showLocationSettings(): Promise<void> {
    /* no-op */
  }

  // ---------------- Tasks ----------------

  async startTask(): Promise<{ taskKey: number }> {
    return { taskKey: 0 };
  }

  async endTask(_options: { taskKey: number }): Promise<void> {
    /* no-op */
  }

  async triggerSOS(payload?: Record<string, unknown>): Promise<void> {
    this.notifyListeners('sos', { ...(payload ?? {}), location: this.lastLocation ?? undefined });
  }

  // ---------------- Geofencing ----------------
  // The browser has no native geofencing API, so we run a JS engine: every location
  // fix is tested against each registered region (haversine). Mirrors native
  // semantics — initial ENTER when registering already-inside, EXIT only after a
  // real entry, a single DWELL per dwell, and `geofenceError` on invalid input.

  private geofences = new Map<string, Geofence>();
  private insideGeofences = new Set<string>();
  private dwellEnterAt = new Map<string, number>();
  private dwellTimers = new Map<string, ReturnType<typeof setTimeout>>();

  async addGeofences(options: { geofences: Geofence[] }): Promise<void> {
    for (const raw of options?.geofences ?? []) {
      const gf = this.normalizeGeofence(raw);
      if (!gf) continue; // invalid → geofenceError already emitted
      this.geofences.set(gf.id, gf);
      // Initial ENTER when already inside (parity with iOS requestState / Android
      // INITIAL_TRIGGER_ENTER).
      if (this.lastLocation && this.isInside(gf, this.lastLocation)) {
        this.handleGeofenceEntered(gf, this.lastLocation);
      }
    }
  }

  async removeGeofences(options?: { ids?: string[] }): Promise<void> {
    const ids = options?.ids ?? [...this.geofences.keys()];
    for (const id of ids) {
      this.geofences.delete(id);
      this.insideGeofences.delete(id);
      this.clearDwell(id);
    }
  }

  async getGeofences(): Promise<{ geofences: Geofence[] }> {
    return { geofences: [...this.geofences.values()] };
  }

  private normalizeGeofence(gf: Geofence): Geofence | null {
    const fail = (message: string): null => {
      this.notifyListeners('geofenceError', { id: gf.id, message });
      return null;
    };
    if (!gf.id) return fail('geofence id is required');
    if (!Number.isFinite(gf.latitude) || gf.latitude < -90 || gf.latitude > 90)
      return fail(`invalid latitude for geofence ${gf.id}`);
    if (!Number.isFinite(gf.longitude) || gf.longitude < -180 || gf.longitude > 180)
      return fail(`invalid longitude for geofence ${gf.id}`);
    const radius = gf.radius ?? 200;
    if (!Number.isFinite(radius) || radius <= 0) return fail(`invalid radius for geofence ${gf.id}`);
    return {
      id: gf.id,
      latitude: gf.latitude,
      longitude: gf.longitude,
      radius,
      notifyOnEntry: gf.notifyOnEntry ?? true,
      notifyOnExit: gf.notifyOnExit ?? false,
      notifyOnDwell: gf.notifyOnDwell ?? false,
      loiteringDelay: gf.loiteringDelay ?? 30_000,
    };
  }

  private evaluateGeofences(loc: Location): void {
    for (const gf of this.geofences.values()) {
      const inside = this.isInside(gf, loc);
      const wasInside = this.insideGeofences.has(gf.id);
      if (inside && !wasInside) this.handleGeofenceEntered(gf, loc);
      else if (!inside && wasInside) this.handleGeofenceExited(gf, loc);
      else if (inside && wasInside) this.evaluateDwell(gf, loc);
    }
  }

  private handleGeofenceEntered(gf: Geofence, loc: Location): void {
    if (this.insideGeofences.has(gf.id)) return;
    this.insideGeofences.add(gf.id);
    if (gf.notifyOnEntry) this.emitGeoEvent(new GeoEvent(gf.id, GeofenceTransition.ENTER), loc);
    if (gf.notifyOnDwell) {
      this.dwellEnterAt.set(gf.id, loc.time);
      // Fast path for a stationary device that may stop emitting fixes; the per-fix
      // evaluateDwell is the resilient path. Whichever fires first wins (fireDwellIfPending).
      const timer = setTimeout(() => this.fireDwellIfPending(gf.id, loc), gf.loiteringDelay ?? 30_000);
      (timer as { unref?: () => void }).unref?.();
      this.dwellTimers.set(gf.id, timer);
    }
  }

  private handleGeofenceExited(gf: Geofence, loc: Location): void {
    const wasInside = this.insideGeofences.delete(gf.id);
    this.clearDwell(gf.id);
    // EXIT only on a real boundary crossing (we were inside).
    if (wasInside && gf.notifyOnExit) this.emitGeoEvent(new GeoEvent(gf.id, GeofenceTransition.EXIT), loc);
  }

  private evaluateDwell(gf: Geofence, loc: Location): void {
    const enterAt = this.dwellEnterAt.get(gf.id);
    if (enterAt === undefined) return;
    if (loc.time - enterAt >= (gf.loiteringDelay ?? 30_000)) this.fireDwellIfPending(gf.id, loc);
  }

  /** Fire DWELL at most once per dwell — `dwellEnterAt` presence is the guard. */
  private fireDwellIfPending(id: string, loc: Location): void {
    if (!this.dwellEnterAt.has(id)) return;
    this.clearDwell(id);
    this.emitGeoEvent(new GeoEvent(id, GeofenceTransition.DWELL), loc);
  }

  private clearDwell(id: string): void {
    this.dwellEnterAt.delete(id);
    const timer = this.dwellTimers.get(id);
    if (timer) {
      clearTimeout(timer);
      this.dwellTimers.delete(id);
    }
  }

  /** Map a domain GeoEvent to its listener name + payload (single emission path). */
  private emitGeoEvent(event: GeoEvent, location: Location): void {
    const eventName =
      event.transition === GeofenceTransition.ENTER
        ? 'geofenceEnter'
        : event.transition === GeofenceTransition.EXIT
          ? 'geofenceExit'
          : 'geofenceDwell';
    this.notifyListeners(eventName, { id: event.geofenceId, action: event.transition, location });
  }

  private isInside(gf: Geofence, loc: Location): boolean {
    return (
      new GeoPoint(gf.latitude, gf.longitude).distanceTo(new GeoPoint(loc.latitude, loc.longitude)) <=
      (gf.radius ?? 200)
    );
  }

  // ---------------- Driver intelligence ----------------

  async getTripScore(): Promise<TripScore | null> {
    return null;
  }

  // ---------------- Config & logs ----------------

  async getConfig(): Promise<ConfigureOptions> {
    return { ...this.config };
  }

  async getLogEntries(_options: { limit: number; fromId?: number }): Promise<{ entries: LogEntry[] }> {
    return { entries: [] };
  }

  // ---------------- Lifecycle ----------------

  async removeAllListeners(): Promise<void> {
    await super.removeAllListeners();
  }

  // ---------------- Private ----------------

  private storeLocation(loc: Location): void {
    this.locations.push(loc);
    const max = this.config.maxLocations ?? 10_000;
    if (this.locations.length > max) this.locations.splice(0, this.locations.length - max);
    if (this.sessionActive) this.sessionLocations.push(loc);
  }

  private async postLocation(loc: Location): Promise<void> {
    const url = this.config.url;
    if (!url) return;
    try {
      const resp = await fetch(url, {
        method: (this.config.httpMethod ?? 'POST').toUpperCase(),
        headers: { 'Content-Type': 'application/json', ...(this.config.httpHeaders ?? {}) },
        body: JSON.stringify(loc),
      });
      if (!resp.ok) this.syncQueue.push(loc);
    } catch {
      this.syncQueue.push(loc);
    }
  }

  private toLocation(pos: GeolocationPosition): Location {
    const c = pos.coords;
    return {
      id: pos.timestamp,
      provider: 'gps',
      locationProvider: 0,
      time: pos.timestamp,
      latitude: c.latitude,
      longitude: c.longitude,
      accuracy: c.accuracy ?? 0,
      speed: c.speed ?? 0,
      altitude: c.altitude ?? 0,
      bearing: c.heading ?? 0,
    };
  }

  private toError(err: GeolocationPositionError): BackgroundGeolocationError {
    return { code: err.code, message: err.message };
  }
} /* c8 ignore next */
