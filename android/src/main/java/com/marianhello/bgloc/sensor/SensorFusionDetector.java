package com.marianhello.bgloc.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;

import com.marianhello.bgloc.data.BackgroundLocation;

/**
 * v4.2 Phase 8 — Real sensor fusion detector.
 *
 * Samples linear acceleration (TYPE_LINEAR_ACCELERATION, gravity removed) and
 * gyroscope (TYPE_GYROSCOPE) at SENSOR_DELAY_GAME (~50 Hz). Used to refine
 * possibleCrash and to detect phoneUsageWhileDriving. Pure-Android, no JNI.
 *
 * Crash detection: |a| (m/s²) above {@code crashImpactG} g during a tripActive
 * window emits {@link Listener#onPossibleCrash}. Combined with the GPS-derived
 * heuristic in {@link com.marianhello.bgloc.driving.DrivingEventsDetector},
 * this gives a far higher-confidence signal at low speeds (parking-lot impact)
 * where GPS alone misses.
 *
 * phoneUsageWhileDriving: while {@code tripActive} is true, if the screen turns
 * on and the user produces touch-shaped jitter on the device for a sustained
 * window, fires {@link Listener#onPhoneUsageWhileDriving}. Conservative — designed
 * to avoid false-positives from passenger usage by tying to {@code tripActive}.
 */
public class SensorFusionDetector implements SensorEventListener {

    public interface Listener {
        /** Triggered when |a| exceeds crashImpactG while tripActive. */
        void onSensorCrash(BackgroundLocation lastLocation, double impactG);
        /** Screen on + sustained device interaction during tripActive. */
        void onPhoneUsageWhileDriving(BackgroundLocation lastLocation);
    }

    public static class Config {
        public boolean enabled = false;
        /** Crash threshold in g. 1g = 9.81 m/s². Default 3g. */
        public double crashImpactG = 3.0;
        /** Cooldown between repeated crash detections. */
        public long   crashCooldownMs = 10_000;
        /** Min sustained gyro+accel jitter window for phoneUsage. */
        public long   phoneUsageWindowMs = 4_000;
        /** Cooldown between repeated phone-usage events. */
        public long   phoneUsageCooldownMs = 60_000;
    }

    private static final float G = 9.80665f;

    private final Context appContext;
    private final Listener listener;
    private final SensorManager sensorManager;
    private final Sensor linearAccel;
    private final Sensor gyroscope;
    private final PowerManager powerManager;
    private final Handler handler;

    private Config cfg = new Config();
    private boolean started = false;
    private boolean tripActive = false;
    private BackgroundLocation lastLocation;

    private long lastCrashAt = 0L;
    private long lastPhoneUsageAt = 0L;

    // phoneUsage state
    private long jitterAboveSince = 0L;
    private static final double JITTER_GYRO_RAD_S = 0.7;     // ~40 deg/s
    private static final double JITTER_ACCEL_MPS2 = 0.5;     // small accel, hand movement

    public SensorFusionDetector(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
        this.sensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        this.linearAccel = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION) : null;
        this.gyroscope = sensorManager != null ? sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) : null;
        this.powerManager = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        this.handler = new Handler(Looper.getMainLooper());
    }

    public synchronized void setConfig(Config c) {
        if (c != null) this.cfg = c;
    }

    public synchronized boolean isAvailable() {
        return sensorManager != null && linearAccel != null;
    }

    /** Start sampling sensors. Idempotent. */
    public synchronized void start() {
        if (started || !cfg.enabled || sensorManager == null) return;
        if (linearAccel != null) {
            sensorManager.registerListener(this, linearAccel, SensorManager.SENSOR_DELAY_GAME, handler);
        }
        if (gyroscope != null) {
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME, handler);
        }
        started = true;
    }

    /** Stop sampling. Idempotent. */
    public synchronized void stop() {
        if (!started) return;
        if (sensorManager != null) sensorManager.unregisterListener(this);
        started = false;
        jitterAboveSince = 0L;
    }

    /** Called by detector host whenever the GPS layer marks tripActive on/off. */
    public synchronized void setTripActive(boolean active) {
        this.tripActive = active;
        if (!active) jitterAboveSince = 0L;
    }

    /** Last known location for event payload. Updated by host. */
    public synchronized void setLastLocation(BackgroundLocation loc) {
        this.lastLocation = loc;
    }

    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { /* ignore */ }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Listener l;
        Config c;
        boolean tripActiveNow;
        BackgroundLocation loc;
        synchronized (this) {
            l = this.listener;
            c = this.cfg;
            tripActiveNow = this.tripActive;
            loc = this.lastLocation;
        }
        if (l == null || c == null || !c.enabled) return;

        long now = System.currentTimeMillis();

        if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
            float ax = event.values[0], ay = event.values[1], az = event.values[2];
            double mag = Math.sqrt(ax * ax + ay * ay + az * az);
            double gMag = mag / G;

            // Crash: high impact during a trip.
            if (tripActiveNow && c.crashImpactG > 0 && gMag >= c.crashImpactG
                    && (now - lastCrashAt) >= c.crashCooldownMs) {
                lastCrashAt = now;
                l.onSensorCrash(loc, gMag);
            }

            // Phone usage signal: small accel jitter during trip + screen on.
            evaluatePhoneUsage(now, mag, /*gyroMag*/ -1, c, l, tripActiveNow, loc);
        } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
            float gx = event.values[0], gy = event.values[1], gz = event.values[2];
            double gyroMag = Math.sqrt(gx * gx + gy * gy + gz * gz);
            evaluatePhoneUsage(now, /*accelMag*/ -1, gyroMag, c, l, tripActiveNow, loc);
        }
    }

    private void evaluatePhoneUsage(long now,
                                    double accelMag,
                                    double gyroMag,
                                    Config c,
                                    Listener l,
                                    boolean tripActiveNow,
                                    BackgroundLocation loc) {
        if (!tripActiveNow) { jitterAboveSince = 0L; return; }
        if (powerManager == null || !isScreenOn()) { jitterAboveSince = 0L; return; }

        boolean above = (accelMag >= 0 && accelMag >= JITTER_ACCEL_MPS2)
                || (gyroMag >= 0 && gyroMag >= JITTER_GYRO_RAD_S);
        if (above) {
            if (jitterAboveSince == 0L) jitterAboveSince = now;
            if ((now - jitterAboveSince) >= c.phoneUsageWindowMs
                    && (now - lastPhoneUsageAt) >= c.phoneUsageCooldownMs) {
                lastPhoneUsageAt = now;
                jitterAboveSince = 0L;
                l.onPhoneUsageWhileDriving(loc);
            }
        } else {
            jitterAboveSince = 0L;
        }
    }

    @SuppressWarnings("deprecation")
    private boolean isScreenOn() {
        if (powerManager == null) return false;
        try {
            // isInteractive added API 20; we target >= 21 elsewhere, but guard anyway.
            return powerManager.isInteractive();
        } catch (Throwable t) {
            return powerManager.isScreenOn();
        }
    }
}
