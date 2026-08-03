# Changelog

All notable changes to `@gachlab/capacitor-background-geolocation` are tracked
here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [3.0.1] - 2026-08-03

### Fixed

- **The body template survived only in memory: every config read from disk lost
  it.** Of the two config serializers, `toJSObject` (config → JavaScript) wrote
  the template and `toJSONObject` (config → SQLite, the one `ConfigDAO`
  persists) did not. Any consumer that re-read the config from storage got
  `template = null`, fell through to `LocationTemplateFactory.empty()` in
  `PostLocationTask`/`BackgroundSync`, and POSTed the flat default payload
  instead of the configured shape — silently, since the plugin cannot tell that
  the server rejected it. It hit the three storage readers: service
  start/reconfigure, every sync flush, and boot. Both serializers now share a
  single `templateToJSON` helper so they cannot drift apart again. ([#50])

[#50]: https://github.com/gachlab/capacitor-background-geolocation/issues/50

## [3.0.0] - 2026-07-06

### v3 API redesign (BREAKING)

The rule: **modernize the structure, not the syntax.** The Capacitor bridge stays
`Promise` + `addListener`; the win is a composed facade over a flat native contract.
The only consumer is `drivers-web`, so the break is intentional. See the blueprint
for the full rationale.

#### Changed (BREAKING)

- **`BackgroundGeolocation` is now a FACTORY**, not a pre-built instance: `const bg =
BackgroundGeolocation()` (zero-config → shared singleton) or `BackgroundGeolocation({ native })`
  to inject a bridge (testing). The whole SDK is functional with constructor-function DI — each
  sub-API is a `Name(deps)` factory closing over its state; dependencies are injected, not `new`ed.
- **Flat plugin surface → composed facade.** `import { BackgroundGeolocation }` is now
  an instance of composed sub-APIs: `bg.tracking` · `bg.locations` · `bg.geofences` ·
  `bg.sync` · `bg.driver` · `bg.logs` · `bg.permissions` · `bg.diagnostics` ·
  `bg.recordings` · `bg.platform`, plus top-level `bg.on()`, `bg.sos()`,
  `bg.capabilities()`, `bg.supports()`. The raw flat proxy is still available as
  `NativeBackgroundGeolocation` (escape hatch).
- **Composed, two-tier config** replaces the ~70 flat fields. `bg.configure(base)` sets
  the shared tier once (`location`/`transport`/`notification`/`survival`/…); features
  override only their delta via a `base ⊕ session ⊕ per-call` cascade (deep-merge maps,
  replace scalars, `null` = unset). No more `[extra: string]` index signature — a typed
  `native` escape hatch remains for raw wire flags.
- **Clean output shapes**: string error codes (`'unavailable'`), `AuthorizationStatus`
  strings, lowercase geofence `action` (`enter`/`exit`/`dwell`), `Geofence.loiteringDelayMs`.
- **Cordova-compat layer retired** (the old ~1600-line `definitions.ts`).

#### Added

- **Capability gating** — `bg.capabilities()` (memoized, rejections not cached) +
  `bg.supports(cap)`; gated APIs (`bg.driver.*`, `bg.diagnostics.oem.*`) throw a typed
  `CapabilityError` off-platform instead of returning silent no-ops.
- **`bg.locations.current({ signal })`** — `AbortSignal` on the one-shot (cancels the
  caller's wait; native GPS keeps its own lifecycle) + `cancelCurrentLocation()`.
- **`bg.logs.stream()`** — async-generator that hides `fromId` paging; breaking early
  stops fetching.
- **Disposable handles** — `using sub = bg.locations.on(cb)` and
  `await using s = await bg.tracking.start()` auto-clean at scope end (`Symbol.dispose`
  / `Symbol.asyncDispose`, polyfilled). Additive: `.remove()` / `.stop()` still work.
- **`transport.mode`** → wire `httpMode` (live-POST batch/single), previously reachable
  only via the escape hatch.

#### Internal

- Native contract segregated into role interfaces (`definitions/roles.ts`), composed by
  intersection; flat wire config in `definitions/wire.ts`.
- `toFlatConfig` **coverage guard test** — fails if a `NativeConfig` wire key has no
  mapping (compile-time exhaustive table + runtime maximal-config check).
- TypeScript target bumped es2017 → **es2022** (+ `esnext.disposable` lib) for async
  generators and `using`.

#### Finalization (altitude + correctness)

- **Single sticky-replay primitive** (`stream.ts`): one `stickyReplay()` now owns the
  ordering guard reimplemented three times before (native stream, `config.on`,
  `bg.on('authorization')`). Fixes the `config.on()` double-delivery race and the
  authorization sticky delivering `{ status: undefined }`. `makeAliasedOn()` dedups the
  sub-API `.on()` bodies; `bg.config` is exported as its authored interface.
- **`configChanged` native event** — the persisted base config is now a native
  source-of-truth: a write from another facade instance (or the raw proxy) notifies every
  `bg.config` subscriber (self-echo deduped).
- **`SyncConfig.method`** — sync HTTP method is independent of `transport.method` (falls
  back to it), no longer hard-tied.
- **web**: `locations.pending({ consume: true })` no longer deletes already-synced fixes —
  only the un-synced (valid) locations are consumed.
- **Android**: `getConfig()` caches in-memory (parity with iOS); `getCurrentLocation`
  honors `maximumAge`; `PluginCall` Long options (`timeout`, `maximumAge`, `locationId`)
  are parsed robustly (Capacitor's `getLong` silently dropped bridged JS numbers, so
  `timeout` was never honored and `deleteLocation` always rejected).
- **Android**: `bg.locations.all()` no longer returns soft-deleted rows — a `delete(id)`'d
  (or synced-out) location is excluded, matching iOS (`status != 0`) and web (hard delete).
- **Native one-shot hardening**: Android fused fix resolves the waiter exactly once
  (atomic with cancel/drain); iOS one-shot starts off the blocking thread and its
  semaphore signals at most once.

## [2.0.0] - 2026-06-25

Legacy cleanup of the Cordova-era inheritance. The only consumer is `drivers-web`,
so these breaking changes are intentional.

### Removed (BREAKING)

- **Cordova-era plugin methods** `isLocationEnabled`, `watchLocationMode`,
  `stopWatchingLocationMode` (iOS + Android) — not part of the TypeScript contract;
  the latter two were no-ops.
- **Android**: the `LocationManager` fallback inside `DistanceFilterLocationProvider`.
  This provider is now **Play Services (fused) only** — devices without Google Play
  Services are no longer supported by it (use the `raw` provider for those).
- **Android**: the legacy per-column config storage in the `configuration` table and
  its fallback hydration — config is now a single `config_json` blob.

### Changed (BREAKING)

- **Android**: the on-disk database was renamed `cordova_bg_geolocation.db` →
  `gachlab_bg_geolocation.db` and reset to v1. The ~190 lines of Cordova migration
  history were dropped. **Locations/config persisted by a previous version are not
  carried over** on upgrade (the unsynced queue starts empty).

### Internal

- Removed dead `JosueLMM`/`MAURLocation` references; copyright is uniformly `gachlab`.

## [1.7.0] - 2026-06-24

### Fixed

- **Android** (geofencing, critical): `GeofenceManager` built its transition
  `PendingIntent` with `FLAG_IMMUTABLE`, which GMS `GeofencingClient.addGeofences`
  rejects with "PendingIntent must be mutable" (opStatusCode 10) on **API 31+**. This
  silently broke **all** geofence transitions on Android 12+ (no ENTER/EXIT/DWELL ever
  fired). Now uses `FLAG_MUTABLE` on API ≥ 31.

### Added

- **`geofenceError` event** (all platforms): a geofence that fails to register or
  monitor now surfaces a dedicated `geofenceError` (`{ id?, message }`) instead of
  failing silently — iOS (region cap / monitoring failure), Android (GMS registration
  failure or invalid geofence), web (invalid geofence).
- **Phone-usage detection by sensor on Android**: mirror of the iOS
  `SensorFusionDetector` phone-usage path (accel/gyro jitter), gated by `sensorFusion`
  so it does not double-count with the GPS bearing-jitter path.
- **Sensor phone-usage feeds the trip score** on both native platforms.
- **Web geofencing**: real JS geofence engine over `navigator.geolocation`
  (initial ENTER when already inside, EXIT, DWELL, `geofenceError`) — previously a no-op.

### Changed

- **iOS**: `SensorFusionDetector` phone-usage is now gated by `sensorFusion`, fixing a
  double-count with the GPS phone-usage path when `sensorFusion` was off.
- **Tooling**: migrated to the native **TypeScript 7** compiler. Type-aware ESLint
  (`@typescript-eslint`) is temporarily paused (incompatible with the native compiler);
  `lint` runs Prettier and `typecheck` (`tsc --noEmit`) is the type gate.

### Tests

- Geofencing E2E across all three platforms: iOS (simulator), Android (GMS emulator),
  and web (Playwright + Chromium with mocked geolocation).

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

- **Android**: set request headers _before_ `requestMethod`/`doOutput` on the
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
