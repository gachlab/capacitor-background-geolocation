// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Permissions sub-API (Fase 2). Functional: PermissionsApi({ native }) → PermissionsApi.

import type { BackgroundGeolocationNative, PermissionState } from '../definitions/roles';
import type { PermissionRequestResult } from '../definitions/values';

export interface PermissionsApi {
  /** Current location permission state. */
  check(): Promise<PermissionState>;
  /** Prompt for location permission. */
  request(): Promise<PermissionState>;
  /** Request `ACCESS_BACKGROUND_LOCATION` (Android 10+). */
  requestBackground(): Promise<PermissionRequestResult>;
  /** Request `ACTIVITY_RECOGNITION` (Android 10+). */
  requestActivity(): Promise<PermissionRequestResult>;
  /** Request `POST_NOTIFICATIONS` (Android 13+). */
  requestNotifications(): Promise<PermissionRequestResult>;
  /** Open a settings screen so the user can change permissions. */
  openSettings(target?: 'app' | 'location'): Promise<void>;
}

export function PermissionsApi(deps: { native: BackgroundGeolocationNative }): PermissionsApi {
  const { native } = deps;
  return {
    async check() {
      return (await native.checkPermissions()).location;
    },
    async request() {
      return (await native.requestPermissions()).location;
    },
    requestBackground() {
      return native.requestBackgroundLocationPermission();
    },
    requestActivity() {
      return native.requestActivityRecognitionPermission();
    },
    requestNotifications() {
      return native.requestNotificationPermission();
    },
    openSettings(target = 'app') {
      return target === 'location' ? native.showLocationSettings() : native.showAppSettings();
    },
  };
}
