// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

import com.gachlab.capacitor.backgroundgeolocation.GachConfigMapper
import org.json.JSONObject
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@DisplayName("GachConfigMapper")
class ConfigMapperTest {

    @Nested @DisplayName("fromJSONObject")
    inner class FromJSON {

        @Test @DisplayName("returns default config when JSON is empty")
        fun emptyJsonGivesDefaults() {
            val cfg = GachConfigMapper.fromJSONObject(JSONObject())
            // All fields null — caller uses BGConfig.getDefault() + merge()
            assertNull(cfg.distanceFilter)
        }

        @Test @DisplayName("reads distanceFilter correctly")
        fun distanceFilter() {
            val cfg = GachConfigMapper.fromJSONObject(JSONObject().put("distanceFilter", 200))
            assertEquals(200, cfg.distanceFilter)
        }

        @Test @DisplayName("reads url and handles null url")
        fun urlHandling() {
            val withUrl  = GachConfigMapper.fromJSONObject(JSONObject().put("url", "https://example.com"))
            assertEquals("https://example.com", withUrl.url)

            val withNull = GachConfigMapper.fromJSONObject(JSONObject().put("url", JSONObject.NULL))
            assertEquals(BGConfig.NULL_STRING, withNull.url)
        }

        @ParameterizedTest(name = "httpMethod={0}")
        @CsvSource("POST", "PUT", "PATCH")
        @DisplayName("normalizes httpMethod to uppercase")
        fun httpMethodUppercase(method: String) {
            val cfg = GachConfigMapper.fromJSONObject(JSONObject().put("httpMethod", method.lowercase()))
            assertEquals(method, cfg.httpMethod)
        }

        @Test @DisplayName("reads drivingEvents block")
        fun drivingEvents() {
            val de = JSONObject()
                .put("enabled", true)
                .put("speedLimit", 120.0)
                .put("hardBrakeMps2", 4.0)
            val cfg = GachConfigMapper.fromJSONObject(JSONObject().put("drivingEvents", de))
            assertNotNull(cfg.drivingEvents)
            assertTrue(cfg.drivingEvents!!.enabled)
            assertEquals(120.0, cfg.drivingEvents!!.speedLimitKmh)
            assertEquals(4.0,   cfg.drivingEvents!!.hardBrakeMps2)
        }
    }

    @Nested @DisplayName("round-trip JSON")
    inner class RoundTrip {

        @Test @DisplayName("toJSONObject / fromJSONObject preserves key fields")
        fun roundTrip() {
            val original = BGConfig.getDefault().apply {
                distanceFilter = 350
                url = "https://example.com/loc"
                syncEnabled = true
            }
            val json   = GachConfigMapper.toJSONObject(original)
            val result = GachConfigMapper.fromJSONObject(json)
            assertEquals(350, result.distanceFilter)
            assertEquals("https://example.com/loc", result.url)
            assertEquals(true, result.syncEnabled)
        }
    }
}
