<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright (c) 2026 gachlab -->

# @gachlab/capacitor-background-geolocation

[![npm version](https://img.shields.io/npm/v/@gachlab/capacitor-background-geolocation.svg)](https://www.npmjs.com/package/@gachlab/capacitor-background-geolocation)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)
[![Capacitor](https://img.shields.io/badge/Capacitor-8%2B-119EFF.svg)](https://capacitorjs.com/)

Capacitor 8+ plugin for accurate background geolocation tracking on iOS and Android.

**v3** exposes a **composed facade**: instead of ~46 flat methods and stringly-typed
`addListener` calls, you get cohesive sub-APIs (`bg.tracking`, `bg.locations`,
`bg.geofences`, `bg.sync`, `bg.driver`, …), a **two-tier composed config**, typed
capability gating, and disposable event handles. The Capacitor bridge is unchanged
under the hood (`Promise` + `addListener`) — the win is structure, not syntax.

> Migrating from the flat v2 API? See **[MIGRATION.md](MIGRATION.md)**.

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
import { BackgroundGeolocation as bg } from '@gachlab/capacitor-background-geolocation';

if ((await bg.permissions.request()) !== 'granted') {
  await bg.permissions.openSettings('app');
}
await bg.permissions.requestBackground();     // Android 10+ background location
await bg.permissions.requestNotifications();  // Android 13+ foreground-service icon
```

`FOREGROUND_SERVICE_LOCATION` is mandatory on Android 14 (API 34+); the plugin declares
its foreground service with `foregroundServiceType="location"`.

## Quick start

```ts
import { BackgroundGeolocation as bg } from '@gachlab/capacitor-background-geolocation';

// 1. Configure the shared base ONCE — every feature inherits it.
await bg.configure({
  location:     { accuracy: 'high', distanceFilter: 25 },
  transport:    { baseUrl: 'https://api.example.com', headers: { Authorization: 'Bearer …' } },
  sync:         { path: '/sync', mode: 'batch', threshold: 100 },
  notification: { channel: 'tracking', title: 'Tracking', foreground: true },
  survival:     { stopOnTerminate: false, startOnBoot: true },
});

// 2. Listen for fixes (typed, disposable).
const sub = bg.locations.on((loc) => console.log('fix', loc.latitude, loc.longitude));

// 3. Start tracking. The session auto-stops if you use `await using`.
const session = await bg.tracking.start();
// …later
await session.stop();
sub.remove();
```

## The facade

`BackgroundGeolocation` composes these sub-APIs. The raw flat proxy is still exported as
`NativeBackgroundGeolocation` for escape-hatch use.

| Sub-API | Methods |
| --- | --- |
| `bg.config` | `configure(patch)` (also `bg.configure`) · `current()` → `BaseConfig` · `on(cb)` — reactive, reload-safe base config |
| `bg.tracking` | `start(override?)` → `TrackingSession` · `stop()` · `status()` |
| `bg.locations` | `on(cb)` · `current(opts?)` · `all()` · `pending({ consume? })` · `stationary()` · `delete(id)` · `clear()` |
| `bg.geofences` | `add(list)` · `remove(ids?)` · `list()` · `on('enter'\|'exit'\|'dwell'\|'error', cb)` |
| `bg.sync` | `flush()` · `clear()` · `pending()` · `on('start'\|'progress'\|'success'\|'error'\|'prioritySuccess'\|'priorityFailed', cb)` |
| `bg.driver` | `lastTripScore()` _(gated)_ · `on(type, cb)` |
| `bg.logs` | `page(opts?)` · `stream(opts?)` |
| `bg.permissions` | `check()` · `request()` · `requestBackground()` · `requestActivity()` · `requestNotifications()` · `openSettings('app'\|'location')` |
| `bg.diagnostics` | `report()` · `version()` · `killReason()` · `oem.*` _(gated: Android)_ |
| `bg.recordings` | `start()` · `clear()` · `locations()` · `count()` |
| `bg.platform` | `startTask()` · `endTask(key)` · `switchMode('background'\|'foreground')` _(iOS)_ |
| top-level | `bg.on(event, cb)` · `bg.sos(payload?)` · `bg.capabilities()` · `bg.supports(cap)` · `bg.require(cap)` · `bg.removeAllListeners()` |

## Configuration (two-tier cascade)

Shared config (transport, notification identity, base sampling) lives on the **plugin**
and is set once via `bg.configure()`. Features inherit it and override only their delta:

```
base (bg.configure)  ⊕  session (tracking.start)  ⊕  per-call (current, add)  =  effective
```

Merge rule: **deep-merge** for maps (`headers`, `queryParams`), **replace** for scalars,
`null` = explicit unset. Config groups:

- **`location`** — `accuracy` (`'high'|'medium'|'low'|'passive'`), `distanceFilter`,
  `provider` (`'distanceFilter'|'activity'|'raw'`), `maxAcceptedAccuracy`,
  `includeBattery`, `mockPolicy`, `activityType`, `interval`, `fastestInterval`,
  `activityInterval`, `activityConfidenceThreshold`.
- **`stationary`** (Android) — `radius`, `timeout`, `pollInterval`, `pollFast`, `exitMode`.
- **`transport`** — `baseUrl`, `headers`, `method`, `mode` (`'batch'|'single'`),
  `queryParams`, `bodyTemplate`. Feature endpoints append their `path` to `baseUrl`.
- **`sync`** — `path`, `mode`, `threshold`, `auto`, `priority.{ events, path, retries, retryDelaysMs }`.
- **`notification`** — `enabled`, `channel`, `icon.{ small, large }`, `color`, `title`,
  `text`, `foreground`, `showTime`, `showDistance`, `sync.{ title, text, completedText, failedText }`.
- **`survival`** — `stopOnTerminate`, `startOnBoot`, `restartOnKill`, `heartbeatInterval`,
  `watchdog.{ enabled, intervalMs }`, `iosBackgroundFallback`, `saveBatteryOnBackground`,
  `pauseLocationUpdates`, `showsBackgroundLocationIndicator`, `wakeLockMode`.
- **`persistence`** — `maxLocations`.
- **`driving`** — the driver-intelligence config (see below).
- **`native`** — a typed escape hatch (`Record<string, unknown>`) for raw wire flags not
  yet surfaced in the clean types.

A session override composes over the base for one tracking run:

```ts
const session = await bg.tracking.start({
  location: { accuracy: 'passive' }, // cheaper accuracy just for this run
});
```

## Events

Global / lifecycle events are on `bg.on(...)`; domain events live on their sub-API. Every
`.on()` returns a disposable `Subscription` (typed by its payload).

```ts
bg.on('authorization', ({ status }) => console.log(status)); // notAuthorized | authorized | authorizedForeground
bg.on('error', (e) => console.warn(e.code, e.message));

const g = bg.geofences.on('enter', (e) => console.log('entered', e.id));   // e.action === 'enter'
const s = bg.sync.on('error', (e) => console.warn(e.httpStatus, e.message));
```

Global events: `start`, `stop`, `foreground`, `background`, `error`, `authorization`,
`heartbeat`, `providerChange`, `serviceRestarted`, `iosFallbackActivated`,
`abortRequested`, `httpAuthorization`.

## Capability gating

Native features that don't exist on every platform are gated. Feature-detect instead of
branching on platform; a gated call off-platform throws a typed `CapabilityError`.

```ts
if (await bg.supports('driverIntelligence')) {
  const score = await bg.driver.lastTripScore();
}

// or assert-or-throw:
await bg.require('driverIntelligence');
const score = await bg.driver.lastTripScore();

const caps = await bg.capabilities(); // static register: platform, backgroundTracking,
                                       // geofencing, maxGeofences, sensorFusion, oemSettings, …
```

## Cancelable one-shots

`bg.locations.current()` accepts an `AbortSignal`. It cancels the caller's wait (the
native GPS keeps its own lifecycle):

```ts
const ac = new AbortController();
const loc = await bg.locations.current({ accuracy: 'passive', timeout: 10_000, signal: ac.signal });
```

## Disposable handles (`using`)

Handles carry `Symbol.dispose` / `Symbol.asyncDispose`, so `using` / `await using`
auto-clean at scope end. `.remove()` / `.stop()` still work if you prefer explicit.

```ts
{
  using sub = bg.locations.on((loc) => render(loc));
  await using session = await bg.tracking.start();
  // …work…
} // sub removed and session stopped automatically here
```

## Logs

`page()` reads one newest-first batch; `stream()` hides the `fromId` paging entirely:

```ts
for await (const entry of bg.logs.stream({ minLevel: 'warn' })) {
  if (entry.timestamp < cutoff) break; // stops paging — no further native calls
  report(entry);
}
```

## Driver intelligence

Enable the GPS-derived driver-insight pipeline via `configure({ driving: { enabled: true } })`.
The native core then emits driving events (subscribe via `bg.driver.on(...)`):

- `tripStart`, `tripEnd`, `moving`, `stopped` — sustained-speed state machine.
- `speeding` — when speed crosses `driving.speedLimit` (km/h).
- `hardBrake`, `rapidAcceleration`, `sharpTurn` — sensor-free heuristics from speed/bearing.
- `possibleCrash` — sudden velocity drop or accelerometer impact (`source: 'gps' | 'sensor'`).
  **Always confirm with the user before notifying anyone.** `driving.crashConfirmWindowMs`
  defers the event until the vehicle stays stopped, cancelling it if speed recovers.
- `phoneUsage` — bearing-jitter heuristic (`driving.sensorFusion: false`) or accel/gyro jitter.
- `idleStart`, `idleEnd` — stationary for ≥ `driving.idleThresholdMs` during an active trip.

```ts
await bg.configure({
  driving: {
    enabled: true,
    speedLimit: 90,             // km/h, 0 to disable
    minTripSpeed: 3.0,          // m/s
    minTripDurationMs: 30_000,
    sensorFusion: false,        // true → accel/gyro pipeline
    idleThresholdMs: 300_000,   // stillness before idleStart (default 5 min)
    idleEndThresholdMs: 30_000, // movement before idleEnd (default 30 s)
    scoring: { speeding: 30, hardBraking: 25, rapidAcceleration: 20, sharpTurn: 15, phoneUsage: 10 }, // sum 100
  },
});

bg.driver.on('tripEnd', async (e) => {
  const score = e.score ?? (await bg.driver.lastTripScore());
  if (score) console.log('overall', score.overall, '/100');
});
bg.driver.on('speeding', (e) => console.warn('over limit', e.speedKmh, 'vs', e.limitKmh));
```

### Trip scoring

When `driving.scoring` is set, each completed trip accumulates a penalty-based score.
`tripEnd` carries `score?: TripScore`; `bg.driver.lastTripScore()` retrieves it on demand.

## Priority sync

Safety-critical events (`possibleCrash`, `sos` by default) are delivered immediately via a
dedicated POST that bypasses the queue, retrying on failure and queueing offline. Configure
it under `sync.priority`:

```ts
await bg.configure({
  transport: { baseUrl: 'https://api.example.com' },
  sync: {
    path: '/sync',
    priority: {
      events: ['possibleCrash', 'sos', 'hardBrake'],
      path: '/priority',            // appended to transport.baseUrl
      retries: 3,
      retryDelaysMs: [10_000, 30_000, 60_000],
    },
  },
});

bg.sync.on('prioritySuccess', (e) => console.log(`${e.eventType} on attempt ${e.attemptNumber}`));
bg.sync.on('priorityFailed', (e) => console.warn(`${e.eventType} failed after ${e.attempts} (HTTP ${e.httpStatus})`));
```

The channel deduplicates by event timestamp so retries never double-post.

## Web limitations

The web fallback uses `navigator.geolocation` and only works while the page is alive
(`bg.capabilities()` reports `backgroundTracking: false`). Sync queue, sessions, OEM
screens, and other native-only features are no-ops or throw. Driver-intelligence calls
throw `CapabilityError` — gate with `bg.supports('driverIntelligence')`.

## Compatibility

- Capacitor `>=8.0.0`
- iOS `>=14.0`
- Android API `>=23` (Android 6.0)
- Web: foreground only via `navigator.geolocation`

## Migration

Coming from the flat v2 API (or the Cordova-era plugin)? The method-by-method mapping is
in **[MIGRATION.md](MIGRATION.md)**.

## License

Apache-2.0. See [LICENSE](LICENSE) and [NOTICE.md](NOTICE.md) for attribution to upstream
contributors.
