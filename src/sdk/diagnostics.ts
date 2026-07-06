// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Diagnostics sub-API (Fase 2). OEM screens are capability-gated (Android).

import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { Capabilities, Diagnostics } from '../definitions/values';
import { ensureCapability } from './errors';

/** OEM battery / auto-start settings — gated behind `capabilities.oemSettings`. */
export interface OemApi {
  isIgnoringBatteryOptimizations(): Promise<boolean>;
  requestIgnoreBatteryOptimizations(): Promise<boolean>;
  openBatterySettings(): Promise<void>;
  openAutoStartSettings(): Promise<{ opened: boolean; manufacturer: string; screen: string }>;
  manufacturerHelp(): Promise<{ manufacturer: string; steps: string[] }>;
}

export function OemApi(deps: {
  native: BackgroundGeolocationNative;
  capabilities: () => Promise<Capabilities>;
}): OemApi {
  const { native, capabilities } = deps;
  const gate = (): Promise<void> => ensureCapability(capabilities, 'oemSettings');
  return {
    async isIgnoringBatteryOptimizations() {
      await gate();
      return (await native.isIgnoringBatteryOptimizations()).whitelisted;
    },
    async requestIgnoreBatteryOptimizations() {
      await gate();
      return (await native.requestIgnoreBatteryOptimizations()).whitelisted;
    },
    async openBatterySettings() {
      await gate();
      return native.openBatterySettings();
    },
    async openAutoStartSettings() {
      await gate();
      return native.openAutoStartSettings();
    },
    async manufacturerHelp() {
      await gate();
      return native.getManufacturerHelp();
    },
  };
}

export interface DiagnosticsApi {
  readonly oem: OemApi;
  /** Extended diagnostics (permissions, battery, OEM, iOS flags). */
  report(): Promise<Diagnostics>;
  /** The native plugin version. */
  version(): Promise<string>;
  /** Why/when the native service was last killed and auto-restarted (Android). */
  killReason(): Promise<{ reason: string | null; timestamp: number | null }>;
}

export function DiagnosticsApi(deps: {
  native: BackgroundGeolocationNative;
  capabilities: () => Promise<Capabilities>;
}): DiagnosticsApi {
  const { native, capabilities } = deps;
  const oem = OemApi({ native, capabilities });
  return {
    oem,
    report() {
      return native.getDiagnostics();
    },
    async version() {
      return (await native.getPluginVersion()).version;
    },
    killReason() {
      return native.getBackgroundKillReason();
    },
  };
}
