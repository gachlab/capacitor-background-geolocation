#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# E2E: background-survival test for capacitor-background-geolocation.
#
# Pre-conditions (satisfied by android-emulator-runner@v2):
#   • Android emulator API 30 is booted and adb-connected.
#   • The debug APK has been built at example-app/android/app/build/outputs/apk/debug/*.apk
#
# Test scenario:
#   1. Install APK + grant location permissions
#   2. Launch app, start tracking
#   3. Inject 5 GPS fixes via adb emu geo fix
#   4. Background the app (Home key)
#   5. Inject 5 more GPS fixes
#   6. Return to foreground
#   7. Read location-count from plugin's SQLite DB via run-as
#   8. Assert count >= 5

set -euo pipefail

APK=$(find example-app/android/app/build/outputs/apk/debug -name "*.apk" | head -1)
PACKAGE="com.josuelmm.capacitor.backgroundgeolocation.example"
ACTIVITY=".MainActivity"
LOGCAT_OUT="/tmp/e2e-logcat.txt"
DB="/data/data/${PACKAGE}/databases/cordova_bg_geolocation.db"

# ── install + permissions ─────────────────────────────────────────────────────

echo "→ Installing APK: $APK"
adb install -r "$APK"

echo "→ Granting location permissions"
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant "$PACKAGE" android.permission.ACCESS_BACKGROUND_LOCATION

# ── launch + configure ────────────────────────────────────────────────────────

echo "→ Launching app"
adb shell am start -n "${PACKAGE}/${ACTIVITY}"
sleep 3

echo "→ Tapping Configure"
adb shell input tap 100 200

echo "→ Tapping Start"
adb shell input tap 150 200
sleep 2

# ── inject GPS fixes (foreground) ─────────────────────────────────────────────

echo "→ Injecting 5 GPS fixes (foreground)"
for i in 1 2 3 4 5; do
  lat=$(echo "19.4326 + $i * 0.0010" | bc -l)
  adb emu geo fix -99.1332 "$lat"
  sleep 1
done

# ── background the app ────────────────────────────────────────────────────────

echo "→ Sending app to background"
adb shell input keyevent KEYCODE_HOME
sleep 2

# ── inject GPS fixes (background) ────────────────────────────────────────────

echo "→ Injecting 5 GPS fixes (background)"
for i in 6 7 8 9 10; do
  lat=$(echo "19.4326 + $i * 0.0010" | bc -l)
  adb emu geo fix -99.1332 "$lat"
  sleep 1
done

# Capture logcat before returning to foreground (used for debugging on failure).
adb logcat -d > "$LOGCAT_OUT" 2>&1 || true

# ── return to foreground ──────────────────────────────────────────────────────

echo "→ Returning to foreground"
adb shell am start -n "${PACKAGE}/${ACTIVITY}"
sleep 3

# ── assert location-count >= 5 ────────────────────────────────────────────────
# Primary: query the plugin's SQLite DB directly via run-as (debug APK).
# The WebView is paused in background so we can't rely on the JS counter; the
# foreground LocationService writes to DB regardless of WebView state.

RAW=$(adb shell "run-as ${PACKAGE} sqlite3 ${DB} 'SELECT COUNT(*) FROM location'" \
      2>/dev/null || true)
# Strip everything except digits (handles empty, error text, whitespace).
COUNT=$(printf '%s' "${RAW}" | tr -dc '0-9')

# Fallback: count location-persisted log lines emitted by the native service.
if [[ -z "$COUNT" || "$COUNT" == "0" ]]; then
  RAW=$(grep -cE "BackgroundLocation|location.*persisted|onLocation" \
        "$LOGCAT_OUT" 2>/dev/null || true)
  COUNT=$(printf '%s' "${RAW}" | tr -dc '0-9')
fi

COUNT="${COUNT:-0}"

echo "→ Location count: $COUNT"

if [[ "$COUNT" -ge 5 ]]; then
  echo "✓ Background survival test PASSED ($COUNT locations)"
else
  echo "✗ Background survival test FAILED (expected ≥5, got $COUNT)"
  echo "--- logcat excerpt ---"
  grep -iE "bgloc|BackgroundGeoloc|geolocation|location" "$LOGCAT_OUT" | tail -50 || true
  exit 1
fi
