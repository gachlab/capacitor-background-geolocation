package com.marianhello.bgloc.driving;

import com.marianhello.bgloc.data.BackgroundLocation;

/**
 * v4.0 Phase 6 — Driver insights state machine (GPS-only).
 *
 * Pure-Java helper, no Android imports. Hosted by {@code LocationServiceImpl}, which
 * feeds it every received location and surfaces emitted events via the plugin
 * {@code MSG_ON_*} broadcast pipeline.
 *
 * Heuristics:
 *   moving   = speed > minMovingSpeed
 *   stopped  = !moving for stoppedDuration ms
 *   tripStart = stopped → moving with speed >= minTripSpeed sustained for minTripDuration ms
 *   tripEnd  = moving → stopped (after a tripStart)
 *   speeding = first crossing above speedLimit (km/h); rearms on drop below
 *
 * Sensor-fusion events (hardBrake / sharpTurn / possibleCrash) are intentionally NOT
 * implemented in this class. They require linear acceleration + gyroscope sampling and
 * are planned for v4.1 in a separate {@code SensorFusionDetector}.
 */
public class DrivingEventsDetector {

    public interface Listener {
        void onMoving(BackgroundLocation location);
        void onStopped(BackgroundLocation location);
        void onTripStart(BackgroundLocation location);
        /** distance in meters, durationMs in milliseconds. */
        void onTripEnd(BackgroundLocation location, double distance, long durationMs);
        void onSpeeding(BackgroundLocation location, double speedKmh, double limitKmh);
        void onProviderChange(String provider);
        // v4.1 GPS-derived driving events
        /** GPS-derived deceleration (m/s²). Negative number, more negative = harder brake. */
        void onHardBrake(BackgroundLocation location, double decelMps2);
        /** GPS-derived acceleration (m/s²). */
        void onRapidAcceleration(BackgroundLocation location, double accelMps2);
        /** Bearing change rate (deg/s). */
        void onSharpTurn(BackgroundLocation location, double degPerSec);
        /** Velocity drop in km/h within {@code crashWindowMs} while tripActive. */
        void onPossibleCrash(BackgroundLocation location, double velocityDropKmh);
    }

    public static class Config {
        public boolean enabled = false;
        public double speedLimitKmh = 0;        // 0 disables speeding
        public double minMovingSpeedMps = 1.0;  // ~3.6 km/h
        public long stoppedDurationMs = 60_000;
        public double minTripSpeedMps = 3.0;    // ~10.8 km/h
        public long minTripDurationMs = 30_000;
        // v4.1 GPS-derived driving events. 0 disables each one.
        public double hardBrakeMps2 = 3.5;      // -m/s² threshold (positive value)
        public double rapidAccelMps2 = 3.5;     // m/s² threshold
        public double sharpTurnDegPerSec = 30;  // deg/sec, requires speed > 5 m/s
        public double crashImpactKmh = 25;      // velocity drop within crashWindow
        public long   crashWindowMs = 2_000;    // window to evaluate the velocity drop
    }

    private final Listener listener;
    private Config cfg = new Config();

    // State
    private boolean isMoving = false;
    private boolean tripActive = false;
    private long tripStartedAt = 0;
    private double tripDistanceMeters = 0;
    private double tripStartLat, tripStartLon;
    private boolean hasTripStartCoord = false;

    private long aboveTripSpeedSince = 0;   // first sample with speed >= minTripSpeed
    private long belowMovingSinceMs = 0;    // first sample with speed < minMovingSpeed

    private boolean wasSpeeding = false;
    private String lastProvider;

    private double prevLat, prevLon;
    private boolean hasPrev = false;

    // v4.1 GPS-derived deltas
    private double prevSpeedMps = 0.0;
    private long   prevSpeedAt = 0L;
    private double prevBearingDeg = 0.0;
    private boolean hasPrevBearing = false;
    private long   prevBearingAt = 0L;
    /** Cooldown so we don't refire the same event on every fix in a sustained brake. */
    private long lastHardBrakeAt = 0L, lastRapidAccelAt = 0L, lastSharpTurnAt = 0L, lastCrashAt = 0L;
    private static final long DRIVING_EVENT_COOLDOWN_MS = 4_000L;

    public DrivingEventsDetector(Listener listener) {
        this.listener = listener;
    }

    public synchronized void setConfig(Config c) {
        if (c != null) this.cfg = c;
    }

    /** Reset internal state. Called when service stops. */
    public synchronized void reset() {
        prevSpeedMps = 0.0;
        prevSpeedAt = 0L;
        prevBearingDeg = 0.0;
        hasPrevBearing = false;
        prevBearingAt = 0L;
        lastHardBrakeAt = lastRapidAccelAt = lastSharpTurnAt = lastCrashAt = 0L;
        isMoving = false;
        tripActive = false;
        tripStartedAt = 0;
        tripDistanceMeters = 0;
        hasTripStartCoord = false;
        aboveTripSpeedSince = 0;
        belowMovingSinceMs = 0;
        wasSpeeding = false;
        lastProvider = null;
        hasPrev = false;
    }

    public synchronized void onLocation(BackgroundLocation loc) {
        if (!cfg.enabled || loc == null) return;
        long now = System.currentTimeMillis();
        double speed = loc.hasSpeed() ? loc.getSpeed() : 0.0;

        // Provider change
        String provider = loc.getProvider();
        if (provider != null && !provider.equals(lastProvider)) {
            lastProvider = provider;
            if (listener != null) listener.onProviderChange(provider);
        }

        // Distance accumulator (very simple: planar haversine approximation via
        // Location.distanceBetween is not used here to keep this class platform-free;
        // consumer can swap to BackgroundLocation.distanceTo in onTripEnd if desired).
        double curLat = loc.getLatitude();
        double curLon = loc.getLongitude();
        if (hasPrev && tripActive) {
            tripDistanceMeters += haversineMeters(prevLat, prevLon, curLat, curLon);
        }
        prevLat = curLat;
        prevLon = curLon;
        hasPrev = true;

        // Moving / stopped state
        boolean nowMoving = speed >= cfg.minMovingSpeedMps;
        if (nowMoving) {
            belowMovingSinceMs = 0;
            if (!isMoving) {
                isMoving = true;
                if (listener != null) listener.onMoving(loc);
            }
            // Trip start arming
            if (!tripActive) {
                if (speed >= cfg.minTripSpeedMps) {
                    if (aboveTripSpeedSince == 0) aboveTripSpeedSince = now;
                    if (now - aboveTripSpeedSince >= cfg.minTripDurationMs) {
                        tripActive = true;
                        tripStartedAt = now;
                        tripDistanceMeters = 0;
                        tripStartLat = curLat;
                        tripStartLon = curLon;
                        hasTripStartCoord = true;
                        if (listener != null) listener.onTripStart(loc);
                    }
                } else {
                    aboveTripSpeedSince = 0;
                }
            }
        } else {
            aboveTripSpeedSince = 0;
            if (belowMovingSinceMs == 0) belowMovingSinceMs = now;
            if (isMoving && (now - belowMovingSinceMs) >= cfg.stoppedDurationMs) {
                isMoving = false;
                if (listener != null) listener.onStopped(loc);
                if (tripActive) {
                    long durMs = now - tripStartedAt;
                    double dist = tripDistanceMeters;
                    tripActive = false;
                    if (listener != null) listener.onTripEnd(loc, dist, durMs);
                }
            }
        }

        // Speeding (km/h)
        if (cfg.speedLimitKmh > 0) {
            double kmh = speed * 3.6;
            if (kmh > cfg.speedLimitKmh) {
                if (!wasSpeeding) {
                    wasSpeeding = true;
                    if (listener != null) listener.onSpeeding(loc, kmh, cfg.speedLimitKmh);
                }
            } else {
                // Rearm: emit again on next crossing.
                wasSpeeding = false;
            }
        }

        // v4.1 GPS-derived driving events (only meaningful during an active trip)
        if (tripActive && prevSpeedAt > 0) {
            long dtMs = now - prevSpeedAt;
            if (dtMs > 0 && dtMs <= 5_000) {
                double dt = dtMs / 1000.0;
                double dv = speed - prevSpeedMps; // m/s
                double accel = dv / dt;            // m/s²

                if (cfg.hardBrakeMps2 > 0
                        && accel <= -cfg.hardBrakeMps2
                        && (now - lastHardBrakeAt) >= DRIVING_EVENT_COOLDOWN_MS) {
                    lastHardBrakeAt = now;
                    if (listener != null) listener.onHardBrake(loc, accel);
                }
                if (cfg.rapidAccelMps2 > 0
                        && accel >= cfg.rapidAccelMps2
                        && (now - lastRapidAccelAt) >= DRIVING_EVENT_COOLDOWN_MS) {
                    lastRapidAccelAt = now;
                    if (listener != null) listener.onRapidAcceleration(loc, accel);
                }

                // Possible crash: sustained velocity drop greater than crashImpactKmh in <= crashWindow.
                if (cfg.crashImpactKmh > 0 && dtMs <= cfg.crashWindowMs) {
                    double dropKmh = (prevSpeedMps - speed) * 3.6; // positive when slowing down
                    if (dropKmh >= cfg.crashImpactKmh
                            && speed < 1.5 // ended near stop
                            && prevSpeedMps * 3.6 >= cfg.crashImpactKmh
                            && (now - lastCrashAt) >= DRIVING_EVENT_COOLDOWN_MS) {
                        lastCrashAt = now;
                        if (listener != null) listener.onPossibleCrash(loc, dropKmh);
                    }
                }
            }
        }

        // Sharp turn (bearing change rate) — requires meaningful speed to avoid GPS jitter.
        if (cfg.sharpTurnDegPerSec > 0 && loc.hasBearing() && speed >= 5.0 && hasPrevBearing) {
            long dtMs = now - prevBearingAt;
            if (dtMs > 0 && dtMs <= 5_000) {
                double bearing = loc.getBearing();
                double diff = Math.abs(bearing - prevBearingDeg);
                if (diff > 180) diff = 360 - diff;
                double rate = diff * 1000.0 / dtMs;
                if (rate >= cfg.sharpTurnDegPerSec
                        && (now - lastSharpTurnAt) >= DRIVING_EVENT_COOLDOWN_MS) {
                    lastSharpTurnAt = now;
                    if (listener != null) listener.onSharpTurn(loc, rate);
                }
            }
            prevBearingDeg = loc.getBearing();
            prevBearingAt = now;
        } else if (loc.hasBearing()) {
            prevBearingDeg = loc.getBearing();
            prevBearingAt = now;
            hasPrevBearing = true;
        }

        prevSpeedMps = speed;
        prevSpeedAt = now;
    }

    private static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon/2) * Math.sin(dLon/2);
        return 2 * R * Math.asin(Math.sqrt(a));
    }
}
