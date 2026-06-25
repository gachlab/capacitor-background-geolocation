#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Copyright (c) 2026 gachlab
#
# iOS geofencing E2E test (#20).
#
# Validates, on a real iOS Simulator + CoreLocation region monitoring:
#   1. Initial ENTER when the device registers a geofence it is ALREADY inside
#      (GMS INITIAL_TRIGGER_ENTER parity, synthesised from requestState).
#   2. DWELL firing, including across a background transition (suspension-resilient
#      evaluateDwell path).
#   3. The 19-region cap surfacing a geofence `error` (code 1005) for the overflow.
#
# Like e2e-ios-driving-events.sh, GPS is injected EXTERNALLY from this host shell
# (xcrun simctl location), not from inside the sandboxed XCUITest process. Here the
# injected fix is STATIONARY at the geofence center so the device is always inside.
#
# Usage (local):
#   cd <repo-root>
#   .github/scripts/e2e-ios-geofencing.sh
#   # Override sim with SIMULATOR_UDID=<udid> to reuse an already-booted device.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
EXAMPLE_IOS="${REPO_ROOT}/example-app/ios/App"
SCHEME="${XCODE_SCHEME:-AppUITests}"
SIM_NAME="${SIMULATOR_NAME:-iPhone 17}"
SIM_OS="${SIMULATOR_OS:-}"
APP_BUNDLE_ID="com.gachlab.capacitor.backgroundgeolocation.example"

# Geofence center — MUST match GF_CENTER in example-app/www/main.js.
GEO_LAT="37.3349"
GEO_LON="-122.009"

log()  { echo "[e2e-geo] $*"; }

# ---------------------------------------------------------------------------
# 1. Find / boot simulator
# ---------------------------------------------------------------------------
if [ -n "${SIMULATOR_UDID:-}" ]; then
  UDID="${SIMULATOR_UDID}"
else
  UDID=$(xcrun simctl list devices available --json \
    | python3 -c "
import json,sys
d=json.load(sys.stdin)
for rt,devs in sorted(d['devices'].items(), reverse=True):
    for dev in devs:
        if dev.get('isAvailable') and '${SIM_NAME}' in dev['name'] and ('${SIM_OS}' == '' or '${SIM_OS}' in rt):
            print(dev['udid']); exit()
")
fi
[ -z "${UDID:-}" ] && { echo "ERROR: no simulator matching '${SIM_NAME}'" >&2; exit 1; }
log "Using simulator UDID: ${UDID}"

STATE=$(xcrun simctl list devices --json \
  | python3 -c "import json,sys; d=json.load(sys.stdin); \
    [print(dev['state']) for rt,devs in d['devices'].items() for dev in devs if dev['udid']=='${UDID}']" \
  | head -1)
if [ "${STATE}" != "Booted" ]; then
  log "Booting simulator…"; xcrun simctl boot "${UDID}"; xcrun simctl bootstatus "${UDID}"
fi

# ---------------------------------------------------------------------------
# 2. Build for testing + install
# ---------------------------------------------------------------------------
DERIVED="${HOME}/Library/Developer/Xcode/DerivedData"
if [ "${SKIP_BUILD:-0}" != "1" ]; then
  log "Building for testing…"
  xcodebuild build-for-testing \
    -project "${EXAMPLE_IOS}/App.xcodeproj" \
    -scheme "${SCHEME}" \
    -destination "id=${UDID}" \
    -quiet
fi
APP_PATH=$(find "${DERIVED}" -name "App.app" -path "*/Debug-iphonesimulator/*" 2>/dev/null | head -1)
log "Installing ${APP_PATH}…"
xcrun simctl install "${UDID}" "${APP_PATH}"

log "Pre-granting location permission…"
xcrun simctl privacy "${UDID}" grant location-always "${APP_BUNDLE_ID}" 2>/dev/null || true
xcrun simctl privacy "${UDID}" grant location "${APP_BUNDLE_ID}" 2>/dev/null || true

# ---------------------------------------------------------------------------
# 3. Stationary GPS injection at the geofence center
# ---------------------------------------------------------------------------
inject_stationary_loop() {
  local udid="$1"
  while true; do
    # Re-set the same fix; CoreLocation coalesces identical coords, so jitter the
    # 6th decimal (~0.1 m) to keep fixes flowing for the evaluateDwell path.
    xcrun simctl location "${udid}" set "${GEO_LAT}1,${GEO_LON}" 2>/dev/null || true
    sleep 1
    xcrun simctl location "${udid}" set "${GEO_LAT}2,${GEO_LON}" 2>/dev/null || true
    sleep 1
  done
}

log "Starting stationary GPS injection at ${GEO_LAT},${GEO_LON}…"
xcrun simctl location "${UDID}" set "${GEO_LAT},${GEO_LON}" 2>/dev/null || true
inject_stationary_loop "${UDID}" &
GPS_PID=$!
trap 'kill "${GPS_PID}" 2>/dev/null || true; xcrun simctl location "${UDID}" clear 2>/dev/null || true' EXIT
sleep 3

# ---------------------------------------------------------------------------
# 4. Run geofence XCUITests
# ---------------------------------------------------------------------------
RESULT_BUNDLE="/tmp/e2e-ios-geofencing.xcresult"
rm -rf "${RESULT_BUNDLE}"
LOG=/tmp/e2e-ios-geofencing.log

set +e
xcodebuild test-without-building \
  -project "${EXAMPLE_IOS}/App.xcodeproj" \
  -scheme "${SCHEME}" \
  -destination "id=${UDID}" \
  -resultBundlePath "${RESULT_BUNDLE}" \
  -only-testing:AppUITests/DrivingEventsE2ETests/testGeofenceInitialEnterAndDwell \
  -only-testing:AppUITests/DrivingEventsE2ETests/testGeofenceLimitEmitsError \
  -only-testing:AppUITests/DrivingEventsE2ETests/testGeofenceDwellSurvivesBackground \
  SIMULATOR_UDID="${UDID}" \
  2>&1 | tee "${LOG}" \
       | grep -E "(Test Case|error:|XCTAssert|\\*\\* TEST|Executed)" || true
XCODE_EXIT=${PIPESTATUS[0]}
set -e

PASS=$(grep -c "Test Case '.*' passed (" "${LOG}" 2>/dev/null || true); PASS=${PASS:-0}
FAILN=$(grep -c "Test Case '.*' failed (" "${LOG}" 2>/dev/null || true); FAILN=${FAILN:-0}

echo ""
echo "────────────────────────────────────────"
if [ "${XCODE_EXIT}" -eq 0 ] && [ "${FAILN}" -eq 0 ] && [ "${PASS}" -gt 0 ]; then
  echo "✓ Geofencing iOS E2E PASSED (${PASS} passed)"
  exit 0
else
  echo "✗ Geofencing iOS E2E FAILED (passed=${PASS}, failed=${FAILN})"
  echo "--- Last 40 lines ---"; tail -40 "${LOG}" || true
  exit 1
fi
