// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · GeofencesApi — geofenceDefaults application + loiteringDelayMs↔loiteringDelay round-trip.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import type { BaseConfig } from '../definitions/config';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { Geofence } from '../definitions/values';
import type { ConfigApi } from '../sdk/config-api';
import { GeofencesApi } from '../sdk/geofences';

/** Fake native that records what add() sends and can echo a stored list back to list(). */
function fakeNative(stored: Record<string, unknown>[] = []): {
  native: BackgroundGeolocationNative;
  added: Record<string, unknown>[][];
} {
  const added: Record<string, unknown>[][] = [];
  const native = {
    addGeofences: async (o: { geofences: Geofence[] }): Promise<void> =>
      void added.push(o.geofences as unknown as Record<string, unknown>[]),
    getGeofences: async (): Promise<{ geofences: Geofence[] }> => ({ geofences: stored as unknown as Geofence[] }),
  } as unknown as BackgroundGeolocationNative;
  return { native, added };
}

/** Minimal ConfigApi stub exposing only current(), which is all geofences.add() reads. */
function fakeConfig(base: BaseConfig): ConfigApi {
  return { current: async (): Promise<BaseConfig> => base } as unknown as ConfigApi;
}

describe('GeofencesApi', () => {
  it('add() renames loiteringDelayMs → the native wire key loiteringDelay', async () => {
    const { native, added } = fakeNative();
    const api = GeofencesApi({ native, config: fakeConfig({}) });
    await api.add([{ id: 'a', latitude: 1, longitude: 2, loiteringDelayMs: 5000 }]);
    const sent = added[0][0];
    assert.equal(sent.loiteringDelay, 5000, 'wire carries loiteringDelay');
    assert.equal('loiteringDelayMs' in sent, false, 'clean field name does not leak to the wire');
  });

  it('add() fills omitted fields from geofenceDefaults', async () => {
    const { native, added } = fakeNative();
    const config = fakeConfig({
      geofenceDefaults: { radius: 150, loiteringDelayMs: 60000, notifyOnEntry: true, notifyOnDwell: true },
    });
    const api = GeofencesApi({ native, config });
    await api.add([{ id: 'a', latitude: 1, longitude: 2 }]); // omits radius / delay / flags
    const sent = added[0][0];
    assert.equal(sent.radius, 150, 'default radius applied');
    assert.equal(sent.loiteringDelay, 60000, 'default loiteringDelay applied (renamed)');
    assert.equal(sent.notifyOnEntry, true);
    assert.equal(sent.notifyOnDwell, true);
  });

  it('add() does NOT override a field the caller set', async () => {
    const { native, added } = fakeNative();
    const config = fakeConfig({ geofenceDefaults: { radius: 150, notifyOnEntry: false } });
    const api = GeofencesApi({ native, config });
    await api.add([{ id: 'a', latitude: 1, longitude: 2, radius: 500, notifyOnEntry: true }]);
    const sent = added[0][0];
    assert.equal(sent.radius, 500, 'caller radius wins over default');
    assert.equal(sent.notifyOnEntry, true, 'caller flag wins over default (even vs default false)');
  });

  it('list() translates the native loiteringDelay back to loiteringDelayMs', async () => {
    const { native } = fakeNative([{ id: 'a', latitude: 1, longitude: 2, radius: 200, loiteringDelay: 4000 }]);
    const api = GeofencesApi({ native, config: fakeConfig({}) });
    const list = await api.list();
    assert.equal(list[0].loiteringDelayMs, 4000, 'wire key translated back to the clean field');
    assert.equal('loiteringDelay' in list[0], false, 'wire key does not leak to the caller');
  });
});
