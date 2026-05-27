// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// Browser fallback for @josuelmm/capacitor-background-geolocation.
// Uses navigator.geolocation. Real background tracking is not possible on the
// web platform — the service runs only while the page is alive.

import { WebPlugin } from '@capacitor/core';
import type { PermissionState } from '@capacitor/core';

import type {
  BackgroundGeolocationError,
  BackgroundGeolocationPlugin,
  ConfigureOptions,
  CurrentLocationOptions,
  Diagnostics,
  Geofence,
  Location,
  LogEntry,
  PermissionRequestResult,
  ServiceStatus,
  StationaryLocation,
} from './definitions';
import { AuthorizationStatus } from './definitions';

const NOT_AVAILABLE = 'Not available on web.';

interface BrowserPermissions {
  query?: (descriptor: { name: string }) => Promise<{ state: PermissionState }>;
}

export class BackgroundGeolocationWeb extends WebPlugin implements BackgroundGeolocationPlugin {
  private watchId: number | null = null;
  private config: ConfigureOptions = {};
  private lastLocation: Location | null = null;

  // ---------------- Tracking control ----------------

  async configure(options: ConfigureOptions): Promise<void> {
    this.config = { ...this.config, ...options };
  }

  async start(): Promise<void> {
    if (!('geolocation' in navigator)) {
      throw this.unavailable('Geolocation API is not available in this browser.');
    }
    if (this.watchId !== null) {
      return;
    }
    this.watchId = navigator.geolocation.watchPosition(
      (pos) => {
        const loc = this.toLocation(pos);
        this.lastLocation = loc;
        this.notifyListeners('location', loc);
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
    throw this.unimplemented(NOT_AVAILABLE);
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
    return { locations: [] };
  }

  async getValidLocations(): Promise<{ locations: Location[] }> {
    return { locations: [] };
  }

  async getValidLocationsAndDelete(): Promise<{ locations: Location[] }> {
    return { locations: [] };
  }

  async deleteLocation(_options: { locationId: number }): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async deleteAllLocations(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  // ---------------- Sync queue ----------------

  async forceSync(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async clearSync(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async getPendingSyncCount(): Promise<{ count: number }> {
    return { count: 0 };
  }

  // ---------------- Sessions ----------------

  async startSession(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async getSessionLocations(): Promise<{ locations: Location[] }> {
    return { locations: [] };
  }

  async clearSession(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async getSessionLocationsCount(): Promise<{ count: number }> {
    return { count: 0 };
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

  async openAutoStartSettings(): Promise<{
    opened: boolean;
    manufacturer: string;
    screen: string;
  }> {
    return { opened: false, manufacturer: 'web', screen: '' };
  }

  async getManufacturerHelp(): Promise<{
    manufacturer: string;
    steps: string[];
  }> {
    return { manufacturer: 'web', steps: [] };
  }

  async getPluginVersion(): Promise<{ version: string }> {
    return { version: '1.0.1' };
  }

  // ---------------- Permissions ----------------

  async checkPermissions(): Promise<{ location: PermissionState }> {
    const perms = (navigator as unknown as { permissions?: BrowserPermissions }).permissions;
    if (perms?.query) {
      try {
        const result = await perms.query({ name: 'geolocation' });
        return { location: result.state };
      } catch {
        /* fall through to probe */
      }
    }
    return { location: 'prompt' };
  }

  async requestPermissions(): Promise<{ location: PermissionState }> {
    if (!('geolocation' in navigator)) {
      return { location: 'denied' };
    }
    return new Promise((resolve) => {
      navigator.geolocation.getCurrentPosition(
        () => resolve({ location: 'granted' }),
        (err) => {
          resolve({
            location: err.code === err.PERMISSION_DENIED ? 'denied' : 'prompt',
          });
        },
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
    if (typeof Notification === 'undefined') {
      return { granted: true, notRequired: true };
    }
    if (Notification.permission === 'granted') {
      return { granted: true };
    }
    try {
      const result = await Notification.requestPermission();
      return result === 'granted' ? { granted: true } : { granted: false, denied: ['notifications'] };
    } catch {
      return { granted: false, denied: ['notifications'] };
    }
  }

  async showAppSettings(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async openSettings(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async showLocationSettings(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  // ---------------- Tasks ----------------

  async startTask(): Promise<{ taskKey: number }> {
    return { taskKey: 0 };
  }

  async endTask(_options: { taskKey: number }): Promise<void> {
    /* no-op */
  }

  async triggerSOS(payload?: Record<string, unknown>): Promise<void> {
    this.notifyListeners('sos', {
      ...(payload ?? {}),
      location: this.lastLocation ?? undefined,
    });
  }

  async headlessTask(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async addGeofences(_options: { geofences: Geofence[] }): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async removeGeofences(_options?: { ids?: string[] }): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async getGeofences(): Promise<{ geofences: Geofence[] }> {
    return { geofences: [] };
  }

  // ---------------- Config & logs ----------------

  async getConfig(): Promise<ConfigureOptions> {
    return { ...this.config };
  }

  async getLogEntries(_options: { limit: number; fromId?: number }): Promise<{ entries: LogEntry[] }> {
    return { entries: [] };
  }

  // ---------------- Helpers ----------------

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
