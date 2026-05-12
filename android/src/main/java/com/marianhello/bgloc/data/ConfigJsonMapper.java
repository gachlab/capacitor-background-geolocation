package com.marianhello.bgloc.data;

import com.marianhello.bgloc.Config;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;

/**
 * v4.4.1 — JSON serializer/deserializer for the full {@link Config} state.
 *
 * Lives in {@code common} so both the SQLite DAO (also common) and the Cordova
 * {@code ConfigMapper} (cordova) can reuse it without creating a common→cordova
 * dependency. Used to persist a single {@code config_json} TEXT column instead of
 * adding one schema column per new field on every release.
 *
 * Round-trip: every JS-configurable key the plugin understands is preserved.
 * Anything not present in the input JSON keeps the {@link Config} default.
 */
public final class ConfigJsonMapper {

    private ConfigJsonMapper() {}

    /** Serialize the current Config state to a JSONObject suitable for storage.
     *  String fields use {@link JSONObject#NULL} when the user explicitly cleared them
     *  (i.e. equals {@link Config#NullString}) so the sentinel survives the round-trip. */
    public static JSONObject toJSONObject(Config c) throws JSONException {
        JSONObject j = new JSONObject();
        if (c == null) return j;
        j.put("stationaryRadius", c.getStationaryRadius());
        j.put("distanceFilter", c.getDistanceFilter());
        j.put("desiredAccuracy", c.getDesiredAccuracy());
        j.put("debug", c.isDebugging());
        j.put("notificationTitle", nullable(c.getNotificationTitle()));
        j.put("notificationText", nullable(c.getNotificationText()));
        j.put("notificationSyncTitle", nullable(c.getNotificationSyncTitle()));
        j.put("notificationSyncText", nullable(c.getNotificationSyncText()));
        j.put("notificationSyncCompletedText", nullable(c.getNotificationSyncCompletedText()));
        j.put("notificationSyncFailedText", nullable(c.getNotificationSyncFailedText()));
        j.put("notificationIconLarge", nullable(c.getLargeNotificationIcon()));
        j.put("notificationIconSmall", nullable(c.getSmallNotificationIcon()));
        j.put("notificationIconColor", nullable(c.getNotificationIconColor()));
        j.put("locationProvider", c.getLocationProvider());
        j.put("interval", c.getInterval());
        j.put("fastestInterval", c.getFastestInterval());
        j.put("activitiesInterval", c.getActivitiesInterval());
        j.put("stopOnTerminate", c.getStopOnTerminate());
        j.put("startOnBoot", c.getStartOnBoot());
        j.put("startForeground", c.getStartForeground());
        j.put("notificationsEnabled", c.getNotificationsEnabled());
        j.put("stopOnStillActivity", c.getStopOnStillActivity());
        j.put("url", nullable(c.getUrl()));
        j.put("syncUrl", nullable(c.getSyncUrl()));
        j.put("syncThreshold", c.getSyncThreshold());
        j.put("syncEnabled", c.getSyncEnabled());
        j.put("maxLocations", c.getMaxLocations());
        j.put("enableWatchdog", c.getEnableWatchdog());
        j.put("showTime", c.getShowTime());
        j.put("showDistance", c.getShowDistance());
        j.put("httpMethod", c.getHttpMethod());
        j.put("syncHttpMethod", c.getSyncHttpMethod());
        j.put("httpMode", c.getHttpMode());
        j.put("syncMode", c.getSyncMode());
        j.put("heartbeatInterval", c.getHeartbeatInterval());
        j.put("mockLocationPolicy", c.getMockLocationPolicy());
        j.put("includeBattery", c.getIncludeBattery());
        // v4.5.1: battery knobs
        j.put("wakeLockMode", c.getWakeLockMode());
        j.put("stationaryTimeout", c.getStationaryTimeout());
        j.put("stationaryPollInterval", c.getStationaryPollInterval());
        j.put("stationaryPollFast", c.getStationaryPollFast());
        // v4.5.2: provider hardening knobs
        j.put("activityConfidenceThreshold", c.getActivityConfidenceThreshold());
        j.put("maxAcceptedAccuracy", c.getMaxAcceptedAccuracy());

        if (c.getHttpHeaders() != null) {
            j.put("httpHeaders", new JSONObject(c.getHttpHeaders()));
        }
        if (c.getQueryParams() != null) {
            j.put("queryParams", new JSONObject(c.getQueryParams()));
        }

        Config.DrivingEventsOptions de = c.getDrivingEvents();
        if (de != null) {
            JSONObject deJson = new JSONObject();
            deJson.put("enabled", de.enabled);
            deJson.put("speedLimit", de.speedLimitKmh);
            deJson.put("minMovingSpeed", de.minMovingSpeedMps);
            deJson.put("stoppedDuration", de.stoppedDurationMs);
            deJson.put("minTripSpeed", de.minTripSpeedMps);
            deJson.put("minTripDuration", de.minTripDurationMs);
            deJson.put("hardBrakeMps2", de.hardBrakeMps2);
            deJson.put("rapidAccelMps2", de.rapidAccelMps2);
            deJson.put("sharpTurnDegPerSec", de.sharpTurnDegPerSec);
            deJson.put("crashImpactKmh", de.crashImpactKmh);
            deJson.put("crashWindowMs", de.crashWindowMs);
            deJson.put("sensorFusion", de.sensorFusion);
            deJson.put("crashImpactG", de.crashImpactG);
            deJson.put("sensorCrashCooldownMs", de.sensorCrashCooldownMs);
            deJson.put("phoneUsageWindowMs", de.phoneUsageWindowMs);
            deJson.put("phoneUsageCooldownMs", de.phoneUsageCooldownMs);
            j.put("drivingEvents", deJson);
        }
        return j;
    }

    /** Deserialize a previously serialized config JSON. Missing keys are skipped (defaults preserved). */
    public static Config fromJSONObject(JSONObject j) throws JSONException {
        Config c = Config.getDefault();
        if (j == null) return c;
        if (j.has("stationaryRadius")) c.setStationaryRadius((float) j.getDouble("stationaryRadius"));
        if (j.has("distanceFilter")) c.setDistanceFilter(j.getInt("distanceFilter"));
        if (j.has("desiredAccuracy")) c.setDesiredAccuracy(j.getInt("desiredAccuracy"));
        if (j.has("debug")) c.setDebugging(j.getBoolean("debug"));
        if (j.has("notificationTitle")) c.setNotificationTitle(readNullable(j, "notificationTitle"));
        if (j.has("notificationText")) c.setNotificationText(readNullable(j, "notificationText"));
        if (j.has("notificationSyncTitle")) c.setNotificationSyncTitle(readNullable(j, "notificationSyncTitle"));
        if (j.has("notificationSyncText")) c.setNotificationSyncText(readNullable(j, "notificationSyncText"));
        if (j.has("notificationSyncCompletedText")) c.setNotificationSyncCompletedText(readNullable(j, "notificationSyncCompletedText"));
        if (j.has("notificationSyncFailedText")) c.setNotificationSyncFailedText(readNullable(j, "notificationSyncFailedText"));
        if (j.has("notificationIconLarge")) c.setLargeNotificationIcon(readNullable(j, "notificationIconLarge"));
        if (j.has("notificationIconSmall")) c.setSmallNotificationIcon(readNullable(j, "notificationIconSmall"));
        if (j.has("notificationIconColor")) c.setNotificationIconColor(readNullable(j, "notificationIconColor"));
        if (j.has("locationProvider")) c.setLocationProvider(j.getInt("locationProvider"));
        if (j.has("interval")) c.setInterval(j.getInt("interval"));
        if (j.has("fastestInterval")) c.setFastestInterval(j.getInt("fastestInterval"));
        if (j.has("activitiesInterval")) c.setActivitiesInterval(j.getInt("activitiesInterval"));
        if (j.has("stopOnTerminate")) c.setStopOnTerminate(j.getBoolean("stopOnTerminate"));
        if (j.has("startOnBoot")) c.setStartOnBoot(j.getBoolean("startOnBoot"));
        if (j.has("startForeground")) c.setStartForeground(j.getBoolean("startForeground"));
        if (j.has("notificationsEnabled")) c.setNotificationsEnabled(j.getBoolean("notificationsEnabled"));
        if (j.has("stopOnStillActivity")) c.setStopOnStillActivity(j.getBoolean("stopOnStillActivity"));
        if (j.has("url")) c.setUrl(readNullable(j, "url"));
        if (j.has("syncUrl")) c.setSyncUrl(readNullable(j, "syncUrl"));
        if (j.has("syncThreshold")) c.setSyncThreshold(j.getInt("syncThreshold"));
        if (j.has("syncEnabled")) c.setSyncEnabled(j.getBoolean("syncEnabled"));
        if (j.has("maxLocations")) c.setMaxLocations(j.getInt("maxLocations"));
        if (j.has("enableWatchdog")) c.setEnableWatchdog(j.getBoolean("enableWatchdog"));
        if (j.has("showTime")) c.setShowTime(j.getBoolean("showTime"));
        if (j.has("showDistance")) c.setShowDistance(j.getBoolean("showDistance"));
        if (has(j, "httpMethod")) c.setHttpMethod(j.getString("httpMethod"));
        if (has(j, "syncHttpMethod")) c.setSyncHttpMethod(j.getString("syncHttpMethod"));
        if (has(j, "httpMode")) c.setHttpMode(j.getString("httpMode"));
        if (has(j, "syncMode")) c.setSyncMode(j.getString("syncMode"));
        if (j.has("heartbeatInterval")) c.setHeartbeatInterval(j.getInt("heartbeatInterval"));
        if (has(j, "mockLocationPolicy")) c.setMockLocationPolicy(j.getString("mockLocationPolicy"));
        if (j.has("includeBattery")) c.setIncludeBattery(j.getBoolean("includeBattery"));
        // v4.5.1: battery knobs
        if (has(j, "wakeLockMode"))  c.setWakeLockMode(j.getString("wakeLockMode"));
        if (j.has("stationaryTimeout") && !j.isNull("stationaryTimeout")) c.setStationaryTimeout(j.getInt("stationaryTimeout"));
        if (j.has("stationaryPollInterval") && !j.isNull("stationaryPollInterval")) c.setStationaryPollInterval(j.getInt("stationaryPollInterval"));
        if (j.has("stationaryPollFast") && !j.isNull("stationaryPollFast")) c.setStationaryPollFast(j.getInt("stationaryPollFast"));
        // v4.5.2
        if (j.has("activityConfidenceThreshold") && !j.isNull("activityConfidenceThreshold")) c.setActivityConfidenceThreshold(j.getInt("activityConfidenceThreshold"));
        if (j.has("maxAcceptedAccuracy") && !j.isNull("maxAcceptedAccuracy")) c.setMaxAcceptedAccuracy((float) j.getDouble("maxAcceptedAccuracy"));

        if (has(j, "httpHeaders")) c.setHttpHeaders(jsonToHashMap(j.getJSONObject("httpHeaders")));
        if (has(j, "queryParams")) c.setQueryParams(jsonToHashMap(j.getJSONObject("queryParams")));

        if (has(j, "drivingEvents")) {
            JSONObject de = j.getJSONObject("drivingEvents");
            Config.DrivingEventsOptions o = new Config.DrivingEventsOptions();
            if (de.has("enabled"))               o.enabled               = de.getBoolean("enabled");
            if (de.has("speedLimit"))            o.speedLimitKmh         = de.getDouble("speedLimit");
            if (de.has("minMovingSpeed"))        o.minMovingSpeedMps     = de.getDouble("minMovingSpeed");
            if (de.has("stoppedDuration"))       o.stoppedDurationMs     = de.getLong("stoppedDuration");
            if (de.has("minTripSpeed"))          o.minTripSpeedMps       = de.getDouble("minTripSpeed");
            if (de.has("minTripDuration"))       o.minTripDurationMs     = de.getLong("minTripDuration");
            if (de.has("hardBrakeMps2"))         o.hardBrakeMps2         = de.getDouble("hardBrakeMps2");
            if (de.has("rapidAccelMps2"))        o.rapidAccelMps2        = de.getDouble("rapidAccelMps2");
            if (de.has("sharpTurnDegPerSec"))    o.sharpTurnDegPerSec    = de.getDouble("sharpTurnDegPerSec");
            if (de.has("crashImpactKmh"))        o.crashImpactKmh        = de.getDouble("crashImpactKmh");
            if (de.has("crashWindowMs"))         o.crashWindowMs         = de.getLong("crashWindowMs");
            if (de.has("sensorFusion"))          o.sensorFusion          = de.getBoolean("sensorFusion");
            if (de.has("crashImpactG"))          o.crashImpactG          = de.getDouble("crashImpactG");
            if (de.has("sensorCrashCooldownMs")) o.sensorCrashCooldownMs = de.getLong("sensorCrashCooldownMs");
            if (de.has("phoneUsageWindowMs"))    o.phoneUsageWindowMs    = de.getLong("phoneUsageWindowMs");
            if (de.has("phoneUsageCooldownMs")) o.phoneUsageCooldownMs   = de.getLong("phoneUsageCooldownMs");
            c.setDrivingEvents(o);
        }
        return c;
    }

    private static HashMap<String, String> jsonToHashMap(JSONObject obj) throws JSONException {
        HashMap<String, String> map = new HashMap<>();
        Iterator<String> keys = obj.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            map.put(k, String.valueOf(obj.get(k)));
        }
        return map;
    }

    private static boolean has(JSONObject j, String key) {
        return j.has(key) && !j.isNull(key);
    }

    /** Map {@link Config#NullString} or null to JSONObject.NULL so the sentinel survives. */
    private static Object nullable(String s) {
        if (s == null || s == Config.NullString) return JSONObject.NULL;
        return s;
    }

    /** Inverse of {@link #nullable(String)}. JSON null → {@link Config#NullString}. */
    private static String readNullable(JSONObject j, String key) throws JSONException {
        if (j.isNull(key)) return Config.NullString;
        return j.getString(key);
    }
}
