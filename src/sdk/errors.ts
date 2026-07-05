// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · typed errors (Fase 2).

import type { Capabilities } from '../definitions/values';

/**
 * Thrown when a capability-gated API is called on a platform that does not support it
 * (e.g. `bg.driver.*` on web). Consumers guard with `await bg.capabilities()` /
 * `await bg.supports(...)` instead of branching on platform.
 */
export class CapabilityError extends Error {
  readonly capability: keyof Capabilities;

  constructor(capability: keyof Capabilities) {
    super(`Capability "${String(capability)}" is not available on this platform.`);
    this.name = 'CapabilityError';
    this.capability = capability;
  }
}

/** The boolean (gate-able) capability keys — excludes `platform` and `maxGeofences`. */
export type BooleanCapability = {
  [K in keyof Capabilities]-?: Capabilities[K] extends boolean ? K : never;
}[keyof Capabilities];

/** Throw {@link CapabilityError} unless the platform supports `capability`. */
export async function ensureCapability(
  capabilities: () => Promise<Capabilities>,
  capability: BooleanCapability,
): Promise<void> {
  const caps = await capabilities();
  if (!caps[capability]) {
    throw new CapabilityError(capability);
  }
}
