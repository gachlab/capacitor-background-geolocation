// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM
//
// Browser fallback for @josuelmm/capacitor-background-geolocation.
// Uses navigator.geolocation. Real background tracking is not possible on the
// web platform — the service runs only while the page is alive.

import { WebPlugin } from '@capacitor/core';
import type { PermissionState } from '@capacitor/core';

import type {
  BackgroundGeolocationPlugin,
  CurrentLocationOptions,
  ErrorEvent,
  Location,
  LocationOptions,
  LogEntry,
  Status,
  Task,
} from './definitions';
import { AuthorizationStatus } from './definitions';

const NOT_AVAILABLE = 'Not available on web.';

interface BrowserPermissions {
  query?: (descriptor: { name: string }) => Promise<{ state: PermissionState }>;
}

export class BackgroundGeolocationWeb
  extends WebPlugin
  implements BackgroundGeolocationPlugin
{
  private watchId: number | null = null;
  private config: LocationOptions = {};

  async configure(options: LocationOptions): Promise<void> {
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
        this.notifyListeners('location', this.toLocation(pos));
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

  async getStationaryLocation(): Promise<Location | null> {
    return null;
  }

  async getValidLocations(): Promise<{ locations: Location[] }> {
    return { locations: [] };
  }

  async getConfig(): Promise<LocationOptions> {
    return { ...this.config };
  }

  async deleteLocation(_options: { locationId: number }): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async deleteAllLocations(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async isLocationEnabled(): Promise<{ enabled: boolean }> {
    return { enabled: 'geolocation' in navigator };
  }

  async showAppSettings(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async showLocationSettings(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async watchLocationMode(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async stopWatchingLocationMode(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async getLogEntries(_options: {
    limit: number;
    fromId?: number;
  }): Promise<{ entries: LogEntry[] }> {
    return { entries: [] };
  }

  async checkStatus(): Promise<Status> {
    return {
      isRunning: this.watchId !== null,
      locationServicesEnabled: 'geolocation' in navigator,
      authorization: AuthorizationStatus.AUTHORIZED_FOREGROUND,
    };
  }

  async startTask(): Promise<Task> {
    return { taskKey: 0 };
  }

  async endTask(_options: { taskKey: number }): Promise<void> {
    /* no-op */
  }

  async forceSync(): Promise<void> {
    throw this.unimplemented(NOT_AVAILABLE);
  }

  async checkPermissions(): Promise<{ location: PermissionState }> {
    const perms = (navigator as unknown as { permissions?: BrowserPermissions })
      .permissions;
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

  private toError(err: GeolocationPositionError): ErrorEvent {
    return { code: err.code, message: err.message };
  }
}
