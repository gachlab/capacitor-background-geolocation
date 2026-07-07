#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# Copyright (c) 2026 gachlab
#
# E2E: Android geofencing (#20) — parity with the iOS geofencing E2E
# (.github/scripts/e2e-ios-geofencing.sh).
#
# Validates, against Google Play Services GeofencingClient on a GMS-capable
# emulator, the same behaviors the iOS suite covers:
#   1. Initial ENTER when registering a geofence the device is ALREADY inside
#      (GMS INITIAL_TRIGGER_ENTER).
#   2. DWELL after the loitering delay (GMS OS-level dwell; survives backgrounding).
#   3. A registration failure surfacing the `geofenceError` event. The iOS 19-region
#      cap does not exist on Android (GMS allows 100), so the Android trigger is an
#      invalid geofence (radius 0) — the parity is at the event level.
#
# REQUIRES a Google-APIs/Play emulator image — geofencing needs GMS. The default
# AOSP image used by the driving/survival E2E will NOT deliver transitions.
# CI: run under android-emulator-runner with `target: google_apis`.
#
# Injection: `adb emu geo fix <lon> <lat>` from this host shell.
# Assertions: the native plugin log `Notifying listeners for event geofence*`
# (more reliable in logcat than WebView console messages).
#
# Usage (local, with a booted GMS emulator):
#   cd <repo-root>
#   # build the APK first: (cd example-app && npx cap sync android &&
#   #   cd android && ./gradlew assembleDebug)
#   .github/scripts/e2e-android-geofencing.sh

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
PACKAGE="com.gachlab.capacitor.backgroundgeolocation.example"
ACTIVITY=".MainActivity"
LOGCAT_OUT="/tmp/e2e-android-geofencing-logcat.txt"

# Geofence center — MUST match GF_CENTER in example-app/www/main.js.
GEO_LAT="37.3349"
GEO_LON="-122.009"

PASS=0
FAIL=0
pass() { echo "✓ $*"; PASS=$((PASS + 1)); }
fail() { echo "✗ $*"; FAIL=$((FAIL + 1)); }

# tap_id <resource-id> — taps the center of the WebView element with that HTML id,
# resolved from the live UIAutomator hierarchy (device-resolution independent).
tap_id() {
  local rid="$1" coords=""
  for _ in 1 2 3 4 5; do
    adb shell uiautomator dump /sdcard/u.xml >/dev/null 2>&1 || true
    adb pull /sdcard/u.xml /tmp/u.xml >/dev/null 2>&1 || true
    coords=$(python3 -c "
import re,sys
try: xml=open('/tmp/u.xml').read()
except Exception: sys.exit(1)
m=re.search(r'resource-id=\"${rid}\"[^>]*bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"', xml) \
  or re.search(r'bounds=\"\[(\d+),(\d+)\]\[(\d+),(\d+)\]\"[^>]*resource-id=\"${rid}\"', xml)
if not m: sys.exit(1)
x1,y1,x2,y2=map(int,m.groups()); print((x1+x2)//2,(y1+y2)//2)
") && [ -n "$coords" ] && break
    sleep 1
  done
  [ -z "$coords" ] && { echo "  ! button id '$rid' not found"; return 1; }
  adb shell input tap $coords
}

inject_center() { adb emu geo fix "${GEO_LON}" "${GEO_LAT}" >/dev/null 2>&1 || true; }

# Feed several fixes so the GMS fused provider has a FRESH inside-the-region location
# before we register — required for INITIAL_TRIGGER_ENTER to fire on registration.
freshen_location() { for _ in 1 2 3 4 5 6; do inject_center; sleep 1; done; }

# wait_event <substring> <timeout-s> — true if the plugin emits it within the window.
wait_event() {
  local needle="$1" timeout="$2" i=0
  while [ "$i" -lt "$timeout" ]; do
    if adb logcat -d 2>/dev/null | grep -q "Notifying listeners for event ${needle}"; then return 0; fi
    inject_center; sleep 1; i=$((i + 1))
  done
  return 1
}

# ── install + permissions ─────────────────────────────────────────────────────
APK=$(find "${REPO_ROOT}/example-app/android/app/build/outputs/apk/debug" -name "*.apk" | head -1)
[ -z "${APK}" ] && { echo "ERROR: APK not built (run assembleDebug first)"; exit 1; }
echo "→ Installing ${APK}"
adb install -r "${APK}" >/dev/null
for p in ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION ACCESS_BACKGROUND_LOCATION; do
  adb shell pm grant "${PACKAGE}" "android.permission.$p" 2>/dev/null || true
done
adb shell settings put secure location_mode 3
adb shell settings put system screen_off_timeout 600000

# ── launch + wait for WebView ready ───────────────────────────────────────────
echo "→ Pre-warming GPS at geofence center"
inject_center; sleep 3
adb logcat -c
echo "→ Launching app"
adb shell am start -n "${PACKAGE}/${ACTIVITY}" >/dev/null
echo "→ Waiting for WebView ready…"
for _ in $(seq 1 90); do
  adb logcat -d 2>/dev/null | grep -q "geofenceError" && break
  sleep 2
done
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
sleep 2

# GMS geofencing is independent of our location service, so Configure/Start are
# best-effort (they exercise the realistic flow but the geofence path doesn't need them).
echo "→ Configure + Start (best-effort)"
tap_id configure || true; sleep 3
tap_id start     || true; sleep 4

# ── scenario 1+2: initial ENTER (already inside) + DWELL ──────────────────────
# Clear any geofences persisted by a previous run first — GMS won't re-fire ENTER
# for an already-monitored region that is already in the ENTER state.
echo "→ Clearing persisted geofences"
tap_id gf-clear; sleep 2
echo "→ Registering a geofence the device is already inside (enter-here)"
freshen_location
adb logcat -c
tap_id gf-enter

if wait_event "geofenceEnter" 20; then pass "initial ENTER fired (already-inside, INITIAL_TRIGGER_ENTER)"
else fail "initial ENTER not fired"; fi

if wait_event "geofenceDwell" 30; then pass "DWELL fired after loitering delay"
else fail "DWELL not fired"; fi

# ── scenario 3: DWELL survives backgrounding (GMS dwell is OS-level) ──────────
echo "→ Re-arming, then backgrounding before the dwell delay"
tap_id gf-clear; sleep 2
freshen_location
adb logcat -c
tap_id gf-enter
# Confirm re-entry while foreground, then background BEFORE the 4 s dwell elapses.
wait_event "geofenceEnter" 20 >/dev/null || true
adb shell input keyevent KEYCODE_HOME
if wait_event "geofenceDwell" 30; then pass "DWELL survived backgrounding (GMS OS-level dwell)"
else fail "DWELL did not fire while backgrounded"; fi
adb shell am start -n "${PACKAGE}/${ACTIVITY}" >/dev/null; sleep 2

# ── scenario 4: registration failure → geofenceError ─────────────────────────
echo "→ Registering an invalid geofence (radius 0)"
tap_id gf-clear; sleep 1
adb logcat -c
tap_id gf-invalid
if wait_event "geofenceError" 15; then pass "geofenceError surfaced on registration failure"
else fail "geofenceError not surfaced"; fi

adb logcat -d > "${LOGCAT_OUT}" 2>&1 || true

# ── summary ───────────────────────────────────────────────────────────────────
echo ""
echo "────────────────────────────────────────"
if [ "${FAIL}" -eq 0 ] && [ "${PASS}" -gt 0 ]; then
  echo "✓ Geofencing Android E2E PASSED (${PASS} checks)"
  exit 0
else
  echo "✗ Geofencing Android E2E FAILED (passed=${PASS}, failed=${FAIL})"
  echo "  logcat: ${LOGCAT_OUT}"
  exit 1
fi
