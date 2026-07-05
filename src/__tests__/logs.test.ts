// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · LogsApi.stream() — the async-generator paging (Fase 3).

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { LogEntry, LogLevel } from '../definitions/values';
import { LogsApi } from '../sdk/logs';

function makeEntries(n: number, level: LogLevel = 'debug'): LogEntry[] {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    timestamp: 1000 + i,
    level,
    message: `entry ${i + 1}`,
    stackTrace: '',
  }));
}

describe('LogsApi.stream()', () => {
  it('yields every entry newest-first across multiple pages', async () => {
    const entries = makeEntries(25);
    const box = { calls: 0 };
    const ordered = [...entries].sort((a, b) => b.id - a.id);
    const native = {
      async getLogEntries(options: { limit: number; fromId?: number }) {
        box.calls++;
        const rows = options.fromId !== undefined ? ordered.filter((e) => e.id < options.fromId!) : ordered;
        return { entries: rows.slice(0, options.limit) };
      },
    } as unknown as BackgroundGeolocationNative;

    const seen: number[] = [];
    for await (const e of new LogsApi(native).stream({ batchSize: 10 })) seen.push(e.id);

    assert.deepEqual(seen, [25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1]);
    assert.equal(box.calls, 3, 'pages of 10 over 25 entries → 3 native calls (10 + 10 + 5)');
  });

  it('makes a single native call when the first page is not full', async () => {
    const entries = makeEntries(4);
    const box = { calls: 0 };
    const ordered = [...entries].sort((a, b) => b.id - a.id);
    const native = {
      async getLogEntries(options: { limit: number; fromId?: number }) {
        box.calls++;
        const rows = options.fromId !== undefined ? ordered.filter((e) => e.id < options.fromId!) : ordered;
        return { entries: rows.slice(0, options.limit) };
      },
    } as unknown as BackgroundGeolocationNative;

    const seen: number[] = [];
    for await (const e of new LogsApi(native).stream({ batchSize: 10 })) seen.push(e.id);

    assert.deepEqual(seen, [4, 3, 2, 1]);
    assert.equal(box.calls, 1);
  });

  it('stops paging when the caller breaks early — no further native calls', async () => {
    const entries = makeEntries(30);
    const box = { calls: 0 };
    const ordered = [...entries].sort((a, b) => b.id - a.id);
    const native = {
      async getLogEntries(options: { limit: number; fromId?: number }) {
        box.calls++;
        const rows = options.fromId !== undefined ? ordered.filter((e) => e.id < options.fromId!) : ordered;
        return { entries: rows.slice(0, options.limit) };
      },
    } as unknown as BackgroundGeolocationNative;

    const seen: number[] = [];
    for await (const e of new LogsApi(native).stream({ batchSize: 10 })) {
      seen.push(e.id);
      if (e.id === 25) break; // 6th entry, mid first page
    }

    assert.deepEqual(seen, [30, 29, 28, 27, 26, 25]);
    assert.equal(box.calls, 1, 'break inside the first page must not trigger a second fetch');
  });

  it('threads minLevel through to the native query', async () => {
    const entries = [...makeEntries(3, 'debug'), ...makeEntries(2, 'error').map((e) => ({ ...e, id: e.id + 100 }))];
    const box = { calls: 0 };
    const seenLevels: (LogLevel | undefined)[] = [];
    const ordered = [...entries].sort((a, b) => b.id - a.id);
    const nativeReal = {
      async getLogEntries(options: { limit: number; fromId?: number; minLevel?: LogLevel }) {
        box.calls++;
        seenLevels.push(options.minLevel);
        let rows = ordered;
        if (options.fromId !== undefined) rows = rows.filter((e) => e.id < options.fromId!);
        if (options.minLevel !== undefined) rows = rows.filter((e) => e.level === options.minLevel);
        return { entries: rows.slice(0, options.limit) };
      },
    } as unknown as BackgroundGeolocationNative;

    const seen: number[] = [];
    for await (const e of new LogsApi(nativeReal).stream({ batchSize: 50, minLevel: 'error' })) seen.push(e.id);

    assert.deepEqual(seen, [102, 101]);
    assert.ok(seenLevels.every((l) => l === 'error'));
  });
});
