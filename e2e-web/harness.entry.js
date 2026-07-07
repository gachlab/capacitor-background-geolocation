// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// Browser E2E harness: loads the REAL plugin via the v3 facade
// (`BackgroundGeolocation()` → web platform → BackgroundGeolocationWeb) and
// records events on window so the Playwright test can assert them. esbuild
// bundles this with @capacitor/core into a single self-contained IIFE
// (harness.bundle.js).

import { BackgroundGeolocation, NativeBackgroundGeolocation } from '../dist/esm/index.js';

// v3: BackgroundGeolocation is a FACTORY — call it to build the composed facade.
// With no `native` it uses the registered Capacitor bridge (web impl on this platform).
const bg = BackgroundGeolocation();

window.__events = [];
const record = (type) => (e) => window.__events.push({ type, id: e?.id, message: e?.message });
window.__count = (type) => window.__events.filter((e) => e.type === type).length;
window.BG = bg;

// The web implementation is lazy-loaded (`registerPlugin({ web: () => import('./web/index') })`).
// Capacitor does NOT replay `addListener` calls made before that import resolves — they are
// silently lost. So we must load the impl FIRST (await one native call), and only THEN attach
// listeners, so they land on the real, loaded instance. Geofence + lifecycle events live on the
// facade `on()`; location events on the `locations` sub-API.
(async () => {
  await NativeBackgroundGeolocation.addListener('start', () => {}); // forces the lazy web impl to load
  bg.on('geofenceEnter', record('geofenceEnter'));
  bg.on('geofenceExit', record('geofenceExit'));
  bg.on('geofenceDwell', record('geofenceDwell'));
  bg.on('geofenceError', record('geofenceError'));
  bg.on('start', record('start'));
  bg.locations.on(record('location'));
  window.__ready = true;
})();
