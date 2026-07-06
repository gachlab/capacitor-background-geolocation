// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Tracking sub-API (Fase 2). Functional: TrackingApi({ native, config }) → TrackingApi.
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

export interface TrackingApi {
  /**
   * Set the shared, plugin-level base config — the tier every feature inherits. Delegates to
   * `bg.config` (reload-safe + observable). Equivalent to `bg.config.configure(base)`.
   */
  configure(base: BaseConfig): Promise<void>;
  /**
   * Start tracking. An optional session override composes over the base for the lifetime of
   * this run (scope: session) — NOT persisted into the base, so a later plain start() reverts it.
   */
  start(override?: StartOverride): Promise<TrackingSession>;
  stop(): Promise<void>;
  status(): Promise<ServiceStatus>;
}

export function TrackingApi(deps: { native: BackgroundGeolocationNative; config: ConfigApi }): TrackingApi {
  const { native, config } = deps;
  return {
    async configure(base: BaseConfig): Promise<void> {
      await config.configure(base);
    },

    async start(override?: StartOverride): Promise<TrackingSession> {
      // Always resolve and apply the session wire (base ⊕ override) — so a plain start()
      // reverts a prior session override (as documented), and a start() right after a reload
      // rehydrates the persisted base before writing it (never clobbers it with an empty base).
      await native.configure(await config.resolveStartWire(override));
      await native.start();
      const session: TrackingSession = {
        stop: (): Promise<void> => native.stop(),
        [Symbol.asyncDispose]: (): Promise<void> => session.stop(),
      };
      return session;
    },

    stop(): Promise<void> {
      return native.stop();
    },

    status(): Promise<ServiceStatus> {
      return native.checkStatus();
    },
  };
}
