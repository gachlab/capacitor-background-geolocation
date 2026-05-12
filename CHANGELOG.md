# Changelog

All notable changes to `@josuelmm/capacitor-background-geolocation` are tracked
here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
- Capacitor 8+ plugin scaffold (`@josuelmm/capacitor-background-geolocation`).
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
