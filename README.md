<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright (c) 2026 JosueLMM -->

# @josuelmm/capacitor-background-geolocation

[![npm version](https://img.shields.io/npm/v/@josuelmm/capacitor-background-geolocation.svg)](https://www.npmjs.com/package/@josuelmm/capacitor-background-geolocation)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Capacitor](https://img.shields.io/badge/Capacitor-8%2B-119EFF.svg)](https://capacitorjs.com/)

Capacitor 8+ plugin for accurate background geolocation tracking on iOS and
Android. Derived from the `@josuelmm/cordova-background-geolocation` native
core (a fork of [`mauron85/cordova-plugin-background-geolocation`](https://github.com/mauron85/cordova-plugin-background-geolocation)),
ported to the Capacitor bridge with a Promise-based API and `addListener`
events.

## Install

```bash
npm install @josuelmm/capacitor-background-geolocation
npx cap sync
```

Requires `@capacitor/core` >= 8.0.0.

## iOS setup

Add to `ios/App/App/Info.plist`:

```xml
<key>NSLocationWhenInUseUsageDescription</key>
<string>This app needs your location to track activity.</string>
<key>NSLocationAlwaysAndWhenInUseUsageDescription</key>
<string>This app needs your location even when the app is in the background.</string>
<key>NSMotionUsageDescription</key>
<string>This app uses motion data to detect when you are moving.</string>
<key>UIBackgroundModes</key>
<array>
  <string>location</string>
  <string>fetch</string>
</array>
```

## Android setup

Add to `android/app/src/main/AndroidManifest.xml` (inside `<manifest>`):

```xml
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
<uses-permission android:name="android.permission.INTERNET" />
```

Runtime permission flow (API 23+) — request foreground first, then background:

```ts
import { BackgroundGeolocation } from '@josuelmm/capacitor-background-geolocation';

const { location } = await BackgroundGeolocation.requestPermissions();
if (location !== 'granted') {
  await BackgroundGeolocation.showAppSettings();
}
```

`FOREGROUND_SERVICE_LOCATION` is mandatory on Android 14 (API 34+); the plugin
declares its foreground service with `foregroundServiceType="location"`.

## Web limitations

The web fallback uses `navigator.geolocation` and only works while the page is
alive. The following are not supported on the web platform and throw
`unimplemented`: `deleteLocation`, `deleteAllLocations`, `forceSync`,
`showAppSettings`, `showLocationSettings`, `watchLocationMode`.

## Usage

```ts
import { BackgroundGeolocation } from '@josuelmm/capacitor-background-geolocation';

await BackgroundGeolocation.configure({
  locationProvider: 'DISTANCE_FILTER',
  desiredAccuracy: 'HIGH',
  stationaryRadius: 25,
  distanceFilter: 10,
  stopOnTerminate: false,
  startOnBoot: false,
  startForeground: true,
  notificationTitle: 'Tracking',
  notificationText: 'Location enabled',
  url: 'https://example.com/locations',
  syncUrl: 'https://example.com/sync',
});

const sub = await BackgroundGeolocation.addListener('location', (loc) => {
  console.log('fix', loc.latitude, loc.longitude);
});

await BackgroundGeolocation.start();
// ...
await BackgroundGeolocation.stop();
sub.remove();
```

## API reference

| Method | Description |
| --- | --- |
| `configure(options)` | Set the native plugin options. Required before `start`. |
| `start()` | Start the background service. |
| `stop()` | Stop the background service. |
| `getCurrentLocation(opts?)` | One-shot location fix. |
| `getStationaryLocation()` | Last stationary fix, or `null`. |
| `getValidLocations()` | Locations not yet POSTed to `url`. |
| `getConfig()` | Returns the persisted config. |
| `deleteLocation({locationId})` | Delete a single stored location. |
| `deleteAllLocations()` | Wipe every stored location. |
| `isLocationEnabled()` | OS-level location services state. |
| `showAppSettings()` | Open this app's settings page. |
| `showLocationSettings()` | Open the system Location settings. |
| `watchLocationMode()` | Begin emitting provider-state changes. |
| `stopWatchingLocationMode()` | Stop emitting provider-state changes. |
| `getLogEntries(opts)` | Tail the native plugin log. |
| `checkStatus()` | Service status snapshot. |
| `startTask()` / `endTask` | iOS background-task pairing. |
| `forceSync()` | Force-flush the sync queue. |
| `checkPermissions()` | Capacitor-style permission state. |
| `requestPermissions()` | Prompt the user for location. |
| `removeAllListeners()` | Detach every active listener. |

## Events

Subscribe with `addListener(eventName, handler)`:

- `location` — new fix
- `stationary` — stationary state entered (Android)
- `activity` — activity recognition update
- `error` — `{ code, message }`
- `authorization` — authorization state change
- `start` / `stop` — service life-cycle
- `foreground` / `background` — app life-cycle
- `abort_requested` — server returned `285 Updates Not Required`
- `http_authorization` — server returned `401 Unauthorized`

## Migration

If you are migrating from `@josuelmm/cordova-background-geolocation`, your
method names are mostly preserved; callbacks become Promises and event
listeners follow Capacitor's `addListener(eventName, handler)` pattern. The
returned handle exposes `.remove()` to detach the listener.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for attribution
to upstream contributors.
