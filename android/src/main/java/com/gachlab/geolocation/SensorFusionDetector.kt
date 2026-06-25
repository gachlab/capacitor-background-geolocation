// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import kotlin.math.sqrt

/**
 * Gate for sensor crash detection: a qualifying impact must occur during an active
 * trip, exceed [crashImpactG] (in g), and respect [crashCooldownMs] since the last
 * one. Pure (no Android deps) so the thresholding is unit-testable on the JVM.
 *
 * Mirrors the iOS SensorFusionDetector crash logic.
 */
internal class CrashImpactGate(
    var crashImpactG: Double = 3.0,
    var crashCooldownMs: Long = 10_000L,
) {
    // Sentinel so the FIRST crash always fires regardless of the clock's absolute
    // base (a 0 init would suppress early crashes when `now` < cooldown).
    private var lastCrashAt = Long.MIN_VALUE

    /** TRUE if this magnitude (in g) should fire a crash now; advances the cooldown. */
    fun evaluate(magnitudeG: Double, tripActive: Boolean, now: Long): Boolean {
        if (!tripActive || magnitudeG < crashImpactG) return false
        if (lastCrashAt != Long.MIN_VALUE && now - lastCrashAt < crashCooldownMs) return false
        lastCrashAt = now
        return true
    }

    companion object {
        const val GRAVITY = 9.80665

        /** Magnitude of a linear-acceleration vector (m/s²) expressed in g. */
        fun magnitudeG(x: Float, y: Float, z: Float): Double =
            sqrt((x.toDouble() * x + y.toDouble() * y + z.toDouble() * z)) / GRAVITY
    }
}

/**
 * Gate for sensor phone-usage detection — mirror of the iOS SensorFusionDetector
 * phone-usage path. Fires when sustained jitter (accel ≥ [ACCEL_JITTER_G] g OR gyro
 * ≥ [GYRO_JITTER_RADS] rad/s) is held for [phoneUsageWindowMs] while a trip is active
 * and the app is in the foreground, respecting [phoneUsageCooldownMs] between fires.
 *
 * Pure (no Android deps) so the state machine is unit-testable on the JVM.
 */
internal class PhoneUsageJitterGate(
    var phoneUsageWindowMs: Long = 4_000L,
    var phoneUsageCooldownMs: Long = 60_000L,
) {
    // Sentinels (vs iOS's 0-init) so the logic is exact even when `now` == 0 in tests and
    // so the first qualifying window fires regardless of the clock's base — same robustness
    // fix as CrashImpactGate.
    private var jitterAboveSince = UNSET
    private var lastPhoneUsageAt = UNSET

    fun reset() {
        jitterAboveSince = UNSET
        lastPhoneUsageAt = UNSET
    }

    /** TRUE if phone usage should fire now; advances window/cooldown state. */
    fun evaluate(
        accelMagG: Double,
        gyroMagRads: Double,
        tripActive: Boolean,
        appActive: Boolean,
        now: Long,
    ): Boolean {
        if (!tripActive || !appActive) { jitterAboveSince = UNSET; return false }

        val jittering = accelMagG >= ACCEL_JITTER_G || gyroMagRads >= GYRO_JITTER_RADS
        if (!jittering) { jitterAboveSince = UNSET; return false }

        if (jitterAboveSince == UNSET) { jitterAboveSince = now; return false }

        val windowElapsed = now - jitterAboveSince
        val cooldownElapsed =
            if (lastPhoneUsageAt == UNSET) Long.MAX_VALUE else now - lastPhoneUsageAt
        if (windowElapsed >= phoneUsageWindowMs && cooldownElapsed >= phoneUsageCooldownMs) {
            lastPhoneUsageAt = now
            jitterAboveSince = UNSET
            return true
        }
        return false
    }

    companion object {
        private const val UNSET = Long.MIN_VALUE

        // Same thresholds as the iOS SensorFusionDetector: userAcceleration magnitude
        // in g, rotationRate magnitude in rad/s.
        const val ACCEL_JITTER_G = 0.5
        const val GYRO_JITTER_RADS = 0.7
    }
}

/**
 * Sensor-based crash + phone-usage detection, the Android counterpart of the iOS
 * `SensorFusionDetector` (CMMotionManager userAcceleration + rotationRate).
 *
 * - **Crash** (always on while running): `TYPE_LINEAR_ACCELERATION` magnitude exceeding
 *   `crashImpactG` during an active trip → [Listener.onCrash]. Runs alongside — and is
 *   fused with — the GPS crash path in [DrivingEventsDetector]: both can fire,
 *   distinguished by `PossibleCrash.source` ('gps' vs 'sensor').
 * - **Phone usage** (only when [sensorFusion] is true): sustained accel/gyro jitter while
 *   the app is foregrounded during a trip → [Listener.onPhoneUsageWhileDriving]. Mirror of
 *   iOS; gated by `sensorFusion` so it does NOT double-count with the GPS bearing-jitter
 *   phone-usage path in [DrivingEventsDetector] (which is itself gated on `!sensorFusion`).
 */
internal class SensorFusionDetector(private val context: Context) : SensorEventListener {

    interface Listener {
        fun onCrash(impactG: Double, location: BGLocation?)
        fun onPhoneUsageWhileDriving(location: BGLocation?)
    }

    var listener: Listener? = null
    @Volatile var tripActive = false
    @Volatile var lastLocation: BGLocation? = null

    var crashImpactG: Double
        get() = gate.crashImpactG
        set(v) { gate.crashImpactG = v }
    var crashCooldownMs: Long
        get() = gate.crashCooldownMs
        set(v) { gate.crashCooldownMs = v }

    /** When true, the sensor phone-usage path is active and the GPS path is gated off. */
    @Volatile var sensorFusion = false
    var phoneUsageWindowMs: Long
        get() = phoneUsageGate.phoneUsageWindowMs
        set(v) { phoneUsageGate.phoneUsageWindowMs = v }
    var phoneUsageCooldownMs: Long
        get() = phoneUsageGate.phoneUsageCooldownMs
        set(v) { phoneUsageGate.phoneUsageCooldownMs = v }

    private val gate = CrashImpactGate()
    private val phoneUsageGate = PhoneUsageJitterGate()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccel: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscope: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val app: Application? = context.applicationContext as? Application

    private var crashRegistered = false
    private var phoneUsageRegistered = false

    // Latest gyro magnitude (rad/s), refreshed by gyroscope events and read on each
    // accelerometer sample — mirrors iOS reading both axes from one CMDeviceMotion.
    @Volatile private var lastGyroMag = 0.0

    // Foreground state, tracked via activity lifecycle (iOS gates on applicationState==.active).
    @Volatile private var appInForeground = false
    private var resumedActivities = 0

    val isAvailable: Boolean get() = linearAccel != null

    private val lifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            resumedActivities++
            appInForeground = true
        }
        override fun onActivityPaused(activity: Activity) {
            resumedActivities = (resumedActivities - 1).coerceAtLeast(0)
            appInForeground = resumedActivities > 0
        }
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {}
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {}
    }

    /** Registers the crash sensor always, and the phone-usage path when [sensorFusion]. */
    fun start() {
        if (linearAccel != null && !crashRegistered) {
            crashRegistered = true
            // SENSOR_DELAY_GAME ≈ 50 Hz, matching the iOS 1/50 s motion interval.
            sensorManager.registerListener(this, linearAccel, SensorManager.SENSOR_DELAY_GAME)
        }
        reconcilePhoneUsage()
    }

    /** Toggles the gyroscope + foreground tracking to match the current [sensorFusion]. */
    private fun reconcilePhoneUsage() {
        if (sensorFusion && gyroscope != null && !phoneUsageRegistered) {
            phoneUsageRegistered = true
            phoneUsageGate.reset()
            appInForeground = probeForeground()
            resumedActivities = if (appInForeground) 1 else 0
            sensorManager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
            app?.registerActivityLifecycleCallbacks(lifecycleCallbacks)
        } else if (!sensorFusion && phoneUsageRegistered) {
            phoneUsageRegistered = false
            gyroscope?.let { sensorManager.unregisterListener(this, it) }
            app?.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        }
    }

    fun stop() {
        if (!crashRegistered && !phoneUsageRegistered) return
        sensorManager.unregisterListener(this)
        if (phoneUsageRegistered) app?.unregisterActivityLifecycleCallbacks(lifecycleCallbacks)
        crashRegistered = false
        phoneUsageRegistered = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> {
                val x = event.values[0].toDouble()
                val y = event.values[1].toDouble()
                val z = event.values[2].toDouble()
                lastGyroMag = sqrt(x * x + y * y + z * z)
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val magG = CrashImpactGate.magnitudeG(event.values[0], event.values[1], event.values[2])
                val now = System.currentTimeMillis()
                if (gate.evaluate(magG, tripActive, now)) {
                    listener?.onCrash(magG, lastLocation)
                }
                if (sensorFusion &&
                    phoneUsageGate.evaluate(magG, lastGyroMag, tripActive, appInForeground, now)
                ) {
                    listener?.onPhoneUsageWhileDriving(lastLocation)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /** Best-effort foreground probe for the current process (no permission required). */
    private fun probeForeground(): Boolean = try {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    } catch (_: Exception) {
        false
    }
}
