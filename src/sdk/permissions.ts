// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Permissions sub-API (Fase 2).

import type { BackgroundGeolocationNative, PermissionState } from '../definitions/roles';
import type { PermissionRequestResult } from '../definitions/values';

export class PermissionsApi {
  constructor(private readonly native: BackgroundGeolocationNative) {}

  /** Current location permission state. */
  async check(): Promise<PermissionState> {
    return (await this.native.checkPermissions()).location;
  }

  /** Prompt for location permission. */
  async request(): Promise<PermissionState> {
    return (await this.native.requestPermissions()).location;
  }

  /** Request `ACCESS_BACKGROUND_LOCATION` (Android 10+). */
  requestBackground(): Promise<PermissionRequestResult> {
    return this.native.requestBackgroundLocationPermission();
  }

  /** Request `ACTIVITY_RECOGNITION` (Android 10+). */
  requestActivity(): Promise<PermissionRequestResult> {
    return this.native.requestActivityRecognitionPermission();
  }

  /** Request `POST_NOTIFICATIONS` (Android 13+). */
  requestNotifications(): Promise<PermissionRequestResult> {
    return this.native.requestNotificationPermission();
  }

  /** Open a settings screen so the user can change permissions. */
  openSettings(target: 'app' | 'location' = 'app'): Promise<void> {
    return target === 'location' ? this.native.showLocationSettings() : this.native.showAppSettings();
  }
}
