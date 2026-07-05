// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · the composed facade (Fase 2).
// Wraps the flat native proxy in clean, disposable, composed sub-APIs. Pure TS, no
// runtime cost beyond the wrappers. This is what apps import.

import type { GeolocationEventListener, GeolocationEventName } from '../definitions/events';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { Capabilities } from '../definitions/values';

import { DiagnosticsApi } from './diagnostics';
import { DriverApi } from './driver';
import { GeofencesApi } from './geofences';
import { LocationsApi } from './locations';
import { LogsApi } from './logs';
import { PermissionsApi } from './permissions';
import { PlatformApi } from './platform';
import { RecordingsApi } from './recordings';
import { listen, type Subscription } from './stream';
import { SyncApi } from './sync';
import { TrackingApi } from './tracking';

export class BackgroundGeolocation {
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
    this.tracking = new TrackingApi(native);
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
   * Static platform capabilities (the `misa` register) — memoized after the first
   * call. Rejections are NOT cached, so a transient failure can be retried.
   */
  capabilities(): Promise<Capabilities> {
    if (this.capsCache === undefined) {
      this.capsCache = this.native.getCapabilities().catch((error: unknown) => {
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
   * Subscribe to a global / lifecycle event: `start`, `stop`, `foreground`,
   * `background`, `error`, `authorization`, `heartbeat`, `providerChange`,
   * `serviceRestarted`, `iosFallbackActivated`, `abortRequested`, `httpAuthorization`.
   * Domain events live on their sub-API (`locations.on`, `geofences.on`, …).
   */
  on<E extends GeolocationEventName>(event: E, listener: GeolocationEventListener<E>): Subscription {
    return listen(this.native, event, listener as (payload: unknown) => void);
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
