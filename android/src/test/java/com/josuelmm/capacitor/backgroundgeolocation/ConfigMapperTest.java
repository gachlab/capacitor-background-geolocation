// SPDX-License-Identifier: Apache-2.0
package com.josuelmm.capacitor.backgroundgeolocation;

import com.marianhello.bgloc.Config;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ConfigMapper")
class ConfigMapperTest {

    // ── fromJSONObject ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("fromJSONObject()")
    class FromJSONObject {

        @Test
        @DisplayName("maps distanceFilter")
        void distanceFilter() throws Exception {
            JSONObject json = new JSONObject().put("distanceFilter", 150);
            Config config = ConfigMapper.fromJSONObject(json);
            assertEquals(150, config.getDistanceFilter());
        }

        @Test
        @DisplayName("maps stationaryRadius")
        void stationaryRadius() throws Exception {
            JSONObject json = new JSONObject().put("stationaryRadius", 30.0);
            Config config = ConfigMapper.fromJSONObject(json);
            assertEquals(30.0, config.getStationaryRadius(), 0.001);
        }

        @Test
        @DisplayName("maps stopOnTerminate")
        void stopOnTerminate() throws Exception {
            JSONObject json = new JSONObject().put("stopOnTerminate", false);
            Config config = ConfigMapper.fromJSONObject(json);
            assertFalse(config.getStopOnTerminate());
        }

        @Test
        @DisplayName("maps startOnBoot")
        void startOnBoot() throws Exception {
            JSONObject json = new JSONObject().put("startOnBoot", true);
            Config config = ConfigMapper.fromJSONObject(json);
            assertTrue(config.getStartOnBoot());
        }

        @ParameterizedTest(name = "interval={0}")
        @CsvSource({"1000", "5000", "60000"})
        @DisplayName("maps interval values")
        void interval(int ms) throws Exception {
            JSONObject json = new JSONObject().put("interval", ms);
            Config config = ConfigMapper.fromJSONObject(json);
            assertEquals(ms, config.getInterval());
        }

        @Test
        @DisplayName("maps url — non-null")
        void urlNonNull() throws Exception {
            JSONObject json = new JSONObject().put("url", "https://example.com/locations");
            Config config = ConfigMapper.fromJSONObject(json);
            assertEquals("https://example.com/locations", config.getUrl());
        }

        @Test
        @DisplayName("maps url — JSON null becomes Config.NullString sentinel")
        void urlJsonNull() throws Exception {
            JSONObject json = new JSONObject();
            json.put("url", JSONObject.NULL);
            Config config = ConfigMapper.fromJSONObject(json);
            assertSame(Config.NullString, config.getUrl(),
                    "null url should map to Config.NullString sentinel, not Java null");
        }

        @Test
        @DisplayName("maps notificationTitle — null value becomes Config.NullString")
        void notificationTitleNull() throws Exception {
            JSONObject json = new JSONObject();
            json.put("notificationTitle", JSONObject.NULL);
            Config config = ConfigMapper.fromJSONObject(json);
            assertSame(Config.NullString, config.getNotificationTitle());
        }

        @Test
        @DisplayName("maps notificationTitle — non-null value")
        void notificationTitleNonNull() throws Exception {
            JSONObject json = new JSONObject().put("notificationTitle", "Tracking active");
            Config config = ConfigMapper.fromJSONObject(json);
            assertEquals("Tracking active", config.getNotificationTitle());
        }

        @Test
        @DisplayName("maps syncThreshold")
        void syncThreshold() throws Exception {
            JSONObject json = new JSONObject().put("syncThreshold", 25);
            Config config = ConfigMapper.fromJSONObject(json);
            assertEquals(25, config.getSyncThreshold());
        }

        @Test
        @DisplayName("maps heartbeatInterval")
        void heartbeatInterval() throws Exception {
            JSONObject json = new JSONObject().put("heartbeatInterval", 30000);
            Config config = ConfigMapper.fromJSONObject(json);
            assertEquals(30000, config.getHeartbeatInterval());
        }

        @Test
        @DisplayName("maps mockLocationPolicy")
        void mockLocationPolicy() throws Exception {
            JSONObject json = new JSONObject().put("mockLocationPolicy", "drop");
            Config config = ConfigMapper.fromJSONObject(json);
            assertEquals("drop", config.getMockLocationPolicy());
        }

        @Test
        @DisplayName("maps enableWatchdog")
        void enableWatchdog() throws Exception {
            JSONObject json = new JSONObject().put("enableWatchdog", true);
            Config config = ConfigMapper.fromJSONObject(json);
            assertTrue(Boolean.TRUE.equals(config.getEnableWatchdog()));
        }

        @Test
        @DisplayName("maps watchdogIntervalMs")
        void watchdogIntervalMs() throws Exception {
            JSONObject json = new JSONObject().put("watchdogIntervalMs", 5000L);
            Config config = ConfigMapper.fromJSONObject(json);
            assertEquals(5000L, config.getWatchdogIntervalMs());
        }

        @Test
        @DisplayName("absent watchdogIntervalMs leaves it null")
        void watchdogIntervalMsAbsent() throws Exception {
            JSONObject json = new JSONObject();
            Config config = ConfigMapper.fromJSONObject(json);
            assertNull(config.getWatchdogIntervalMs());
        }

        @Test
        @DisplayName("absent fields leave Config at its defaults — does not throw")
        void emptyObject() throws Exception {
            JSONObject json = new JSONObject();
            assertDoesNotThrow(() -> ConfigMapper.fromJSONObject(json));
        }

        @Nested
        @DisplayName("drivingEvents nested object")
        class DrivingEvents {

            @Test
            @DisplayName("maps enabled flag")
            void enabled() throws Exception {
                JSONObject de = new JSONObject().put("enabled", true);
                JSONObject json = new JSONObject().put("drivingEvents", de);
                Config config = ConfigMapper.fromJSONObject(json);
                assertNotNull(config.getDrivingEvents());
                assertTrue(config.getDrivingEvents().enabled);
            }

            @Test
            @DisplayName("maps speedLimit (km/h)")
            void speedLimit() throws Exception {
                JSONObject de = new JSONObject().put("enabled", true).put("speedLimit", 100.0);
                JSONObject json = new JSONObject().put("drivingEvents", de);
                Config config = ConfigMapper.fromJSONObject(json);
                assertEquals(100.0, config.getDrivingEvents().speedLimitKmh, 0.001);
            }

            @Test
            @DisplayName("maps hardBrakeMps2 threshold")
            void hardBrake() throws Exception {
                JSONObject de = new JSONObject().put("enabled", true).put("hardBrakeMps2", 8.0);
                JSONObject json = new JSONObject().put("drivingEvents", de);
                Config config = ConfigMapper.fromJSONObject(json);
                assertEquals(8.0, config.getDrivingEvents().hardBrakeMps2, 0.001);
            }

            @Test
            @DisplayName("null drivingEvents object is ignored — no NPE")
            void nullDrivingEvents() throws Exception {
                JSONObject json = new JSONObject();
                json.put("drivingEvents", JSONObject.NULL);
                assertDoesNotThrow(() -> ConfigMapper.fromJSONObject(json));
            }
        }
    }

    // ── toJSObject ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("toJSObject()")
    class ToJSObject {

        @Test
        @DisplayName("roundtrip: distanceFilter survives from→to")
        void roundtripDistanceFilter() throws Exception {
            JSONObject json = new JSONObject().put("distanceFilter", 200);
            Config config = ConfigMapper.fromJSONObject(json);
            var jsObj = ConfigMapper.toJSObject(config);
            assertEquals(200, jsObj.getInt("distanceFilter"));
        }

        @Test
        @DisplayName("roundtrip: url null sentinel serialises as JSON null")
        void roundtripUrlNull() throws Exception {
            JSONObject json = new JSONObject();
            json.put("url", JSONObject.NULL);
            Config config = ConfigMapper.fromJSONObject(json);
            var jsObj = ConfigMapper.toJSObject(config);
            assertTrue(jsObj.isNull("url"), "url should be JSON null in output");
        }

        @Test
        @DisplayName("roundtrip: syncEnabled survives from→to")
        void roundtripSyncEnabled() throws Exception {
            JSONObject json = new JSONObject().put("sync", false);
            Config config = ConfigMapper.fromJSONObject(json);
            var jsObj = ConfigMapper.toJSObject(config);
            assertFalse(jsObj.getBoolean("sync"));
        }

        @Test
        @DisplayName("watchdogIntervalMs survives from→to roundtrip")
        void watchdogIntervalMsRoundtrip() throws Exception {
            JSONObject json = new JSONObject().put("watchdogIntervalMs", 3000L);
            Config config = ConfigMapper.fromJSONObject(json);
            var jsObj = ConfigMapper.toJSObject(config);
            assertEquals(3000L, jsObj.getLong("watchdogIntervalMs"));
        }

        @Test
        @DisplayName("drivingEvents are serialised back to nested object")
        void drivingEventsRoundtrip() throws Exception {
            JSONObject de = new JSONObject()
                    .put("enabled", true)
                    .put("speedLimit", 90.0)
                    .put("hardBrakeMps2", 7.5);
            JSONObject json = new JSONObject().put("drivingEvents", de);
            Config config = ConfigMapper.fromJSONObject(json);
            var jsObj = ConfigMapper.toJSObject(config);

            assertTrue(jsObj.has("drivingEvents"), "drivingEvents key should be present");
            var deOut = jsObj.getJSONObject("drivingEvents");
            assertTrue(deOut.getBoolean("enabled"));
            assertEquals(90.0, deOut.getDouble("speedLimit"), 0.001);
            assertEquals(7.5, deOut.getDouble("hardBrakeMps2"), 0.001);
        }
    }
}
