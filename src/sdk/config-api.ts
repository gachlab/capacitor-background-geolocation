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
import { mergeConfig, safeClone, toFlatConfig } from './config-mapper';
import { stickyReplay, type Subscription } from './stream';

/** Wire key carrying the clean base as an opaque JSON blob for reload rehydration. */
const BASE_CONFIG_KEY = 'baseConfigJson';

export interface ConfigApi {
  /** Merge a patch into the shared base, persist it, and notify subscribers. Reload-safe. */
  configure(patch: BaseConfig): Promise<void>;
  /** The current effective base config (a copy). */
  current(): Promise<BaseConfig>;
  /** Subscribe to base-config changes; the current config is delivered immediately (sticky). */
  on(listener: (config: BaseConfig) => void): Subscription<BaseConfig>;
  /**
   * Resolved wire for `tracking.start(override)`, rehydrating the persisted base FIRST so a
   * start() right after a reload can't clobber the saved base with an empty one. Tags the
   * PERSISTENT base (not the override) into `baseConfigJson`.
   */
  resolveStartWire(override?: StartOverride): Promise<NativeConfig>;
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
    const snapshot = safeClone(base);
    for (const listener of [...listeners]) listener(snapshot);
  };

  const current = async (): Promise<BaseConfig> => {
    await ensureRehydrated();
    return safeClone(base);
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
      // Wrap with the shared sticky primitive: it delivers current() once, but suppresses that
      // replay if a live configure() emit beat it (the double-delivery race) or the listener was
      // removed first. `wrapped` is what emit() calls, so a live emit marks the replay as beaten.
      const wrapped: (config: BaseConfig) => void = stickyReplay(listener, current, () => listeners.has(wrapped));
      listeners.add(wrapped);
      const remove = (): void => {
        listeners.delete(wrapped);
      };
      return { remove, [Symbol.dispose]: remove };
    },

    async resolveStartWire(override?: StartOverride): Promise<NativeConfig> {
      await ensureRehydrated(); // never resolve a start from an un-rehydrated (empty) base
      return wireFor(mergeConfig(base, override), base);
    },
  };
}
