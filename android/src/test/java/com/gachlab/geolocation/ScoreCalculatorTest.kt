// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScoreCalculatorTest {

    private fun loc(lat: Double = 19.4326, lon: Double = -99.1332): BGLocation =
        BGLocation("gps").also { it.latitude = lat; it.longitude = lon; it.time = System.currentTimeMillis() }

    @Test fun `perfect trip scores 100`() {
        val calc = ScoreCalculator()
        val score = calc.compute("t1", 1000L, 2000L, 5000.0)
        assertEquals(100, score.overall)
        assertEquals(0, score.events.size)
        assertEquals(0L, score.totalIdleMs)
    }

    @Test fun `hard brake deducts from braking category`() {
        val calc = ScoreCalculator()
        calc.recordHardBrake(loc(), 1500L)
        val score = calc.compute("t1", 1000L, 2000L, 1000.0)
        val expectedBraking = 88
        assertEquals(expectedBraking, score.breakdown.hardBraking)
        assertEquals(1, score.events.size)
        assertEquals("hardBrake", score.events[0].type)
        assertEquals(12, score.events[0].penalty)
    }

    @Test fun `overall is 100 for clean trip with default weights`() {
        val calc = ScoreCalculator(ScoringWeights(30, 25, 20, 15, 10))
        val score = calc.compute("t1", 0L, 1000L, 2000.0)
        assertEquals(100, score.overall)
        assertEquals(100, score.breakdown.speeding)
        assertEquals(100, score.breakdown.hardBraking)
    }

    @Test fun `custom weights double speeding impact`() {
        val weights = ScoringWeights(speeding = 50, hardBraking = 25, rapidAccel = 15, sharpTurn = 5, phoneUsage = 5)
        assertTrue(weights.isValid())
        val calc = ScoreCalculator(weights)
        calc.recordSpeeding(loc(), 1500L)
        val score = calc.compute("t1", 1000L, 2000L, 1000.0)
        val speedScore = 100 - 15   // 85
        val expectedOverall = (85 * 50 + 100 * 25 + 100 * 15 + 100 * 5 + 100 * 5) / 100
        assertEquals(expectedOverall, score.overall)
    }

    @Test fun `idle fields tracked correctly`() {
        val calc = ScoreCalculator()
        calc.recordIdleStart()
        calc.recordIdleEnd(120_000L)
        calc.recordIdleStart()
        calc.recordIdleEnd(60_000L)
        val score = calc.compute("t1", 0L, 1000L, 5000.0)
        assertEquals(2, score.idleCount)
        assertEquals(180_000L, score.totalIdleMs)
    }

    @Test fun `score is clamped at zero for many events`() {
        val calc = ScoreCalculator()
        repeat(20) { calc.recordHardBrake(loc(), 1000L + it) }
        val score = calc.compute("t1", 0L, 1000L, 1000.0)
        assertEquals(0, score.breakdown.hardBraking)
        assertTrue(score.overall >= 0)
    }

    @Test fun `distanceKm converts correctly`() {
        val calc = ScoreCalculator()
        val score = calc.compute("t1", 0L, 1000L, 12345.0)
        assertEquals(12.345, score.distanceKm, 0.001)
    }
}
