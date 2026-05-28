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
PACKAGE="com.gachlab.capacitor.backgroundgeolocation.example"
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

adb logcat -c  # clear buffer so the readiness check below only sees this session
echo "→ Launching app"
adb shell am start -n "${PACKAGE}/${ACTIVITY}"
# Wait for Capacitor WebView to finish registering all event listeners.
# On CI (swiftshader emulator) this takes ~60 s; polling is more reliable than a
# fixed sleep. We check for "phoneUsageWhileDriving" which is the LAST addListener
# call in main.js — once it appears the onclick handlers are wired up.
echo "→ Waiting for WebView ready…"
for i in $(seq 1 90); do
  adb logcat -d 2>/dev/null | grep -q "phoneUsageWhileDriving" && break
  sleep 2
done
sleep 2  # brief settle after last listener registers

# Wake the screen and dismiss keyguard so taps land on the app, not the lock screen.
adb shell input keyevent KEYCODE_WAKEUP || true
adb shell wm dismiss-keyguard || true
sleep 1

# Nexus 6 emulator: 1440x2560 px at 3.5x density.
# Button row renders at y≈500 physical px; Configure is at x≈200, Start at x≈450.
echo "→ Tapping Configure"
adb shell input tap 200 500

sleep 2

echo "→ Tapping Start"
adb shell input tap 450 500
sleep 3

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

# Fallback: count lines tagged "LocationService" from our native service.
# Only matches lines like "I LocationService: ..." — excludes Android system
# services (LocationManagerService, GnssLocationProvider, etc.).
if [[ -z "$COUNT" || "$COUNT" == "0" ]]; then
  RAW=$(grep -cE "[IDWEV] LocationService[: ]" "$LOGCAT_OUT" 2>/dev/null || true)
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
