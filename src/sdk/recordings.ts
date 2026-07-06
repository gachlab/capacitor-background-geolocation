// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Recordings sub-API (Fase 2).
// A recording session is a distinct store from live tracking: it captures a discrete
// route you start and stop explicitly.

import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { Location } from '../definitions/values';

export interface RecordingsApi {
  /** Begin a recording session (clears the session store and starts capturing). */
  start(): Promise<void>;
  /** Stop capturing and clear the session store. */
  clear(): Promise<void>;
  /** Every location captured in the current session. */
  locations(): Promise<Location[]>;
  /** Count of locations in the current session. */
  count(): Promise<number>;
}

export function RecordingsApi(deps: { native: BackgroundGeolocationNative }): RecordingsApi {
  const { native } = deps;
  return {
    start() {
      return native.startSession();
    },
    clear() {
      return native.clearSession();
    },
    async locations() {
      return (await native.getSessionLocations()).locations;
    },
    async count() {
      return (await native.getSessionLocationsCount()).count;
    },
  };
}
