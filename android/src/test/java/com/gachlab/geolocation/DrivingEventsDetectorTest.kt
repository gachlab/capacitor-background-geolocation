// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import com.gachlab.geolocation.fixtures.MockTripBuilder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Nested
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DisplayName("DrivingEventsDetector")
class DrivingEventsDetectorTest {

    private val events = mutableListOf<String>()
    private lateinit var detector: DrivingEventsDetector

    private val cfg = DrivingEventsDetector.Config(
        enabled            = true,
        speedLimitKmh      = 120.0,
        minMovingSpeedMps  = 1.0,
        stoppedDurationMs  = STOPPED_MS,
        minTripSpeedMps    = 3.0,
        minTripDurationMs  = 0L,        // fire tripStart on first qualifying fix
        hardBrakeMps2      = 3.5,
        rapidAccelMps2     = 3.5,
        sharpTurnDegPerSec = 30.0,
        crashImpactKmh     = 25.0,
        crashWindowMs      = 2_000L
    )

    private val listener = object : DrivingEventsDetector.Listener {
        override fun onMoving(loc: BGLocation) { events += "moving" }
        override fun onStopped(loc: BGLocation) { events += "stopped" }
        override fun onTripStart(loc: BGLocation) { events += "tripStart" }
        override fun onTripEnd(loc: BGLocation, distanceMeters: Double, durationMs: Long) { events += "tripEnd" }
        override fun onSpeeding(loc: BGLocation, speedKmh: Double, limitKmh: Double) { events += "speeding" }
        override fun onProviderChange(provider: String) {}
        override fun onHardBrake(loc: BGLocation, decelMps2: Double) { events += "hardBrake" }
        override fun onRapidAcceleration(loc: BGLocation, accelMps2: Double) { events += "rapidAccel" }
        override fun onSharpTurn(loc: BGLocation, degPerSec: Double) { events += "sharpTurn" }
        override fun onPossibleCrash(loc: BGLocation, velocityDropKmh: Double) { events += "crash" }
    }

    @BeforeEach fun setup() {
        events.clear()
        detector = DrivingEventsDetector(listener)
        detector.setConfig(cfg)
    }

    @Nested @DisplayName("basic state transitions")
    inner class BasicTransitions {
        @Test @DisplayName("fires moving then tripStart on first fast fix")
        fun movingAndTripStart() {
            val locs = MockTripBuilder()
                .startAt(19.4326, -99.1332)
                .driveFor(0.5, 60.0)
                .build()
            locs.forEach { detector.onLocation(it) }
            assertTrue(events.contains("moving"), "expected 'moving' in $events")
            assertTrue(events.contains("tripStart"), "expected 'tripStart' in $events")
        }

        @Test @DisplayName("fires stopped and tripEnd after idling long enough")
        fun stoppedAndTripEnd() {
            val locs = MockTripBuilder()
                .startAt(19.4326, -99.1332)
                .driveFor(0.5, 60.0)
                .idleFor(1)
                .build()
            locs.forEach { detector.onLocation(it) }
            Thread.sleep(STOPPED_MS + 50)
            val stoppedLoc = BGLocation("gps").also {
                it.latitude = 19.44; it.longitude = -99.13
                it.speed = 0f; it.time = System.currentTimeMillis()
            }
            detector.onLocation(stoppedLoc)
            assertTrue(events.contains("stopped"), "expected 'stopped' in $events")
            assertTrue(events.contains("tripEnd"), "expected 'tripEnd' in $events")
        }

        @Test @DisplayName("fires speeding when above limit")
        fun speedingEvent() {
            val locs = MockTripBuilder()
                .startAt(19.4326, -99.1332)
                .driveFor(1.0, 150.0)  // 150 km/h > 120 limit
                .build()
            locs.forEach { detector.onLocation(it) }
            assertTrue(events.contains("speeding"), "expected 'speeding' in $events")
        }
    }

    @Nested @DisplayName("GPS-derived driving events")
    inner class GpsDrivingEvents {
        @Test @DisplayName("detects hard brake during active trip")
        fun hardBrake() {
            val locs = MockTripBuilder()
                .startAt(19.4326, -99.1332)
                .driveFor(0.5, 60.0)
                .build()
            locs.forEach { detector.onLocation(it) }
            Thread.sleep(5)
            val brakeLoc = BGLocation("gps").also {
                it.latitude = 19.437; it.longitude = -99.133
                it.speed = 0f; it.time = System.currentTimeMillis()
            }
            detector.onLocation(brakeLoc)
            assertTrue(events.contains("hardBrake"), "expected 'hardBrake' in $events")
        }

        @Test @DisplayName("does NOT fire when disabled")
        fun disabledCfg() {
            detector.setConfig(cfg.copy(enabled = false))
            val locs = MockTripBuilder()
                .startAt(19.4326, -99.1332)
                .driveFor(1.0, 60.0)
                .build()
            locs.forEach { detector.onLocation(it) }
            assertTrue(events.isEmpty(), "expected no events when disabled, got $events")
        }
    }

    companion object {
        private const val STOPPED_MS = 10L
    }
}
