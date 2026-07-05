// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Diagnostics sub-API (Fase 2). OEM screens are capability-gated (Android).

import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { Capabilities, Diagnostics } from '../definitions/values';
import { ensureCapability } from './errors';

/** OEM battery / auto-start settings — gated behind `capabilities.oemSettings`. */
export class OemApi {
  constructor(
    private readonly native: BackgroundGeolocationNative,
    private readonly caps: () => Promise<Capabilities>,
  ) {}

  private gate(): Promise<void> {
    return ensureCapability(this.caps, 'oemSettings');
  }

  async isIgnoringBatteryOptimizations(): Promise<boolean> {
    await this.gate();
    return (await this.native.isIgnoringBatteryOptimizations()).whitelisted;
  }

  async requestIgnoreBatteryOptimizations(): Promise<boolean> {
    await this.gate();
    return (await this.native.requestIgnoreBatteryOptimizations()).whitelisted;
  }

  async openBatterySettings(): Promise<void> {
    await this.gate();
    return this.native.openBatterySettings();
  }

  async openAutoStartSettings(): Promise<{ opened: boolean; manufacturer: string; screen: string }> {
    await this.gate();
    return this.native.openAutoStartSettings();
  }

  async manufacturerHelp(): Promise<{ manufacturer: string; steps: string[] }> {
    await this.gate();
    return this.native.getManufacturerHelp();
  }
}

export class DiagnosticsApi {
  readonly oem: OemApi;

  constructor(
    private readonly native: BackgroundGeolocationNative,
    caps: () => Promise<Capabilities>,
  ) {
    this.oem = new OemApi(native, caps);
  }

  /** Extended diagnostics (permissions, battery, OEM, iOS flags). */
  report(): Promise<Diagnostics> {
    return this.native.getDiagnostics();
  }

  /** The native plugin version. */
  async version(): Promise<string> {
    return (await this.native.getPluginVersion()).version;
  }

  /** Why/when the native service was last killed and auto-restarted (Android). */
  killReason(): Promise<{ reason: string | null; timestamp: number | null }> {
    return this.native.getBackgroundKillReason();
  }
}
