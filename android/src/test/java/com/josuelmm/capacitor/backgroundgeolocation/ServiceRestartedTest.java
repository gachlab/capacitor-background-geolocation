// SPDX-License-Identifier: MIT
package com.josuelmm.capacitor.backgroundgeolocation;

import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.ConfigJsonMapper;
import com.marianhello.bgloc.service.LocationServiceImpl;

import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the P2 serviceRestarted event.
 *
 * Scope: Config field behaviour + JSON mapper round-trips.
 * No Android framework needed; runs on JVM with Gradle :test.
 */
@DisplayName("P2 — serviceRestarted event")
class ServiceRestartedTest {

    // ── MSG constant ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("MSG_ON_SERVICE_RESTARTED is 125")
    void msgConstant() {
        assertEquals(125, LocationServiceImpl.MSG_ON_SERVICE_RESTARTED);
    }

    // ── Config.watchdogIntervalMs ────────────────────────────────────────────

    @Nested
    @DisplayName("Config.watchdogIntervalMs")
    class WatchdogIntervalField {

        @Test
        @DisplayName("defaults to null (uses 60 s built-in default)")
        void defaultIsNull() {
            Config c = new Config();
            assertNull(c.getWatchdogIntervalMs());
        }

        @Test
        @DisplayName("getDefault() leaves it null")
        void getDefaultIsNull() {
            assertNull(Config.getDefault().getWatchdogIntervalMs());
        }

        @Test
        @DisplayName("setter/getter roundtrip")
        void setterGetter() {
            Config c = new Config();
            c.setWatchdogIntervalMs(5_000L);
            assertEquals(5_000L, c.getWatchdogIntervalMs());
        }

        @Test
        @DisplayName("copy constructor preserves value")
        void copyConstructor() {
            Config src = new Config();
            src.setWatchdogIntervalMs(3_000L);
            Config copy = new Config(src);
            assertEquals(3_000L, copy.getWatchdogIntervalMs());
        }

        @Test
        @DisplayName("copy constructor preserves null")
        void copyConstructorNull() {
            Config src = new Config();
            Config copy = new Config(src);
            assertNull(copy.getWatchdogIntervalMs());
        }

        @Test
        @DisplayName("merge() picks up watchdogIntervalMs from config2")
        void merge() {
            Config base = Config.getDefault();
            Config override = new Config();
            override.setWatchdogIntervalMs(2_000L);
            Config merged = Config.merge(base, override);
            assertEquals(2_000L, merged.getWatchdogIntervalMs());
        }

        @Test
        @DisplayName("merge() keeps base value when config2 has null")
        void mergeKeepsBase() {
            Config base = Config.getDefault();
            base.setWatchdogIntervalMs(10_000L);
            Config override = new Config();   // watchdogIntervalMs == null
            Config merged = Config.merge(base, override);
            assertEquals(10_000L, merged.getWatchdogIntervalMs());
        }
    }

    // ── ConfigJsonMapper ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("ConfigJsonMapper watchdogIntervalMs")
    class JsonMapper {

        @Test
        @DisplayName("toJSONObject emits key when set")
        void toJsonEmitsKey() throws Exception {
            Config c = Config.getDefault();
            c.setWatchdogIntervalMs(4_000L);
            JSONObject j = ConfigJsonMapper.toJSONObject(c);
            assertTrue(j.has("watchdogIntervalMs"));
            assertEquals(4_000L, j.getLong("watchdogIntervalMs"));
        }

        @Test
        @DisplayName("toJSONObject omits key when null")
        void toJsonOmitsWhenNull() throws Exception {
            Config c = Config.getDefault();   // watchdogIntervalMs == null
            JSONObject j = ConfigJsonMapper.toJSONObject(c);
            assertFalse(j.has("watchdogIntervalMs"),
                    "null watchdogIntervalMs should not appear in JSON");
        }

        @Test
        @DisplayName("fromJSONObject maps watchdogIntervalMs")
        void fromJson() throws Exception {
            JSONObject j = new JSONObject().put("watchdogIntervalMs", 7_000L);
            Config c = ConfigJsonMapper.fromJSONObject(j);
            assertEquals(7_000L, c.getWatchdogIntervalMs());
        }

        @Test
        @DisplayName("fromJSONObject ignores absent key (null result)")
        void fromJsonAbsent() throws Exception {
            Config c = ConfigJsonMapper.fromJSONObject(new JSONObject());
            assertNull(c.getWatchdogIntervalMs());
        }

        @Test
        @DisplayName("full roundtrip via ConfigJsonMapper")
        void fullRoundtrip() throws Exception {
            Config src = Config.getDefault();
            src.setEnableWatchdog(true);
            src.setWatchdogIntervalMs(6_000L);

            JSONObject j = ConfigJsonMapper.toJSONObject(src);
            Config restored = ConfigJsonMapper.fromJSONObject(j);

            assertTrue(Boolean.TRUE.equals(restored.getEnableWatchdog()));
            assertEquals(6_000L, restored.getWatchdogIntervalMs());
        }
    }

    // ── ConfigMapper (Capacitor bridge) ───────────────────────────────────────

    @Nested
    @DisplayName("ConfigMapper (Capacitor) watchdogIntervalMs")
    class CapacitorMapper {

        @Test
        @DisplayName("fromJSONObject maps watchdogIntervalMs")
        void fromJson() throws Exception {
            org.json.JSONObject json = new org.json.JSONObject().put("watchdogIntervalMs", 3_000L);
            Config c = ConfigMapper.fromJSONObject(json);
            assertEquals(3_000L, c.getWatchdogIntervalMs());
        }

        @Test
        @DisplayName("toJSObject emits watchdogIntervalMs")
        void toJs() throws Exception {
            Config c = Config.getDefault();
            c.setWatchdogIntervalMs(5_000L);
            com.getcapacitor.JSObject js = ConfigMapper.toJSObject(c);
            assertEquals(5_000L, js.getLong("watchdogIntervalMs"));
        }

        @Test
        @DisplayName("toJSObject omits watchdogIntervalMs key when not set")
        void toJsNull() throws Exception {
            Config c = Config.getDefault();   // watchdogIntervalMs == null
            com.getcapacitor.JSObject js = ConfigMapper.toJSObject(c);
            // JSONObject.put(key, null) removes the key, so absence is correct.
            assertFalse(js.has("watchdogIntervalMs") && !js.isNull("watchdogIntervalMs"),
                    "unset watchdogIntervalMs must not appear as a non-null value");
        }
    }
}
