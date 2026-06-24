// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
 * Sensor-based crash detection via `TYPE_LINEAR_ACCELERATION`, the Android
 * counterpart of the iOS `SensorFusionDetector` (CMMotionManager userAcceleration).
 *
 * Fires [Listener.onCrash] when the impact magnitude exceeds `crashImpactG` during
 * an active trip. Runs alongside — and is fused with — the GPS crash path in
 * [DrivingEventsDetector]: both can fire, distinguished by the `PossibleCrash`
 * `source` field ('gps' vs 'sensor').
 */
internal class SensorFusionDetector(context: Context) : SensorEventListener {

    interface Listener {
        fun onCrash(impactG: Double, location: BGLocation?)
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

    private val gate = CrashImpactGate()
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccel: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private var started = false

    val isAvailable: Boolean get() = linearAccel != null

    fun start() {
        if (started || linearAccel == null) return
        started = true
        // SENSOR_DELAY_GAME ≈ 50 Hz, matching the iOS 1/50 s motion interval.
        sensorManager.registerListener(this, linearAccel, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (!started) return
        started = false
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_LINEAR_ACCELERATION) return
        val magG = CrashImpactGate.magnitudeG(event.values[0], event.values[1], event.values[2])
        if (gate.evaluate(magG, tripActive, System.currentTimeMillis())) {
            listener?.onCrash(magG, lastLocation)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
