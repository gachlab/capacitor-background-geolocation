// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM

import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.josuelmm.capacitor.backgroundgeolocation.example',
  appName: 'Background Geolocation Example',
  webDir: 'www',
  server: {
    androidScheme: 'https',
    cleartext: true,
  },
};

export default config;
