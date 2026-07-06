// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 gachlab
//
// Minimal host page exercising the plugin on web/Android/iOS — the base of the E2E
// integration tests. Uses the REAL v3 facade.
//
// No bundler: the plugin's IIFE build (dist/plugin.js, copied to www/bg-plugin.js by
// scripts/copy-plugin.mjs and loaded by index.html) exposes the facade as the global
// `capacitorBackgroundGeolocation`. This is the SAME facade npm consumers import via
//   import { BackgroundGeolocation } from '@gachlab/capacitor-background-geolocation'
// so the E2E exercises the real facade → config-mapper → native pipeline.

/* global capacitorBackgroundGeolocation */

document.addEventListener('DOMContentLoaded', () => {
  const bg = capacitorBackgroundGeolocation.BackgroundGeolocation(); // factory (zero-config → shared singleton)

  const out = document.getElementById('log');
  const statusEl = document.querySelector('[data-testid="service-status"]');
  const countEl = document.querySelector('[data-testid="location-count"]');
  const lastEvEl = document.querySelector('[data-testid="last-event"]');

  let locationCount = 0;

  const log = (label, data) => {
    const line =
      `[${new Date().toISOString().slice(11, 19)}] ${label}` +
      (data === undefined ? '' : ' ' + JSON.stringify(data));
    out.textContent = line + '\n' + out.textContent;
    lastEvEl.textContent = label;
  };

  async function safe(label, fn) {
    try {
      const r = await fn();
      log(label, r);
    } catch (e) {
      log(label + ' ERROR', { message: e?.message ?? String(e) });
    }
  }

  // ── Tracking ────────────────────────────────────────────────────────────────
  // v3 two-tier config: clean composed shape → the facade's config-mapper resolves it
  // to the native wire. Fully typed now — no escape hatch needed.
  document.getElementById('configure').onclick = () =>
    safe('configure', () =>
      bg.configure({
        location: { provider: 'raw', accuracy: 'high', distanceFilter: 0, interval: 1000 },
        stationary: { radius: 25 },
        survival: { stopOnTerminate: false, startOnBoot: false, heartbeatInterval: 30000 },
        notification: { enabled: true, foreground: true, title: 'Example tracking', text: 'Location enabled' },
        debug: false,
        driving: {
          enabled: true,
          speedLimit: 90,
          // Lowered thresholds for E2E emulator testing
          crashImpactKmh: 10,
          crashWindowMs: 6000,
          crashConfirmWindowMs: 2000,
          sensorFusion: false,
          phoneUsageWindowMs: 3000,
          phoneUsageCooldownMs: 5000,
          minTripDurationMs: 0,
          minMovingSpeed: 0.5,
        },
      }),
    );

  // Same as Configure but with the native geofence stationary-exit backstop enabled
  // (stationary-geofence E2E; needs Play Services / a GMS emulator).
  document.getElementById('configure-gf-exit').onclick = () =>
    safe('configure', () =>
      bg.configure({
        location: { provider: 'raw', accuracy: 'high', distanceFilter: 0, interval: 1000 },
        stationary: { radius: 25, exitMode: 'geofence' },
        survival: { stopOnTerminate: false },
        notification: { enabled: true, foreground: true, title: 'GF-exit test', text: 'Location enabled' },
        debug: false,
      }),
    );

  // changePace equivalent: force the movement state machine for on-device geofence-mode
  // testing. This plugin overloads switchMode: mode 1 (foreground) = moving, mode 0
  // (background) = stationary.
  document.getElementById('pace-moving').onclick = () =>
    safe('switchMode(moving)', () => bg.platform.switchMode('foreground'));
  document.getElementById('pace-stationary').onclick = () =>
    safe('switchMode(stationary)', () => bg.platform.switchMode('background'));

  document.getElementById('start').onclick = () => safe('start', () => bg.tracking.start());
  document.getElementById('stop').onclick = () => safe('stop', () => bg.tracking.stop());
  document.getElementById('status').onclick = () => safe('checkStatus', () => bg.tracking.status());
  document.getElementById('current').onclick = () =>
    safe('getCurrentLocation', () => bg.locations.current({ accuracy: 'high', timeout: 15000 }));

  // ── Locations ─────────────────────────────────────────────────────────────
  document.getElementById('valid').onclick = () => safe('locations.pending', () => bg.locations.pending());
  document.getElementById('clear').onclick = () => safe('locations.clear', () => bg.locations.clear());

  // ── Diagnostics ─────────────────────────────────────────────────────────────
  document.getElementById('diag').onclick = () =>
    safe('getDiagnostics', async () => {
      const d = await bg.diagnostics.report();
      return JSON.parse(JSON.stringify(d));
    });
  document.getElementById('ver').onclick = () => safe('getPluginVersion', () => bg.diagnostics.version());
  document.getElementById('sos').onclick = () => safe('triggerSOS', () => bg.sos({ reason: 'manual' }));

  // ── Geofencing ──────────────────────────────────────────────────────────────
  // GF_CENTER must match the coordinate injected by the E2E scripts (device starts
  // already-inside).
  const GF_CENTER = { latitude: 37.3349, longitude: -122.009 };
  document.getElementById('gf-enter').onclick = () =>
    safe('geofences.add[enter-here]', () =>
      bg.geofences.add([
        {
          id: 'gf-here',
          latitude: GF_CENTER.latitude,
          longitude: GF_CENTER.longitude,
          radius: 200,
          notifyOnEntry: true,
          notifyOnExit: true,
          notifyOnDwell: true,
          loiteringDelayMs: 4000,
        },
      ]),
    );
  // Register 21 geofences in one call. iOS caps user geofences at 19 → the last two
  // overflow and surface a geofence `error`.
  document.getElementById('gf-limit').onclick = () =>
    safe('geofences.add[21]', () => {
      const geofences = [];
      for (let i = 0; i < 21; i++) {
        geofences.push({
          id: `gf-${i}`,
          latitude: 37.3349 + i * 0.01,
          longitude: -122.009 + i * 0.01,
          radius: 150,
          notifyOnEntry: true,
        });
      }
      return bg.geofences.add(geofences);
    });
  // Invalid geofence (radius 0) → registration failure → `geofenceError`.
  document.getElementById('gf-invalid').onclick = () =>
    safe('geofences.add[invalid]', () =>
      bg.geofences.add([{ id: 'gf-bad', latitude: 37.3349, longitude: -122.009, radius: 0, notifyOnEntry: true }]),
    );
  document.getElementById('gf-list').onclick = () => safe('geofences.list', () => bg.geofences.list());
  document.getElementById('gf-clear').onclick = () => safe('geofences.clear', () => bg.geofences.clear());

  // ── Permissions ───────────────────────────────────────────────────────────
  document.getElementById('perm').onclick = () => safe('requestPermissions', () => bg.permissions.request());
  document.getElementById('bgperm').onclick = () =>
    safe('requestBackground', () => bg.permissions.requestBackground());
  document.getElementById('actperm').onclick = () =>
    safe('requestActivity', () => bg.permissions.requestActivity());
  document.getElementById('notifperm').onclick = () =>
    safe('requestNotifications', () => bg.permissions.requestNotifications());

  // ── Event subscriptions (v3 sub-API shape) ──────────────────────────────────
  // Reactive config (v3): sticky — fires immediately with the current base, then on every
  // configure(). Exercises the config-mapper round-trip + persisted-blob rehydration in E2E.
  bg.config.on((cfg) => log('config:changed', { location: cfg.location, driving: cfg.driving?.enabled }));

  bg.locations.on((loc) => {
    locationCount++;
    countEl.textContent = String(locationCount);
    log('event:location', loc);
  });
  bg.on('stationary', (loc) => log('event:stationary', loc));
  bg.on('error', (err) => log('event:error', err));
  bg.on('start', () => {
    statusEl.textContent = 'running';
    log('event:start');
  });
  bg.on('stop', () => {
    statusEl.textContent = 'stopped';
    log('event:stop');
  });
  bg.on('activity', (a) => log('event:activity', a));
  bg.on('authorization', (a) => log('event:authorization', a));
  bg.on('heartbeat', (h) => log('event:heartbeat', h));

  bg.driver.on('tripStart', (loc) => log('event:tripStart', loc));
  bg.driver.on('tripEnd', (t) => log('event:tripEnd', t));
  bg.driver.on('speeding', (s) => log('event:speeding', s));
  bg.driver.on('hardBrake', (loc) => log('event:hardBrake', loc));
  bg.driver.on('sharpTurn', (loc) => log('event:sharpTurn', loc));
  bg.driver.on('rapidAcceleration', (loc) => log('event:rapidAcceleration', loc));
  bg.driver.on('possibleCrash', (loc) => {
    console.log('[BGGL-E2E] driving-event:possibleCrash');
    log('event:possibleCrash', loc);
  });
  bg.driver.on('phoneUsage', (loc) => {
    console.log('[BGGL-E2E] driving-event:phoneUsageWhileDriving');
    log('event:phoneUsageWhileDriving', loc);
  });

  bg.on('sos', (s) => log('event:sos', s));

  bg.geofences.on('enter', (e) => {
    console.log('[BGGL-E2E] geofence:enter ' + e.id);
    log('event:geofenceEnter', e);
  });
  bg.geofences.on('exit', (e) => {
    console.log('[BGGL-E2E] geofence:exit ' + e.id);
    log('event:geofenceExit', e);
  });
  bg.geofences.on('dwell', (e) => {
    console.log('[BGGL-E2E] geofence:dwell ' + e.id);
    log('event:geofenceDwell', e);
  });
  bg.geofences.on('error', (e) => {
    console.log('[BGGL-E2E] geofence:error ' + (e.id ?? ''));
    log('event:geofenceError', e);
  });

  log('ready');
});
