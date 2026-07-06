// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Tracking sub-API (Fase 2).
// The shared base config lives in ConfigApi (bg.config); tracking delegates configure() to it
// and resolves the session cascade on start().

import type { BaseConfig, StartOverride } from '../definitions/config';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { ServiceStatus } from '../definitions/values';
import type { ConfigApi } from './config-api';
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
  constructor(
    private readonly native: BackgroundGeolocationNative,
    private readonly config: ConfigApi,
  ) {}

  /**
   * Set the shared, plugin-level base config — the tier every feature inherits. Call once at
   * startup; calling again patches (deep-merge) the existing base. Delegates to `bg.config`
   * (reload-safe + observable). Equivalent to `bg.config.configure(base)`.
   */
  async configure(base: BaseConfig): Promise<void> {
    await this.config.configure(base);
  }

  /**
   * Start tracking. An optional session override composes over the base for the lifetime of
   * this run (scope: session) — it is NOT persisted into the base, so a later plain start()
   * reverts it.
   */
  async start(override?: StartOverride): Promise<TrackingSession> {
    if (override !== undefined) {
      await this.native.configure(this.config.wireForStart(override));
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
