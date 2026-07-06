// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Driver-intelligence sub-API (the X-extension, Fase 2).
// Read/score calls are capability-gated (throw CapabilityError off-platform, e.g.
// web). Subscribing is left ungated — on unsupported platforms the events simply
// never fire, so `on()` stays synchronous and cheap.

import type { GeolocationEventMap, GeolocationEventName } from '../definitions/events';
import type { BackgroundGeolocationNative } from '../definitions/roles';
import type { Capabilities, TripScore } from '../definitions/values';
import { ensureCapability } from './errors';
import { makeAliasedOn, type Subscription } from './stream';

/** Short driver event names → their payloads. */
export interface DriverEvents {
  tripStart: GeolocationEventMap['tripStart'];
  tripEnd: GeolocationEventMap['tripEnd'];
  moving: GeolocationEventMap['moving'];
  stopped: GeolocationEventMap['stopped'];
  speeding: GeolocationEventMap['speeding'];
  hardBrake: GeolocationEventMap['hardBrake'];
  rapidAcceleration: GeolocationEventMap['rapidAcceleration'];
  sharpTurn: GeolocationEventMap['sharpTurn'];
  possibleCrash: GeolocationEventMap['possibleCrash'];
  phoneUsage: GeolocationEventMap['phoneUsageWhileDriving'];
  idleStart: GeolocationEventMap['idleStart'];
  idleEnd: GeolocationEventMap['idleEnd'];
}

/**
 * Real renames only. Every other short name is identical to its native event name and
 * passes through unchanged via `makeAliasedOn` — `phoneUsage` is the sole alias.
 */
const DRIVER_EVENT: Partial<Record<keyof DriverEvents, GeolocationEventName>> = {
  phoneUsage: 'phoneUsageWhileDriving',
};

export interface DriverApi {
  /** Score for the most recently completed trip, or `null`. Gated. */
  lastTripScore(): Promise<TripScore | null>;
  /** Subscribe to a driving/trip event. */
  on<E extends keyof DriverEvents>(
    event: E,
    listener: (payload: DriverEvents[E]) => void,
  ): Subscription<DriverEvents[E]>;
}

export function DriverApi(deps: {
  native: BackgroundGeolocationNative;
  capabilities: () => Promise<Capabilities>;
}): DriverApi {
  const { native, capabilities } = deps;
  return {
    async lastTripScore() {
      await ensureCapability(capabilities, 'driverIntelligence');
      return native.getTripScore();
    },
    on: makeAliasedOn<DriverEvents>(native, DRIVER_EVENT),
  };
}
