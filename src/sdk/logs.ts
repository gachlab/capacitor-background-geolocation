// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Logs sub-API (Fase 2 · stream added Fase 3).
// `page()` reads one newest-first batch; `stream()` is the async-generator that hides
// the fromId paging entirely (unlocked by the ES2018 target bump — async generators).

import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { LogEntry, LogLevel } from '../definitions/values';

export interface LogPageOptions {
  /** Max entries to return. @default 100 */
  limit?: number;
  /** Page backwards: only entries older than this id (`id < fromId`). */
  fromId?: number;
  /** Minimum severity to include. @default 'debug' */
  minLevel?: LogLevel;
}

export interface LogStreamOptions {
  /** Entries fetched per underlying `getLogEntries` call. @default 100 */
  batchSize?: number;
  /** Start paging older than this id (`id < fromId`). Omit to start from newest. */
  fromId?: number;
  /** Minimum severity to include. @default 'debug' */
  minLevel?: LogLevel;
}

export interface LogsApi {
  /**
   * One newest-first page of log entries. Pass the smallest returned `id` as `fromId`
   * to fetch the next older batch. On web this resolves to `[]`.
   */
  page(options?: LogPageOptions): Promise<LogEntry[]>;

  /**
   * Stream all log entries newest-first, paging transparently — the generator fetches
   * the next older batch on demand, so a caller just `for await`s without touching
   * `fromId`. Ends when the store is exhausted. On web this yields nothing.
   *
   * @example
   * for await (const entry of bg.logs.stream({ minLevel: 'warn' })) {
   *   if (entry.timestamp < cutoff) break; // stops paging — no further native calls
   *   report(entry);
   * }
   */
  stream(options?: LogStreamOptions): AsyncGenerator<LogEntry, void, void>;
}

export function LogsApi(deps: { native: BackgroundGeolocationNative }): LogsApi {
  const { native } = deps;

  const page = async (options: LogPageOptions = {}): Promise<LogEntry[]> => {
    const result = await native.getLogEntries({
      limit: options.limit ?? 100,
      fromId: options.fromId,
      minLevel: options.minLevel,
    });
    return result.entries;
  };

  async function* stream(options: LogStreamOptions = {}): AsyncGenerator<LogEntry, void, void> {
    const batchSize = options.batchSize ?? 100;
    let fromId = options.fromId;
    for (;;) {
      const entries = await page({ limit: batchSize, fromId, minLevel: options.minLevel });
      for (const entry of entries) yield entry;
      // A short page means the store is exhausted; the newest-first page ends on the
      // smallest id, so that id seeds the next (older) page.
      if (entries.length < batchSize) return;
      fromId = entries[entries.length - 1].id;
    }
  }

  return { page, stream };
}
