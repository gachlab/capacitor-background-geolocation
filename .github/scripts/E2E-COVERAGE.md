<!-- SPDX-License-Identifier: MIT -->
# End-to-end test coverage

Per-platform E2E suites and what each exercises on real devices/emulators/browsers.
Unit tests (Android JVM + instrumented, iOS XCTest, web `node:test`) cover the logic;
these E2E suites validate the full native/browser integration.

| Area | iOS | Android | Web |
|---|---|---|---|
| Geofencing (ENTER/EXIT/DWELL/`geofenceError`) | `e2e-ios-geofencing.sh` (simulator) | `e2e-android-geofencing.sh` (GMS emulator) | `e2e-web/` Playwright (Chromium + mocked geolocation) |
| Driving events — **GPS** (crash, hard-brake, sharp-turn, speeding, phone-usage-by-GPS) | `e2e-ios-driving-events.sh` | `e2e-driving-events.sh` | — (no driving detector on web, by design) |
| Background survival | — | `e2e-background-survival.sh` | — (web is foreground-only) |

## Intentionally unit-only (no E2E)

Two areas are validated by unit tests only, for concrete reasons — not oversight:

1. **Sensor-based detection** — crash-by-sensor (`#23`) and phone-usage-by-sensor.
   The `SensorFusionDetector` needs sustained accelerometer **and** gyroscope jitter at
   ~50 Hz, with the app foregrounded during an active trip. There is no reliable way to
   drive this on CI hardware: `adb emu sensor set` cannot deterministically reproduce the
   sustained-window logic, and iOS offers **no API to inject CoreMotion data into the
   simulator** at all. The example app therefore runs its driving E2E with
   `sensorFusion: false`. Coverage lives in unit tests instead: `CrashImpactGateTest`,
   `PhoneUsageJitterGateTest`, and `sensorPhoneUsageReachesScore` (Android) /
   `testExternalPhoneUsageReachesScore` (iOS).

2. **Aggregate trip score (`TripScore`)** — emitted only at `tripEnd`, which requires the
   device to hold a stop for `stoppedDuration`. Forcing that in the shared driving E2E
   would lower `stoppedDuration` globally and end the trip before the GPS-crash confirm
   window (crash detection requires `TRIP_ACTIVE`), breaking the existing crash/phone
   scenarios. The detector→score pipeline is covered end-to-end by unit tests
   (`PhoneUsageScoringTest`, `DrivingEventsDetectorTest.TripScoreTests` on Android;
   `DrivingEventsDetectorTests` score tests on iOS).
