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
# Inject a stationary fix first, then drive north at ~30 m per 0.5 s
# (= 60 m/s ≈ 216 km/h — well above the 90 km/h speedLimit in the config).
# After 20 moving fixes, slow to ~10 km/h for 3 fixes, then stop (crash sim).
GPS_PID=""

inject_gps_loop() {
  local udid="$1"
  local lat=37.3317
  local lon=-122.0307
  local step=0.00027   # ~30 m per fix at this latitude
  local i=0

  # Stationary start
  xcrun simctl location "${udid}" set "${lat},${lon}" 2>/dev/null || true
  sleep 1

  while true; do
    lat=$(python3 -c "print(${lat} + ${step})")
    xcrun simctl location "${udid}" set "${lat},${lon}" 2>/dev/null || true
    sleep 0.5
    i=$((i + 1))

    # After 30 fast fixes: slow down + stop (possibleCrash window)
    if [ $i -eq 30 ]; then
      step=0.00003   # ~3 m per fix ≈ slow roll
    fi
    if [ $i -eq 35 ]; then
      # Near-stop: micro-step ~0.0000045° ≈ 0.5 m per fix → speed ≈ 1 m/s < 1.5 m/s.
      # Distinct positions so CoreLocation delivers every fix (deduplication workaround).
      # 6 fixes × 0.5 s = 3 s; crash confirms after 5 (2.5 s > crashConfirmWindowSec=2 s).
      for j in 1 2 3 4 5 6; do
        lat=$(python3 -c "print(${lat} + 0.0000045)")
        xcrun simctl location "${udid}" set "${lat},${lon}" 2>/dev/null || true
        sleep 0.5
      done
      # Reset loop for a second trip cycle
      i=0
      step=0.00027
    fi
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
rm -rf "${RESULT_BUNDLE}"

set +e
xcodebuild test-without-building \
  -project "${EXAMPLE_IOS}/App.xcodeproj" \
  -scheme "${SCHEME}" \
  -destination "id=${UDID}" \
  -resultBundlePath "${RESULT_BUNDLE}" \
  SIMULATOR_UDID="${UDID}" \
  2>&1 | tee /tmp/e2e-ios-xcodebuild.log \
       | grep -E "(Test Case|error:|XCTAssert|\\*\\* TEST|Executed)" || true
XCODE_EXIT=$?
set -e

# ---------------------------------------------------------------------------
# 5. Parse results
# ---------------------------------------------------------------------------
TESTS_RUN=$(grep -c "Test Case '.*' passed (" /tmp/e2e-ios-xcodebuild.log 2>/dev/null || true); TESTS_RUN=${TESTS_RUN:-0}
TESTS_FAIL=$(grep -c "Test Case '.*' failed (" /tmp/e2e-ios-xcodebuild.log 2>/dev/null || true); TESTS_FAIL=${TESTS_FAIL:-0}

while IFS= read -r line; do
  pass "${line}"
done < <(grep "Test Case '.*' passed (" /tmp/e2e-ios-xcodebuild.log 2>/dev/null || true)

while IFS= read -r line; do
  fail "${line}"
done < <(grep "Test Case '.*' failed (" /tmp/e2e-ios-xcodebuild.log 2>/dev/null || true)

echo ""
echo "────────────────────────────────────────"
if [ "${XCODE_EXIT}" -eq 0 ] && [ "${FAIL}" -eq 0 ] && [ "${TESTS_RUN}" -gt 0 ]; then
  echo "✓ Driving events iOS E2E PASSED (${TESTS_RUN}/${TESTS_RUN})"
  exit 0
else
  echo "✗ Driving events iOS E2E FAILED (passed=${TESTS_RUN}, failed=${FAIL})"
  echo "--- Last 40 lines of xcodebuild output ---"
  tail -40 /tmp/e2e-ios-xcodebuild.log || true
  exit 1
fi
