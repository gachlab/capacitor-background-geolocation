// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Sync-queue sub-API (Fase 2).

import type { GeolocationEventMap, GeolocationEventName } from '../definitions/events';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import { listen, type Subscription } from './stream';

/** Short sync event names → their payloads. */
export interface SyncEvents {
  start: void;
  progress: GeolocationEventMap['syncProgress'];
  success: GeolocationEventMap['syncSuccess'];
  error: GeolocationEventMap['syncError'];
  prioritySuccess: GeolocationEventMap['prioritySyncSuccess'];
  priorityFailed: GeolocationEventMap['prioritySyncFailed'];
}

const SYNC_EVENT: Record<keyof SyncEvents, GeolocationEventName> = {
  start: 'syncStart',
  progress: 'syncProgress',
  success: 'syncSuccess',
  error: 'syncError',
  prioritySuccess: 'prioritySyncSuccess',
  priorityFailed: 'prioritySyncFailed',
};

export class SyncApi {
  constructor(private readonly native: BackgroundGeolocationNative) {}

  /** Force an immediate flush of the pending queue. */
  flush(): Promise<void> {
    return this.native.forceSync();
  }

  /** Discard every pending queued location. */
  clear(): Promise<void> {
    return this.native.clearSync();
  }

  /** Number of locations waiting to be uploaded. */
  async pending(): Promise<number> {
    return (await this.native.getPendingSyncCount()).count;
  }

  /** Subscribe to a sync event. */
  on<E extends keyof SyncEvents>(event: E, listener: (payload: SyncEvents[E]) => void): Subscription<SyncEvents[E]> {
    return listen<SyncEvents[E]>(this.native, SYNC_EVENT[event], listener);
  }
}
