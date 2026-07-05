// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 gachlab
//
// Minimal host page exercising the plugin on web/Android/iOS — the base of the E2E
// integration tests.
//
// No bundler is used. The Capacitor native-bridge.js is injected before this script,
// so window.Capacitor.Plugins.BackgroundGeolocation (the raw native contract) is
// available without imports.
//
// v3 note: real apps import the composed facade
//   import { BackgroundGeolocation } from '@gachlab/capacitor-background-geolocation'
// and use bg.tracking.configure(...) / bg.locations.on(...). Here — no bundler — we
// build the SAME facade SHAPE inline over the raw proxy. The underlying native calls,
// wire config and event names are IDENTICAL to what the facade emits, so device E2E
// behaviour is unchanged. Config stays in the native wire format the (as-yet
// unchanged) native side parses.

/* global Capacitor */

document.addEventListener('DOMContentLoaded', () => {
  const native = Capacitor.Plugins.BackgroundGeolocation;

  // ── v3 facade shape over the raw native proxy (identical underlying calls) ──
  const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);
  const bg = {
    tracking: {
      configure: (wire) => native.configure(wire),
      start: () => native.start(),
      stop: () => native.stop(),
      status: () => native.checkStatus(),
    },
    locations: {
      // Mirror the facade: clean `accuracy` → native `enableHighAccuracy`, so the
      // underlying native getCurrentLocation call is identical to the raw one.
      current: (o = {}) => {
        const nativeOpts = { timeout: o.timeout, maximumAge: o.maximumAge };
        if (o.accuracy !== undefined) nativeOpts.enableHighAccuracy = o.accuracy === 'high';
        return native.getCurrentLocation(nativeOpts);
      },
      pending: (o) => (o && o.consume ? native.getValidLocationsAndDelete() : native.getValidLocations()),
      clear: () => native.deleteAllLocations(),
      on: (cb) => native.addListener('location', cb),
    },
    geofences: {
      add: (geofences) => native.addGeofences({ geofences }),
      list: () => native.getGeofences(),
      clear: (ids) => native.removeGeofences(ids ? { ids } : undefined),
      on: (ev, cb) => native.addListener('geofence' + cap(ev), cb), // enter→geofenceEnter
    },
    diagnostics: {
      report: () => native.getDiagnostics(),
      version: () => native.getPluginVersion(),
    },
    permissions: {
      request: () => native.requestPermissions(),
      requestBackground: () => native.requestBackgroundLocationPermission(),
      requestActivity: () => native.requestActivityRecognitionPermission(),
      requestNotifications: () => native.requestNotificationPermission(),
    },
    driver: {
      on: (ev, cb) => native.addListener(ev, cb),
    },
    platform: {
      // 'moving' → mode 1, 'stationary' → mode 0 (native switchMode wire is 0|1)
      switchMode: (mode) => native.switchMode({ mode: mode === 'moving' ? 1 : 0 }),
    },
    sos: (payload) => native.triggerSOS(payload),
    on: (ev, cb) => native.addListener(ev, cb), // global/lifecycle events
  };

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
  // Config is the native wire format (locationProvider/desiredAccuracy numeric,
  // drivingEvents blob) — unchanged so the (as-yet unmodified) native side parses it.
  document.getElementById('configure').onclick = () =>
    safe('configure', () =>
      bg.tracking.configure({
        locationProvider: 2,
        desiredAccuracy: 0,
        stationaryRadius: 25,
        distanceFilter: 0,
        debug: false,
        stopOnTerminate: false,
        startOnBoot: false,
        interval: 1000,
        notificationsEnabled: true,
        startForeground: true,
        notificationTitle: 'Example tracking',
        notificationText: 'Location enabled',
        heartbeatInterval: 30000,
        drivingEvents: {
          enabled: true,
          speedLimit: 90,
          // Lowered thresholds for E2E emulator testing
          crashImpactKmh: 10,
          crashWindowMs: 6000,
          crashConfirmWindowMs: 2000,
          sensorFusion: false,
          phoneUsageWindowMs: 3000,
          phoneUsageCooldownMs: 5000,
          minTripDuration: 0,
          minMovingSpeed: 0.5,
        },
      }),
    );

  // Same provider as Configure but with the native geofence stationary-exit backstop
  // enabled — used by the stationary-geofence E2E (needs Play Services / a GMS emulator).
  document.getElementById('configure-gf-exit').onclick = () =>
    safe('configure', () =>
      bg.tracking.configure({
        locationProvider: 2,
        desiredAccuracy: 0,
        stationaryRadius: 25,
        stationaryExitMode: 'geofence',
        distanceFilter: 0,
        debug: false,
        stopOnTerminate: false,
        interval: 1000,
        notificationsEnabled: true,
        startForeground: true,
        notificationTitle: 'GF-exit test',
        notificationText: 'Location enabled',
      }),
    );

  // changePace equivalent: force the movement state machine for manual on-device
  // geofence-mode testing. 'moving' → switchMode(1), 'stationary' → switchMode(0).
  document.getElementById('pace-moving').onclick = () =>
    safe('switchMode(moving)', () => bg.platform.switchMode('moving'));
  document.getElementById('pace-stationary').onclick = () =>
    safe('switchMode(stationary)', () => bg.platform.switchMode('stationary'));

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
  // GF_CENTER must match the coordinate injected by the E2E scripts so the device
  // starts already-inside.
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
          loiteringDelay: 4000,
        },
      ]),
    );
  // Register 21 geofences in one call. iOS caps user geofences at 19, so the last two
  // overflow and must surface a geofence `error` (code 1005).
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

  // ── Event subscriptions (v3 sub-API shape; same native events under the hood) ──
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
  bg.driver.on('phoneUsageWhileDriving', (loc) => {
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
