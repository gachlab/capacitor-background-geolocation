#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Copyright (c) 2026 gachlab
#
# iOS driving-events E2E test.
#
# Responsibilities:
#   1. Boot an iPhone simulator.
#   2. Build the example-app for testing (unless SKIP_BUILD=1).
#   3. Install the app on the simulator.
#   4. Start a background GPS injection loop so speed is non-zero when the
#      plugin starts (xcrun simctl location set runs in THIS host process,
#      not inside the sandboxed XCUITest process).
#   5. Run the XCUITest suite DrivingEventsE2ETests.
#   6. Report pass/fail and exit with the right code.
#
# Usage (local):
#   cd <repo-root>
#   .github/scripts/e2e-ios-driving-events.sh
#
# Usage (CI — GitHub Actions macos-latest runner):
#   The workflow calls this script after npm ci + cap sync ios.
#   XCODE_SCHEME, SIMULATOR_NAME, and SIMULATOR_OS can be overridden via env.

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXAMPLE_IOS="${REPO_ROOT}/example-app/ios/App"
SCHEME="${XCODE_SCHEME:-AppUITests}"
SIM_NAME="${SIMULATOR_NAME:-iPhone 17}"
SIM_OS="${SIMULATOR_OS:-}"          # e.g. "iOS 26.5"; empty = any available
APP_BUNDLE_ID="com.gachlab.capacitor.backgroundgeolocation.example"

PASS=0
FAIL=0

log()  { echo "[e2e-ios] $*"; }
pass() { echo "✓ $*"; PASS=$((PASS + 1)); }
fail() { echo "✗ $*"; FAIL=$((FAIL + 1)); }

# ---------------------------------------------------------------------------
# 1. Find / boot simulator
# ---------------------------------------------------------------------------
log "Looking for simulator: ${SIM_NAME}${SIM_OS:+ (${SIM_OS})}…"

if [ -n "${SIM_OS}" ]; then
  UDID=$(xcrun simctl list devices available --json \
    | python3 -c "
import json,sys
d=json.load(sys.stdin)
for rt,devs in d['devices'].items():
    for dev in devs:
        if dev.get('isAvailable') and '${SIM_NAME}' in dev['name'] and '${SIM_OS}' in rt:
            print(dev['udid']); exit()
")
else
  UDID=$(xcrun simctl list devices available --json \
    | python3 -c "
import json,sys
d=json.load(sys.stdin)
for rt,devs in sorted(d['devices'].items(), reverse=True):
    for dev in devs:
        if dev.get('isAvailable') and '${SIM_NAME}' in dev['name']:
            print(dev['udid']); exit()
")
fi

if [ -z "${UDID:-}" ]; then
  echo "ERROR: No available simulator matching '${SIM_NAME}${SIM_OS:+ / ${SIM_OS}}'" >&2
  exit 1
fi
log "Using simulator UDID: ${UDID}"
export SIMULATOR_UDID="${UDID}"

# Boot if not already running
STATE=$(xcrun simctl list devices --json \
  | python3 -c "import json,sys; d=json.load(sys.stdin); \
    [print(dev['state']) for rt,devs in d['devices'].items() for dev in devs if dev['udid']=='${UDID}']" \
  | head -1)
if [ "${STATE}" != "Booted" ]; then
  log "Booting simulator…"
  xcrun simctl boot "${UDID}"
  sleep 10
fi

# ---------------------------------------------------------------------------
# 2. Build for testing (skip when SKIP_BUILD=1 or artefact already present)
# ---------------------------------------------------------------------------
DERIVED="${HOME}/Library/Developer/Xcode/DerivedData"
XCTEST_PATH=$(find "${DERIVED}" -name "AppUITests.xctest" -path "*/Debug-iphonesimulator/*" 2>/dev/null | head -1 || true)

if [ "${SKIP_BUILD:-0}" = "1" ] && [ -n "${XCTEST_PATH}" ]; then
  log "Skipping build — using existing: ${XCTEST_PATH}"
else
  log "Building for testing…"
  xcodebuild build-for-testing \
    -project "${EXAMPLE_IOS}/App.xcodeproj" \
    -scheme "${SCHEME}" \
    -destination "id=${UDID}" \
    -quiet
  XCTEST_PATH=$(find "${DERIVED}" -name "AppUITests.xctest" -path "*/Debug-iphonesimulator/*" 2>/dev/null | head -1)
fi

# Locate the app bundle (Runner app that hosts the XCTest)
RUNNER_APP=$(dirname "$(dirname "${XCTEST_PATH}")")
APP_PATH=$(find "${DERIVED}" -name "App.app" -path "*/Debug-iphonesimulator/*" 2>/dev/null | head -1 || true)

log "Installing app on simulator…"
xcrun simctl install "${UDID}" "${APP_PATH}"

# Pre-grant location permissions (always + when-in-use) so CoreLocation delivers
# updates without a dialog and background geolocation works correctly.
log "Pre-granting location permission…"
xcrun simctl privacy "${UDID}" grant location-always "${APP_BUNDLE_ID}" 2>/dev/null || true
xcrun simctl privacy "${UDID}" grant location "${APP_BUNDLE_ID}" 2>/dev/null || true

# ---------------------------------------------------------------------------
# 3. Background GPS injection loop
# ---------------------------------------------------------------------------
# Drive the simulated location with `simctl location start`, which makes the
# *simulator* interpolate between waypoints and issue updates at a fixed interval on
# its own — reliable regardless of host/simctl latency. This replaces the old loop of
# one-shot `simctl location set` calls, which stalled on slow CI runners (3-4 s/call,
# sometimes no fixes for 90 s+) and was the dominant E2E flake.
#
# Each cycle has two legs:
#   • high-speed: 30 m/s (108 km/h) updates every 1 s → fires `speeding`.
#   • near-stop:  0.5 m/s crawl → the 30 → 0.5 m/s drop across the leg boundary is
#                 the sharp decel that fires `possibleCrash`; held a few seconds to
#                 clear crashConfirmWindowSec.
# The short cycle repeats, so many speeding/crash opportunities land within the test
# windows on any runner — each test only needs one to register.
GPS_PID=""

inject_gps_loop() {
  local udid="$1"
  local lon=-122.0307
  local a=37.3317        # high-leg start
  local b=37.33386       # high-leg end (~240 m N of a ≈ 8 s at 30 m/s)
  local c=37.333882      # near-stop end (~2 m past b)

  # Stationary start so speed begins defined.
  xcrun simctl location "${udid}" set "${a},${lon}" 2>/dev/null || true
  sleep 1

  while true; do
    # High-speed leg — sim-driven updates at 30 m/s.
    xcrun simctl location "${udid}" start --speed=30 --interval=1 \
      "${a},${lon}" "${b},${lon}" 2>/dev/null || true
    sleep 8
    # Near-stop leg — overrides with a ~0.5 m/s crawl; the boundary decel fires crash.
    xcrun simctl location "${udid}" start --speed=0.5 --interval=1 \
      "${b},${lon}" "${c},${lon}" 2>/dev/null || true
    sleep 5
  done
}

log "Starting background GPS injection…"
inject_gps_loop "${UDID}" &
GPS_PID=$!
trap 'kill "${GPS_PID}" 2>/dev/null || true; xcrun simctl location "${UDID}" clear 2>/dev/null || true' EXIT

sleep 5   # let several fixes land and speed build up before the app starts

# ---------------------------------------------------------------------------
# 4. Run XCUITests
# ---------------------------------------------------------------------------
log "Running XCUITests (scheme=${SCHEME})…"
RESULT_BUNDLE="/tmp/e2e-ios-results.xcresult"

# One XCUITest attempt. Succeeds only if both tests pass (run>0, failed==0).
# Only the driving tests — the geofence tests share this AppUITests class but need a
# stationary fix at the geofence center, which e2e-ios-geofencing.sh injects instead.
run_attempt() {
  rm -rf "${RESULT_BUNDLE}"
  set +e
  xcodebuild test-without-building \
    -project "${EXAMPLE_IOS}/App.xcodeproj" \
    -scheme "${SCHEME}" \
    -destination "id=${UDID}" \
    -resultBundlePath "${RESULT_BUNDLE}" \
    -only-testing:AppUITests/DrivingEventsE2ETests/testSpeedingEventFires \
    -only-testing:AppUITests/DrivingEventsE2ETests/testPossibleCrashEventFires \
    SIMULATOR_UDID="${UDID}" \
    2>&1 | tee /tmp/e2e-ios-xcodebuild.log \
         | grep -E "(Test Case|error:|XCTAssert|\\*\\* TEST|Executed)" || true
  set -e
  local run fail_n
  run=$(grep -c "Test Case '.*' passed (" /tmp/e2e-ios-xcodebuild.log 2>/dev/null || true);  run=${run:-0}
  fail_n=$(grep -c "Test Case '.*' failed (" /tmp/e2e-ios-xcodebuild.log 2>/dev/null || true); fail_n=${fail_n:-0}
  [ "${fail_n}" -eq 0 ] && [ "${run}" -gt 0 ]
}

# UI E2E on a hosted simulator is intrinsically flaky for reasons unrelated to the
# code under test: WebView cold-load (Configure button not yet rendered), system
# permission dialogs, and GPS-injection timing. Retry the whole suite once — a fresh
# app launch clears those transient glitches. The driving-event *detection* logic is
# pinned by the JVM/Swift unit tests, so a retry here masks environment noise, not bugs.
ATTEMPTS=2
PASSED=0
for attempt in $(seq 1 "${ATTEMPTS}"); do
  log "XCUITest attempt ${attempt}/${ATTEMPTS}…"
  if run_attempt; then PASSED=1; break; fi
  if [ "${attempt}" -lt "${ATTEMPTS}" ]; then log "Attempt ${attempt} failed — retrying after transient failure…"; fi
done

# ---------------------------------------------------------------------------
# 5. Report (from the last attempt's log)
# ---------------------------------------------------------------------------
while IFS= read -r line; do pass "${line}"; done < <(grep "Test Case '.*' passed (" /tmp/e2e-ios-xcodebuild.log 2>/dev/null || true)
while IFS= read -r line; do fail "${line}"; done < <(grep "Test Case '.*' failed (" /tmp/e2e-ios-xcodebuild.log 2>/dev/null || true)

echo ""
echo "────────────────────────────────────────"
if [ "${PASSED}" -eq 1 ]; then
  TESTS_RUN=$(grep -c "Test Case '.*' passed (" /tmp/e2e-ios-xcodebuild.log 2>/dev/null || true)
  echo "✓ Driving events iOS E2E PASSED (${TESTS_RUN}/${TESTS_RUN}) after ${attempt} attempt(s)"
  exit 0
else
  echo "✗ Driving events iOS E2E FAILED after ${ATTEMPTS} attempts"
  echo "--- Last 40 lines of xcodebuild output ---"
  tail -40 /tmp/e2e-ios-xcodebuild.log || true
  exit 1
fi
