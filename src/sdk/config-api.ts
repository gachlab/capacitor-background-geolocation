// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · reactive config (bg.config). Owns the plugin-level base config: the write path
// (configure), a reload-safe rehydration, and an observable read path (current/on).
//
// Functional style: `createConfigApi(native)` closes over the state and returns a ConfigApi.
// The base is round-tripped through the native's persisted config as an opaque JSON blob
// (baseConfigJson) so it survives a page reload / process restart. Change notification is
// facade-local: config only changes via this API in the single consumer.

import type { BaseConfig, StartOverride } from '../definitions/config';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { NativeConfig } from '../definitions/wire';
import { mergeConfig, toFlatConfig } from './config-mapper';
import type { Subscription } from './stream';

/** Wire key carrying the clean base as an opaque JSON blob for reload rehydration. */
const BASE_CONFIG_KEY = 'baseConfigJson';

export interface ConfigApi {
  /** Merge a patch into the shared base, persist it, and notify subscribers. Reload-safe. */
  configure(patch: BaseConfig): Promise<void>;
  /** The current effective base config (a copy). */
  current(): Promise<BaseConfig>;
  /** Subscribe to base-config changes; the current config is delivered immediately (sticky). */
  on(listener: (config: BaseConfig) => void): Subscription<BaseConfig>;
  /** Resolved wire for `tracking.start(override)` — tags the PERSISTENT base (not the override). */
  wireForStart(override: StartOverride): NativeConfig;
}

export function ConfigApi(deps: { native: BackgroundGeolocationNative }): ConfigApi {
  const { native } = deps;
  let base: BaseConfig = {};
  let rehydrating: Promise<void> | undefined;
  const listeners = new Set<(config: BaseConfig) => void>();

  /** Resolved config → wire, tagging `base` (not session overrides) so a reload can rehydrate it. */
  const wireFor = (resolved: BaseConfig, persistent: BaseConfig = resolved): NativeConfig => {
    const wire = toFlatConfig(resolved) as NativeConfig;
    wire[BASE_CONFIG_KEY] = JSON.stringify(persistent);
    return wire;
  };

  /**
   * Once per session: seed `base` from the native's persisted blob so a reload doesn't lose it.
   * A SHARED promise (not a boolean flag) so a concurrent configure() awaits the SAME rehydration
   * instead of racing it — otherwise a late getConfig() could clobber a base already mutated.
   */
  const ensureRehydrated = (): Promise<void> =>
    (rehydrating ??= (async () => {
      if (Object.keys(base).length > 0) return; // already configured this session
      try {
        const wire = (await native.getConfig()) as NativeConfig;
        const blob = wire[BASE_CONFIG_KEY];
        if (typeof blob === 'string') base = JSON.parse(blob) as BaseConfig;
      } catch {
        /* first run / no persisted config — start from an empty base */
      }
    })());

  const emit = (): void => {
    const snapshot = structuredClone(base);
    for (const listener of [...listeners]) listener(snapshot);
  };

  const current = async (): Promise<BaseConfig> => {
    await ensureRehydrated();
    return structuredClone(base);
  };

  return {
    async configure(patch: BaseConfig): Promise<void> {
      await ensureRehydrated();
      base = mergeConfig(base, patch);
      await native.configure(wireFor(base));
      emit();
    },

    current,

    on(listener: (config: BaseConfig) => void): Subscription<BaseConfig> {
      listeners.add(listener);
      // Sticky replay of the current config (skipped if removed before it resolves).
      void current().then((config) => {
        if (listeners.has(listener)) listener(config);
      });
      const remove = (): void => {
        listeners.delete(listener);
      };
      return { remove, [Symbol.dispose]: remove };
    },

    wireForStart(override: StartOverride): NativeConfig {
      return wireFor(mergeConfig(base, override), base);
    },
  };
}
