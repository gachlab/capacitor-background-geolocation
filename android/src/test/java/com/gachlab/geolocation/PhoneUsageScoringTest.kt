// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * #21 — verifies the detector→score wiring for phone usage: when bearing-jitter
 * phone usage is detected mid-trip, the aggregate [TripScore] must carry a
 * `phoneUsage` penalty (previously the event fired but never reached the score).
 *
 * Pure JVM. The detector clocks off `System.currentTimeMillis()`, so the phone
 * window is kept generous (500 ms) and the post-window wait clearly exceeds it,
 * making the timing robust under load. Other event categories are tuned out of
 * range so only phone usage is penalised.
 */
@DisplayName("DrivingEvents — phone usage feeds the trip score (#21)")
class PhoneUsageScoringTest {

    private val events = mutableListOf<String>()
    private var finalScore: TripScore? = null
    private lateinit var detector: DrivingEventsDetector

    private val cfg = DrivingEventsDetector.Config(
        enabled              = true,
        speedLimitKmh        = 500.0,    // never speeding at ~36 km/h
        minMovingSpeedMps    = 1.0,
        stoppedDurationMs    = 200L,
        minTripSpeedMps      = 3.0,
        minTripDurationMs    = 0L,
        hardBrakeMps2        = 50.0,     // disable (constant speed anyway)
        rapidAccelMps2       = 50.0,
        sharpTurnDegPerSec   = 100_000.0, // disable: the jitter must read as phone usage, not sharp turn
        crashImpactKmh       = 100_000.0,
        crashWindowMs        = 2_000L,
        idleThresholdMs      = 10_000L,
        idleEndThresholdMs   = 10_000L,
        sensorFusion         = false,
        phoneUsageWindowMs   = 500L,
        phoneUsageCooldownMs = 0L,
    )

    private val listener = object : DrivingEventsDetector.Listener {
        override fun onMoving(loc: BGLocation) {}
        override fun onStopped(loc: BGLocation) {}
        override fun onTripStart(loc: BGLocation) { events += "tripStart" }
        override fun onTripEnd(loc: BGLocation, distanceMeters: Double, durationMs: Long, score: TripScore) {
            events += "tripEnd"; finalScore = score
        }
        override fun onIdleStart(loc: BGLocation, startedAt: Long) {}
        override fun onIdleEnd(loc: BGLocation, durationMs: Long, startedAt: Long) {}
        override fun onSpeeding(loc: BGLocation, speedKmh: Double, limitKmh: Double) { events += "speeding" }
        override fun onProviderChange(provider: String) {}
        override fun onHardBrake(loc: BGLocation, decelMps2: Double) { events += "hardBrake" }
        override fun onRapidAcceleration(loc: BGLocation, accelMps2: Double) { events += "rapidAccel" }
        override fun onSharpTurn(loc: BGLocation, degPerSec: Double) { events += "sharpTurn" }
        override fun onPossibleCrash(loc: BGLocation, velocityDropKmh: Double) { events += "crash" }
        override fun onPhoneUsageWhileDriving(loc: BGLocation) { events += "phoneUsage" }
    }

    @BeforeEach fun setup() {
        events.clear(); finalScore = null
        detector = DrivingEventsDetector(listener)
        detector.setConfig(cfg)
    }

    private var lat = 19.4326
    /** A fix at ~36 km/h (within the phone-usage speed band 1.39–22.2 m/s). */
    private fun fix(bearing: Float, speedMps: Float = 10f): BGLocation {
        lat += 0.001
        return BGLocation("gps").also {
            it.latitude = lat
            it.longitude = -99.1332
            it.speed = speedMps
            it.bearing = bearing
            it.time = System.currentTimeMillis()
        }
    }

    @Test
    @DisplayName("phone usage detected mid-trip penalises the phoneUsage category")
    fun phoneUsageReachesScore() {
        // 1) Warm up into an active trip (also seeds prevBearing).
        detector.onLocation(fix(100f)); Thread.sleep(15)
        detector.onLocation(fix(100f)); Thread.sleep(15)
        detector.onLocation(fix(100f)); Thread.sleep(15)
        assertTrue(events.contains("tripStart"), "expected tripStart in $events")

        // 2) Three quick ~12° bearing oscillations → jitterCount >= 3 inside the window.
        detector.onLocation(fix(112f)); Thread.sleep(15)
        detector.onLocation(fix(100f)); Thread.sleep(15)
        detector.onLocation(fix(112f)); Thread.sleep(15)

        // 3) Wait past the phone-usage window, then one more jittered fix → triggers.
        Thread.sleep(600)
        detector.onLocation(fix(100f))
        assertTrue(events.contains("phoneUsage"), "expected phoneUsage to fire, got $events")

        // 4) Stop long enough to end the trip and deliver the score.
        detector.onLocation(fix(0f, speedMps = 0f)); Thread.sleep(260)
        detector.onLocation(fix(0f, speedMps = 0f))
        assertTrue(events.contains("tripEnd"), "expected tripEnd in $events")

        // --- The score must reflect the phone usage ---
        val score = finalScore
        assertNotNull(score, "tripEnd should deliver a TripScore")
        score!!
        assertTrue(
            score.breakdown.phoneUsage < 100,
            "phoneUsage category should be penalised, got ${score.breakdown.phoneUsage}"
        )
        assertTrue(
            score.events.any { it.type == "phoneUsage" },
            "phoneUsage should be among scored events: ${score.events.map { it.type }}"
        )
        // No other category should be penalised by this scenario.
        assertTrue(score.breakdown.speeding == 100, "speeding should be clean, got ${score.breakdown.speeding}")
        assertTrue(score.breakdown.sharpTurns == 100, "sharpTurns should be clean, got ${score.breakdown.sharpTurns}")
    }

    @Test
    @DisplayName("sensor phone usage (recordExternalPhoneUsage) feeds the score under sensorFusion")
    fun sensorPhoneUsageReachesScore() {
        // Under sensorFusion the GPS jitter path is gated off; the sensor detector
        // feeds phone usage to the score through recordExternalPhoneUsage.
        detector.setConfig(cfg.copy(sensorFusion = true))

        // Warm up into an active trip (constant bearing → no GPS jitter).
        detector.onLocation(fix(100f)); Thread.sleep(15)
        detector.onLocation(fix(100f)); Thread.sleep(15)
        detector.onLocation(fix(100f)); Thread.sleep(15)
        assertTrue(events.contains("tripStart"), "expected tripStart in $events")

        // Sensor-fusion path records the penalty directly.
        detector.recordExternalPhoneUsage(fix(100f))

        // Stop to end the trip and deliver the score.
        detector.onLocation(fix(0f, speedMps = 0f)); Thread.sleep(260)
        detector.onLocation(fix(0f, speedMps = 0f))
        assertTrue(events.contains("tripEnd"), "expected tripEnd in $events")

        val score = finalScore
        assertNotNull(score, "tripEnd should deliver a TripScore")
        score!!
        assertTrue(
            score.breakdown.phoneUsage < 100,
            "phoneUsage category should be penalised by the sensor path, got ${score.breakdown.phoneUsage}"
        )
        assertTrue(
            score.events.any { it.type == "phoneUsage" },
            "phoneUsage should be among scored events: ${score.events.map { it.type }}"
        )
        // The GPS jitter path stayed gated off — no detector-fired phoneUsage event.
        assertTrue(!events.contains("phoneUsage"), "GPS phoneUsage event must stay suppressed: $events")
    }
}
