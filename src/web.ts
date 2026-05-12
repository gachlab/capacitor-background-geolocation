// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM
//
// Phase 1 stub. Full web fallback added in Phase 5.

import { WebPlugin } from '@capacitor/core';

import type { BackgroundGeolocationPlugin } from './definitions';

export class BackgroundGeolocationWeb
  extends WebPlugin
  implements BackgroundGeolocationPlugin
{
  async echo(options: { value: string }): Promise<{ value: string }> {
    return options;
  }
}
