// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · the composed facade (Fase 2), functional style.
// `BackgroundGeolocation({ native })` wires the flat native proxy into clean, disposable,
// composed sub-APIs — dependencies are INJECTED (native + siblings) so every unit is mockable
// in isolation, with no `new` and no `this`. This is what apps import.

import type { BaseConfig } from '../definitions/config';
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

export interface BackgroundGeolocation {
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

  /** Set the shared base config. Alias for `bg.config.configure()`. Observe with `bg.config.on()`. */
  configure(base: BaseConfig): Promise<void>;
  /** Static platform capabilities (memoized; rejections are not cached). */
  capabilities(): Promise<Capabilities>;
  /** Feature-detect a single capability. */
  supports<K extends keyof Capabilities>(capability: K): Promise<Capabilities[K]>;
  /** Assert a capability is available, else throw CapabilityError. */
  require(capability: BooleanCapability): Promise<void>;
  /** Subscribe to a global / lifecycle event (domain events live on their sub-API). */
  on<E extends GeolocationEventName>(event: E, listener: GeolocationEventListener<E>): Subscription;
  /** Trigger an SOS — emits the `sos` event with the latest location plus `payload`. */
  sos(payload?: Record<string, unknown>): Promise<void>;
  /** Remove every listener registered on the plugin. */
  removeAllListeners(): Promise<void>;
}

export function BackgroundGeolocation(deps: { native: BackgroundGeolocationNative }): BackgroundGeolocation {
  const { native } = deps;

  let capsCache: Promise<Capabilities> | undefined;
  const capabilities = (): Promise<Capabilities> => {
    if (capsCache === undefined) {
      capsCache = native
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
          capsCache = undefined;
          throw error;
        });
    }
    return capsCache;
  };

  const config = ConfigApi({ native });
  const tracking = TrackingApi({ native, config });
  const locations = LocationsApi({ native });
  const geofences = GeofencesApi({ native });
  const sync = SyncApi({ native });
  const recordings = RecordingsApi({ native });
  const permissions = PermissionsApi({ native });
  const diagnostics = DiagnosticsApi({ native, capabilities });
  const driver = DriverApi({ native, capabilities });
  const logs = LogsApi({ native });
  const platform = PlatformApi({ native });

  return {
    config,
    tracking,
    locations,
    geofences,
    sync,
    recordings,
    permissions,
    diagnostics,
    driver,
    logs,
    platform,

    configure(base: BaseConfig): Promise<void> {
      return config.configure(base);
    },

    capabilities,

    async supports<K extends keyof Capabilities>(capability: K): Promise<Capabilities[K]> {
      return (await capabilities())[capability];
    },

    async require(capability: BooleanCapability): Promise<void> {
      await ensureCapability(capabilities, capability);
    },

    on<E extends GeolocationEventName>(event: E, listener: GeolocationEventListener<E>): Subscription {
      // Sticky: a new `authorization` subscriber gets the current state immediately. The replay is
      // gated inside subscribe() (skipped if removed / beaten by a live event) and its rejection is
      // swallowed there, so it never leaks an unhandled promise rejection.
      const replay =
        event === 'authorization'
          ? (): Promise<{ status: unknown }> => native.checkStatus().then((s) => ({ status: s.authorization }))
          : undefined;
      return listen(native, event, listener as (payload: unknown) => void, replay);
    },

    sos(payload?: Record<string, unknown>): Promise<void> {
      return native.triggerSOS(payload);
    },

    removeAllListeners(): Promise<void> {
      return native.removeAllListeners();
    },
  };
}
