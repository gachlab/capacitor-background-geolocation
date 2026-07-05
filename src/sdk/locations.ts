// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Locations sub-API (Fase 2) — the exemplar.
// Shows the three facade patterns: on() → Subscription, current() with AbortSignal,
// and query() consolidating the five overlapping native getters into one intent-
// revealing call. All adaptation lives here; the natives keep their existing methods.

import type { CurrentLocationOptions } from '../definitions/config';
import type { BackgroundGeolocationNative, NativeCurrentOptions } from '../definitions/roles';
import type { Accuracy, Location } from '../definitions/values';
import { rejectOnAbort, subscribe, type Subscription } from './stream';

/** Which store `query()` reads. Sessions live in `bg.recordings`, not here. */
export interface LocationQuery {
  /** @default 'all' */
  scope?: 'all' | 'valid' | 'stationary';
  /** Delete the returned rows after reading (replaces the `…AndDelete` native getter). */
  consume?: boolean;
}

/** A one-shot only needs to know whether to ask the OS for its most accurate fix. */
const HIGH_ACCURACY: Record<Accuracy, boolean> = {
  high: true,
  medium: false,
  low: false,
  passive: false,
};

export class LocationsApi {
  constructor(private readonly native: BackgroundGeolocationNative) {}

  /**
   * Subscribe to live fixes. Multi-subscriber and removable — the honest primitive
   * for a hot GPS stream.
   */
  on(listener: (location: Location) => void): Subscription {
    return subscribe<Location>((cb) => this.native.addListener('location', cb), listener);
  }

  /**
   * One-shot fix. `signal` cancels the caller's wait (it does NOT stop the native GPS
   * work already in flight — honest about what AbortSignal can and can't do here).
   */
  async current(options: CurrentLocationOptions = {}): Promise<Location> {
    const nativeOptions: NativeCurrentOptions = {
      timeout: options.timeout,
      maximumAge: options.maximumAge,
      enableHighAccuracy: options.accuracy === undefined ? undefined : HIGH_ACCURACY[options.accuracy],
    };
    const request = this.native.getCurrentLocation(nativeOptions);
    if (options.signal === undefined) {
      return request;
    }
    return Promise.race([request, rejectOnAbort(options.signal)]);
  }

  /**
   * Read stored locations. One intent-revealing call replaces getLocations,
   * getValidLocations, getValidLocationsAndDelete and getStationaryLocation — reading
   * and deleting are no longer fused into a method name.
   */
  async query(query: LocationQuery = {}): Promise<Location[]> {
    const scope = query.scope ?? 'all';
    if (scope === 'stationary') {
      const stationary = await this.native.getStationaryLocation();
      return stationary === null ? [] : [stationary];
    }
    if (scope === 'valid') {
      const result = query.consume
        ? await this.native.getValidLocationsAndDelete()
        : await this.native.getValidLocations();
      return result.locations;
    }
    return (await this.native.getLocations()).locations;
  }

  /** Delete one stored location by DB id. */
  delete(locationId: number): Promise<void> {
    return this.native.deleteLocation({ locationId });
  }

  /** Delete every stored location. */
  clear(): Promise<void> {
    return this.native.deleteAllLocations();
  }
}
