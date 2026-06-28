// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import com.gachlab.geolocation.domain.Journey
import com.gachlab.geolocation.domain.TripConfig

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue

/**
 * Integration test: exercises a full trip through a SINGLE detector instance and
 * asserts that the individual driving events compose into one aggregate [TripScore].
 *
 * The unit tests in [DrivingEventsDetectorTest] verify each event in isolation.
 * This test verifies the lifecycle as a whole — tripStart → speeding → hardBrake →
 * idleStart → idleEnd → tripEnd — and that the resulting score reflects the penalties
 * and idle accounting that accumulated along the way. Pure JVM (no Android), runs
 * under `./gradlew test`.
 */
@DisplayName("DrivingEvents — full trip lifecycle (integration)")
class DrivingEventsIntegrationTest {

    private val events = mutableListOf<String>()
    private var finalScore: TripScore? = null
    private lateinit var detector: DrivingEventsDetector

    // Tight thresholds so the lifecycle runs in well under a second of wall-clock.
    private val cfg = TripConfig(
        enabled            = true,
        speedLimitKmh      = 120.0,
        minMovingSpeedMps  = 1.0,
        // idleThreshold must be << stoppedDuration so idle fires DURING the trip,
        // not as the trip-ending stop.
        stoppedDurationMs  = 400L,
        minTripSpeedMps    = 3.0,
        minTripDurationMs  = 0L,
        hardBrakeMps2      = 3.5,
        rapidAccelMps2     = 3.5,
        sharpTurnDegPerSec = 30.0,
        crashImpactKmh     = 1_000.0,   // disable crash so the brake reads as hardBrake only
        crashWindowMs      = 2_000L,
        idleThresholdMs    = 80L,
        idleEndThresholdMs = 80L,
    )

    private val listener = object : DrivingEventsDetector.Listener {
        override fun onMoving(loc: BGLocation) { events += "moving" }
        override fun onStopped(loc: BGLocation) { events += "stopped" }
        override fun onTripStart(loc: BGLocation) { events += "tripStart" }
        override fun onTripEnd(loc: BGLocation, journey: Journey) {
            events += "tripEnd"
            finalScore = journey.score
        }
        override fun onIdleStart(loc: BGLocation, startedAt: Long) { events += "idleStart" }
        override fun onIdleEnd(loc: BGLocation, durationMs: Long, startedAt: Long) { events += "idleEnd" }
        override fun onSpeeding(loc: BGLocation, speedKmh: Double, limitKmh: Double) { events += "speeding" }
        override fun onProviderChange(provider: String) {}
        override fun onHardBrake(loc: BGLocation, decelMps2: Double) { events += "hardBrake" }
        override fun onRapidAcceleration(loc: BGLocation, accelMps2: Double) { events += "rapidAccel" }
        override fun onSharpTurn(loc: BGLocation, degPerSec: Double) { events += "sharpTurn" }
        override fun onPossibleCrash(loc: BGLocation, velocityDropKmh: Double) { events += "crash" }
        override fun onPhoneUsageWhileDriving(loc: BGLocation) { events += "phoneUsage" }
    }

    @BeforeEach fun setup() {
        events.clear()
        finalScore = null
        detector = DrivingEventsDetector(listener)
        detector.setConfig(cfg)
    }

    private var lat = 19.4326
    private fun fix(speedMps: Float): BGLocation {
        lat += 0.001 // ~111 m between fixes so the trip accrues real distance
        return BGLocation("gps").also {
            it.latitude = lat
            it.longitude = -99.1332
            it.speed = speedMps
            it.time = System.currentTimeMillis()
        }
    }

    @Test
    @DisplayName("a full trip produces an aggregate score reflecting its events")
    fun fullTripLifecycle() {
        // 1) Start moving fast → moving + tripStart
        detector.onLocation(fix(20f))   // 72 km/h
        Thread.sleep(20)
        // 2) Exceed the speed limit → speeding
        detector.onLocation(fix(40f))   // 144 km/h > 120
        Thread.sleep(20)
        // 3) Slam to a stop → hardBrake (and begins the stopped/idle clock)
        detector.onLocation(fix(0f))
        // 4) Stay stopped past the idle threshold (but under stoppedDuration) → idleStart
        Thread.sleep(cfg.idleThresholdMs + 60)
        detector.onLocation(fix(0f))
        // 5) Resume driving. idleEnd needs a moving fix to arm the post-idle clock and a
        //    second moving fix once idleEndThresholdMs has elapsed (detector lines 177-185).
        Thread.sleep(20)
        detector.onLocation(fix(20f))            // arms postIdleMovingAt
        Thread.sleep(cfg.idleEndThresholdMs + 40)
        detector.onLocation(fix(20f))            // → idleEnd
        // 6) Come to a final stop and wait past stoppedDuration → stopped + tripEnd
        detector.onLocation(fix(0f))
        Thread.sleep(cfg.stoppedDurationMs + 80)
        detector.onLocation(fix(0f))

        // --- Event ordering ---
        assertTrue(events.contains("tripStart"), "expected tripStart in $events")
        assertTrue(events.contains("speeding"), "expected speeding in $events")
        assertTrue(events.contains("hardBrake"), "expected hardBrake in $events")
        assertTrue(events.contains("idleStart"), "expected idleStart in $events")
        assertTrue(events.contains("idleEnd"), "expected idleEnd in $events")
        assertTrue(events.contains("tripEnd"), "expected tripEnd in $events")
        assertTrue(!events.contains("crash"), "crash must not fire when impact threshold is disabled: $events")

        // --- Aggregate score ---
        val score = finalScore
        assertNotNull(score, "tripEnd should deliver a TripScore")
        score!!
        assertTrue(score.overall in 0..99, "a trip with speeding + hardBrake should score < 100, got ${score.overall}")
        // breakdown holds per-category scores (100 - penalty); a penalised category drops below 100.
        assertTrue(score.breakdown.speeding < 100, "speeding category should be penalised, got ${score.breakdown.speeding}")
        assertTrue(score.breakdown.hardBraking < 100, "hardBraking category should be penalised, got ${score.breakdown.hardBraking}")
        assertTrue(score.events.any { it.type == "speeding" }, "speeding should be among scored events: ${score.events.map { it.type }}")
        assertTrue(score.events.any { it.type == "hardBrake" }, "hardBrake should be among scored events: ${score.events.map { it.type }}")
        assertTrue(score.idleCount >= 1, "at least one idle period expected, got ${score.idleCount}")
        assertTrue(score.distanceKm > 0.0, "trip should accrue distance, got ${score.distanceKm}")
        assertTrue(score.endedAt >= score.startedAt, "endedAt must be >= startedAt")
    }
}
