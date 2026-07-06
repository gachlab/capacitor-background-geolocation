// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · event subscription primitive (Fase 2).
// Adapts the native `addListener` into a typed, removable Subscription — the honest
// primitive for a HOT event source. No async-iterable: a GPS stream can't be pulled,
// so an iterable would either buffer unboundedly or lie about backpressure, and it
// would be single-consumer. A callback is multi-subscriber and truthful.
//
// `using` support (Fase 4): the Subscription carries an additive [Symbol.dispose] that
// just calls remove(), so `using sub = api.on(cb)` auto-removes at scope end. It stays
// optional and lax — .remove() is always there, nothing depends on the symbol.

import type { PluginListenerHandle } from '@capacitor/core';

import './dispose'; // ensure Symbol.dispose exists at runtime

import type { GeolocationEventName } from '../definitions/events';
import type { BackgroundGeolocationNative } from '../definitions/roles';

/**
 * A live event subscription, parameterized by the payload it delivers. `remove()`
 * stops it. The `TPayload` type is carried for tooling/readability (a
 * `Subscription<Location>` reads as what it is) and future typed composition.
 */
export interface Subscription<TPayload = unknown> {
  remove(): void;
  /** `using sub = api.on(cb)` auto-removes at scope end. Optional/additive — equals remove(). */
  [Symbol.dispose](): void;
  /** Phantom marker — never assigned; only carries `TPayload` at the type level. */
  readonly __payload?: TPayload;
}

/**
 * The single sticky-replay primitive. Given a fresh subscriber, schedule `replay()` and
 * hand its value to that SAME subscriber — but only if it is still meaningful, still
 * wanted, and not stale. Returns a wrapped listener that MUST be used in place of the
 * original for live delivery, so a live event marks the replay as beaten.
 *
 * The ordering guard lives here, once, so every sticky source — the native-backed hot
 * stream (`subscribe`) and any facade-local (Set-based) source (e.g. `ConfigApi.on`) —
 * gets the same three gates:
 *   - `value != null`: nothing to replay (no last fix, no authorization yet).
 *   - `!gotLive`: a fresher live event already reached this subscriber; replaying the
 *     last-known value now would deliver it out of order.
 *   - `isActive()`: the subscription was removed before the (async) replay settled.
 * A rejected `replay()` is swallowed — sticky delivery is best-effort and must never
 * surface as an unhandled promise rejection (version-skew / pre-init native calls reject).
 */
export function stickyReplay<T>(
  listener: (value: T) => void,
  replay: () => Promise<T | null | undefined>,
  isActive: () => boolean,
): (value: T) => void {
  let gotLive = false;
  replay().then(
    (value) => {
      if (value != null && !gotLive && isActive()) listener(value);
    },
    () => {},
  );
  return (value: T): void => {
    gotLive = true;
    listener(value);
  };
}

/**
 * Wire a native `addListener` to a listener and return a Subscription. The native
 * handle resolves asynchronously; `remove()` is safe to call before it settles — the
 * listener is removed as soon as the handle is available. An optional `replay` makes the
 * subscription sticky (see {@link stickyReplay}).
 */
export function subscribe<T>(
  addListener: (cb: (event: T) => void) => Promise<PluginListenerHandle>,
  listener: (event: T) => void,
  replay?: () => Promise<T | null | undefined>,
): Subscription<T> {
  let handle: PluginListenerHandle | null = null;
  let removed = false;

  const onEvent = replay ? stickyReplay(listener, replay, () => !removed) : listener;

  addListener(onEvent).then(
    (h) => {
      handle = h;
      if (removed) void h.remove();
    },
    // Native rejection (version-skew / pre-init) is swallowed: registration is best-effort and
    // must never surface as an unhandled promise rejection (mirrors the replay arm above).
    () => {},
  );

  const subscription: Subscription<T> = {
    remove(): void {
      removed = true;
      if (handle) {
        void handle.remove();
        handle = null;
      }
    },
    [Symbol.dispose](): void {
      subscription.remove();
    },
  };
  return subscription;
}

/**
 * Subscribe to a native event by name with a typed payload. Centralizes the single
 * unavoidable cast at the generic-addListener boundary (the runtime name→payload
 * mapping is correct; TS can't follow the indirection), so sub-APIs stay clean.
 */
export function listen<T>(
  native: BackgroundGeolocationNative,
  eventName: GeolocationEventName,
  listener: (payload: T) => void,
  replay?: () => Promise<T | null | undefined>,
): Subscription<T> {
  const add = native.addListener.bind(native) as unknown as (
    name: GeolocationEventName,
    cb: (event: T) => void,
  ) => Promise<PluginListenerHandle>;
  return subscribe<T>((cb) => add(eventName, cb), listener, replay);
}

/**
 * Build an aliased `.on(event, listener)` for a sub-API whose short event names map to
 * native event names. `map` need only carry the REAL renames; a short name absent from it
 * passes through unchanged (`event as GeolocationEventName`). This is the single home for
 * the sub-API `on()` body — geofences/sync/driver differ only by their `map`.
 */
export function makeAliasedOn<Events>(
  native: BackgroundGeolocationNative,
  map: Partial<Record<keyof Events, GeolocationEventName>>,
): <E extends keyof Events>(event: E, listener: (payload: Events[E]) => void) => Subscription<Events[E]> {
  return <E extends keyof Events>(event: E, listener: (payload: Events[E]) => void): Subscription<Events[E]> =>
    listen<Events[E]>(native, map[event] ?? (event as GeolocationEventName), listener);
}

/** Reject when an AbortSignal fires — used to race one-shot native calls. */
export function rejectOnAbort(signal: AbortSignal): Promise<never> {
  return new Promise<never>((_resolve, reject) => {
    if (signal.aborted) {
      reject(abortError());
      return;
    }
    signal.addEventListener('abort', () => reject(abortError()), { once: true });
  });
}

/** A DOMException-style AbortError (name is what callers check). */
export function abortError(): Error {
  const error = new Error('The request was aborted.');
  error.name = 'AbortError';
  return error;
}
