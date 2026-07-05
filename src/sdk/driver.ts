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
import { listen, type Subscription } from './stream';

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

const DRIVER_EVENT: Record<keyof DriverEvents, GeolocationEventName> = {
  tripStart: 'tripStart',
  tripEnd: 'tripEnd',
  moving: 'moving',
  stopped: 'stopped',
  speeding: 'speeding',
  hardBrake: 'hardBrake',
  rapidAcceleration: 'rapidAcceleration',
  sharpTurn: 'sharpTurn',
  possibleCrash: 'possibleCrash',
  phoneUsage: 'phoneUsageWhileDriving',
  idleStart: 'idleStart',
  idleEnd: 'idleEnd',
};

export class DriverApi {
  constructor(
    private readonly native: BackgroundGeolocationNative,
    private readonly caps: () => Promise<Capabilities>,
  ) {}

  /** Score for the most recently completed trip, or `null`. Gated. */
  async lastTripScore(): Promise<TripScore | null> {
    await ensureCapability(this.caps, 'driverIntelligence');
    return this.native.getTripScore();
  }

  /** Subscribe to a driving/trip event. */
  on<E extends keyof DriverEvents>(
    event: E,
    listener: (payload: DriverEvents[E]) => void,
  ): Subscription<DriverEvents[E]> {
    return listen<DriverEvents[E]>(this.native, DRIVER_EVENT[event], listener);
  }
}
