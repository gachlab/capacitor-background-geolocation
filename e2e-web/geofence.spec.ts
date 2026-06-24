// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// Web geofencing E2E — parity with the iOS (simulator) and Android (GMS emulator)
// geofencing E2E. Runs the REAL plugin in a real Chromium with mocked geolocation,
// exercising the JS geofence engine end to end.

import { test, expect } from '@playwright/test';

const PATH = '/e2e-web/harness.html';
const CENTER = { latitude: 19.5, longitude: -99.0 };
const FAR = { latitude: 20.5, longitude: -99.0 }; // ~111 km away → outside

/* eslint-disable @typescript-eslint/no-explicit-any */
const count = (page: import('@playwright/test').Page, type: string) =>
  page.evaluate((t) => (window as any).__count(t), type);

test.describe('web geofencing E2E (real browser geolocation)', () => {
  test('geofenceError on invalid radius', async ({ page, context }) => {
    await context.setGeolocation(CENTER);
    await page.goto(PATH);
    await page.waitForFunction(() => (window as any).__ready === true);
    await page.evaluate(() => (window as any).BG.start());

    await page.evaluate(() =>
      (window as any).BG.addGeofences({ geofences: [{ id: 'bad', latitude: 19.5, longitude: -99.0, radius: 0 }] }),
    );
    await expect.poll(() => count(page, 'geofenceError')).toBe(1);
  });

  test('initial ENTER already-inside, then DWELL, then EXIT', async ({ page, context }) => {
    await context.setGeolocation(CENTER);
    await page.goto(PATH);
    await page.waitForFunction(() => (window as any).__ready === true);
    await page.evaluate(() => (window as any).BG.start());

    // A real watchPosition fix must land first so lastLocation is inside the region.
    await expect.poll(() => count(page, 'location')).toBeGreaterThan(0);

    // Register a geofence the device is already inside → synthesised ENTER.
    await page.evaluate(() =>
      (window as any).BG.addGeofences({
        geofences: [
          {
            id: 'depot',
            latitude: 19.5,
            longitude: -99.0,
            radius: 200,
            notifyOnEntry: true,
            notifyOnExit: true,
            notifyOnDwell: true,
            loiteringDelay: 1000,
          },
        ],
      }),
    );
    await expect.poll(() => count(page, 'geofenceEnter')).toBe(1);

    // Stay inside past the loitering delay → DWELL (timer fast-path + per-fix eval).
    await page.waitForTimeout(1200);
    await context.setGeolocation({ latitude: 19.5000005, longitude: -99.0 }); // jitter, still inside
    await expect.poll(() => count(page, 'geofenceDwell')).toBe(1);

    // Leave the region → EXIT.
    await context.setGeolocation(FAR);
    await expect.poll(() => count(page, 'geofenceExit')).toBe(1);

    // DWELL fires once only.
    expect(await count(page, 'geofenceDwell')).toBe(1);
  });
});
