// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM

import { registerPlugin } from '@capacitor/core';

import type {
  BackgroundGeolocationPlugin,
  HeadlessTaskEvent,
} from './definitions';

/**
 * Internal native surface. The public `headlessTask(fn)` is adapted to the
 * native `registerHeadlessTask({ task: string })` so the function body can
 * cross the Capacitor bridge.
 */
type BackgroundGeolocationNative = Omit<
  BackgroundGeolocationPlugin,
  'headlessTask'
> & {
  registerHeadlessTask(options: { task: string }): Promise<void>;
};

const NativeBackgroundGeolocation = registerPlugin<BackgroundGeolocationNative>(
  'BackgroundGeolocation',
  {
    web: () => import('./web').then((m) => new m.BackgroundGeolocationWeb()),
  },
);

/**
 * Public plugin. `headlessTask(fn)` serialises the callback before sending it
 * to the native side; every other method is a 1:1 passthrough.
 */
const BackgroundGeolocation: BackgroundGeolocationPlugin = new Proxy(
  NativeBackgroundGeolocation as unknown as BackgroundGeolocationPlugin,
  {
    get(target, prop, receiver) {
      if (prop === 'headlessTask') {
        return (task: (event: HeadlessTaskEvent) => unknown): Promise<void> =>
          NativeBackgroundGeolocation.registerHeadlessTask({
            task: task.toString(),
          });
      }
      return Reflect.get(target, prop, receiver);
    },
  },
);

export * from './definitions';
export { BackgroundGeolocation };
