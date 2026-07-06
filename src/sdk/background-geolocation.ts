// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · the composed facade (Fase 2).
// Wraps the flat native proxy in clean, disposable, composed sub-APIs. Pure TS, no
// runtime cost beyond the wrappers. This is what apps import.

import type { GeolocationEventListener, GeolocationEventName } from '../definitions/events';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { Capabilities } from '../definitions/values';

import { ConfigApi } from './config-api';
import { DiagnosticsApi } from './diagnostics';
import { DriverApi } from './driver';
import { type BooleanCapability, ensureCapability } from './errors';
import { GeofencesApi } from './geofences';
import { LocationsApi } from './locations';
import { LogsApi } from './logs';
import { PermissionsApi } from './permissions';
import { PlatformApi } from './platform';
import { RecordingsApi } from './recordings';
import { listen, type Subscription } from './stream';
import { SyncApi } from './sync';
import { TrackingApi } from './tracking';

/** The native wire-contract version this facade speaks; native `contractVersion` must be ≥ this. */
const CONTRACT_VERSION = 3;

export class BackgroundGeolocation {
  readonly config: ConfigApi;
  readonly tracking: TrackingApi;
  readonly locations: LocationsApi;
  readonly geofences: GeofencesApi;
  readonly sync: SyncApi;
  readonly recordings: RecordingsApi;
  readonly permissions: PermissionsApi;
  readonly diagnostics: DiagnosticsApi;
  readonly driver: DriverApi;
  readonly logs: LogsApi;
  readonly platform: PlatformApi;

  private capsCache?: Promise<Capabilities>;

  constructor(private readonly native: BackgroundGeolocationNative) {
    const caps = (): Promise<Capabilities> => this.capabilities();
    this.config = new ConfigApi(native);
    this.tracking = new TrackingApi(native, this.config);
    this.locations = new LocationsApi(native);
    this.geofences = new GeofencesApi(native);
    this.sync = new SyncApi(native);
    this.recordings = new RecordingsApi(native);
    this.permissions = new PermissionsApi(native);
    this.diagnostics = new DiagnosticsApi(native, caps);
    this.driver = new DriverApi(native, caps);
    this.logs = new LogsApi(native);
    this.platform = new PlatformApi(native);
  }

  /**
   * Set the shared, plugin-level base config. Convenience alias for `bg.config.configure()`
   * (and `bg.tracking.configure()`). Observe changes with `bg.config.on()`.
   */
  configure(base: Parameters<ConfigApi['configure']>[0]): Promise<void> {
    return this.config.configure(base);
  }

  /**
   * Static platform capabilities (the `misa` register) — memoized after the first
   * call. Rejections are NOT cached, so a transient failure can be retried.
   */
  capabilities(): Promise<Capabilities> {
    if (this.capsCache === undefined) {
      this.capsCache = this.native
        .getCapabilities()
        .then((caps) => {
          // Turn a stale-native `cap sync` (renamed events/methods silently no-op) into a signal.
          if ((caps.contractVersion ?? 0) < CONTRACT_VERSION) {
            // eslint-disable-next-line no-console
            console.warn(
              `[BackgroundGeolocation] native contract v${caps.contractVersion ?? 0} is older than ` +
                `this JS facade (v${CONTRACT_VERSION}) — run 'npx cap sync' and rebuild. Some events/methods may not work.`,
            );
          }
          return caps;
        })
        .catch((error: unknown) => {
          this.capsCache = undefined;
          throw error;
        });
    }
    return this.capsCache;
  }

  /** Feature-detect a single capability (e.g. `await bg.supports('driverIntelligence')`). */
  async supports<K extends keyof Capabilities>(capability: K): Promise<Capabilities[K]> {
    return (await this.capabilities())[capability];
  }

  /**
   * Assert a capability is available, else throw {@link CapabilityError}. The explicit
   * guard form of {@link supports} — reads as a precondition before a run of gated calls:
   * `await bg.require('driverIntelligence'); const s = await bg.driver.lastTripScore();`
   */
  async require(capability: BooleanCapability): Promise<void> {
    await ensureCapability(() => this.capabilities(), capability);
  }

  /**
   * Subscribe to a global / lifecycle event: `start`, `stop`, `foreground`,
   * `background`, `error`, `authorization`, `heartbeat`, `providerChange`,
   * `serviceRestarted`, `iosFallbackActivated`, `abortRequested`, `httpAuthorization`.
   * Domain events live on their sub-API (`locations.on`, `geofences.on`, …).
   */
  on<E extends GeolocationEventName>(event: E, listener: GeolocationEventListener<E>): Subscription {
    // Sticky: a new `authorization` subscriber gets the current state immediately. The replay
    // is gated inside subscribe() (skipped if removed / beaten by a live event) and its
    // rejection is swallowed there, so it never leaks an unhandled promise rejection.
    const replay =
      event === 'authorization'
        ? (): Promise<{ status: unknown }> => this.native.checkStatus().then((s) => ({ status: s.authorization }))
        : undefined;
    return listen(this.native, event, listener as (payload: unknown) => void, replay);
  }

  /** Trigger an SOS — emits the `sos` event with the latest location plus `payload`. */
  sos(payload?: Record<string, unknown>): Promise<void> {
    return this.native.triggerSOS(payload);
  }

  /** Remove every listener registered on the plugin. */
  removeAllListeners(): Promise<void> {
    return this.native.removeAllListeners();
  }
}
