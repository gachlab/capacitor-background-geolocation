// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
import { defineConfig } from '@playwright/test';

// Real-browser E2E for the web implementation. Chromium's geolocation mocking
// drives the actual navigator.geolocation.watchPosition path through the plugin.
export default defineConfig({
  testDir: '.',
  timeout: 30_000,
  fullyParallel: false,
  use: {
    headless: true,
    baseURL: 'http://localhost:5599',
    permissions: ['geolocation'],
    geolocation: { latitude: 19.5, longitude: -99.0 },
  },
  // Serve the repo root so /e2e-web/harness.html + its self-contained bundle load.
  webServer: {
    command: 'python3 -m http.server 5599',
    cwd: '..',
    url: 'http://localhost:5599/e2e-web/harness.html',
    reuseExistingServer: !process.env.CI,
    timeout: 30_000,
  },
});
