// SPDX-License-Identifier: MIT
package com.josuelmm.capacitor.backgroundgeolocation;

import com.josuelmm.capacitor.backgroundgeolocation.fixtures.MockTripBuilder;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.driving.DrivingEventsDetector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DrivingEventsDetector.
 *
 * DrivingEventsDetector is pure Java (no Android imports), so these run on the JVM
 * without a device or emulator. All timing-sensitive tests use Thread.sleep() to ensure
 * System.currentTimeMillis() advances across consecutive onLocation() calls.
 *
 * Test config helpers set minTripDurationMs = 0 so tripStart fires on the first
 * qualifying fix, and stoppedDurationMs = STOPPED_MS (10 ms) so stopped/tripEnd
 * fire after a deliberate sleep rather than immediately — this gap is required for
 * hardBrake detection, which checks (tripActive && dtMs > 0).
 */
@DisplayName("DrivingEventsDetector")
class DrivingEventsDetectorTest {

    /** ms to wait for the stopped-duration window to expire in tests. */
    private static final int STOPPED_MS = 10;

    private RecordingListener listener;

    @BeforeEach
    void setUp() {
        listener = new RecordingListener();
    }

    // ── 1. Happy path ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("full trip: events fired in correct order (tripStart → speeding → hardBrake → stopped → tripEnd)")
    void happyPath() throws InterruptedException {
        var cfg = config(100.0); // speedLimit 100 km/h
        cfg.stoppedDurationMs = STOPPED_MS;
        var det = detector(cfg);

        // Stationary start
        det.onLocation(loc(19.4326, -99.1332, 0.0f));

        // Drive at 120 km/h (33.3 m/s) north → tripStart + speeding
        det.onLocation(loc(19.4340, -99.1332, 33.3f));

        // Sleep so dtMs > 0 for hard-brake detection
        Thread.sleep(5);
        // Brake to zero — tripActive is still true (stoppedDurationMs not elapsed)
        det.onLocation(loc(19.4340, -99.1332, 0.0f));

        // Wait for stopped window to expire, then send one more idle fix
        Thread.sleep(STOPPED_MS + 10);
        det.onLocation(loc(19.4340, -99.1332, 0.0f));

        assertSubsequence(listener.events, "tripStart", "speeding", "hardBrake", "stopped", "tripEnd");
    }

    // ── 2. Speeding ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("speeding: fires once above limit; re-arms on drop; fires again on next crossing")
    void speedingFiresOncePerCrossing() {
        var cfg = config(100.0);
        cfg.stoppedDurationMs = Long.MAX_VALUE; // never stop during this test
        var det = detector(cfg);

        // Arm the trip
        det.onLocation(loc(19.43, -99.13, 28.0f)); // 100.8 km/h — trip active + first speeding

        // Still above limit → no second event (wasSpeeding = true)
        det.onLocation(loc(19.44, -99.13, 28.0f));
        det.onLocation(loc(19.45, -99.13, 28.0f));

        // Drop below limit → re-arms
        det.onLocation(loc(19.46, -99.13, 20.0f)); // 72 km/h

        // Back above limit → fires again
        det.onLocation(loc(19.47, -99.13, 28.0f));

        assertEquals(2, listener.speedingCount,
                "speeding should fire exactly twice (once per crossing)");
    }

    // ── 3. Hard brake ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("hard brake: deceleration above threshold fires hardBrake")
    void hardBrakeDetected() throws InterruptedException {
        var cfg = config(0.0); // no speed limit
        cfg.hardBrakeMps2 = 3.5;
        cfg.stoppedDurationMs = STOPPED_MS;
        var det = detector(cfg);

        // Get trip active: speed = 15 m/s (54 km/h) > minTripSpeed 3 m/s
        det.onLocation(loc(19.4326, -99.1332, 15.0f)); // prevSpeedAt = now1

        // Sleep to ensure dtMs > 0, then brake to zero
        Thread.sleep(5);
        det.onLocation(loc(19.4326, -99.1332, 0.0f));  // accel ≈ -3000 m/s² >> 3.5 → fires

        assertTrue(listener.events.contains("hardBrake"),
                "hardBrake should be emitted on deceleration above threshold");
    }

    // ── 4. Trip metrics ───────────────────────────────────────────────────────

    @Test
    @DisplayName("trip metrics: tripEnd carries distance > 0 and durationMs > 0")
    void tripEndMetrics() throws InterruptedException {
        var cfg = config(0.0);
        cfg.stoppedDurationMs = 0; // fire stopped immediately
        var det = new DrivingEventsDetector(listener);
        det.setConfig(cfg);

        // Start trip: 5 m/s north
        det.onLocation(loc(19.4326, -99.1332, 5.0f));

        // Sleep to ensure durationMs > 0 and a new lat for distance > 0
        Thread.sleep(5);
        det.onLocation(loc(19.4340, -99.1332, 0.0f)); // stopped + tripEnd

        assertNotNull(listener.lastTripEndDistance,    "distance must be set on tripEnd");
        assertNotNull(listener.lastTripEndDurationMs,  "durationMs must be set on tripEnd");
        assertTrue(listener.lastTripEndDistance > 0,   "distance must be > 0");
        assertTrue(listener.lastTripEndDurationMs > 0, "durationMs must be > 0");
    }

    // ── 5. No false positives ────────────────────────────────────────────────

    @Test
    @DisplayName("no false positives: stationary fixes never fire tripStart")
    void stationaryNeverFiresTripStart() {
        var cfg = config(0.0);
        cfg.stoppedDurationMs = 0;
        var det = detector(cfg);

        for (int i = 0; i < 10; i++) {
            det.onLocation(loc(19.4326, -99.1332, 0.0f));
        }

        assertFalse(listener.events.contains("tripStart"),
                "stationary fixtures must not produce tripStart");
    }

    // ── 6. Disabled detector ──────────────────────────────────────────────────

    @Test
    @DisplayName("disabled detector fires no events regardless of speed")
    void disabledDetectorFiresNoEvents() {
        var cfg = config(100.0);
        cfg.enabled = false;
        var det = detector(cfg);

        det.onLocation(loc(19.43, -99.13, 40.0f));
        det.onLocation(loc(19.44, -99.13, 40.0f));

        assertTrue(listener.events.isEmpty(), "disabled detector must emit nothing");
    }

    // ── 7. Detector reset ────────────────────────────────────────────────────

    @Test
    @DisplayName("reset() clears state so a second trip starts cleanly")
    void resetClearsState() {
        var cfg = config(0.0);
        cfg.stoppedDurationMs = 0;
        var det = detector(cfg);

        det.onLocation(loc(19.43, -99.13, 5.0f)); // tripStart
        det.reset();
        listener.events.clear();

        det.onLocation(loc(19.50, -99.13, 5.0f)); // tripStart again after reset
        assertTrue(listener.events.contains("tripStart"),
                "tripStart should fire again after reset()");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private DrivingEventsDetector.Config config(double speedLimitKmh) {
        var c = new DrivingEventsDetector.Config();
        c.enabled           = true;
        c.minTripDurationMs = 0;   // fire tripStart immediately
        c.stoppedDurationMs = STOPPED_MS;
        c.minMovingSpeedMps = 1.0;
        c.minTripSpeedMps   = 3.0;
        c.hardBrakeMps2     = 3.5;
        c.speedLimitKmh     = speedLimitKmh;
        return c;
    }

    private DrivingEventsDetector detector(DrivingEventsDetector.Config cfg) {
        var det = new DrivingEventsDetector(listener);
        det.setConfig(cfg);
        return det;
    }

    private static BackgroundLocation loc(double lat, double lon, float speedMps) {
        BackgroundLocation l = new BackgroundLocation("gps");
        l.setLatitude(lat);
        l.setLongitude(lon);
        l.setSpeed(speedMps);
        return l;
    }

    /** Assert that {@code haystack} contains all elements of {@code needles} in order (not necessarily contiguous). */
    private static void assertSubsequence(List<String> haystack, String... needles) {
        int idx = 0;
        for (String needle : needles) {
            boolean found = false;
            while (idx < haystack.size()) {
                if (haystack.get(idx++).equals(needle)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "expected event '" + needle + "' in subsequence; got: " + haystack);
        }
    }

    // ── RecordingListener ─────────────────────────────────────────────────────

    static class RecordingListener implements DrivingEventsDetector.Listener {
        final List<String> events = new ArrayList<>();
        int speedingCount = 0;
        Double lastTripEndDistance;
        Long   lastTripEndDurationMs;

        @Override public void onMoving(BackgroundLocation l)    { events.add("moving"); }
        @Override public void onStopped(BackgroundLocation l)   { events.add("stopped"); }
        @Override public void onTripStart(BackgroundLocation l) { events.add("tripStart"); }
        @Override public void onTripEnd(BackgroundLocation l, double dist, long durMs) {
            events.add("tripEnd");
            lastTripEndDistance   = dist;
            lastTripEndDurationMs = durMs;
        }
        @Override public void onSpeeding(BackgroundLocation l, double kmh, double limit) {
            events.add("speeding");
            speedingCount++;
        }
        @Override public void onProviderChange(String p)                              { events.add("providerChange:" + p); }
        @Override public void onHardBrake(BackgroundLocation l, double a)            { events.add("hardBrake"); }
        @Override public void onRapidAcceleration(BackgroundLocation l, double a)    { events.add("rapidAccel"); }
        @Override public void onSharpTurn(BackgroundLocation l, double r)            { events.add("sharpTurn"); }
        @Override public void onPossibleCrash(BackgroundLocation l, double drop)     { events.add("possibleCrash"); }
    }
}
