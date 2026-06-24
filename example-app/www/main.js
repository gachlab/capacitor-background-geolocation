// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 gachlab
//
// Minimal host page exercising the plugin's public API on web/Android/iOS.
//
// No bundler is used. The Capacitor native-bridge.js is injected before this
// script runs, so window.Capacitor.Plugins.BackgroundGeolocation is available
// without any npm imports.

/* global Capacitor */

document.addEventListener('DOMContentLoaded', () => {
  const BackgroundGeolocation = Capacitor.Plugins.BackgroundGeolocation;

  const out      = document.getElementById('log');
  const statusEl = document.querySelector('[data-testid="service-status"]');
  const countEl  = document.querySelector('[data-testid="location-count"]');
  const lastEvEl = document.querySelector('[data-testid="last-event"]');

  let locationCount = 0;

  const log = (label, data) => {
    const line = `[${new Date().toISOString().slice(11, 19)}] ${label}` +
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

  // Tracking
  document.getElementById('configure').onclick = () =>
    safe('configure', () =>
      BackgroundGeolocation.configure({
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

  document.getElementById('start').onclick = () => safe('start', () => BackgroundGeolocation.start());
  document.getElementById('stop').onclick = () => safe('stop', () => BackgroundGeolocation.stop());
  document.getElementById('status').onclick = () => safe('checkStatus', () => BackgroundGeolocation.checkStatus());
  document.getElementById('current').onclick = () =>
    safe('getCurrentLocation', () =>
      BackgroundGeolocation.getCurrentLocation({ enableHighAccuracy: true, timeout: 15000 }),
    );

  // Locations
  document.getElementById('valid').onclick = () =>
    safe('getValidLocations', () => BackgroundGeolocation.getValidLocations());
  document.getElementById('clear').onclick = () =>
    safe('deleteAllLocations', () => BackgroundGeolocation.deleteAllLocations());

  // Diagnostics
  document.getElementById('diag').onclick = () =>
    safe('getDiagnostics', async () => {
      const d = await BackgroundGeolocation.getDiagnostics();
      return JSON.parse(JSON.stringify(d));
    });
  document.getElementById('ver').onclick = () =>
    safe('getPluginVersion', () => BackgroundGeolocation.getPluginVersion());
  document.getElementById('sos').onclick = () =>
    safe('triggerSOS', () => BackgroundGeolocation.triggerSOS({ reason: 'manual' }));

  // Geofencing — GF_CENTER must match the coordinate injected by the E2E script
  // (.github/scripts/e2e-ios-geofencing.sh) so the device starts already-inside.
  const GF_CENTER = { latitude: 37.3349, longitude: -122.009 };
  document.getElementById('gf-enter').onclick = () =>
    safe('addGeofences[enter-here]', () =>
      BackgroundGeolocation.addGeofences({
        geofences: [{
          id: 'gf-here',
          latitude: GF_CENTER.latitude,
          longitude: GF_CENTER.longitude,
          radius: 200,
          notifyOnEntry: true,
          notifyOnExit: true,
          notifyOnDwell: true,
          loiteringDelay: 4000,
        }],
      }),
    );
  // Register 21 geofences in one call. iOS caps user geofences at 19, so the last
  // two overflow and must surface a geofence `error` (code 1005).
  document.getElementById('gf-limit').onclick = () =>
    safe('addGeofences[21]', () => {
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
      return BackgroundGeolocation.addGeofences({ geofences });
    });
  // Invalid geofence (radius 0) → registration failure → `geofenceError`. On Android
  // GMS rejects it (the >19 cap is iOS-only, so this is the Android error trigger).
  document.getElementById('gf-invalid').onclick = () =>
    safe('addGeofences[invalid]', () =>
      BackgroundGeolocation.addGeofences({
        geofences: [{ id: 'gf-bad', latitude: 37.3349, longitude: -122.009, radius: 0, notifyOnEntry: true }],
      }),
    );
  document.getElementById('gf-list').onclick = () =>
    safe('getGeofences', () => BackgroundGeolocation.getGeofences());
  document.getElementById('gf-clear').onclick = () =>
    safe('removeGeofences', () => BackgroundGeolocation.removeGeofences());

  // Permissions
  document.getElementById('perm').onclick = () =>
    safe('requestPermissions', () => BackgroundGeolocation.requestPermissions());
  document.getElementById('bgperm').onclick = () =>
    safe('requestBackgroundLocationPermission', () =>
      BackgroundGeolocation.requestBackgroundLocationPermission(),
    );
  document.getElementById('actperm').onclick = () =>
    safe('requestActivityRecognitionPermission', () =>
      BackgroundGeolocation.requestActivityRecognitionPermission(),
    );
  document.getElementById('notifperm').onclick = () =>
    safe('requestNotificationPermission', () =>
      BackgroundGeolocation.requestNotificationPermission(),
    );

  // Event subscriptions
  BackgroundGeolocation.addListener('location', (loc) => {
    locationCount++;
    countEl.textContent = String(locationCount);
    log('event:location', loc);
  });
  BackgroundGeolocation.addListener('stationary', (loc) => log('event:stationary', loc));
  BackgroundGeolocation.addListener('error', (err) => log('event:error', err));
  BackgroundGeolocation.addListener('start', () => { statusEl.textContent = 'running'; log('event:start'); });
  BackgroundGeolocation.addListener('stop',  () => { statusEl.textContent = 'stopped'; log('event:stop'); });
  BackgroundGeolocation.addListener('activity', (a) => log('event:activity', a));
  BackgroundGeolocation.addListener('authorization', (a) => log('event:authorization', a));
  BackgroundGeolocation.addListener('heartbeat', (h) => log('event:heartbeat', h));
  BackgroundGeolocation.addListener('tripStart', (loc) => log('event:tripStart', loc));
  BackgroundGeolocation.addListener('tripEnd', (t) => log('event:tripEnd', t));
  BackgroundGeolocation.addListener('speeding', (s) => log('event:speeding', s));
  BackgroundGeolocation.addListener('sos', (s) => log('event:sos', s));
  BackgroundGeolocation.addListener('hardBrake', (loc) => log('event:hardBrake', loc));
  BackgroundGeolocation.addListener('sharpTurn', (loc) => log('event:sharpTurn', loc));
  BackgroundGeolocation.addListener('rapidAcceleration', (loc) => log('event:rapidAcceleration', loc));
  BackgroundGeolocation.addListener('possibleCrash', (loc) => {
    console.log('[BGGL-E2E] driving-event:possibleCrash');
    log('event:possibleCrash', loc);
  });
  BackgroundGeolocation.addListener('phoneUsageWhileDriving', (loc) => {
    console.log('[BGGL-E2E] driving-event:phoneUsageWhileDriving');
    log('event:phoneUsageWhileDriving', loc);
  });
  BackgroundGeolocation.addListener('geofenceEnter', (e) => {
    console.log('[BGGL-E2E] geofence:enter ' + e.id);
    log('event:geofenceEnter', e);
  });
  BackgroundGeolocation.addListener('geofenceExit', (e) => {
    console.log('[BGGL-E2E] geofence:exit ' + e.id);
    log('event:geofenceExit', e);
  });
  BackgroundGeolocation.addListener('geofenceDwell', (e) => {
    console.log('[BGGL-E2E] geofence:dwell ' + e.id);
    log('event:geofenceDwell', e);
  });
  BackgroundGeolocation.addListener('geofenceError', (e) => {
    console.log('[BGGL-E2E] geofence:error ' + (e.id ?? ''));
    log('event:geofenceError', e);
  });

  log('ready');
});
