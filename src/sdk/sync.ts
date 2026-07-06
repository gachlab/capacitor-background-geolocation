// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Sync-queue sub-API (Fase 2).

import type { GeolocationEventMap, GeolocationEventName } from '../definitions/events';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import { makeAliasedOn, type Subscription } from './stream';

/** Short sync event names → their payloads. */
export interface SyncEvents {
  start: void;
  progress: GeolocationEventMap['syncProgress'];
  success: GeolocationEventMap['syncSuccess'];
  error: GeolocationEventMap['syncError'];
  prioritySuccess: GeolocationEventMap['prioritySyncSuccess'];
  priorityFailed: GeolocationEventMap['prioritySyncFailed'];
}

/** Real renames only (every sync event is prefixed / renamed on the wire). */
const SYNC_EVENT: Partial<Record<keyof SyncEvents, GeolocationEventName>> = {
  start: 'syncStart',
  progress: 'syncProgress',
  success: 'syncSuccess',
  error: 'syncError',
  prioritySuccess: 'prioritySyncSuccess',
  priorityFailed: 'prioritySyncFailed',
};

export interface SyncApi {
  /** Force an immediate flush of the pending queue. */
  flush(): Promise<void>;
  /** Discard every pending queued location. */
  clear(): Promise<void>;
  /** Number of locations waiting to be uploaded. */
  pending(): Promise<number>;
  /** Subscribe to a sync event. */
  on<E extends keyof SyncEvents>(event: E, listener: (payload: SyncEvents[E]) => void): Subscription<SyncEvents[E]>;
}

export function SyncApi(deps: { native: BackgroundGeolocationNative }): SyncApi {
  const { native } = deps;
  return {
    flush() {
      return native.forceSync();
    },
    clear() {
      return native.clearSync();
    },
    async pending() {
      return (await native.getPendingSyncCount()).count;
    },
    on: makeAliasedOn<SyncEvents>(native, SYNC_EVENT),
  };
}
