#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# E2E: driving-events test for capacitor-background-geolocation.
#
# Pre-conditions (satisfied by android-emulator-runner@v2):
#   • Android emulator API 30 is booted and adb-connected.
#   • The debug APK has been built at example-app/android/app/build/outputs/apk/debug/*.apk
#
# Injection mechanism:
#   `adb emu geo nmea` is accepted by the emulator console but does NOT
#   reach the Android LocationManager on API-30 x86_64 emulators — the NMEA
#   subsystem is inactive by default. `adb emu geo fix` injects real Location
#   objects but omits speed/bearing. LocationService therefore computes both
#   from consecutive-fix displacement (added in v1.6.0).
#
# Position design:
#   Fixes are spaced so that the derived speed and bearing match each scenario:
#   • Crash   – 40 km/h north (0.0001° lat/s ≈ 11 m/s), then same-spot stop.
#   • Phone   – 20 km/h north (0.0000503° lat/s) with ±lon jitter of 0.0000056°
#               per step, producing bearing swings of ~18–24° (within 5–25° range).
#   • Recovery – 30 km/h north, sudden stop, recovery to 18 km/h before confirm window.
#
# Test scenario:
#   1. Install APK + grant location permissions
#   2. Configure with low-threshold drivingEvents (crashImpactKmh=10,
#      crashConfirmWindowMs=2000, phoneUsageWindowMs=3000, sensorFusion=false)
#   3. Start tracking
#   4. Scenario 1 – crash:  6 fixes at ~40 km/h, then stop, wait confirm, confirm.
#   5. Scenario 2 – phone:  8 zigzag fixes at 20 km/h with bearing ±18–24° swings.
#   6. Scenario 3 – cancel: speed recovers before confirm window → no crash.
#   7. Assert logcat contains exactly 1 possibleCrash and phoneUsageWhileDriving.

set -euo pipefail

APK=$(find example-app/android/app/build/outputs/apk/debug -name "*.apk" | head -1)
PACKAGE="com.gachlab.capacitor.backgroundgeolocation.example"
ACTIVITY=".MainActivity"
LOGCAT_OUT="/tmp/e2e-driving-logcat.txt"
PASS=0

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
# Wait for Capacitor WebView to fully initialize on the emulator (can take 10-15 s).
# Do NOT press KEYCODE_BACK here — that would finish the Activity before the taps.
sleep 15

# Nexus 6 1440×2560 (560 dpi, ~3.5× scale): first button row sits at y≈600px
# (status bar 84px + body padding + h1/h2 + status row).
# Configure center ≈ x200, Start center ≈ x450.
echo "→ Tapping Configure (with driving events + crash/phone-usage thresholds)"
adb shell input tap 200 600
sleep 3

echo "→ Tapping Start"
adb shell input tap 450 600
sleep 5

# Clear logcat so we only see events from this run
adb logcat -c

# ── scenario 1: crash sequence ────────────────────────────────────────────────
# 6 fixes northward at ~40 km/h (0.0001°/s ≈ 11 m/s), then a stationary fix.
# LocationService computes speed from displacement. The velocity drop (≥10 km/h)
# triggers the crash candidate; after crashConfirmWindowMs=2000 ms the event fires.

echo "→ Injecting crash sequence (40 km/h → 0)"
BASE_LAT="19.432600"
for i in 1 2 3 4 5 6; do
  lat=$(echo "$BASE_LAT + $i * 0.0001" | bc -l | awk '{printf "%.7f", $1}')
  adb emu geo fix -99.133200 "$lat"
  sleep 1
done

echo "→ Injecting stop fix (same position → computed speed ≈ 0)"
lat=$(echo "$BASE_LAT + 6 * 0.0001" | bc -l | awk '{printf "%.7f", $1}')
adb emu geo fix -99.133200 "$lat"
sleep 1

echo "→ Waiting for crashConfirmWindowMs (3 s margin)"
sleep 3

echo "→ Injecting confirm fix (still stopped)"
adb emu geo fix -99.133200 "$lat"
sleep 2

# ── scenario 2: phone-usage jitter sequence ───────────────────────────────────
# 8 fixes at ~20 km/h with longitude alternating ±0.0000056°.
# This produces bearing swings of ~18–24° between consecutive fixes
# (within the 5–25° jitter range), triggering phoneUsageWhileDriving.

echo "→ Injecting phone-usage jitter sequence (20 km/h, lon ±0.0000056°)"
BASE_LAT2="19.450000"
LON_POS="-99.1331944"   #  +0.0000056° from -99.133200
LON_NEG="-99.1332056"   #  -0.0000056° from -99.133200
for i in 1 2 3 4 5 6 7 8; do
  lat=$(echo "$BASE_LAT2 + $i * 0.0000503" | bc -l | awk '{printf "%.7f", $1}')
  # Alternate east/west to create bearing jitter
  if (( i % 2 == 1 )); then
    lon="$LON_POS"
  else
    lon="$LON_NEG"
  fi
  adb emu geo fix "$lon" "$lat"
  sleep 1
done

echo "→ Waiting for phoneUsageWindowMs (2 s margin)"
sleep 2

# ── scenario 3: crash confirm cancellation (false-positive suppression) ───────
# Speed drops from 30 km/h to 0, then recovers to 18 km/h before the 2 s
# confirm window elapses. No possibleCrash event should fire for this sequence.

echo "→ Injecting crash-then-recovery sequence (30 km/h → 0 → 18 km/h)"
BASE_LAT3="19.470000"
for i in 1 2 3 4; do
  lat=$(echo "$BASE_LAT3 + $i * 0.0000753" | bc -l | awk '{printf "%.7f", $1}')
  adb emu geo fix -99.133200 "$lat"
  sleep 1
done

# Sudden stop — crash candidate (deferred, not yet fired)
lat_stop=$(echo "$BASE_LAT3 + 4 * 0.0000753" | bc -l | awk '{printf "%.7f", $1}')
adb emu geo fix -99.133200 "$lat_stop"
sleep 1

# Speed recovers before confirm window (1 s < confirmWindowMs=2000 ms)
lat_rec=$(echo "$lat_stop + 0.0000452" | bc -l | awk '{printf "%.7f", $1}')
adb emu geo fix -99.133200 "$lat_rec"
sleep 1

lat_rec2=$(echo "$lat_rec + 0.0000452" | bc -l | awk '{printf "%.7f", $1}')
adb emu geo fix -99.133200 "$lat_rec2"
sleep 3   # well past confirm window — no crash should have fired

# ── capture logcat ────────────────────────────────────────────────────────────

adb logcat -d > "$LOGCAT_OUT" 2>&1 || true

# ── assertions ────────────────────────────────────────────────────────────────

echo ""
echo "── Assertions ──────────────────────────────────────────────────────"

# Count crash events — we expect exactly 1 (from scenario 1, not from scenario 3)
CRASH_COUNT=$(grep -c "driving-event: possibleCrash" "$LOGCAT_OUT" || true)

if [[ "$CRASH_COUNT" -ge 1 ]]; then
  echo "✓ possibleCrash fired (scenario 1) [count=$CRASH_COUNT]"
  PASS=$(( PASS + 1 ))
else
  echo "✗ possibleCrash NOT found in logcat"
  echo "  Hint: check speed computation from displacement and crashImpactKmh threshold"
fi

# Scenario 3 must NOT have produced a second crash (recovery should cancel it).
if [[ "$CRASH_COUNT" -le 1 ]]; then
  echo "✓ crash confirm cancellation: no extra crash from recovery sequence [count=$CRASH_COUNT]"
  PASS=$(( PASS + 1 ))
else
  echo "✗ crash confirm cancellation FAILED: expected ≤1 crash but got $CRASH_COUNT"
fi

if grep -q "driving-event: phoneUsageWhileDriving" "$LOGCAT_OUT"; then
  echo "✓ phoneUsageWhileDriving fired"
  PASS=$(( PASS + 1 ))
else
  echo "✗ phoneUsageWhileDriving NOT found in logcat"
  echo "  Hint: check bearing jitter range and phoneUsageWindowMs threshold"
fi

echo ""
if [[ "$PASS" -eq 3 ]]; then
  echo "✓ Driving events E2E PASSED ($PASS/3)"
else
  echo "✗ Driving events E2E FAILED ($PASS/3)"
  echo "--- LocationService logs ---"
  grep "LocationService" "$LOGCAT_OUT" | tail -30 || echo "(none — service may not have started)"
  echo "--- driving-event logs ---"
  grep -iE "driving-event|possibleCrash|phoneUsage" "$LOGCAT_OUT" | tail -40 || echo "(none)"
  echo "--- fatal/crash logs ---"
  grep -iE "AndroidRuntime|FATAL EXCEPTION|E/Capacitor" "$LOGCAT_OUT" | tail -20 || echo "(none)"
  exit 1
fi
