# Changelog

All notable changes to `@josuelmm/capacitor-background-geolocation` are tracked
here. The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and the project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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
