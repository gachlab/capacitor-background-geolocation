// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · capability gating on the facade (Fase 3) — supports() / require() / memo.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { Capabilities } from '../definitions/values';
import { BackgroundGeolocation } from '../sdk/background-geolocation';
import { CapabilityError } from '../sdk/errors';

function fakeNative(caps: Partial<Capabilities>, box = { calls: 0 }): BackgroundGeolocationNative {
  const full: Capabilities = {
    platform: 'android',
    contractVersion: 3,
    backgroundTracking: true,
    activityRecognition: true,
    geofencing: true,
    maxGeofences: 100,
    sensorFusion: true,
    driverIntelligence: false,
    oemSettings: false,
    ...caps,
  };
  return {
    getCapabilities: async (): Promise<Capabilities> => {
      box.calls++;
      return full;
    },
  } as unknown as BackgroundGeolocationNative;
}

describe('facade capability gating', () => {
  it('supports() reports the underlying capability', async () => {
    const bg = BackgroundGeolocation({ native: fakeNative({ driverIntelligence: true }) });
    assert.equal(await bg.supports('driverIntelligence'), true);
    assert.equal(await bg.supports('oemSettings'), false);
  });

  it('require() resolves when the capability is present', async () => {
    const bg = BackgroundGeolocation({ native: fakeNative({ driverIntelligence: true }) });
    await assert.doesNotReject(() => bg.require('driverIntelligence'));
  });

  it('require() throws CapabilityError when the capability is absent', async () => {
    const bg = BackgroundGeolocation({ native: fakeNative({ driverIntelligence: false }) });
    await assert.rejects(
      () => bg.require('driverIntelligence'),
      (err: unknown) => err instanceof CapabilityError && err.capability === 'driverIntelligence',
    );
  });

  it('capabilities() is memoized across supports()/require() calls', async () => {
    const box = { calls: 0 };
    const bg = BackgroundGeolocation({ native: fakeNative({ driverIntelligence: true }, box) });
    await bg.supports('geofencing');
    await bg.require('driverIntelligence');
    await bg.capabilities();
    assert.equal(box.calls, 1, 'the static capability register is fetched once');
  });
});
