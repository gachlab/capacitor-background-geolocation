// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · ConfigApi (bg.config) — reactive base config: configure/current/on + reload rehydration.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import type { BaseConfig } from '../definitions/config';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { NativeConfig } from '../definitions/wire';
import { ConfigApi } from '../sdk/config-api';

/** A fake native that records configure() wire payloads and can seed getConfig(). */
function fakeNative(seed?: NativeConfig): { native: BackgroundGeolocationNative; sent: NativeConfig[] } {
  const sent: NativeConfig[] = [];
  const native = {
    configure: async (cfg: NativeConfig): Promise<void> => void sent.push(cfg),
    getConfig: async (): Promise<NativeConfig> => seed ?? {},
  } as unknown as BackgroundGeolocationNative;
  return { native, sent };
}

const tick = (): Promise<void> => new Promise((r) => setTimeout(r, 0));

describe('ConfigApi', () => {
  it('configure() deep-merges the base and sends the resolved wire', async () => {
    const { native, sent } = fakeNative();
    const api = ConfigApi({ native });
    await api.configure({ location: { accuracy: 'high', distanceFilter: 25 } });
    await api.configure({ location: { distanceFilter: 50 }, debug: true }); // patch

    const base = await api.current();
    assert.deepEqual(base.location, { accuracy: 'high', distanceFilter: 50 }); // merged, not replaced
    assert.equal(base.debug, true);
    // last wire carries the resolved config + the base blob for rehydration
    const last = sent[sent.length - 1];
    assert.equal(last.desiredAccuracy, 0); // high
    assert.equal(last.distanceFilter, 50);
    assert.equal(typeof last.baseConfigJson, 'string');
  });

  it('on() replays the current config immediately and emits on change', async () => {
    const { native } = fakeNative();
    const api = ConfigApi({ native });
    await api.configure({ debug: true });

    const seen: BaseConfig[] = [];
    api.on((c) => seen.push(c));
    await tick();
    assert.equal(seen.length, 1, 'sticky replay of current');
    assert.equal(seen[0].debug, true);

    await api.configure({ location: { accuracy: 'low' } });
    assert.equal(seen.length, 2, 'emits on configure');
    assert.equal(seen[1].location?.accuracy, 'low');
  });

  it('a removed listener stops receiving changes', async () => {
    const { native } = fakeNative();
    const api = ConfigApi({ native });
    const seen: BaseConfig[] = [];
    const sub = api.on((c) => seen.push(c));
    await tick(); // consume sticky replay (empty base)
    sub.remove();
    await api.configure({ debug: true });
    assert.equal(seen.filter((c) => c.debug).length, 0, 'no emit after remove');
  });

  it('rehydrates the base from the native persisted blob (survives reload)', async () => {
    const persisted: BaseConfig = { transport: { baseUrl: 'https://api.me' }, location: { accuracy: 'high' } };
    const { native, sent } = fakeNative({ baseConfigJson: JSON.stringify(persisted) } as NativeConfig);
    const api = ConfigApi({ native }); // fresh instance = post-reload facade

    // A PARTIAL configure after "reload" must merge onto the rehydrated base, not reset it.
    await api.configure({ location: { distanceFilter: 10 } });
    const base = await api.current();
    assert.equal(base.transport?.baseUrl, 'https://api.me', 'persisted baseUrl survives');
    assert.equal(base.location?.accuracy, 'high', 'persisted accuracy survives');
    assert.equal(base.location?.distanceFilter, 10, 'patch applied');
    // the wire still carries the full resolved config (url not dropped)
    assert.equal(sent[sent.length - 1].url, 'https://api.me');
  });

  // ── review-fix regressions ────────────────────────────────────────────────

  it('does not alias the caller patch — later mutation cannot rewrite the stored base', async () => {
    const { native } = fakeNative();
    const api = ConfigApi({ native });
    const patch: BaseConfig = { location: { accuracy: 'high' } };
    await api.configure(patch);
    patch.location!.accuracy = 'low'; // mutate the object the caller still holds
    const base = await api.current();
    assert.equal(base.location?.accuracy, 'high', 'stored base must be insulated from caller mutation');
  });

  it('resolveStartWire() rehydrates the persisted base first — a start() after reload does not clobber it', async () => {
    const persisted: BaseConfig = { transport: { baseUrl: 'https://api.me' }, location: { accuracy: 'high' } };
    const { native } = fakeNative({ baseConfigJson: JSON.stringify(persisted) } as NativeConfig);
    const api = ConfigApi({ native }); // post-reload facade, no configure()/current() yet

    const wire = await api.resolveStartWire({ location: { distanceFilter: 5 } });
    assert.equal(wire.url, 'https://api.me', 'persisted base survives a cold start(override)');
    assert.equal(wire.distanceFilter, 5, 'override applied over the rehydrated base');
    const blob = JSON.parse(wire.baseConfigJson as string) as BaseConfig;
    assert.equal(blob.transport?.baseUrl, 'https://api.me', 'the persisted blob is NOT overwritten with {}');
  });

  it('configure() survives a non-cloneable value in the native escape hatch (no post-commit crash)', async () => {
    const { native, sent } = fakeNative();
    const api = ConfigApi({ native });
    const seen: BaseConfig[] = [];
    api.on((c) => seen.push(c));
    await tick();
    // A function is not structured-cloneable; emit()/current() must degrade, not throw.
    await assert.doesNotReject(() => api.configure({ native: { onEvent: (() => {}) as unknown } }));
    assert.equal(sent.length, 1, 'native.configure still received the wire');
    assert.ok(seen.length >= 1, 'subscribers still notified despite the non-cloneable value');
  });
});
