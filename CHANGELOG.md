# Changelog

All notable changes to `@gachlab/capacitor-background-geolocation` are tracked
here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.6.7] - 2026-06-18

### Fixed
- **iOS** (`BackgroundSync`): guard `syncUrl` / documents-directory resolution to
  avoid a force-unwrap crash when sync is not yet configured (#42).

## [1.6.6] - 2026-06-18

### Fixed
- **Android**: resolve `@`-placeholders inside nested objects and arrays in the
  location POST template, not just top-level keys (#40).

## [1.6.5] - 2026-06-18

### Fixed
- **Android**: set request headers *before* `requestMethod`/`doOutput` on the
  `HttpURLConnection`. Real fix for the intermittent HTTP `-1` responses (#38).

## [1.6.4] - 2026-06-18

### Fixed
- **Android**: set request headers before `setFixedLengthStreamingMode` as a
  first attempt at the HTTP `-1` failures (superseded by 1.6.5) (#37).

## [1.6.3] - 2026-06-18

### Fixed
- **Android**: escape `}` in the `UrlTemplateResolver` regex — strict ART
  runtimes threw on class init otherwise (#35).

## [1.6.2] - 2026-05-30

### Fixed
- **Android**: replace `ThreadLocal.withInitial` with a subclass override for
  API 23+ compatibility (#33).

## [1.6.1] - 2026-05-29

### Fixed
- **Android build**: declare Kotlin via buildscript classpath instead of
  `apply plugin: 'kotlin-android'` — AGP 9.x integrates Kotlin and applying it
  explicitly threw "extension 'kotlin' already registered" (#31, #32).

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

## [1.5.0] - 2026-05-27

### Added
- **Priority sync** for safety-critical events. Configured events (`possibleCrash` and `sos`
  by default) are POSTed immediately via a dedicated channel that bypasses the regular sync
  queue. The channel deduplicates by event timestamp, retries with configurable backoff, and
  queues events in memory when offline (flushed as soon as connectivity is restored).
  - New config fields: `prioritySyncEvents`, `prioritySyncUrl`, `prioritySyncRetries`,
    `prioritySyncRetryDelays`.
  - New events: `prioritySyncSuccess` (`{ eventType, attemptNumber }`),
    `prioritySyncFailed` (`{ eventType, httpStatus, attempts }`).
  - Android: `PrioritySyncManager` using `NetworkCallback` for connectivity detection.
  - iOS: `PrioritySyncManager.swift` using `NWPathMonitor` + `URLSession`.

---

## [1.4.0] - 2026-05-27

### Added
- **Idle detection** during active trips. Fires `idleStart` when the vehicle has been
  stationary for ≥ `drivingEvents.idleThresholdMs` (default 5 min); fires `idleEnd` when
  movement resumes. `idleEnd` payload includes `durationMs`.
  New config fields: `drivingEvents.idleThresholdMs`, `drivingEvents.idleEndThresholdMs`.
- **Per-trip driver behavior score** (`getTripScore()` + `tripEnd.score`). Penalty-based score
  0–100 across speeding, hard-braking, rapid-acceleration, sharp-turn, and phone-usage events.
  Category weights are configurable via `drivingEvents.scoring`; weights must sum to 100.
  `tripEnd` now includes `score?: TripScore`. New method: `getTripScore()`.
  - Android: `TripScore.kt` + `ScoreCalculator.kt` (stateless, JVM-testable).
  - iOS: `TripScore.swift` + `ScoreCalculator.swift` equivalents.
  - New unit tests: `ScoreCalculatorTest` (6 cases) + updated `DrivingEventsDetectorTest`.

---

## [1.3.0] - 2026-05-27

### Added
- **Geofencing API**: `addGeofences`, `addGeofence`, `removeGeofence`, `removeGeofences`,
  `removeAllGeofences`, `getGeofences`. Zones persist across service restarts.
  - Android: `GeofencingClient` (Google Play Services); `GeofenceBroadcastReceiver`;
    geofences stored in SQLite and re-registered on startup.
  - iOS: `CLCircularRegion` via `CLLocationManager.startMonitoring(for:)`; dwell detection
    via per-region `Timer`; zones stored in `UserDefaults`. **Limit: 19 user geofences**
    (one slot reserved for the significant-change monitor).
  - New events: `geofenceEnter`, `geofenceExit`, `geofenceDwell` — each carries
    `{ geofenceId, label, location, metadata, dwellMs? }`.
  - New `GeofenceConfig` type: `{ id, latitude, longitude, radius, label?, notifyOnEnter?,
    notifyOnExit?, notifyOnDwell?, dwellMilliseconds?, metadata? }`.
- **Trip–geofence integration**: `drivingEvents.tripStartGeofenceIds` / `tripEndGeofenceIds` —
  auto-start or auto-end a trip when the device crosses a nominated geofence boundary.

---

## [1.2.0] - 2026-05-27

### Added
- **WorkManager headless task** (Android): `registerHeadlessTask()` now schedules a
  `PeriodicWorkRequest` via `WorkManager` instead of the previous `JsEvaluator` WebView.
  This survives Android 12+ background-activity restrictions that would prevent launching
  a WebView from a killed process. New config field: `headlessTaskTimeoutMs`.
- **iOS background fallback config**: `iosBackgroundFallback: 'significantChanges' |
  'regionMonitoring' | 'none'` lets apps pick the strategy used when iOS suspends regular
  location updates. New event: `iosFallbackActivated → { reason: string }`.
- **`getBackgroundKillReason()`** available on both platforms. Android returns the last
  watchdog / OOM / system-kill cause persisted in SQLite; iOS returns `{ reason: null,
  timestamp: null }` (iOS does not expose a kill reason).

---

## [1.1.0] - 2026-05-27

### Added
- **Android core rewritten in Kotlin** under `com.gachlab.*`. No Java remains in the main
  source tree. Eliminated runtime dependencies: `gson`, `slf4j`, `logback-android`,
  `jparkie-promise`, `android-permissions`. SyncAdapter / AuthenticatorService /
  ContentProvider replaced by WorkManager + `LocationDAO` / `SessionDAO` / `ConfigDAO`.
- **iOS core rewritten in Swift**. All `MAURBackgroundSync`, `BGFacade`, and provider
  classes ported to Swift 5. No Objective-C in `Sources/`.
- **`DrivingEventsDetector`** — pure Kotlin state machine, zero Android imports, fully
  testable on the JVM without an emulator. Covers trip lifecycle, speeding, hard-brake,
  rapid-acceleration, sharp-turn, and crash detection.
- **`OemHelper`** — auto-start / background-activity intents for Xiaomi, Huawei, Oppo,
  Vivo, Samsung, OnePlus, and Asus.
- **`serviceRestarted`** event: fires when the Android foreground service is restarted by
  the watchdog, an OS kill, or the boot receiver.
  Payload: `{ reason: 'watchdog' | 'system_kill' | 'boot' }`.
- **Kill diagnostics**: `getBackgroundKillReason()` on Android persists the most recent
  watchdog / OOM / system-kill cause in SQLite for post-mortem debugging.
- **E2E test infrastructure**: `e2e-background-survival.sh` + `android-e2e` CI job.
  Installs the example-app APK, grants permissions, injects GPS fixes via `adb emu geo fix`,
  and asserts ≥ 5 locations stored in the plugin's SQLite DB.
- **Unit tests**: `DrivingEventsDetectorTest` (JUnit 5), `ConfigMapperTest` (Android);
  `DrivingEventsDetectorTests`, `BackgroundGeolocationPluginTests` (iOS XCTest).

### Removed
- All Objective-C source files from iOS (`MAUR*` prefix classes superseded by Swift).
- `com.marianhello.*`, `com.evgenii.*`, `ru.andremoniy.*`, `org.apache.*`, `org.chromium.*`
  Java packages from Android.

---

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

Pulled in v4.5.4 native bug-fixes from upstream cordova plugin.

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

First public release.

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
- TypeScript API mirroring the legacy Cordova plugin surface: `configure`, `start`, `stop`, `getCurrentLocation`,
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
