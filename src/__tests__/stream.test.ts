// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · the single sticky-replay primitive (stream.ts). The ordering guard shared by the
// native-backed stream, ConfigApi.on, and bg.on('authorization') lives here; test it directly.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { stickyReplay } from '../sdk/stream';

const tick = (): Promise<void> => new Promise((r) => setTimeout(r, 0));

describe('stickyReplay', () => {
  it('delivers the replay value when no live event beats it', async () => {
    const seen: string[] = [];
    stickyReplay<string>(
      (v) => seen.push(v),
      async () => 'X',
      () => true,
    );
    await tick();
    assert.deepEqual(seen, ['X']);
  });

  it('suppresses the replay once a live event was delivered (no out-of-order stale value)', async () => {
    const seen: string[] = [];
    const live = stickyReplay<string>(
      (v) => seen.push(v),
      async () => 'stale',
      () => true,
    );
    live('fresh'); // a live event reaches the subscriber before the (async) replay settles
    await tick();
    assert.deepEqual(seen, ['fresh'], 'the stale replay must be skipped after a live delivery');
  });

  it('suppresses the replay when the subscription is no longer active (removed)', async () => {
    const seen: string[] = [];
    let active = true;
    stickyReplay<string>(
      (v) => seen.push(v),
      async () => 'X',
      () => active,
    );
    active = false; // removed before the replay settles
    await tick();
    assert.deepEqual(seen, []);
  });

  it('skips a nullish replay value (nothing to replay yet)', async () => {
    const seen: unknown[] = [];
    stickyReplay<string | null>(
      (v) => seen.push(v),
      async () => null,
      () => true,
    );
    await tick();
    assert.deepEqual(seen, []);
  });

  it('swallows a rejected replay (no unhandled rejection)', async () => {
    const seen: string[] = [];
    assert.doesNotThrow(() =>
      stickyReplay<string>(
        (v) => seen.push(v),
        async () => {
          throw new Error('native rejected');
        },
        () => true,
      ),
    );
    await tick();
    assert.deepEqual(seen, []);
  });
});
