// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Tracking sub-API (Fase 2).
// Owns the plugin-level base config and resolves the cascade on start().

import type { BaseConfig, StartOverride } from '../definitions/config';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { ServiceStatus } from '../definitions/values';
import { mergeConfig, toFlatConfig } from './config-mapper';
import './dispose'; // ensure Symbol.asyncDispose exists at runtime

/**
 * A running tracking session. `stop()` ends it. `[Symbol.asyncDispose]` (Fase 4) lets
 * `await using s = await bg.tracking.start()` auto-stop at scope end — optional and
 * lax: it just calls stop(), which is always there.
 */
export interface TrackingSession {
  stop(): Promise<void>;
  [Symbol.asyncDispose](): Promise<void>;
}

export class TrackingApi {
  private base: BaseConfig = {};

  constructor(private readonly native: BackgroundGeolocationNative) {}

  /**
   * Set the shared, plugin-level base config — the tier every feature inherits. Call
   * once at startup; calling again patches (deep-merge) the existing base.
   *
   * The native side REPLACES its config with the resolved base on every call (so a field
   * you drop reverts to default — one source of truth). This base lives in memory and does
   * NOT survive a page reload / process restart, so **re-send the full base on each app
   * start**. A partial `configure()` after a restart resolves against an empty base, not the
   * previously-persisted config — it would reset omitted fields (e.g. `transport.baseUrl`).
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
    const session: TrackingSession = {
      stop: (): Promise<void> => native.stop(),
      [Symbol.asyncDispose]: (): Promise<void> => session.stop(),
    };
    return session;
  }

  stop(): Promise<void> {
    return this.native.stop();
  }

  status(): Promise<ServiceStatus> {
    return this.native.checkStatus();
  }
}
