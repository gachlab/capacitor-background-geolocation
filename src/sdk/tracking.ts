// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Tracking sub-API (Fase 2).
// Owns the plugin-level base config and resolves the cascade on start().

import type { BaseConfig, StartOverride } from '../definitions/config';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { ServiceStatus } from '../definitions/values';
import { mergeConfig, toFlatConfig } from './config-mapper';

/**
 * A running tracking session. `stop()` ends it. `Symbol.asyncDispose` (so
 * `await using` auto-stops at scope end) is added with the ES2022 bump (Fase 4).
 */
export interface TrackingSession {
  stop(): Promise<void>;
}

export class TrackingApi {
  private base: BaseConfig = {};

  constructor(private readonly native: BackgroundGeolocationNative) {}

  /**
   * Set the shared, plugin-level base config — the tier every feature inherits. Call
   * once at startup; calling again patches (deep-merge) the existing base.
   */
  async configure(base: BaseConfig): Promise<void> {
    this.base = mergeConfig(this.base, base);
    await this.native.configure(toFlatConfig(this.base));
  }

  /**
   * Start tracking. An optional session override composes over the base for the
   * lifetime of this run (scope: session).
   */
  async start(override?: StartOverride): Promise<TrackingSession> {
    if (override !== undefined) {
      await this.native.configure(toFlatConfig(mergeConfig(this.base, override)));
    }
    await this.native.start();
    const native = this.native;
    return { stop: (): Promise<void> => native.stop() };
  }

  stop(): Promise<void> {
    return this.native.stop();
  }

  status(): Promise<ServiceStatus> {
    return this.native.checkStatus();
  }
}
