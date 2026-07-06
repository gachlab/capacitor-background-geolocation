// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · reactive config (bg.config). Owns the plugin-level base config: the write path
// (configure), a reload-safe rehydration, and an observable read path (current/on).
//
// The base is round-tripped through the native's persisted config as an opaque JSON blob
// (baseConfigJson), so it survives a page reload / process restart — the facade rehydrates
// it instead of losing it and letting a partial reconfigure reset persisted fields. Change
// notification is facade-local: config only changes via this API in the single consumer, so
// no native `configChanged` event is needed.

import type { BaseConfig, StartOverride } from '../definitions/config';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { NativeConfig } from '../definitions/wire';
import { mergeConfig, toFlatConfig } from './config-mapper';
import type { Subscription } from './stream';

/** Wire key carrying the clean base as an opaque JSON blob for reload rehydration. */
const BASE_CONFIG_KEY = 'baseConfigJson';

export class ConfigApi {
  private base: BaseConfig = {};
  private rehydrated = false;
  private readonly listeners = new Set<(config: BaseConfig) => void>();

  constructor(private readonly native: BackgroundGeolocationNative) {}

  /**
   * Merge a patch into the shared base, persist it, and notify subscribers. Safe across reload:
   * the base is round-tripped through the native, so a partial patch after a restart merges onto
   * the previously-persisted base rather than resetting omitted fields.
   */
  async configure(patch: BaseConfig): Promise<void> {
    await this.ensureRehydrated();
    this.base = mergeConfig(this.base, patch);
    await this.native.configure(this.wireFor(this.base));
    this.emit();
  }

  /** The current effective base config (a copy — mutating it does not change the plugin). */
  async current(): Promise<BaseConfig> {
    await this.ensureRehydrated();
    return structuredClone(this.base);
  }

  /**
   * Subscribe to base-config changes. Sticky: the current config is delivered immediately on
   * subscribe. Session overrides (`tracking.start(override)`) are transient and NOT emitted here.
   */
  on(listener: (config: BaseConfig) => void): Subscription<BaseConfig> {
    this.listeners.add(listener);
    // Sticky replay of the current config (skipped if removed before it resolves).
    void this.current().then((config) => {
      if (this.listeners.has(listener)) listener(config);
    });
    const remove = (): void => {
      this.listeners.delete(listener);
    };
    return { remove, [Symbol.dispose]: remove };
  }

  /** Resolved wire for `tracking.start(override)` — tags the PERSISTENT base (not the override). */
  wireForStart(override: StartOverride): NativeConfig {
    return this.wireFor(mergeConfig(this.base, override), this.base);
  }

  private emit(): void {
    const snapshot = structuredClone(this.base);
    for (const listener of [...this.listeners]) listener(snapshot);
  }

  /** Resolved config → wire, tagging `base` (not session overrides) so a reload can rehydrate it. */
  private wireFor(resolved: BaseConfig, base: BaseConfig = resolved): NativeConfig {
    const wire = toFlatConfig(resolved) as NativeConfig;
    wire[BASE_CONFIG_KEY] = JSON.stringify(base);
    return wire;
  }

  /** Once per session: seed `base` from the native's persisted blob so a reload doesn't lose it. */
  private async ensureRehydrated(): Promise<void> {
    if (this.rehydrated) return;
    this.rehydrated = true;
    if (Object.keys(this.base).length > 0) return; // already configured this session
    try {
      const wire = (await this.native.getConfig()) as NativeConfig;
      const blob = wire[BASE_CONFIG_KEY];
      if (typeof blob === 'string') this.base = JSON.parse(blob) as BaseConfig;
    } catch {
      /* first run / no persisted config — start from an empty base */
    }
  }
}
