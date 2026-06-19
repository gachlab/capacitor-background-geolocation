// SPDX-License-Identifier: MIT
package com.gachlab.geolocation

import org.json.JSONArray
import org.json.JSONObject
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class LocationTemplateTest {

    private fun makeLocation(lat: Double = 10.5, lng: Double = -66.9): BGLocation {
        val l = BGLocation()
        l.latitude = lat
        l.longitude = lng
        l.time = 1700000000000L
        l.speed = 13.5f
        l.hasSpeed = true
        return l
    }

    @Test
    fun `nested object template resolves placeholders at any depth`() {
        // The shape a Cordova-style consumer (e.g. GuestHub pickup) sends.
        val tmpl = JSONObject(
            """{"location":{"coords":{"latitude":"@latitude","longitude":"@longitude"},"timestamp":"@time"}}"""
        )
        val out = HashMapLocationTemplate(tmpl).locationToJson(makeLocation())
        val coords = out.getJSONObject("location").getJSONObject("coords")
        assertEquals(10.5, coords.getDouble("latitude"), 1e-9)
        assertEquals(-66.9, coords.getDouble("longitude"), 1e-9)
        assertEquals(1700000000000L, out.getJSONObject("location").getLong("timestamp"))
    }

    @Test
    fun `flat object template resolves placeholders and preserves literals`() {
        val tmpl = JSONObject("""{"lat":"@latitude","label":"fixed","n":7}""")
        val out = HashMapLocationTemplate(tmpl).locationToJson(makeLocation())
        assertEquals(10.5, out.getDouble("lat"), 1e-9)
        assertEquals("fixed", out.getString("label"))
        assertEquals(7, out.getInt("n"))
    }

    @Test
    fun `array template resolves placeholders and recurses into object elements`() {
        val tmpl = JSONArray("""["@latitude", {"lng":"@longitude"}]""")
        val out = ArrayListLocationTemplate(tmpl).locationToJson(makeLocation())
        assertEquals(10.5, out.getDouble(0), 1e-9)
        assertEquals(-66.9, out.getJSONObject(1).getDouble("lng"), 1e-9)
    }

    @Test
    fun `unknown placeholder is left verbatim`() {
        val out = HashMapLocationTemplate(JSONObject("""{"x":"@nope"}""")).locationToJson(makeLocation())
        assertEquals("@nope", out.getString("x"))
    }
}
