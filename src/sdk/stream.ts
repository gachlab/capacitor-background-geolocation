// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · event subscription primitive (Fase 2).
// Adapts the native `addListener` into a typed, removable Subscription — the honest
// primitive for a HOT event source. No async-iterable: a GPS stream can't be pulled,
// so an iterable would either buffer unboundedly or lie about backpressure, and it
// would be single-consumer. A callback is multi-subscriber and truthful.
//
// `using` / Symbol.dispose support arrives with the ES2022 target bump (Fase 4); the
// Subscription is shaped to accept it additively then, without breaking today.

import type { PluginListenerHandle } from '@capacitor/core';

/**
 * A live event subscription, parameterized by the payload it delivers. `remove()`
 * stops it. The `TPayload` type is carried for tooling/readability (a
 * `Subscription<Location>` reads as what it is) and future typed composition.
 */
export interface Subscription<TPayload = unknown> {
  remove(): void;
  /** Phantom marker — never assigned; only carries `TPayload` at the type level. */
  readonly __payload?: TPayload;
}

/**
 * Wire a native `addListener` to a listener and return a Subscription. The native
 * handle resolves asynchronously; `remove()` is safe to call before it settles — the
 * listener is removed as soon as the handle is available.
 */
export function subscribe<T>(
  addListener: (cb: (event: T) => void) => Promise<PluginListenerHandle>,
  listener: (event: T) => void,
): Subscription<T> {
  let handle: PluginListenerHandle | null = null;
  let removed = false;

  void addListener(listener).then((h) => {
    handle = h;
    if (removed) void h.remove();
  });

  return {
    remove(): void {
      removed = true;
      if (handle) {
        void handle.remove();
        handle = null;
      }
    },
  };
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
