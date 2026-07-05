// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Platform-specific tasks (Fase 2). iOS background tasks + mode switch.
// These are platform-gated by nature; on other platforms the natives no-op.

import type { BackgroundGeolocationNative } from '../definitions/roles';

export class PlatformApi {
  constructor(private readonly native: BackgroundGeolocationNative) {}

  /** iOS: begin a background task. Pair with `endTask`. Returns the task key. */
  async startTask(): Promise<number> {
    return (await this.native.startTask()).taskKey;
  }

  /** iOS: end a background task started by `startTask`. */
  endTask(taskKey: number): Promise<void> {
    return this.native.endTask({ taskKey });
  }

  /** iOS: switch operation mode. Maps to the native 0|1 wire. */
  switchMode(mode: 'background' | 'foreground'): Promise<void> {
    return this.native.switchMode({ mode: mode === 'foreground' ? 1 : 0 });
  }
}
