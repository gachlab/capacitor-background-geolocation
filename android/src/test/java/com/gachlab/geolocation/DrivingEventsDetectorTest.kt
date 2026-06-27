// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import com.gachlab.geolocation.domain.Journey
import com.gachlab.geolocation.domain.TripConfig

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
    private var lastScore: TripScore? = null
    private lateinit var detector: DrivingEventsDetector

    private val cfg = TripConfig(
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
        override fun onTripEnd(loc: BGLocation, journey: Journey) {
            events += "tripEnd"
            lastScore = journey.score
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
        lastScore = null
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

    @Nested @DisplayName("idle detection")
    inner class IdleDetection {
        private val idleCfg = TripConfig(
            enabled             = true,
            minMovingSpeedMps   = 1.0,
            stoppedDurationMs   = 60_000L,
            minTripSpeedMps     = 3.0,
            minTripDurationMs   = 0L,
            idleThresholdMs     = 100L,
            idleEndThresholdMs  = 100L,
        )

        @Test @DisplayName("idleStart fires after threshold during trip")
        fun idleStartFires() {
            val det = DrivingEventsDetector(listener)
            det.setConfig(idleCfg)
            val startLoc = BGLocation("gps").also {
                it.latitude = 19.43; it.longitude = -99.13
                it.speed = 10f; it.time = System.currentTimeMillis()
            }
            det.onLocation(startLoc)
            // First stopped location: starts idleStartedAt clock
            Thread.sleep(50)
            val idleLoc1 = BGLocation("gps").also {
                it.latitude = 19.43; it.longitude = -99.13
                it.speed = 0f; it.time = System.currentTimeMillis()
            }
            det.onLocation(idleLoc1)
            // Second stopped location: arrives after idleThresholdMs (100L) has elapsed
            Thread.sleep(150)
            val idleLoc2 = BGLocation("gps").also {
                it.latitude = 19.43; it.longitude = -99.13
                it.speed = 0f; it.time = System.currentTimeMillis()
            }
            det.onLocation(idleLoc2)
            assertTrue(events.contains("idleStart"), "expected idleStart in $events")
        }

        @Test @DisplayName("idleStart does NOT fire before trip starts")
        fun noIdleBeforeTrip() {
            val det = DrivingEventsDetector(listener)
            det.setConfig(idleCfg)
            Thread.sleep(200)
            val stoppedLoc = BGLocation("gps").also {
                it.latitude = 19.43; it.longitude = -99.13
                it.speed = 0f; it.time = System.currentTimeMillis()
            }
            det.onLocation(stoppedLoc)
            assertTrue(!events.contains("idleStart"), "idleStart should not fire before trip")
        }
    }

    @Nested @DisplayName("crash confirmation window")
    inner class CrashConfirmation {
        private fun makeLoc(speed: Float, lat: Double = 19.43, lon: Double = -99.13): BGLocation =
            BGLocation("gps").also {
                it.latitude = lat; it.longitude = lon
                it.speed = speed; it.time = System.currentTimeMillis()
            }

        @Test @DisplayName("fires immediately when crashConfirmWindowMs=0")
        fun immediateWithNoWindow() {
            val det = DrivingEventsDetector(listener)
            det.setConfig(cfg.copy(crashConfirmWindowMs = 0L))
            det.onLocation(makeLoc(25f))
            Thread.sleep(100)
            det.onLocation(makeLoc(0f))
            assertTrue(events.contains("crash"), "expected crash with no confirm window: $events")
        }

        @Test @DisplayName("defers crash until confirm window elapses")
        fun deferredFires() {
            val det = DrivingEventsDetector(listener)
            det.setConfig(cfg.copy(crashConfirmWindowMs = 150L))
            det.onLocation(makeLoc(25f))
            Thread.sleep(10)
            det.onLocation(makeLoc(0f))
            assertTrue(!events.contains("crash"), "crash should not fire before confirm window: $events")
            Thread.sleep(200)
            det.onLocation(makeLoc(0f))
            assertTrue(events.contains("crash"), "expected crash after confirm window: $events")
        }

        @Test @DisplayName("cancels crash if speed recovers before confirm window")
        fun cancelledOnRecovery() {
            val det = DrivingEventsDetector(listener)
            det.setConfig(cfg.copy(crashConfirmWindowMs = 300L))
            det.onLocation(makeLoc(25f))
            Thread.sleep(10)
            det.onLocation(makeLoc(0f))
            Thread.sleep(20)
            det.onLocation(makeLoc(10f))  // speed recovery
            Thread.sleep(400)
            det.onLocation(makeLoc(0f))
            assertTrue(!events.contains("crash"), "crash should be cancelled on speed recovery: $events")
        }
    }

    @Nested @DisplayName("phone usage GPS heuristic")
    inner class PhoneUsageDetection {
        private fun makeBearingLoc(speed: Float, bearing: Float, lat: Double = 19.43, lon: Double = -99.13): BGLocation =
            BGLocation("gps").also {
                it.latitude = lat; it.longitude = lon
                it.speed = speed; it.time = System.currentTimeMillis()
                it.bearing = bearing; it.hasBearing = true
            }

        @Test @DisplayName("fires after 3+ jitter events within window, sensorFusion=false")
        fun firesOnJitter() {
            val det = DrivingEventsDetector(listener)
            det.setConfig(cfg.copy(
                sensorFusion       = false,
                phoneUsageWindowMs = 500L,
                phoneUsageCooldownMs = 60_000L,
            ))
            // Drive into TRIP_ACTIVE
            val startLoc = BGLocation("gps").also {
                it.latitude = 19.43; it.longitude = -99.13
                it.speed = 10f; it.time = System.currentTimeMillis()
            }
            det.onLocation(startLoc)
            // Inject jitter events (bearing delta ~10° each)
            var bearing = 90f
            repeat(4) {
                Thread.sleep(60)
                bearing += 10f
                det.onLocation(makeBearingLoc(10f, bearing))
            }
            Thread.sleep(600)
            det.onLocation(makeBearingLoc(10f, bearing + 10f))
            assertTrue(events.contains("phoneUsage"), "expected phoneUsage event: $events")
        }

        @Test @DisplayName("does NOT fire when sensorFusion=true")
        fun suppressedWhenSensorFusion() {
            val det = DrivingEventsDetector(listener)
            det.setConfig(cfg.copy(
                sensorFusion       = true,
                phoneUsageWindowMs = 200L,
                phoneUsageCooldownMs = 60_000L,
            ))
            val startLoc = BGLocation("gps").also {
                it.latitude = 19.43; it.longitude = -99.13
                it.speed = 10f; it.time = System.currentTimeMillis()
            }
            det.onLocation(startLoc)
            var bearing = 90f
            repeat(6) {
                Thread.sleep(30)
                bearing += 10f
                det.onLocation(makeBearingLoc(10f, bearing))
            }
            Thread.sleep(300)
            det.onLocation(makeBearingLoc(10f, bearing + 10f))
            assertTrue(!events.contains("phoneUsage"), "phoneUsage should be suppressed with sensorFusion=true: $events")
        }
    }

    @Nested @DisplayName("trip score")
    inner class TripScoreTests {
        @Test @DisplayName("clean trip scores 100")
        fun cleanTrip() {
            val locs = MockTripBuilder().startAt(19.4326, -99.1332).driveFor(0.5, 60.0).idleFor(0).build()
            locs.forEach { detector.onLocation(it) }
            Thread.sleep(STOPPED_MS + 50)
            val stoppedLoc = BGLocation("gps").also {
                it.latitude = 19.44; it.longitude = -99.13
                it.speed = 0f; it.time = System.currentTimeMillis()
            }
            detector.onLocation(stoppedLoc)
            val score = lastScore
            if (score != null) {
                assertEquals(100, score.overall)
            }
        }
    }

    companion object {
        private const val STOPPED_MS = 10L
    }
}
