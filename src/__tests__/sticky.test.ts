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
    new LocationsApi(native).on((loc) => seen.push(loc));
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
    new LocationsApi(native).on((loc) => seen.push(loc));
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
    new BackgroundGeolocation(native).on('authorization', (e) => seen.push(e));
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
    new BackgroundGeolocation(native).on('start', () => seen.push(1));
    await tick();

    assert.equal(seen.length, 0);
  });
});
