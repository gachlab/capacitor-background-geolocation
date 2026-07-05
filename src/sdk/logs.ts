// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Logs sub-API (Fase 2).
// `page()` reads one newest-first batch. The `stream()` async-generator that hides the
// fromId paging entirely arrives with the ES2018+ target bump (Fase 3/4) — async
// generators require es2018, and today's target is es2017.

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

export class LogsApi {
  constructor(private readonly native: BackgroundGeolocationNative) {}

  /**
   * One newest-first page of log entries. Pass the smallest returned `id` as `fromId`
   * to fetch the next older batch. On web this resolves to `[]`.
   */
  async page(options: LogPageOptions = {}): Promise<LogEntry[]> {
    const result = await this.native.getLogEntries({
      limit: options.limit ?? 100,
      fromId: options.fromId,
      minLevel: options.minLevel,
    });
    return result.entries;
  }
}
