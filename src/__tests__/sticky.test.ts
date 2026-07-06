// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · sticky events (§08) — a new subscriber gets the last value immediately.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { Location, ServiceStatus } from '../definitions/values';
import { BackgroundGeolocation } from '../sdk/background-geolocation';
import { LocationsApi } from '../sdk/locations';

const FIX: Location = {
  latitude: 1,
  longitude: 2,
  accuracy: 5,
  time: 1000,
} as Location;

/** A no-op listener handle for addListener. */
const handle = { remove: async (): Promise<void> => {} };

function tick(): Promise<void> {
  return new Promise((r) => setTimeout(r, 0));
}

describe('sticky location', () => {
  it('replays the last known fix to a new subscriber', async () => {
    const native = {
      addListener: async () => handle,
      getLastLocation: async (): Promise<Location | null> => FIX,
    } as unknown as BackgroundGeolocationNative;

    const seen: Location[] = [];
    LocationsApi({ native }).on((loc) => seen.push(loc));
    await tick();

    assert.equal(seen.length, 1);
    assert.deepEqual(seen[0], FIX);
  });

  it('replays nothing when there is no last fix yet', async () => {
    const native = {
      addListener: async () => handle,
      getLastLocation: async (): Promise<Location | null> => null,
    } as unknown as BackgroundGeolocationNative;

    const seen: Location[] = [];
    LocationsApi({ native }).on((loc) => seen.push(loc));
    await tick();

    assert.equal(seen.length, 0);
  });

  it('does NOT replay to a subscriber removed before the replay resolves', async () => {
    const native = {
      addListener: async () => handle,
      getLastLocation: async (): Promise<Location | null> => FIX,
    } as unknown as BackgroundGeolocationNative;

    const seen: Location[] = [];
    const sub = LocationsApi({ native }).on((loc) => seen.push(loc));
    sub.remove(); // synchronous removal, before getLastLocation() settles
    await tick();

    assert.equal(seen.length, 0, 'replay must be gated on removal');
  });

  it('does NOT replay a stale fix once a live fix already arrived', async () => {
    let liveCb: ((loc: Location) => void) | undefined;
    const LIVE: Location = { ...FIX, latitude: 99, time: 2000 };
    const native = {
      addListener: async (_name: string, cb: (loc: Location) => void) => {
        liveCb = cb;
        return handle;
      },
      // resolves AFTER the live fix below
      getLastLocation: async (): Promise<Location | null> => {
        await tick();
        return FIX;
      },
    } as unknown as BackgroundGeolocationNative;

    const seen: Location[] = [];
    LocationsApi({ native }).on((loc) => seen.push(loc));
    // liveCb is wired synchronously by on(); fire a fresh live fix before the (awaited) replay settles.
    liveCb?.(LIVE);
    await tick();
    await tick();

    assert.deepEqual(seen, [LIVE], 'the older replayed fix must be skipped after a live one');
  });

  it('swallows a native rejection from the replay (no unhandled rejection)', async () => {
    const native = {
      addListener: async () => handle,
      getLastLocation: async (): Promise<Location | null> => {
        throw new Error('not implemented');
      },
    } as unknown as BackgroundGeolocationNative;

    const seen: Location[] = [];
    assert.doesNotThrow(() => LocationsApi({ native }).on((loc) => seen.push(loc)));
    await tick();
    assert.equal(seen.length, 0);
  });
});

describe('sticky authorization', () => {
  it('replays the current authorization to a new bg.on subscriber', async () => {
    const status: ServiceStatus = { isRunning: false, locationServicesEnabled: true, authorization: 'authorized' };
    const native = {
      addListener: async () => handle,
      checkStatus: async (): Promise<ServiceStatus> => status,
    } as unknown as BackgroundGeolocationNative;

    const seen: { status: string }[] = [];
    BackgroundGeolocation({ native }).on('authorization', (e) => seen.push(e));
    await tick();

    assert.equal(seen.length, 1);
    assert.deepEqual(seen[0], { status: 'authorized' });
  });

  it('does not replay for a non-sticky event', async () => {
    const native = {
      addListener: async () => handle,
      checkStatus: async (): Promise<ServiceStatus> => {
        throw new Error('checkStatus must not be called for non-sticky events');
      },
    } as unknown as BackgroundGeolocationNative;

    const seen: unknown[] = [];
    BackgroundGeolocation({ native }).on('start', () => seen.push(1));
    await tick();

    assert.equal(seen.length, 0);
  });
});
