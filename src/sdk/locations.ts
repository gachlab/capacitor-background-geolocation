// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Locations sub-API (Fase 2) — the exemplar.
// Facade patterns: on() → typed Subscription, current() with an AbortSignal that
// genuinely STOPS the native GPS one-shot, and named/precisely-typed read methods
// (all / pending / stationary) instead of a stringly-typed query(). All adaptation
// lives here; the natives keep their existing methods.

import type { CurrentLocationOptions } from '../definitions/config';
import type { BackgroundGeolocationNative, NativeCurrentOptions } from '../definitions/roles';
import type { Accuracy, Location, StationaryLocation } from '../definitions/values';
import { abortError, rejectOnAbort, subscribe, type Subscription } from './stream';

/** Options for reading the pending (not-yet-uploaded) queue. */
export interface PendingQuery {
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
  private requestSeq = 0; // monotonic id source for correlating current() with its cancel

  constructor(private readonly native: BackgroundGeolocationNative) {}

  /**
   * Subscribe to live fixes. Multi-subscriber and removable — the honest primitive
   * for a hot GPS stream. Returns a `Subscription<Location>`.
   */
  on(listener: (location: Location) => void): Subscription<Location> {
    // Sticky: replay the last known fix to the new subscriber (if any), so a late subscriber
    // is not blind until the next GPS update. The replay is gated inside subscribe() — it is
    // skipped if the subscription was removed or a fresher live fix already arrived.
    return subscribe<Location>(
      (cb) => this.native.addListener('location', cb),
      listener,
      () => this.native.getLastLocation(),
    );
  }

  /**
   * One-shot fix. When `signal` fires, the caller's wait rejects with an `AbortError`
   * AND the native GPS one-shot is cancelled (via `cancelCurrentLocation`) — so the
   * signal actually stops the work, not just the promise.
   */
  async current(options: CurrentLocationOptions = {}): Promise<Location> {
    const nativeOptions: NativeCurrentOptions = {
      timeout: options.timeout,
      maximumAge: options.maximumAge,
      enableHighAccuracy: options.accuracy === undefined ? undefined : HIGH_ACCURACY[options.accuracy],
    };

    const signal = options.signal;
    if (signal === undefined) {
      return this.native.getCurrentLocation(nativeOptions);
    }
    if (signal.aborted) {
      throw abortError();
    }

    // Tag this one-shot so abort cancels exactly it — concurrent current() calls with
    // independent signals no longer cross-cancel.
    const requestId = `loc-${++this.requestSeq}`;
    const request = this.native.getCurrentLocation({ ...nativeOptions, requestId });
    const onAbort = (): void => {
      void this.native.cancelCurrentLocation({ requestId });
    };
    signal.addEventListener('abort', onAbort, { once: true });
    try {
      return await Promise.race([request, rejectOnAbort(signal)]);
    } finally {
      signal.removeEventListener('abort', onAbort);
    }
  }

  /** All stored locations. */
  async all(): Promise<Location[]> {
    return (await this.native.getLocations()).locations;
  }

  /**
   * Locations stored locally that have not yet been uploaded. Pass `{ consume: true }`
   * to delete them as they are read (read and delete stay explicit, not fused).
   */
  async pending(options: PendingQuery = {}): Promise<Location[]> {
    const result = options.consume
      ? await this.native.getValidLocationsAndDelete()
      : await this.native.getValidLocations();
    return result.locations;
  }

  /** The last stationary location, or `null`. Typed with its `radius`. */
  stationary(): Promise<StationaryLocation | null> {
    return this.native.getStationaryLocation();
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
