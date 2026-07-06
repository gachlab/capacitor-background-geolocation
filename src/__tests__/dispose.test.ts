// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · disposable handles (Fase 4) — `using` / `await using` auto-cleanup.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import type { BackgroundGeolocationNative } from '../definitions/roles';
import { subscribe } from '../sdk/stream';
import { ConfigApi } from '../sdk/config-api';
import { TrackingApi } from '../sdk/tracking';

/** Settle the microtask/timer queue so subscribe()'s async handle resolves. */
const tick = (): Promise<void> => new Promise((r) => setTimeout(r, 0));

describe('Subscription disposal', () => {
  it('[Symbol.dispose]() removes the subscription (equals remove())', async () => {
    let removed = false;
    const handle = { remove: async (): Promise<void> => void (removed = true) };
    const sub = subscribe(async () => handle, () => {});
    await tick();
    sub[Symbol.dispose]();
    assert.equal(removed, true);
  });

  it('`using` auto-removes at scope end', async () => {
    let removed = false;
    const handle = { remove: async (): Promise<void> => void (removed = true) };
    {
      using sub = subscribe(async () => handle, () => {});
      await tick();
      assert.equal(removed, false, 'still live inside the block');
      void sub;
    }
    assert.equal(removed, true, 'disposed when the block exits');
  });
});

describe('TrackingSession disposal', () => {
  function fakeNative(counter: { stopped: number }): BackgroundGeolocationNative {
    return {
      configure: async (): Promise<void> => {},
      start: async (): Promise<void> => {},
      stop: async (): Promise<void> => void counter.stopped++,
    } as unknown as BackgroundGeolocationNative;
  }

  it('[Symbol.asyncDispose]() stops the session (equals stop())', async () => {
    const counter = { stopped: 0 };
    const native = fakeNative(counter);
    const session = await TrackingApi({ native, config: ConfigApi({ native }) }).start();
    await session[Symbol.asyncDispose]();
    assert.equal(counter.stopped, 1);
  });

  it('`await using` auto-stops at scope end', async () => {
    const counter = { stopped: 0 };
    const native = fakeNative(counter);
    const api = TrackingApi({ native, config: ConfigApi({ native }) });
    {
      await using session = await api.start();
      assert.equal(counter.stopped, 0, 'still running inside the block');
      void session;
    }
    assert.equal(counter.stopped, 1, 'stopped when the block exits');
  });
});
