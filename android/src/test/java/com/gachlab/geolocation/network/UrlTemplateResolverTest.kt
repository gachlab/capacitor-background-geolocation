// SPDX-License-Identifier: MIT
package com.gachlab.geolocation.network

import com.gachlab.geolocation.BGLocation
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class UrlTemplateResolverTest {

    private fun makeLocation(lat: Double = 10.5, lng: Double = -66.9): BGLocation {
        val l = BGLocation()
        l.latitude = lat
        l.longitude = lng
        l.time = System.currentTimeMillis()
        l.speed = 50.0f
        l.altitude = 100.0
        l.bearing = 45.0f
        l.hasSpeed = true
        l.hasAltitude = true
        l.hasBearing = true
        return l
    }

    @Test
    fun `resolve returns url unchanged when no placeholders`() {
        val result = UrlTemplateResolver.resolve("https://example.com/locations", makeLocation())
        assertEquals("https://example.com/locations", result)
    }

    @Test
    fun `resolve replaces latitude and longitude placeholders`() {
        val result = UrlTemplateResolver.resolve(
            "https://example.com/{latitude}/{longitude}", makeLocation(10.5, -66.9)
        )
        assertTrue(result.contains("10.5"), "Should contain latitude")
        assertTrue(result.contains("-66.9"), "Should contain longitude")
    }

    @Test
    fun `resolve handles transport id placeholder via queryParams`() {
        val result = UrlTemplateResolver.resolve(
            "https://example.com/transports/{id}/locations",
            makeLocation(),
            mapOf("id" to "transport-123")
        )
        assertEquals("https://example.com/transports/transport-123/locations", result)
    }

    @Test
    fun `resolve handles url without placeholders with queryParams`() {
        val result = UrlTemplateResolver.resolve(
            "http://10.0.2.2:3000/pickup/transports/abc/locations",
            makeLocation(),
            null
        )
        assertEquals("http://10.0.2.2:3000/pickup/transports/abc/locations", result)
    }

    @Test
    fun `object initializes without exception`() {
        assertNotNull(UrlTemplateResolver)
    }
}
