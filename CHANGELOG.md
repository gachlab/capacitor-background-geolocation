# Changelog

All notable changes to `@gachlab/capacitor-background-geolocation` are tracked
here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.6.0] - 2026-05-27

### Added
- **`crashConfirmWindowMs`** (Android + iOS): deferred `possibleCrash` confirmation window.
  When > 0, the crash event is held until the vehicle stays stopped for the configured ms
  after the velocity drop; if speed recovers before the window elapses the event is
  cancelled. Default `0` preserves existing fire-immediately behaviour.
- **`phoneUsageWhileDriving` GPS heuristic** (Android + iOS): bearing-jitter detection for
  the case where `sensorFusion: false`. Fires `phoneUsageWhileDriving` when ≥ 3 bearing
  oscillations (5–25° deltas at 5–80 km/h) occur within `phoneUsageWindowMs`.
  New config fields: `sensorFusion`, `phoneUsageWindowMs`, `phoneUsageCooldownMs`.
- **E2E driving-events test** (`.github/scripts/e2e-driving-events.sh`): three scenarios —
  crash detection, phone-usage jitter, and crash-confirm cancellation on speed recovery —
  wired into CI as the `android-e2e-driving` job.
- **Web implementation** (`src/web.ts`): location store (SQLite-like in-memory), session
  support, and sync queue. Methods that require native GPS resolve with empty/stub results
  on web as documented.

### Removed
- `registerHeadlessTask` removed from the iOS bridge. The method was a no-op on iOS since
  headless tasks are an Android-only concept; keeping it caused confusion and stale test
  coverage. Android support is unchanged.

## [1.0.2] - 2026-05-25

### Fixed
- **iOS background sync: HTTP 400 "Invalid request payload JSON format".** `MAURBackgroundSync`
  was collecting all pending locations, serialising them into a JSON **array**, and uploading
  the entire array in a single `NSURLSessionUploadTask`. Strict REST backends (Fastify/Hapi
  with schema validation) expect a single JSON **object** per request — matching what the
  single-POST path (`MAURPostLocationTask`) sends. Fix rewrites `sync:withTemplate:` to
  iterate locations and create one `uploadTaskWithRequest:fromFile:` per location so every
  request body is a single serialised location object. Progress/success/failure delegate
  callbacks and file cleanup remain per-task.

## [1.0.1] - 2026-05-25

### Fixed
- **iOS background sync: HTTP 415 on every location POST.** `MAURBackgroundSync`
  was calling `addValue:forHTTPHeaderField:` for all `httpHeaders` entries
  including `Content-Type`, appending a second `application/json` value to the
  header already set by the hardcoded `setValue:` above it. The resulting
  `Content-Type: application/json, application/json` was rejected by strict
  servers with HTTP 415 Unsupported Media Type. Fix mirrors the existing guard
  in `MAURPostLocationTask` (skip `Content-Type` in the `addValue:` loop).

## [1.0.0] - 2026-05-13

Pulled in the v4.5.4 native bug-fix from `@josuelmm/cordova-background-geolocation`.

### Fixed
- **HTTP POST: skip null / `JSONObject.NULL` / `NSNull` values when
  serialising form-urlencoded bodies.** Previously these were sent as the
  literal string `"null"` (or `"<null>"` on iOS), which Traccar's
  `OsmAndProtocolDecoder` rejects with HTTP 400 / `NumberFormatException`
  on inputs like `speed=null`. Placeholders that resolve to no value
  (`@speed`, `@events`, `@battery`, …) are now omitted from the body.
  Affects both platforms.
  - Android: `com.marianhello.bgloc.HttpPostService.toQueryString`
  - iOS: `MAURPostLocationTask` form-encoder branch

## [1.0.0] - 2026-05-12

First public release. Full TypeScript-API parity with
`@josuelmm/cordova-background-geolocation`.

### Added
- **40 plugin methods** mirroring the Cordova spec. New entries since 0.1.0:
  `switchMode`, `getLocations`, `getValidLocationsAndDelete`, `clearSync`,
  `getPendingSyncCount`, `startSession`, `getSessionLocations`, `clearSession`,
  `getSessionLocationsCount`, `getDiagnostics`,
  `isIgnoringBatteryOptimizations`, `requestIgnoreBatteryOptimizations`,
  `openBatterySettings`, `openAutoStartSettings`, `getManufacturerHelp`,
  `getPluginVersion`, `requestBackgroundLocationPermission`,
  `requestActivityRecognitionPermission`, `requestNotificationPermission`,
  `openSettings`, `triggerSOS`.
- **28 event-listener overloads** covering the v3.5/v4.x driver-insight surface:
  `heartbeat`, `syncStart`, `syncProgress`, `syncSuccess`, `syncError`,
  `tripStart`, `tripEnd`, `moving`, `stopped`, `speeding`, `providerChange`,
  `sos`, `hardBrake`, `rapidAcceleration`, `sharpTurn`, `possibleCrash`,
  `phoneUsageWhileDriving` in addition to the existing 11 lifecycle events.
- Full `ConfigureOptions` interface (60+ fields) including sync transport
  (`headers`, `httpMethod`, `httpMode`, `bodyTemplate`, …), heartbeat,
  mock-location policy, battery stamping, WakeLock policy, stationary tuning,
  accuracy filtering, and the `drivingEvents` configuration block.
- New TS types: `ConfigureOptions` (with `LocationOptions` alias),
  `StationaryLocation`, `LocationError`, `BackgroundGeolocationError`,
  `Activity`, `ServiceStatus` (with `Status` alias), `Diagnostics`,
  `HeadlessTaskEvent`, `PermissionRequestResult`, `ActivityType`,
  `LocationErrorCode`, `HeadlessTaskEventName`.
- `@awesome-cordova-plugins`-style compatibility enums and aliases:
  `BackgroundGeolocationEvents`, `BackgroundGeolocationLocationCode`,
  `BackgroundGeolocationNativeProvider`,
  `BackgroundGeolocationLocationProvider`,
  `BackgroundGeolocationAuthorizationStatus`,
  `BackgroundGeolocationLogLevel`, `BackgroundGeolocationProvider`,
  `BackgroundGeolocationAccuracy`, `BackgroundGeolocationMode`,
  `BackgroundGeolocationIOSActivity`, plus `BackgroundGeolocationConfig`,
  `BackgroundGeolocationResponse`,
  `BackgroundGeolocationCurrentPositionConfig`,
  `BackgroundGeolocationLogEntry`.
- Web fallback implementations for diagnostics, OEM helpers, sync/session
  count queries, notification-permission probing, plugin-version reporting,
  and `triggerSOS` emission.
- Example app coverage for `getDiagnostics`, `getPluginVersion`, `triggerSOS`,
  and additional event subscriptions (`activity`, `authorization`,
  `heartbeat`, `tripStart`, `tripEnd`, `speeding`, `sos`).

### Changed
- Renamed `LocationOptions` → `ConfigureOptions` (with a back-compat alias).
- Renamed `Status` → `ServiceStatus` (with a back-compat alias).
- Tightened `LogEntry` shape to match the Cordova spec (`timestamp`, `level`,
  `message`, `stackTrace`).

- **Android headless task** (`headlessTask(fn)`) — port of the upstream
  Cordova feature. The callback runs in an isolated WebView context when
  the host activity has been killed (`stopOnTerminate: false`). iOS resolves
  the call as a no-op; Web throws `unimplemented`.

### Removed
- Placeholder `watchLocationMode` / `stopWatchingLocationMode` methods (not
  present in the Cordova spec).

## [0.1.0] - 2026-05-12

Initial scaffold and native bridges.

### Added
- Capacitor 8+ plugin scaffold (`@gachlab/capacitor-background-geolocation`).
- TypeScript API mirroring the legacy `@josuelmm/cordova-background-geolocation`
  surface: `configure`, `start`, `stop`, `getCurrentLocation`,
  `getStationaryLocation`, `getValidLocations`, `getConfig`, `deleteLocation`,
  `deleteAllLocations`, `isLocationEnabled`, `showAppSettings`,
  `showLocationSettings`, `watchLocationMode`, `stopWatchingLocationMode`,
  `getLogEntries`, `checkStatus`, `startTask`, `endTask`, `forceSync`,
  `checkPermissions`, `requestPermissions`.
- Event listeners: `location`, `stationary`, `activity`, `error`,
  `authorization`, `start`, `stop`, `foreground`, `background`,
  `abort_requested`, `http_authorization`.
- Android bridge over the `com.marianhello.bgloc` native core, with full
  permission handling for fine/coarse location, activity recognition, and
  notifications. `ACCESS_BACKGROUND_LOCATION` is intentionally NOT declared
  by the library — consumer apps opt in.
- iOS bridge over the `MAUR*` native core, including a SwiftPM target and a
  CocoaPods podspec with bundled `INTULocationManager`, `FMDB`, and
  `CocoaLumberjack`.
- Web fallback using `navigator.geolocation` (foreground only).
- Example app at `example-app/` exercising the critical methods.
- GitHub Actions workflows: build (web/android/iOS) and release (npm publish
  on `v*` tags).

### Notes
- Not yet published to npm. First public release will be tagged `v1.0.0`.
