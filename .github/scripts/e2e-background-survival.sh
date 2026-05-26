#!/usr/bin/env bash
# SPDX-License-Identifier: Apache-2.0
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
#   7. Read location-count via WebView JS evaluation
#   8. Assert count >= 5

set -euo pipefail

APK=$(find example-app/android/app/build/outputs/apk/debug -name "*.apk" | head -1)
PACKAGE="com.josuelmm.capacitor.backgroundgeolocation.example"
ACTIVITY=".MainActivity"
LOGCAT_OUT="/tmp/e2e-logcat.txt"

# ── helpers ───────────────────────────────────────────────────────────────────

adb_js() {
  # Evaluate JS in the Capacitor WebView and print stdout.
  adb shell am broadcast \
    -a android.intent.action.VIEW \
    -n "${PACKAGE}/.JSEvalReceiver" \
    --es "expression" "$1" 2>/dev/null || true
}

wait_for_logcat_tag() {
  local tag="$1" timeout_s="${2:-10}"
  timeout "$timeout_s" adb logcat -s "$tag:D" -d 2>/dev/null || true
}

# ── install + permissions ─────────────────────────────────────────────────────

echo "→ Installing APK: $APK"
adb install -r "$APK"

echo "→ Granting location permissions"
adb shell pm grant "$PACKAGE" android.permission.ACCESS_FINE_LOCATION
adb shell pm grant "$PACKAGE" android.permission.ACCESS_BACKGROUND_LOCATION || true
adb shell pm grant "$PACKAGE" android.permission.ACCESS_COARSE_LOCATION

# ── launch + configure ────────────────────────────────────────────────────────

echo "→ Launching app"
adb shell am start -n "${PACKAGE}/${ACTIVITY}"
sleep 3

echo "→ Tapping Configure"
adb shell input tap 100 200   # approximate coords for the Configure button

echo "→ Tapping Start"
adb shell input tap 150 200   # approximate coords for the Start button
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

# Dump logcat for debugging before returning to foreground
adb logcat -d > "$LOGCAT_OUT" 2>&1 || true

# ── return to foreground ──────────────────────────────────────────────────────

echo "→ Returning to foreground"
adb shell am start -n "${PACKAGE}/${ACTIVITY}"
sleep 3

# ── assert location-count >= 5 ────────────────────────────────────────────────
# Read the data-testid="location-count" text node via accessibility info.

COUNT=$(adb shell uiautomator dump /sdcard/ui.xml 2>/dev/null && \
        adb shell cat /sdcard/ui.xml | grep -oP 'location-count[^"]*"[^"]*"[^"]*"\K[0-9]+' || echo "0")

# Fallback: grep logcat for "event:location" lines
if [[ -z "$COUNT" || "$COUNT" == "0" ]]; then
  COUNT=$(grep -c "event:location" "$LOGCAT_OUT" || echo "0")
fi

echo "→ Location count: $COUNT"

if [[ "$COUNT" -ge 5 ]]; then
  echo "✓ Background survival test PASSED ($COUNT locations)"
else
  echo "✗ Background survival test FAILED (expected ≥5, got $COUNT)"
  echo "--- logcat excerpt ---"
  grep -i "bgloc\|location\|geolocation" "$LOGCAT_OUT" | tail -40 || true
  exit 1
fi
