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

v1.0.0 brings full TypeScript-API parity with the Cordova source plugin:
**40 methods + 28 events**, the full `ConfigureOptions` surface, extended
`Diagnostics`, OEM helpers, sync/session queue management, driver-insight
events, and `@awesome-cordova-plugins`-style compatibility enums.

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
// Android 10+: separate background-location prompt
await BackgroundGeolocation.requestBackgroundLocationPermission();
// Android 13+: notification permission for the foreground-service icon
await BackgroundGeolocation.requestNotificationPermission();
```

`FOREGROUND_SERVICE_LOCATION` is mandatory on Android 14 (API 34+); the plugin
declares its foreground service with `foregroundServiceType="location"`.

## Web limitations

The web fallback uses `navigator.geolocation` and only works while the page is
alive. Sync queue, sessions, OEM screens, and other native-only methods throw
`unimplemented`. `getDiagnostics`, `getPluginVersion`, and `triggerSOS` resolve
with neutral payloads so app code stays portable.

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
  heartbeatInterval: 30_000,
  drivingEvents: { enabled: true, speedLimit: 90 },
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

### Tracking control

| Method | Description |
| --- | --- |
| `configure(options)` | Set the native plugin options. Required before `start`. |
| `start()` | Start the background service. |
| `stop()` | Stop the background service. |
| `switchMode({ mode })` | iOS: switch BACKGROUND (`0`) / FOREGROUND (`1`) mode. |
| `checkStatus()` | Service status snapshot (`isRunning`, `locationServicesEnabled`, `authorization`). |

### Locations

| Method | Description |
| --- | --- |
| `getCurrentLocation(opts?)` | One-shot location fix. |
| `getStationaryLocation()` | Last stationary fix, or `null`. |
| `getLocations()` | All locations stored locally. |
| `getValidLocations()` | Locations not yet POSTed to `url`. |
| `getValidLocationsAndDelete()` | Same as above, but deletes the rows returned. |
| `deleteLocation({ locationId })` | Delete a single stored location. |
| `deleteAllLocations()` | Wipe every stored location. |

### Sync queue

| Method | Description |
| --- | --- |
| `forceSync()` | Force-flush the sync queue to `syncUrl`. |
| `clearSync()` | Discard every pending sync-queue entry. |
| `getPendingSyncCount()` | Count of locations waiting to be synced. |

### Sessions

| Method | Description |
| --- | --- |
| `startSession()` | Begin a recording session (clears the session table). |
| `getSessionLocations()` | All locations in the current session. |
| `clearSession()` | Clear the session table and stop collecting. |
| `getSessionLocationsCount()` | Count of locations in the current session. |

### Diagnostics & OEMs

| Method | Description |
| --- | --- |
| `getDiagnostics()` | Permissions, battery optimisation, OEM, iOS flags. |
| `isIgnoringBatteryOptimizations()` | Android battery-optimisation whitelist state. |
| `requestIgnoreBatteryOptimizations()` | Prompt the user to whitelist the app. |
| `openBatterySettings()` | Open the battery settings screen (Android). |
| `openAutoStartSettings()` | Open the OEM auto-start/background-activity screen. |
| `getManufacturerHelp()` | OEM-specific user instructions. |
| `getPluginVersion()` | Native plugin version string. |

### Permissions

| Method | Description |
| --- | --- |
| `checkPermissions()` | Capacitor-style location permission state. |
| `requestPermissions()` | Prompt for foreground location. |
| `requestBackgroundLocationPermission()` | Android 10+ background location. |
| `requestActivityRecognitionPermission()` | Android 10+ activity recognition. |
| `requestNotificationPermission()` | Android 13+ notifications. |
| `showAppSettings()` | Open this app's settings page. |
| `openSettings()` | Alias for `showAppSettings`. |
| `showLocationSettings()` | Open the system Location settings (Android). |

### Tasks

| Method | Description |
| --- | --- |
| `startTask()` | iOS: begin a background task; returns `{ taskKey }`. |
| `endTask({ taskKey })` | iOS: end the background task. |
| `triggerSOS(payload?)` | Emit an `sos` event with the latest known location. |
| `headlessTask(fn)` | Android: register a JS callback that runs even when the activity has been killed. iOS no-op. |

### Config & logs

| Method | Description |
| --- | --- |
| `getConfig()` | Returns the persisted configuration. |
| `getLogEntries({ limit, fromId?, minLevel? })` | Tail the native plugin log. |

### Lifecycle

| Method | Description |
| --- | --- |
| `removeAllListeners()` | Detach every active listener. |

## Events

Subscribe with `addListener(eventName, handler)`. The handle's `remove()`
detaches a single listener.

| Event | Payload | Description |
| --- | --- | --- |
| `location` | `Location` | New fix. |
| `stationary` | `StationaryLocation` | Stationary state entered. |
| `activity` | `Activity` | Activity recognition update. |
| `start` | `void` | Service started. |
| `stop` | `void` | Service stopped. |
| `error` | `BackgroundGeolocationError` | Recoverable or fatal error. |
| `authorization` | `{ status }` | Authorization state changed. |
| `foreground` | `void` | App entered the foreground. |
| `background` | `void` | App entered the background. |
| `abort_requested` | `void` | Server returned `285 Updates Not Required`. |
| `http_authorization` | `void` | Server returned `401 Unauthorized`. |
| `heartbeat` | `{ location? }` | Periodic tick with latest known location. |
| `syncStart` | `void` | Batch sync upload started. |
| `syncProgress` | `{ progress }` | Sync upload progress (0..100). |
| `syncSuccess` | `{ sent }` | Sync upload completed. |
| `syncError` | `{ httpStatus, message }` | Sync upload failed. |
| `tripStart` | `Location` | Trip started (driver insights). |
| `tripEnd` | `{ location, distance, durationMs }` | Trip ended. |
| `moving` | `Location` | User started moving. |
| `stopped` | `Location` | User stopped. |
| `speeding` | `{ location, speedKmh, limitKmh }` | Speed exceeded `drivingEvents.speedLimit`. |
| `providerChange` | `{ provider }` | Native location provider changed. |
| `sos` | `{ location?, ... }` | `triggerSOS()` was invoked. |
| `hardBrake` | `{ location, value }` | GPS-derived hard brake. |
| `rapidAcceleration` | `{ location, value }` | GPS-derived rapid acceleration. |
| `sharpTurn` | `{ location, value }` | GPS-derived sharp turn. |
| `possibleCrash` | `{ location, value, source }` | Heuristic crash detection. |
| `phoneUsageWhileDriving` | `{ location? }` | Sustained phone interaction during a trip. |

## Headless task (Android)

When `stopOnTerminate: false` and the user swipes the app away, Android
keeps the foreground service alive but kills the host activity. In that
state your regular `addListener` callbacks won't fire — the JS bridge no
longer exists.

`headlessTask` registers a small JS function that the plugin runs inside
a hidden Android WebView every time a `location`, `stationary`, or
`activity` event fires while the activity is gone.

```ts
import { BackgroundGeolocation } from '@josuelmm/capacitor-background-geolocation';

await BackgroundGeolocation.configure({
  stopOnTerminate: false,
  startOnBoot: true,
  // ...other options
});

await BackgroundGeolocation.headlessTask(function (event) {
  // event.name   : 'location' | 'stationary' | 'activity'
  // event.params : the corresponding payload
  if (event.name === 'location' || event.name === 'stationary') {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', 'https://example.com/headless');
    xhr.setRequestHeader('Content-Type', 'application/json');
    xhr.send(JSON.stringify(event.params));
  }
  return 'processed: ' + event.name;
});

await BackgroundGeolocation.start();
```

Caveats:

- **Android only.** On iOS the call resolves immediately — Apple does not
  allow running JS in a killed-app scenario. Use the regular
  `addListener('location', …)` flow on iOS.
- **Isolated scope.** The function body is serialised via `fn.toString()`
  and re-evaluated inside a fresh WebView. Closures, imports, and outer
  variables are NOT available. Only `XMLHttpRequest`, `fetch`, `JSON`,
  and the language built-ins.
- **Prefer `url` / `syncUrl`.** Configuring an HTTP endpoint on the
  native side is more reliable than headless JS — the native sync layer
  handles retries, batching, and OS-level battery throttling.

## Driving events

Set `drivingEvents.enabled: true` in `configure()` to turn on the
GPS-derived driver-insight pipeline. The native core then emits:

- `tripStart`, `tripEnd`, `moving`, `stopped` — sustained-speed state machine.
- `speeding` — when speed crosses `drivingEvents.speedLimit` (km/h).
- `providerChange` — OS switched between GPS / network / fused.
- `hardBrake`, `rapidAcceleration`, `sharpTurn` — sensor-free heuristics
  from speed / bearing deltas.
- `possibleCrash` — sudden velocity drop or accelerometer impact. The
  payload carries `source: 'gps' | 'sensor'`. **Always confirm with the
  user before notifying anyone** — false positives are possible.
- `phoneUsageWhileDriving` — only when `drivingEvents.sensorFusion: true`,
  via accelerometer + gyroscope jitter.

```ts
await BackgroundGeolocation.configure({
  // ...tracking options
  drivingEvents: {
    enabled: true,
    speedLimit: 90,          // km/h, 0 to disable
    minTripSpeed: 3.0,       // m/s
    minTripDuration: 30000,  // ms
    sensorFusion: false,     // true → accel/gyro pipeline
  },
});

BackgroundGeolocation.addListener('tripStart', (loc) => console.log('trip', loc));
BackgroundGeolocation.addListener('tripEnd', (e) =>
  console.log('trip end', e.distance, 'm in', e.durationMs, 'ms'),
);
BackgroundGeolocation.addListener('speeding', (e) =>
  console.warn('over limit', e.speedKmh, 'vs', e.limitKmh),
);
```

## Configuration reference

`ConfigureOptions` covers 60+ fields. Highlights:

- **Provider & accuracy**: `locationProvider`, `desiredAccuracy`,
  `distanceFilter`, `stationaryRadius`, `maxAcceptedAccuracy`,
  `activityConfidenceThreshold`.
- **Cadence**: `interval`, `fastestInterval`, `activitiesInterval`,
  `heartbeatInterval`, `stationaryTimeout`, `stationaryPollInterval`,
  `stationaryPollFast`.
- **Service lifecycle**: `stopOnTerminate`, `startOnBoot`,
  `enableWatchdog`, `wakeLockMode`.
- **Notifications (Android)**: `notificationsEnabled`, `startForeground`,
  `notificationTitle`, `notificationText`, `notificationIconColor`,
  `notificationIconSmall`, `notificationIconLarge`,
  `notificationSyncTitle`, `notificationSyncText`, `showTime`,
  `showDistance`.
- **iOS-only**: `activityType`, `pauseLocationUpdates`,
  `saveBatteryOnBackground`, `showsBackgroundLocationIndicator`.
- **HTTP transport**: `url`, `syncUrl`, `syncThreshold`, `sync`,
  `headers`, `httpMethod`, `syncHttpMethod`, `httpMode`, `syncMode`,
  `postTemplate`, `bodyTemplate`, `queryParams`, `maxLocations`.
- **Data quality**: `mockLocationPolicy` (`'allow' | 'flag' | 'drop'`),
  `includeBattery`.
- **Driver insights**: `drivingEvents.{enabled, speedLimit, minTripSpeed,
  hardBrakeMps2, rapidAccelMps2, sharpTurnDegPerSec, crashImpactKmh,
  sensorFusion, crashImpactG, phoneUsageWindowMs, …}`.

Inspect `src/definitions.ts` (or the generated `.d.ts` in `dist/esm/`)
for the full annotated list with default values and JSDoc.

## Compatibility

- Capacitor `>=8.0.0`
- iOS `>=14.0`
- Android API `>=23` (Android 6.0)
- Web: foreground only via `navigator.geolocation`

## Migration from `@josuelmm/cordova-background-geolocation`

The TypeScript surface is intentionally identical in shape, with two
mechanical adaptations:

- **Promises instead of callbacks.** Every method that used
  `success?, fail?` callbacks now returns `Promise<T>`. The resolved value is
  what the Cordova `success` callback received, wrapped in an envelope object
  when the return is a primitive (`getPendingSyncCount` → `{ count }`,
  `getPluginVersion` → `{ version }`, `getLocations` → `{ locations }`, …).
- **`addListener` instead of `on`.** `BackgroundGeolocation.on('location', cb)`
  becomes
  ```ts
  const sub = await BackgroundGeolocation.addListener('location', cb);
  // ...
  sub.remove();
  ```
  `removeAllListeners()` still detaches everything.

Type names from the Cordova plugin (`ConfigureOptions`, `Location`,
`StationaryLocation`, `LocationError`, `Activity`, `ServiceStatus`,
`Diagnostics`, `LogEntry`) are exported from this package. The
`@awesome-cordova-plugins`-style enums (`BackgroundGeolocationEvents`,
`BackgroundGeolocationLocationProvider`, etc.) are also re-exported so most
codebases compile with only the import-path swap.

`headlessTask(fn)` is supported on Android with the same signature as the
Cordova plugin — see [Headless task (Android)](#headless-task-android).
On iOS the call resolves as a no-op (Apple does not allow JS execution
while the host process is killed).

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for attribution
to upstream contributors.
