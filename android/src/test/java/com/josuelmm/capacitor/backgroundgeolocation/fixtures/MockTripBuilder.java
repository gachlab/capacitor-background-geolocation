// SPDX-License-Identifier: MIT
package com.josuelmm.capacitor.backgroundgeolocation.fixtures;

import com.marianhello.bgloc.data.BackgroundLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds synthetic BackgroundLocation sequences for unit-testing DrivingEventsDetector.
 *
 * Coordinates are moved northward (increasing latitude) using the approximation
 * 1° lat ≈ 111 000 m. Accuracy is sufficient to produce non-zero haversine distances;
 * geodesic correctness is not required here.
 *
 * Usage:
 * <pre>
 *   List&lt;BackgroundLocation&gt; trip = new MockTripBuilder()
 *       .startAt(19.4326, -99.1332)
 *       .driveFor(1.0, 60.0)   // 1 km at 60 km/h
 *       .speedUp(120.0)        // ramp to 120 km/h
 *       .idleFor(3)
 *       .build();
 * </pre>
 */
public final class MockTripBuilder {

    private static final double METERS_PER_DEG_LAT = 111_000.0;
    private static final long   DEFAULT_FIX_MS     = 1_000L;

    private final List<BackgroundLocation> locations = new ArrayList<>();

    private double curLat     = 0.0;
    private double curLon     = 0.0;
    private float  curSpeedMps = 0.0f;
    private long   curTimeMs  = 1_716_000_000_000L; // arbitrary epoch base

    /** Initial stationary fix at the given coordinates. */
    public MockTripBuilder startAt(double lat, double lon) {
        curLat = lat;
        curLon = lon;
        addFix(0.0f);
        return this;
    }

    /** Generate fixes driving north at {@code speedKmh} for {@code distanceKm}. */
    public MockTripBuilder driveFor(double distanceKm, double speedKmh) {
        float mps = (float) (speedKmh / 3.6);
        double metersPerFix = mps * (DEFAULT_FIX_MS / 1_000.0);
        int fixes = Math.max(1, (int) Math.ceil(distanceKm * 1_000.0 / metersPerFix));
        double dLat = metersPerFix / METERS_PER_DEG_LAT;
        for (int i = 0; i < fixes; i++) {
            curLat    += dLat;
            curTimeMs += DEFAULT_FIX_MS;
            addFix(mps);
        }
        return this;
    }

    /** Ramp speed linearly to {@code toKmh} over 5 fixes. */
    public MockTripBuilder speedUp(double toKmh) {
        double fromKmh = curSpeedMps * 3.6;
        double step    = (toKmh - fromKmh) / 5.0;
        for (int i = 1; i <= 5; i++) {
            curTimeMs += DEFAULT_FIX_MS;
            addFix((float) ((fromKmh + step * i) / 3.6));
        }
        return this;
    }

    /**
     * Single fix that drops speed to zero (hard-stop).
     * The caller must ensure real wall-clock time passes between the preceding
     * high-speed fix and this one so that DrivingEventsDetector's dtMs > 0 check
     * is satisfied — use {@code Thread.sleep(5)} before feeding this fix.
     */
    public MockTripBuilder hardBrake() {
        curTimeMs += DEFAULT_FIX_MS;
        addFix(0.0f);
        return this;
    }

    /** {@code count} zero-speed fixes at the current position. */
    public MockTripBuilder idleFor(int count) {
        for (int i = 0; i < count; i++) {
            curTimeMs += DEFAULT_FIX_MS;
            addFix(0.0f);
        }
        return this;
    }

    public List<BackgroundLocation> build() {
        return new ArrayList<>(locations);
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private void addFix(float speedMps) {
        curSpeedMps = speedMps;
        BackgroundLocation loc = new BackgroundLocation("gps");
        loc.setLatitude(curLat);
        loc.setLongitude(curLon);
        loc.setSpeed(speedMps);
        loc.setTime(curTimeMs);
        locations.add(loc);
    }
}
