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

/** A live event subscription. Call `remove()` to stop receiving events. */
export interface Subscription {
  remove(): void;
}

/**
 * Wire a native `addListener` to a listener and return a Subscription. The native
 * handle resolves asynchronously; `remove()` is safe to call before it settles — the
 * listener is removed as soon as the handle is available.
 */
export function subscribe<T>(
  addListener: (cb: (event: T) => void) => Promise<PluginListenerHandle>,
  listener: (event: T) => void,
): Subscription {
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
      reject(makeAbortError());
      return;
    }
    signal.addEventListener('abort', () => reject(makeAbortError()), { once: true });
  });
}

function makeAbortError(): Error {
  const error = new Error('The request was aborted.');
  error.name = 'AbortError';
  return error;
}
