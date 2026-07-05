// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Recordings sub-API (Fase 2).
// A recording session is a distinct store from live tracking: it captures a discrete
// route you start and stop explicitly.

import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { Location } from '../definitions/values';

export class RecordingsApi {
  constructor(private readonly native: BackgroundGeolocationNative) {}

  /** Begin a recording session (clears the session store and starts capturing). */
  start(): Promise<void> {
    return this.native.startSession();
  }

  /** Stop capturing and clear the session store. */
  clear(): Promise<void> {
    return this.native.clearSession();
  }

  /** Every location captured in the current session. */
  async locations(): Promise<Location[]> {
    return (await this.native.getSessionLocations()).locations;
  }

  /** Count of locations in the current session. */
  async count(): Promise<number> {
    return (await this.native.getSessionLocationsCount()).count;
  }
}
