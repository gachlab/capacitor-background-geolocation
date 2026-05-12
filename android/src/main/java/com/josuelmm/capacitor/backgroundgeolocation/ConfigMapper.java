// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM
//
// Adapted from `com.marianhello.bgloc.cordova.ConfigMapper` in the upstream
// Cordova plugin. Capacitor's JSObject extends org.json.JSONObject, so the
// mapping logic carries over verbatim — only the package and a Capacitor-flavored
// entry point that accepts `JSObject` are new.

package com.josuelmm.capacitor.backgroundgeolocation;

import com.getcapacitor.JSObject;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.ArrayListLocationTemplate;
import com.marianhello.bgloc.data.HashMapLocationTemplate;
import com.marianhello.bgloc.data.LocationTemplate;
import com.marianhello.bgloc.data.LocationTemplateFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Map;

public final class ConfigMapper {

    private ConfigMapper() {}

    public static Config fromJSObject(JSObject obj) throws JSONException {
        return fromJSONObject(obj);
    }

    public static Config fromJSONObject(JSONObject jObject) throws JSONException {
        Config config = new Config();

        if (jObject.has("stationaryRadius")) {
            config.setStationaryRadius(jObject.getDouble("stationaryRadius"));
        }
        if (jObject.has("distanceFilter")) {
            config.setDistanceFilter(jObject.getInt("distanceFilter"));
        }
        if (jObject.has("desiredAccuracy")) {
            config.setDesiredAccuracy(jObject.getInt("desiredAccuracy"));
        }
        if (jObject.has("debug")) {
            config.setDebugging(jObject.getBoolean("debug"));
        }
        if (jObject.has("notificationsEnabled")) {
            config.setNotificationsEnabled(jObject.getBoolean("notificationsEnabled"));
        }
        if (jObject.has("notificationTitle")) {
            config.setNotificationTitle(!jObject.isNull("notificationTitle") ? jObject.getString("notificationTitle") : Config.NullString);
        }
        if (jObject.has("notificationText")) {
            config.setNotificationText(!jObject.isNull("notificationText") ? jObject.getString("notificationText") : Config.NullString);
        }
        if (jObject.has("notificationSyncTitle")) {
            config.setNotificationSyncTitle(jObject.isNull("notificationSyncTitle") ? null : jObject.getString("notificationSyncTitle"));
        }
        if (jObject.has("notificationSyncText")) {
            config.setNotificationSyncText(jObject.isNull("notificationSyncText") ? null : jObject.getString("notificationSyncText"));
        }
        if (jObject.has("notificationSyncCompletedText")) {
            config.setNotificationSyncCompletedText(jObject.isNull("notificationSyncCompletedText") ? null : jObject.getString("notificationSyncCompletedText"));
        }
        if (jObject.has("notificationSyncFailedText")) {
            config.setNotificationSyncFailedText(jObject.isNull("notificationSyncFailedText") ? null : jObject.getString("notificationSyncFailedText"));
        }
        if (jObject.has("stopOnTerminate")) {
            config.setStopOnTerminate(jObject.getBoolean("stopOnTerminate"));
        }
        if (jObject.has("startOnBoot")) {
            config.setStartOnBoot(jObject.getBoolean("startOnBoot"));
        }
        if (jObject.has("locationProvider")) {
            config.setLocationProvider(jObject.getInt("locationProvider"));
        }
        if (jObject.has("interval")) {
            config.setInterval(jObject.getInt("interval"));
        }
        if (jObject.has("fastestInterval")) {
            config.setFastestInterval(jObject.getInt("fastestInterval"));
        }
        if (jObject.has("activitiesInterval")) {
            config.setActivitiesInterval(jObject.getInt("activitiesInterval"));
        }
        if (jObject.has("notificationIconColor")) {
            config.setNotificationIconColor(!jObject.isNull("notificationIconColor") ? jObject.getString("notificationIconColor") : Config.NullString);
        }
        if (jObject.has("notificationIconLarge")) {
            config.setLargeNotificationIcon(!jObject.isNull("notificationIconLarge") ? jObject.getString("notificationIconLarge") : Config.NullString);
        }
        if (jObject.has("notificationIconSmall")) {
            config.setSmallNotificationIcon(!jObject.isNull("notificationIconSmall") ? jObject.getString("notificationIconSmall") : Config.NullString);
        }
        if (jObject.has("startForeground")) {
            config.setStartForeground(jObject.getBoolean("startForeground"));
        }
        if (jObject.has("stopOnStillActivity")) {
            config.setStopOnStillActivity(jObject.getBoolean("stopOnStillActivity"));
        }
        if (jObject.has("url")) {
            config.setUrl(!jObject.isNull("url") ? jObject.getString("url") : Config.NullString);
        }
        if (jObject.has("syncUrl")) {
            config.setSyncUrl(!jObject.isNull("syncUrl") ? jObject.getString("syncUrl") : Config.NullString);
        }
        if (jObject.has("syncThreshold")) {
            config.setSyncThreshold(jObject.getInt("syncThreshold"));
        }
        if (jObject.has("sync")) {
            config.setSyncEnabled(jObject.getBoolean("sync"));
        }
        if (jObject.has("httpHeaders")) {
            config.setHttpHeaders(jObject.getJSONObject("httpHeaders"));
        }
        if (jObject.has("headers")) {
            config.setHttpHeaders(jObject.getJSONObject("headers"));
        }
        if (jObject.has("maxLocations")) {
            config.setMaxLocations(jObject.getInt("maxLocations"));
        }
        if (jObject.has("postTemplate")) {
            if (jObject.isNull("postTemplate")) {
                config.setTemplate(LocationTemplateFactory.getDefault());
            } else {
                Object postTemplate = jObject.get("postTemplate");
                config.setTemplate(LocationTemplateFactory.fromJSON(postTemplate));
            }
        }
        if (jObject.has("bodyTemplate")) {
            if (jObject.isNull("bodyTemplate")) {
                config.setTemplate(LocationTemplateFactory.getDefault());
            } else {
                Object bodyTemplate = jObject.get("bodyTemplate");
                config.setTemplate(LocationTemplateFactory.fromJSON(bodyTemplate));
            }
        }
        if (jObject.has("httpMethod") && !jObject.isNull("httpMethod")) {
            config.setHttpMethod(jObject.getString("httpMethod"));
        }
        if (jObject.has("syncHttpMethod") && !jObject.isNull("syncHttpMethod")) {
            config.setSyncHttpMethod(jObject.getString("syncHttpMethod"));
        }
        if (jObject.has("httpMode") && !jObject.isNull("httpMode")) {
            config.setHttpMode(jObject.getString("httpMode"));
        }
        if (jObject.has("syncMode") && !jObject.isNull("syncMode")) {
            config.setSyncMode(jObject.getString("syncMode"));
        }
        if (jObject.has("queryParams") && !jObject.isNull("queryParams")) {
            config.setQueryParams(jObject.getJSONObject("queryParams"));
        }
        if (jObject.has("heartbeatInterval") && !jObject.isNull("heartbeatInterval")) {
            config.setHeartbeatInterval(jObject.getInt("heartbeatInterval"));
        }
        if (jObject.has("mockLocationPolicy") && !jObject.isNull("mockLocationPolicy")) {
            config.setMockLocationPolicy(jObject.getString("mockLocationPolicy"));
        }
        if (jObject.has("drivingEvents") && !jObject.isNull("drivingEvents")) {
            JSONObject de = jObject.getJSONObject("drivingEvents");
            Config.DrivingEventsOptions opts = new Config.DrivingEventsOptions();
            if (de.has("enabled"))            opts.enabled            = de.getBoolean("enabled");
            if (de.has("speedLimit"))         opts.speedLimitKmh      = de.getDouble("speedLimit");
            if (de.has("minMovingSpeed"))     opts.minMovingSpeedMps  = de.getDouble("minMovingSpeed");
            if (de.has("stoppedDuration"))    opts.stoppedDurationMs  = de.getLong("stoppedDuration");
            if (de.has("minTripSpeed"))       opts.minTripSpeedMps    = de.getDouble("minTripSpeed");
            if (de.has("minTripDuration"))    opts.minTripDurationMs  = de.getLong("minTripDuration");
            if (de.has("hardBrakeMps2"))      opts.hardBrakeMps2      = de.getDouble("hardBrakeMps2");
            if (de.has("rapidAccelMps2"))     opts.rapidAccelMps2     = de.getDouble("rapidAccelMps2");
            if (de.has("sharpTurnDegPerSec")) opts.sharpTurnDegPerSec = de.getDouble("sharpTurnDegPerSec");
            if (de.has("crashImpactKmh"))     opts.crashImpactKmh     = de.getDouble("crashImpactKmh");
            if (de.has("crashWindowMs"))      opts.crashWindowMs      = de.getLong("crashWindowMs");
            if (de.has("sensorFusion"))          opts.sensorFusion          = de.getBoolean("sensorFusion");
            if (de.has("crashImpactG"))          opts.crashImpactG          = de.getDouble("crashImpactG");
            if (de.has("sensorCrashCooldownMs")) opts.sensorCrashCooldownMs = de.getLong("sensorCrashCooldownMs");
            if (de.has("phoneUsageWindowMs"))    opts.phoneUsageWindowMs    = de.getLong("phoneUsageWindowMs");
            if (de.has("phoneUsageCooldownMs"))  opts.phoneUsageCooldownMs  = de.getLong("phoneUsageCooldownMs");
            config.setDrivingEvents(opts);
        }
        if (jObject.has("enableWatchdog")) {
            config.setEnableWatchdog(jObject.getBoolean("enableWatchdog"));
        }
        if (jObject.has("showTime")) {
            config.setShowTime(jObject.getBoolean("showTime"));
        }
        if (jObject.has("showDistance")) {
            config.setShowDistance(jObject.getBoolean("showDistance"));
        }
        if (jObject.has("includeBattery")) {
            config.setIncludeBattery(jObject.getBoolean("includeBattery"));
        }
        if (jObject.has("wakeLockMode") && !jObject.isNull("wakeLockMode")) {
            config.setWakeLockMode(jObject.getString("wakeLockMode"));
        }
        if (jObject.has("stationaryTimeout") && !jObject.isNull("stationaryTimeout")) {
            config.setStationaryTimeout(jObject.getInt("stationaryTimeout"));
        }
        if (jObject.has("stationaryPollInterval") && !jObject.isNull("stationaryPollInterval")) {
            config.setStationaryPollInterval(jObject.getInt("stationaryPollInterval"));
        }
        if (jObject.has("stationaryPollFast") && !jObject.isNull("stationaryPollFast")) {
            config.setStationaryPollFast(jObject.getInt("stationaryPollFast"));
        }
        if (jObject.has("activityConfidenceThreshold") && !jObject.isNull("activityConfidenceThreshold")) {
            config.setActivityConfidenceThreshold(jObject.getInt("activityConfidenceThreshold"));
        }
        if (jObject.has("maxAcceptedAccuracy") && !jObject.isNull("maxAcceptedAccuracy")) {
            config.setMaxAcceptedAccuracy((float) jObject.getDouble("maxAcceptedAccuracy"));
        }

        return config;
    }

    public static JSObject toJSObject(Config config) throws JSONException {
        JSObject json = new JSObject();
        json.put("stationaryRadius", config.getStationaryRadius());
        json.put("distanceFilter", config.getDistanceFilter());
        json.put("desiredAccuracy", config.getDesiredAccuracy());
        json.put("debug", config.isDebugging());
        json.put("notificationsEnabled", config.getNotificationsEnabled());
        json.put("notificationTitle", config.getNotificationTitle() != Config.NullString ? config.getNotificationTitle() : JSONObject.NULL);
        json.put("notificationText", config.getNotificationText() != Config.NullString ? config.getNotificationText() : JSONObject.NULL);
        json.put("notificationSyncTitle", config.getNotificationSyncTitle());
        json.put("notificationSyncText", config.getNotificationSyncText());
        json.put("notificationSyncCompletedText", config.getNotificationSyncCompletedText());
        json.put("notificationSyncFailedText", config.getNotificationSyncFailedText());
        json.put("notificationIconLarge", config.getLargeNotificationIcon() != Config.NullString ? config.getLargeNotificationIcon() : JSONObject.NULL);
        json.put("notificationIconSmall", config.getSmallNotificationIcon() != Config.NullString ? config.getSmallNotificationIcon() : JSONObject.NULL);
        json.put("notificationIconColor", config.getNotificationIconColor() != Config.NullString ? config.getNotificationIconColor() : JSONObject.NULL);
        json.put("stopOnTerminate", config.getStopOnTerminate());
        json.put("startOnBoot", config.getStartOnBoot());
        json.put("startForeground", config.getStartForeground());
        json.put("locationProvider", config.getLocationProvider());
        json.put("interval", config.getInterval());
        json.put("fastestInterval", config.getFastestInterval());
        json.put("activitiesInterval", config.getActivitiesInterval());
        json.put("stopOnStillActivity", config.getStopOnStillActivity());
        json.put("url", config.getUrl() != Config.NullString ? config.getUrl() : JSONObject.NULL);
        json.put("syncUrl", config.getSyncUrl() != Config.NullString ? config.getSyncUrl() : JSONObject.NULL);
        json.put("syncThreshold", config.getSyncThreshold());
        json.put("sync", config.getSyncEnabled());
        json.put("httpHeaders", new JSONObject(config.getHttpHeaders()));
        json.put("maxLocations", config.getMaxLocations());
        json.put("enableWatchdog", Boolean.TRUE.equals(config.getEnableWatchdog()));
        json.put("showTime", Boolean.TRUE.equals(config.getShowTime()));
        json.put("showDistance", Boolean.TRUE.equals(config.getShowDistance()));

        LocationTemplate tpl = config.getTemplate();
        Object template = JSONObject.NULL;
        if (tpl instanceof HashMapLocationTemplate) {
            Map map = ((HashMapLocationTemplate) tpl).toMap();
            if (map != null) {
                template = new JSONObject(map);
            }
        } else if (tpl instanceof ArrayListLocationTemplate) {
            Object[] keys = ((ArrayListLocationTemplate) tpl).toArray();
            if (keys != null) {
                template = new JSONArray(Arrays.asList(keys));
            }
        }
        json.put("postTemplate", template);

        json.put("httpMethod", config.getHttpMethod());
        json.put("syncHttpMethod", config.getSyncHttpMethod());
        json.put("httpMode", config.getHttpMode());
        json.put("syncMode", config.getSyncMode());
        json.put("queryParams", config.getQueryParams() != null ? new JSONObject(config.getQueryParams()) : JSONObject.NULL);
        json.put("heartbeatInterval", config.getHeartbeatInterval());
        json.put("mockLocationPolicy", config.getMockLocationPolicy());

        Config.DrivingEventsOptions de = config.getDrivingEvents();
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
            json.put("drivingEvents", deJson);
        }
        json.put("includeBattery", config.getIncludeBattery() != null ? config.getIncludeBattery() : true);
        json.put("wakeLockMode", config.getWakeLockMode() != null ? config.getWakeLockMode() : "posting");
        json.put("stationaryTimeout", config.getStationaryTimeout());
        json.put("stationaryPollInterval", config.getStationaryPollInterval());
        json.put("stationaryPollFast", config.getStationaryPollFast());
        json.put("activityConfidenceThreshold", config.getActivityConfidenceThreshold());
        json.put("maxAcceptedAccuracy", config.getMaxAcceptedAccuracy());

        return json;
    }
}
