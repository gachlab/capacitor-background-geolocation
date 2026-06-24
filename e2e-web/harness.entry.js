// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// Browser E2E harness: loads the REAL plugin via Capacitor's registerPlugin
// (web platform → BackgroundGeolocationWeb) and records events on window so the
// Playwright test can assert them. esbuild bundles this with @capacitor/core into
// a single self-contained IIFE (harness.bundle.js).

import { BackgroundGeolocation } from '../dist/esm/index.js';

window.__events = [];
const record = (type) => (e) => window.__events.push({ type, id: e?.id, message: e?.message });
window.__count = (type) => window.__events.filter((e) => e.type === type).length;
window.BG = BackgroundGeolocation;

// Await listener registration (this lazily loads the web implementation) before
// signalling ready, so no event fired by the test can be missed.
(async () => {
  for (const ev of ['geofenceEnter', 'geofenceExit', 'geofenceDwell', 'geofenceError', 'location', 'start']) {
    await BackgroundGeolocation.addListener(ev, record(ev));
  }
  window.__ready = true;
})();
