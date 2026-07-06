// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// v3 SDK · Platform-specific tasks (Fase 2). iOS background tasks + mode switch.
// These are platform-gated by nature; on other platforms the natives no-op.

import type { BackgroundGeolocationNative } from '../definitions/roles';

export interface PlatformApi {
  /** iOS: begin a background task. Pair with `endTask`. Returns the task key. */
  startTask(): Promise<number>;
  /** iOS: end a background task started by `startTask`. */
  endTask(taskKey: number): Promise<void>;
  /** iOS: switch operation mode. Maps to the native 0|1 wire. */
  switchMode(mode: 'background' | 'foreground'): Promise<void>;
}

export function PlatformApi(deps: { native: BackgroundGeolocationNative }): PlatformApi {
  const { native } = deps;
  return {
    async startTask() {
      return (await native.startTask()).taskKey;
    },
    endTask(taskKey) {
      return native.endTask({ taskKey });
    },
    switchMode(mode) {
      return native.switchMode({ mode: mode === 'foreground' ? 1 : 0 });
    },
  };
}
