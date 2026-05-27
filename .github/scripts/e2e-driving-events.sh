#!/usr/bin/env bash
# SPDX-License-Identifier: MIT
# E2E: driving-events test for capacitor-background-geolocation.
#
# Pre-conditions (satisfied by android-emulator-runner@v2):
#   • Android emulator API 30 is booted and adb-connected.
#   • The debug APK has been built at example-app/android/app/build/outputs/apk/debug/*.apk
#
# Test scenario:
#   1. Install APK + grant location permissions
#   2. Configure with low-threshold drivingEvents (crashImpactKmh=10,
#      crashConfirmWindowMs=2000, phoneUsageWindowMs=3000, sensorFusion=false)
#   3. Start tracking
#   4. Inject a crash sequence via NMEA: 6 fixes at ~36 km/h (10 m/s), then
#      an immediate stop → velocity drop ≥10 km/h within 4 s window
#   5. Wait crashConfirmWindowMs (2 s) + inject one more stopped fix
#   6. Assert logcat contains "driving-event: possibleCrash"
#   7. Inject a phone-usage jitter sequence: 8 fixes at ~20 km/h with
#      bearing oscillating ±12° every second for ≥3 s
#   8. Assert logcat contains "driving-event: phoneUsageWhileDriving"

set -euo pipefail

APK=$(find example-app/android/app/build/outputs/apk/debug -name "*.apk" | head -1)
PACKAGE="com.josuelmm.capacitor.backgroundgeolocation.example"
ACTIVITY=".MainActivity"
LOGCAT_OUT="/tmp/e2e-driving-logcat.txt"
PASS=0

# ── NMEA helpers ──────────────────────────────────────────────────────────────

# nmea_checksum <sentence_body>  (body = everything between $ and *, exclusive)
nmea_checksum() {
  local body="$1"
  local cs=0
  for (( i=0; i<${#body}; i++ )); do
    cs=$(( cs ^ $(printf '%d' "'${body:$i:1}") ))
  done
  printf '%02X' "$cs"
}

# send_nmea_fix <lat_deg> <lon_deg> <speed_kmh> <bearing_deg>
#   Injects a $GPRMC sentence via the emulator console.
#   lat/lon in decimal degrees (positive=N/E, negative=S/W).
send_nmea_fix() {
  local lat_d="$1" lon_d="$2" speed_kmh="$3" bearing="$4"

  # Convert decimal degrees → DDMM.MMMM
  local lat_abs lon_abs lat_hem lon_hem
  if (( $(echo "$lat_d >= 0" | bc -l) )); then lat_hem="N"; lat_abs="$lat_d";
  else lat_hem="S"; lat_abs=$(echo "0 - $lat_d" | bc -l); fi
  if (( $(echo "$lon_d >= 0" | bc -l) )); then lon_hem="E"; lon_abs="$lon_d";
  else lon_hem="W"; lon_abs=$(echo "0 - $lon_d" | bc -l); fi

  local lat_deg_int lon_deg_int
  lat_deg_int=$(echo "$lat_abs" | awk '{printf "%d", $1}')
  lon_deg_int=$(echo "$lon_abs" | awk '{printf "%d", $1}')
  local lat_min lon_min
  lat_min=$(echo "($lat_abs - $lat_deg_int) * 60" | bc -l | awk '{printf "%.4f", $1}')
  lon_min=$(echo "($lon_abs - $lon_deg_int) * 60" | bc -l | awk '{printf "%.4f", $1}')
  local lat_str lon_str
  lat_str=$(printf '%02d%s' "$lat_deg_int" "$lat_min")
  lon_str=$(printf '%03d%s' "$lon_deg_int" "$lon_min")

  # Speed in knots (1 km/h = 0.539957 knots)
  local speed_kn
  speed_kn=$(echo "$speed_kmh * 0.539957" | bc -l | awk '{printf "%.2f", $1}')

  local ts
  ts=$(date -u +"%H%M%S.00")
  local date_str
  date_str=$(date -u +"%d%m%y")

  local body="GPRMC,${ts},A,${lat_str},${lat_hem},${lon_str},${lon_hem},${speed_kn},${bearing},${date_str},,,A"
  local cs
  cs=$(nmea_checksum "$body")

  local sentence="\$${body}*${cs}"
  adb emu geo nmea "$sentence"
}

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
sleep 6
adb shell input keyevent KEYCODE_BACK || true
sleep 1

# Nexus 6 1440×2560: Configure≈x200 y500, Start≈x450 y500
echo "→ Tapping Configure (with driving events + crash/phone-usage thresholds)"
adb shell input tap 200 500
sleep 2

echo "→ Tapping Start"
adb shell input tap 450 500
sleep 3

# Clear logcat so we only see events from this run
adb logcat -c

# ── scenario 1: crash sequence ────────────────────────────────────────────────
# 36 km/h northbound for 6 seconds, then sudden stop.
# The velocity drop (≥10 km/h threshold) triggers possibleCrash detection.
# After crashConfirmWindowMs=2000 ms of staying stopped the event fires.

echo "→ Injecting crash sequence (36 km/h → 0)"
BASE_LAT="19.432600"
for i in 1 2 3 4 5 6; do
  lat=$(echo "$BASE_LAT + $i * 0.000325" | bc -l | awk '{printf "%.6f", $1}')  # ~36 m/s northward
  send_nmea_fix "$lat" "-99.133200" "36.0" "0.0"
  sleep 1
done

echo "→ Injecting stop fix (speed=0)"
lat=$(echo "$BASE_LAT + 7 * 0.000325" | bc -l | awk '{printf "%.6f", $1}')
send_nmea_fix "$lat" "-99.133200" "0.0" "0.0"
sleep 1

echo "→ Waiting for crashConfirmWindowMs (3 s margin)"
sleep 3

echo "→ Injecting confirm fix (still stopped)"
send_nmea_fix "$lat" "-99.133200" "0.0" "0.0"
sleep 2

# ── scenario 2: phone-usage jitter sequence ───────────────────────────────────
# 8 fixes at ~20 km/h with bearing oscillating ±12° every second.
# After phoneUsageWindowMs=3000 ms with ≥3 jitter events, the event fires.

echo "→ Injecting phone-usage jitter sequence (20 km/h, bearing ±12°)"
BASE_LAT2="19.450000"
for i in 1 2 3 4 5 6 7 8; do
  lat=$(echo "$BASE_LAT2 + $i * 0.000180" | bc -l | awk '{printf "%.6f", $1}')  # ~20 m northward
  # Alternate bearing: 0°, 12°, 0°, 12°, ...
  bearing=$(( (i % 2) * 12 ))
  send_nmea_fix "$lat" "-99.133200" "20.0" "$bearing"
  sleep 1
done

echo "→ Waiting for phoneUsageWindowMs (2 s margin)"
sleep 2

# ── scenario 3: crash confirm cancellation (false-positive suppression) ───────
# Speed drops from 30 km/h to 0, then recovers to 15 km/h before the 2 s
# confirm window elapses. No possibleCrash event should fire for this sequence.

echo "→ Injecting crash-then-recovery sequence (30 km/h → 0 → 15 km/h)"
BASE_LAT3="19.470000"
for i in 1 2 3 4; do
  lat=$(echo "$BASE_LAT3 + $i * 0.000270" | bc -l | awk '{printf "%.6f", $1}')
  send_nmea_fix "$lat" "-99.133200" "30.0" "0.0"
  sleep 1
done

lat=$(echo "$BASE_LAT3 + 5 * 0.000270" | bc -l | awk '{printf "%.6f", $1}')
send_nmea_fix "$lat" "-99.133200" "0.0" "0.0"   # sudden stop → crash candidate
sleep 1

lat=$(echo "$BASE_LAT3 + 6 * 0.000270" | bc -l | awk '{printf "%.6f", $1}')
send_nmea_fix "$lat" "-99.133200" "15.0" "0.0"  # speed recovers before confirm window
sleep 1

lat=$(echo "$BASE_LAT3 + 7 * 0.000270" | bc -l | awk '{printf "%.6f", $1}')
send_nmea_fix "$lat" "-99.133200" "15.0" "0.0"
sleep 3  # well past confirm window — no crash should have fired

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
  echo "  Hint: check crashImpactKmh threshold and NMEA speed injection"
fi

# Scenario 3 must NOT have produced a second crash (recovery should cancel it).
# We can't distinguish scenario 1 vs 3 crash lines without timestamps, but at
# minimum we verify at most 1 crash fired (scenario 3 adds 0 extra crashes).
# A stricter check would require timestamps; this is a best-effort guard.
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
  echo "--- relevant logcat ---"
  grep -iE "driving-event|DrivingEvents|possibleCrash|phoneUsage|LocationService" \
    "$LOGCAT_OUT" | tail -60 || true
  exit 1
fi
