// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.fixtures

import com.gachlab.geolocation.BGLocation

class MockTripBuilder {

    private val locations = mutableListOf<BGLocation>()
    private var curLat      = 0.0
    private var curLon      = 0.0
    private var curSpeedMps = 0f
    private var curTimeMs   = 1_716_000_000_000L

    fun startAt(lat: Double, lon: Double): MockTripBuilder {
        curLat = lat; curLon = lon; addFix(0f); return this
    }

    fun driveFor(distanceKm: Double, speedKmh: Double): MockTripBuilder {
        val mps = (speedKmh / 3.6).toFloat()
        val metersPerFix = mps * (FIX_MS / 1_000.0)
        val fixes = maxOf(1, Math.ceil(distanceKm * 1_000.0 / metersPerFix).toInt())
        val dLat  = metersPerFix / METERS_PER_DEG_LAT
        repeat(fixes) { curLat += dLat; curTimeMs += FIX_MS; addFix(mps) }
        return this
    }

    fun speedUp(toKmh: Double): MockTripBuilder {
        val fromKmh = curSpeedMps * 3.6
        val step    = (toKmh - fromKmh) / 5.0
        for (i in 1..5) { curTimeMs += FIX_MS; addFix(((fromKmh + step * i) / 3.6).toFloat()) }
        return this
    }

    fun hardBrake(): MockTripBuilder { curTimeMs += FIX_MS; addFix(0f); return this }

    fun idleFor(count: Int): MockTripBuilder {
        repeat(count) { curTimeMs += FIX_MS; addFix(0f) }; return this
    }

    fun build(): List<BGLocation> = locations.toList()

    private fun addFix(speedMps: Float) {
        curSpeedMps = speedMps
        val loc = BGLocation("gps")
        loc.latitude  = curLat
        loc.longitude = curLon
        loc.speed     = speedMps
        loc.time      = curTimeMs
        locations += loc
    }

    companion object {
        private const val METERS_PER_DEG_LAT = 111_000.0
        private const val FIX_MS = 1_000L
    }
}
