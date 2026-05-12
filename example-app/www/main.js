// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM
//
// Minimal host page exercising the plugin's public API on web/Android/iOS.

import { BackgroundGeolocation } from '@josuelmm/capacitor-background-geolocation';

const out = document.getElementById('log');
const log = (label, data) => {
  const line = `[${new Date().toISOString().slice(11, 19)}] ${label}` +
    (data === undefined ? '' : ' ' + JSON.stringify(data));
  out.textContent = line + '\n' + out.textContent;
};

async function safe(label, fn) {
  try {
    const r = await fn();
    log(label, r);
  } catch (e) {
    log(label + ' ERROR', { message: e?.message ?? String(e) });
  }
}

document.getElementById('configure').onclick = () =>
  safe('configure', () =>
    BackgroundGeolocation.configure({
      locationProvider: 'DISTANCE_FILTER',
      desiredAccuracy: 'HIGH',
      stationaryRadius: 25,
      distanceFilter: 10,
      debug: false,
      stopOnTerminate: false,
      startOnBoot: false,
      interval: 5000,
      notificationsEnabled: true,
      startForeground: true,
      notificationTitle: 'Example tracking',
      notificationText: 'Location enabled',
    }),
  );

document.getElementById('start').onclick = () => safe('start', () => BackgroundGeolocation.start());
document.getElementById('stop').onclick = () => safe('stop', () => BackgroundGeolocation.stop());
document.getElementById('current').onclick = () =>
  safe('getCurrentLocation', () =>
    BackgroundGeolocation.getCurrentLocation({ enableHighAccuracy: true, timeout: 15000 }),
  );
document.getElementById('valid').onclick = () =>
  safe('getValidLocations', () => BackgroundGeolocation.getValidLocations());
document.getElementById('clear').onclick = () =>
  safe('deleteAllLocations', () => BackgroundGeolocation.deleteAllLocations());
document.getElementById('perm').onclick = () =>
  safe('requestPermissions', () => BackgroundGeolocation.requestPermissions());

// Subscribe to the most useful events.
BackgroundGeolocation.addListener('location', (loc) => log('event:location', loc));
BackgroundGeolocation.addListener('stationary', (loc) => log('event:stationary', loc));
BackgroundGeolocation.addListener('error', (err) => log('event:error', err));
BackgroundGeolocation.addListener('start', () => log('event:start'));
BackgroundGeolocation.addListener('stop', () => log('event:stop'));

log('ready');
