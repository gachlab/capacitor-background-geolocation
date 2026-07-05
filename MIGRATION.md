<!-- SPDX-License-Identifier: Apache-2.0 -->
<!-- Copyright (c) 2026 gachlab -->

# Migration to v3

v3 is a **breaking** redesign of the TypeScript API: the ~46 flat methods and
stringly-typed `addListener` calls become a **composed facade** of cohesive sub-APIs,
with a two-tier composed config, typed capability gating, and disposable handles. The
native bridge is unchanged — this is a pure TS-surface migration.

```ts
// before (v2)
import { BackgroundGeolocation } from '@gachlab/capacitor-background-geolocation';
// after (v3) — same import; it now returns the composed facade
import { BackgroundGeolocation as bg } from '@gachlab/capacitor-background-geolocation';
```

Need the old flat surface temporarily? It's still exported as `NativeBackgroundGeolocation`
(the raw proxy). Prefer migrating to the facade.

## Config: flat → two-tier composed

The ~70 flat `configure()` fields are grouped. Set shared config **once**; features
override only their delta. Wire names (`desiredAccuracy`, `url`, `syncUrl`, `httpMode`, …)
are gone from the public surface.

```ts
// before
await BackgroundGeolocation.configure({
  desiredAccuracy: 'HIGH',
  distanceFilter: 25,
  url: 'https://api.me/loc',
  syncUrl: 'https://api.me/sync',
  syncMode: 'batch',
  httpMode: 'batch',
  notificationTitle: 'Tracking',
  startForeground: true,
  stopOnTerminate: false,
});

// after
await bg.configure({
  location:     { accuracy: 'high', distanceFilter: 25 },
  transport:    { baseUrl: 'https://api.me', mode: 'batch' },
  sync:         { path: '/sync', mode: 'batch' },
  notification: { title: 'Tracking', foreground: true },
  survival:     { stopOnTerminate: false },
});
```

Key field renames: `desiredAccuracy: 'HIGH'` → `location.accuracy: 'high'` ·
`locationProvider: 'DISTANCE_FILTER'` → `location.provider: 'distanceFilter'` ·
`url` → `transport.baseUrl` (+ `sync.path` appended) · `httpMode` → `transport.mode` ·
`syncMode`/`syncThreshold`/`sync` → `sync.{ mode, threshold, auto }` ·
`notification*` → `notification.{…}` · `stationaryRadius`/`stationaryTimeout` →
`stationary.{ radius, timeout }` · `drivingEvents` → `driving`. Merge semantics: maps
deep-merge, scalars replace, `null` unsets.

## Methods

| v2 (flat) | v3 (facade) |
| --- | --- |
| `configure(opts)` | `bg.configure(base)` |
| `start()` / `stop()` | `bg.tracking.start(override?)` → `TrackingSession` · `bg.tracking.stop()` |
| `checkStatus()` | `bg.tracking.status()` |
| `getCurrentLocation(o)` | `bg.locations.current(o)` (now accepts `{ signal }`) |
| `getStationaryLocation()` | `bg.locations.stationary()` |
| `getLocations()` | `bg.locations.all()` |
| `getValidLocations()` | `bg.locations.pending()` |
| `getValidLocationsAndDelete()` | `bg.locations.pending({ consume: true })` |
| `deleteLocation({ locationId })` | `bg.locations.delete(id)` |
| `deleteAllLocations()` | `bg.locations.clear()` |
| `forceSync()` / `clearSync()` | `bg.sync.flush()` · `bg.sync.clear()` |
| `getPendingSyncCount()` → `{ count }` | `bg.sync.pending()` → `number` |
| `startSession()` / `clearSession()` | `bg.recordings.start()` · `bg.recordings.clear()` |
| `getSessionLocations()` | `bg.recordings.locations()` |
| `getSessionLocationsCount()` → `{ count }` | `bg.recordings.count()` → `number` |
| `addGeofences({ geofences })` | `bg.geofences.add(list)` |
| `removeGeofences({ ids })` | `bg.geofences.remove(ids?)` |
| `getGeofences()` | `bg.geofences.list()` |
| `checkPermissions()` / `requestPermissions()` | `bg.permissions.check()` · `bg.permissions.request()` |
| `requestBackgroundLocationPermission()` | `bg.permissions.requestBackground()` |
| `requestActivityRecognitionPermission()` | `bg.permissions.requestActivity()` |
| `requestNotificationPermission()` | `bg.permissions.requestNotifications()` |
| `showAppSettings()` / `openSettings()` | `bg.permissions.openSettings('app')` |
| `showLocationSettings()` | `bg.permissions.openSettings('location')` |
| `getDiagnostics()` | `bg.diagnostics.report()` |
| `getPluginVersion()` → `{ version }` | `bg.diagnostics.version()` → `string` |
| `getBackgroundKillReason()` | `bg.diagnostics.killReason()` |
| `getCapabilities()` | `bg.capabilities()` |
| `isIgnoringBatteryOptimizations()` → `{ whitelisted }` | `bg.diagnostics.oem.isIgnoringBatteryOptimizations()` → `boolean` |
| `requestIgnoreBatteryOptimizations()` | `bg.diagnostics.oem.requestIgnoreBatteryOptimizations()` |
| `openBatterySettings()` | `bg.diagnostics.oem.openBatterySettings()` |
| `openAutoStartSettings()` | `bg.diagnostics.oem.openAutoStartSettings()` |
| `getManufacturerHelp()` | `bg.diagnostics.oem.manufacturerHelp()` |
| `getTripScore()` | `bg.driver.lastTripScore()` _(gated)_ |
| `getConfig()` | on `NativeBackgroundGeolocation` (raw) — the facade owns the resolved base |
| `getLogEntries({ limit, fromId })` | `bg.logs.page(o)` / `bg.logs.stream(o)` |
| `startTask()` → `{ taskKey }` | `bg.platform.startTask()` → `number` |
| `endTask({ taskKey })` | `bg.platform.endTask(key)` |
| `switchMode({ mode: 0\|1 })` | `bg.platform.switchMode('background'\|'foreground')` |
| `triggerSOS(p)` | `bg.sos(p)` |
| `removeAllListeners()` | `bg.removeAllListeners()` |

## Events

`addListener(name, cb)` → typed `.on(...)` that returns a disposable `Subscription`.

```ts
// before
const h = await BackgroundGeolocation.addListener('location', cb);
h.remove();

// after — domain events on their sub-API, global events on bg.on
const sub = bg.locations.on(cb);
sub.remove();               // or: using sub = bg.locations.on(cb)

bg.on('authorization', ({ status }) => {});   // global/lifecycle
bg.geofences.on('enter', (e) => {});          // e.action === 'enter'
bg.sync.on('progress', (e) => {});
bg.driver.on('tripEnd', (e) => {});
```

**Payload shape changes** (v3 clean output):

- `authorization.status` is a **string** — `'notAuthorized' | 'authorized' | 'authorizedForeground'` (was an int).
- Geofence `action` is **lowercase** — `'enter' | 'exit' | 'dwell'` (was `'ENTER'`/…).
- `error.code` is a **string** — `'permissionDenied' | 'unavailable' | 'timeout'`.
- Event names are camelCase: `abort_requested` → `abortRequested`, `http_authorization` → `httpAuthorization`.
- `Geofence.loiteringDelay` → `Geofence.loiteringDelayMs`.

## Capability gating replaces platform branching

```ts
// before
if (Capacitor.getPlatform() !== 'web') {
  const s = await BackgroundGeolocation.getTripScore(); // web → null
}

// after — feature-detect; gated call throws CapabilityError off-platform
if (await bg.supports('driverIntelligence')) {
  const s = await bg.driver.lastTripScore();
}
```
