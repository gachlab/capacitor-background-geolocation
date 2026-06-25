<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright (c) 2026 gachlab -->

# @gachlab/capacitor-background-geolocation

[![npm version](https://img.shields.io/npm/v/@gachlab/capacitor-background-geolocation.svg)](https://www.npmjs.com/package/@gachlab/capacitor-background-geolocation)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Capacitor](https://img.shields.io/badge/Capacitor-8%2B-119EFF.svg)](https://capacitorjs.com/)

Capacitor 8+ plugin for accurate background geolocation tracking on iOS and
Android, ported to the Capacitor bridge with a Promise-based API and `addListener`
events.

Full TypeScript-API parity with the Cordova source plugin:
**40+ methods · 32 events**, the full `ConfigureOptions` surface, extended
`Diagnostics`, OEM helpers, sync/session queue management, geofencing (v1.3.0),
driver-insight events with idle detection and trip scoring (v1.4.0), priority
sync for safety-critical events (v1.5.0), and deferred crash confirmation with
GPS-based phone-usage detection (v1.6.0).

## Install

```bash
npm install @gachlab/capacitor-background-geolocation
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
import { BackgroundGeolocation } from '@gachlab/capacitor-background-geolocation';

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
import { BackgroundGeolocation } from '@gachlab/capacitor-background-geolocation';

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

### Driver intelligence

| Method | Description |
| --- | --- |
| `getTripScore()` | Returns the `TripScore` for the most recently completed trip, or `null` if no trip has ended yet. @since 1.4.0 |

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
| `idleStart` | `IdleStartEvent` | Vehicle stationary for `idleThresholdMs` during an active trip. @since 1.4.0 |
| `idleEnd` | `IdleEndEvent` | Vehicle resumed movement after an idle episode. @since 1.4.0 |
| `serviceRestarted` | `ServiceRestartedEvent` | Android service restarted by watchdog, OS kill, or boot. @since 1.1.0 |
| `iosFallbackActivated` | `IosFallbackActivatedEvent` | iOS background fallback strategy activated. @since 1.2.0 |
| `geofenceEnter` | `GeofenceEvent` | Device entered a registered geofence. @since 1.3.0 |
| `geofenceExit` | `GeofenceEvent` | Device exited a registered geofence. @since 1.3.0 |
| `geofenceDwell` | `GeofenceEvent` | Device has been inside a geofence for `loiteringDelay` ms. @since 1.3.0 |
| `geofenceError` | `GeofenceErrorEvent` | A geofence failed to register or monitor (e.g. iOS region cap, GMS failure, invalid geofence). @since 1.7.0 |
| `prioritySyncSuccess` | `PrioritySyncSuccessEvent` | Priority POST delivered; carries `eventType` and `attemptNumber`. @since 1.5.0 |
| `prioritySyncFailed` | `PrioritySyncFailedEvent` | Priority POST exhausted retries; carries `eventType`, `httpStatus`, and `attempts`. @since 1.5.0 |

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
  user before notifying anyone** — false positives are possible. Use
  `crashConfirmWindowMs` to hold the event until the vehicle stays stopped,
  cancelling it automatically if speed recovers within the window.
- `phoneUsageWhileDriving` — bearing-jitter heuristic when
  `drivingEvents.sensorFusion: false` (GPS path, @since 1.6.0); or
  accelerometer + gyroscope jitter when `sensorFusion: true`.
- `idleStart`, `idleEnd` — vehicle stationary for ≥ `idleThresholdMs` (default 5 min)
  during an active trip; `idleEnd` carries `durationMs`. @since 1.4.0

```ts
await BackgroundGeolocation.configure({
  // ...tracking options
  drivingEvents: {
    enabled: true,
    speedLimit: 90,           // km/h, 0 to disable
    minTripSpeed: 3.0,        // m/s
    minTripDuration: 30000,   // ms
    sensorFusion: false,      // true → accel/gyro pipeline
    idleThresholdMs: 300000,  // ms of stillness to emit idleStart (default 5 min)
    idleEndThresholdMs: 30000, // ms of movement to confirm idleEnd (default 30 s)
  },
});

BackgroundGeolocation.addListener('tripStart', (loc) => console.log('trip started', loc));
BackgroundGeolocation.addListener('tripEnd', (e) => {
  console.log('trip end', e.distance, 'm in', e.durationMs, 'ms');
  if (e.score) console.log('score', e.score.overall, '/100');
});
BackgroundGeolocation.addListener('idleStart', (e) =>
  console.log('idle since', new Date(e.startedAt)),
);
BackgroundGeolocation.addListener('idleEnd', (e) =>
  console.log('idle ended after', e.durationMs, 'ms'),
);
BackgroundGeolocation.addListener('speeding', (e) =>
  console.warn('over limit', e.speedKmh, 'vs', e.limitKmh),
);
```

### Trip scoring @since 1.4.0

When `drivingEvents.enabled: true` and `drivingEvents.scoring` is set, each
completed trip accumulates a penalty-based score. `tripEnd` includes
`score?: TripScore`, and `getTripScore()` retrieves the last score on demand.

```ts
await BackgroundGeolocation.configure({
  drivingEvents: {
    enabled: true,
    scoring: {
      speeding: 30,       // penalty weight (must sum to 100)
      hardBraking: 25,
      rapidAccel: 20,
      sharpTurn: 15,
      phoneUsage: 10,
    },
  },
});

BackgroundGeolocation.addListener('tripEnd', async (e) => {
  const score = e.score ?? await BackgroundGeolocation.getTripScore();
  if (score) {
    console.log('overall', score.overall, '/100');
    console.log('distance', score.distanceKm, 'km');
    console.log('idle episodes', score.idleCount, 'total', score.totalIdleMs, 'ms');
  }
});
```

## Priority sync @since 1.5.0

Safety-critical events (`possibleCrash`, `sos` by default) are delivered
immediately via a dedicated HTTP POST that bypasses the regular sync queue.
The channel retries automatically on failure and queues events offline until
connectivity is restored.

```ts
await BackgroundGeolocation.configure({
  url: 'https://api.example.com/locations',
  // Optional: separate endpoint just for priority events
  prioritySyncUrl: 'https://api.example.com/priority',
  // Which events trigger an immediate POST (default: ['possibleCrash', 'sos'])
  prioritySyncEvents: ['possibleCrash', 'sos', 'hardBrake'],
  // Max retry attempts before prioritySyncFailed fires (default: 3)
  prioritySyncRetries: 3,
  // Milliseconds between retries (default: [10000, 30000, 60000])
  prioritySyncRetryDelays: [10_000, 30_000, 60_000],
});

BackgroundGeolocation.addListener('prioritySyncSuccess', (e) => {
  console.log(`${e.eventType} delivered on attempt ${e.attemptNumber}`);
});
BackgroundGeolocation.addListener('prioritySyncFailed', (e) => {
  console.warn(`${e.eventType} failed after ${e.attempts} attempts (HTTP ${e.httpStatus})`);
});
```

The priority channel deduplicates by event timestamp so retries never double-post
the same event. When there is no network, events are queued in memory and flushed
as soon as connectivity is restored.

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
  crashConfirmWindowMs, sensorFusion, crashImpactG, phoneUsageWindowMs,
  phoneUsageCooldownMs, idleThresholdMs, idleEndThresholdMs,
  scoring.{speeding, hardBraking, rapidAccel, sharpTurn, phoneUsage}, …}`.
  `crashConfirmWindowMs > 0` defers `possibleCrash` until the vehicle stays
  stopped for the configured ms; speed recovery before the window cancels
  the event. `phoneUsageWindowMs` / `phoneUsageCooldownMs` tune the GPS
  bearing-jitter detector (v1.6.0).
- **Priority sync** _(v1.5.0)_: `prioritySyncEvents`, `prioritySyncUrl`,
  `prioritySyncRetries`, `prioritySyncRetryDelays`.

Inspect `src/definitions.ts` (or the generated `.d.ts` in `dist/esm/`)
for the full annotated list with default values and JSDoc.

## Compatibility

- Capacitor `>=8.0.0`
- iOS `>=14.0`
- Android API `>=23` (Android 6.0)
- Web: foreground only via `navigator.geolocation`

## Migration from Cordova

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

`headlessTask` from the Cordova plugin is not supported — configure `url`
and `syncUrl` instead. The native service continues running and POSTing
locations even when the host activity has been killed.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for attribution
to upstream contributors.
