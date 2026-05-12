// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM
//
// Capacitor 8+ bridge for the upstream `com.marianhello.bgloc` native core.
// The Cordova entry point (com.tenforwardconsulting.bgloc.cordova.BackgroundGeolocationPlugin)
// is the reference for behavior; this file mirrors its method set with Capacitor idioms.

package com.josuelmm.capacitor.backgroundgeolocation;

import android.Manifest;
import android.content.Context;

import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

import com.marianhello.bgloc.BackgroundGeolocationFacade;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.PluginDelegate;
import com.marianhello.bgloc.PluginException;
import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.logging.LogEntry;
import com.marianhello.logging.LoggerManager;

import org.json.JSONArray;
import org.json.JSONException;

import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;

@CapacitorPlugin(
    name = "BackgroundGeolocation",
    permissions = {
        @Permission(
            alias = "location",
            strings = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }
        ),
        @Permission(
            alias = "activity",
            strings = { "android.permission.ACTIVITY_RECOGNITION" }
        ),
        @Permission(
            alias = "notifications",
            strings = { "android.permission.POST_NOTIFICATIONS" }
        )
    }
)
public class BackgroundGeolocationPlugin extends Plugin implements PluginDelegate {

    private static final String LOCATION_EVENT           = "location";
    private static final String STATIONARY_EVENT         = "stationary";
    private static final String ACTIVITY_EVENT           = "activity";
    private static final String FOREGROUND_EVENT         = "foreground";
    private static final String BACKGROUND_EVENT         = "background";
    private static final String AUTHORIZATION_EVENT      = "authorization";
    private static final String START_EVENT              = "start";
    private static final String STOP_EVENT               = "stop";
    private static final String ABORT_REQUESTED_EVENT    = "abort_requested";
    private static final String HTTP_AUTHORIZATION_EVENT = "http_authorization";
    private static final String ERROR_EVENT              = "error";

    public static final String PLUGIN_VERSION = "0.1.0";

    private BackgroundGeolocationFacade facade;
    private org.slf4j.Logger logger;
    private final AtomicInteger taskCounter = new AtomicInteger(0);

    @Override
    public void load() {
        super.load();
        logger = LoggerManager.getLogger(BackgroundGeolocationPlugin.class);
        facade = new BackgroundGeolocationFacade(getAppContext(), this);
        facade.resume();
    }

    @Override
    protected void handleOnPause() {
        if (facade != null) facade.pause();
        notifyListeners(BACKGROUND_EVENT, new JSObject());
        super.handleOnPause();
    }

    @Override
    protected void handleOnResume() {
        if (facade != null) facade.resume();
        notifyListeners(FOREGROUND_EVENT, new JSObject());
        super.handleOnResume();
    }

    @Override
    protected void handleOnDestroy() {
        if (facade != null) facade.destroy();
        super.handleOnDestroy();
    }

    private Context getAppContext() {
        return getBridge().getActivity().getApplicationContext();
    }

    private void rejectWith(PluginCall call, String message, Throwable cause, int code) {
        JSObject details = new JSObject();
        details.put("code", code);
        if (cause != null && cause.getMessage() != null) {
            details.put("cause", cause.getMessage());
        }
        call.reject(message, String.valueOf(code), cause, details);
    }

    private void rejectWith(PluginCall call, PluginException e) {
        JSObject details = new JSObject();
        details.put("code", e.getCode());
        call.reject(e.getMessage() != null ? e.getMessage() : "Plugin error",
                    String.valueOf(e.getCode()), e, details);
    }

    @PluginMethod
    public void configure(PluginCall call) {
        try {
            JSObject data = call.getData();
            Config config = ConfigMapper.fromJSONObject(data);
            facade.configure(config);
            call.resolve();
        } catch (JSONException e) {
            rejectWith(call, "Configuration error", e, PluginException.JSON_ERROR);
        } catch (PluginException e) {
            rejectWith(call, e);
        }
    }

    @PluginMethod
    public void start(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            requestPermissionForAlias("location", call, "startAfterPermission");
            return;
        }
        facade.start();
        call.resolve();
    }

    @PermissionCallback
    private void startAfterPermission(PluginCall call) {
        if (getPermissionState("location") != PermissionState.GRANTED) {
            rejectWith(call, "Location permission denied", null, PluginException.PERMISSION_DENIED_ERROR);
            return;
        }
        facade.start();
        call.resolve();
    }

    @PluginMethod
    public void stop(PluginCall call) {
        facade.stop();
        call.resolve();
    }

    @PluginMethod
    public void getCurrentLocation(PluginCall call) {
        int timeout = call.getInt("timeout", Integer.MAX_VALUE);
        long maximumAge = call.getLong("maximumAge", Long.MAX_VALUE);
        boolean enableHighAccuracy = Boolean.TRUE.equals(call.getBoolean("enableHighAccuracy", false));
        try {
            BackgroundLocation location = facade.getCurrentLocation(timeout, maximumAge, enableHighAccuracy);
            call.resolve(JSObject.fromJSONObject(location.toJSONObject()));
        } catch (JSONException e) {
            rejectWith(call, "Error serializing location", e, PluginException.JSON_ERROR);
        } catch (PluginException e) {
            rejectWith(call, e);
        }
    }

    @PluginMethod
    public void getStationaryLocation(PluginCall call) {
        try {
            BackgroundLocation stationary = facade.getStationaryLocation();
            if (stationary != null) {
                call.resolve(JSObject.fromJSONObject(stationary.toJSONObject()));
            } else {
                call.resolve();
            }
        } catch (JSONException e) {
            rejectWith(call, "Getting stationary location failed", e, PluginException.JSON_ERROR);
        }
    }

    @PluginMethod
    public void getValidLocations(PluginCall call) {
        try {
            JSObject ret = new JSObject();
            ret.put("locations", locationsToJsonArray(facade.getValidLocations()));
            call.resolve(ret);
        } catch (JSONException e) {
            rejectWith(call, "Converting locations to JSON failed", e, PluginException.JSON_ERROR);
        }
    }

    @PluginMethod
    public void getLocations(PluginCall call) {
        try {
            JSObject ret = new JSObject();
            ret.put("locations", locationsToJsonArray(facade.getLocations()));
            call.resolve(ret);
        } catch (JSONException e) {
            rejectWith(call, "Converting locations to JSON failed", e, PluginException.JSON_ERROR);
        }
    }

    @PluginMethod
    public void getConfig(PluginCall call) {
        try {
            Config config = facade.getConfig();
            call.resolve(ConfigMapper.toJSObject(config));
        } catch (JSONException e) {
            rejectWith(call, "Error getting config", e, PluginException.JSON_ERROR);
        }
    }

    @PluginMethod
    public void deleteLocation(PluginCall call) {
        Long locationId = call.getLong("locationId");
        if (locationId == null) {
            rejectWith(call, "locationId is required", null, PluginException.JSON_ERROR);
            return;
        }
        facade.deleteLocation(locationId);
        call.resolve();
    }

    @PluginMethod
    public void deleteAllLocations(PluginCall call) {
        facade.deleteAllLocations();
        call.resolve();
    }

    @PluginMethod
    public void isLocationEnabled(PluginCall call) {
        try {
            JSObject ret = new JSObject();
            ret.put("enabled", facade.locationServicesEnabled());
            call.resolve(ret);
        } catch (PluginException e) {
            rejectWith(call, e);
        }
    }

    @PluginMethod
    public void showAppSettings(PluginCall call) {
        BackgroundGeolocationFacade.showAppSettings(getAppContext());
        call.resolve();
    }

    @PluginMethod
    public void showLocationSettings(PluginCall call) {
        BackgroundGeolocationFacade.showLocationSettings(getAppContext());
        call.resolve();
    }

    @PluginMethod
    public void watchLocationMode(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void stopWatchingLocationMode(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void getLogEntries(PluginCall call) {
        try {
            int limit = call.getInt("limit", 100);
            int offset = call.getInt("fromId", 0);
            String minLevel = call.getString("minLevel", "DEBUG");
            JSObject ret = new JSObject();
            ret.put("entries", logsToJsonArray(limit, offset, minLevel));
            call.resolve(ret);
        } catch (Exception e) {
            rejectWith(call, "Getting logs failed", e, PluginException.SERVICE_ERROR);
        }
    }

    @PluginMethod
    public void checkStatus(PluginCall call) {
        try {
            JSObject ret = new JSObject();
            ret.put("isRunning", facade.isRunning());
            ret.put("locationServicesEnabled", facade.locationServicesEnabled());
            ret.put("authorization", facade.getAuthorizationStatus());
            ret.put("hasPermissions", facade.hasPermissions());
            call.resolve(ret);
        } catch (PluginException e) {
            rejectWith(call, e);
        }
    }

    @PluginMethod
    public void startTask(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("taskKey", taskCounter.incrementAndGet());
        call.resolve(ret);
    }

    @PluginMethod
    public void endTask(PluginCall call) {
        call.resolve();
    }

    @PluginMethod
    public void forceSync(PluginCall call) {
        facade.forceSync();
        call.resolve();
    }

    @PluginMethod
    public void clearSync(PluginCall call) {
        facade.clearSync();
        call.resolve();
    }

    @PluginMethod
    public void getPendingSyncCount(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("count", facade.getPendingSyncCount());
        call.resolve(ret);
    }

    @PluginMethod
    public void switchMode(PluginCall call) {
        Integer mode = call.getInt("mode");
        if (mode == null) {
            rejectWith(call, "mode is required", null, PluginException.JSON_ERROR);
            return;
        }
        facade.switchMode(mode);
        call.resolve();
    }

    @PluginMethod
    public void startSession(PluginCall call) {
        facade.startSession();
        call.resolve();
    }

    @PluginMethod
    public void clearSession(PluginCall call) {
        facade.clearSession();
        call.resolve();
    }

    @PluginMethod
    public void getSessionLocations(PluginCall call) {
        try {
            JSObject ret = new JSObject();
            ret.put("locations", locationsToJsonArray(facade.getSessionLocations()));
            call.resolve(ret);
        } catch (JSONException e) {
            rejectWith(call, "getSessionLocations failed", e, PluginException.JSON_ERROR);
        }
    }

    @PluginMethod
    public void getSessionLocationsCount(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("count", facade.getSessionLocationsCount());
        call.resolve(ret);
    }

    @PluginMethod
    public void getPluginVersion(PluginCall call) {
        JSObject ret = new JSObject();
        ret.put("version", PLUGIN_VERSION);
        call.resolve(ret);
    }

    @Override
    @PluginMethod
    public void removeAllListeners(PluginCall call) {
        super.removeAllListeners(call);
    }

    @Override
    public void onAuthorizationChanged(int authStatus) {
        JSObject payload = new JSObject();
        payload.put("status", authStatus);
        notifyListeners(AUTHORIZATION_EVENT, payload);
    }

    @Override
    public void onLocationChanged(BackgroundLocation location) {
        try {
            notifyListeners(LOCATION_EVENT, JSObject.fromJSONObject(location.toJSONObjectWithId()));
        } catch (JSONException e) {
            emitError(new PluginException(e.getMessage(), PluginException.JSON_ERROR));
        }
    }

    @Override
    public void onStationaryChanged(BackgroundLocation location) {
        try {
            notifyListeners(STATIONARY_EVENT, JSObject.fromJSONObject(location.toJSONObjectWithId()));
        } catch (JSONException e) {
            emitError(new PluginException(e.getMessage(), PluginException.JSON_ERROR));
        }
    }

    @Override
    public void onActivityChanged(BackgroundActivity activity) {
        try {
            notifyListeners(ACTIVITY_EVENT, JSObject.fromJSONObject(activity.toJSONObject()));
        } catch (JSONException e) {
            emitError(new PluginException(e.getMessage(), PluginException.JSON_ERROR));
        }
    }

    @Override
    public void onServiceStatusChanged(int status) {
        switch (status) {
            case BackgroundGeolocationFacade.SERVICE_STARTED:
                notifyListeners(START_EVENT, new JSObject());
                return;
            case BackgroundGeolocationFacade.SERVICE_STOPPED:
                notifyListeners(STOP_EVENT, new JSObject());
                return;
        }
    }

    @Override
    public void onAbortRequested() {
        notifyListeners(ABORT_REQUESTED_EVENT, new JSObject());
    }

    @Override
    public void onHttpAuthorization() {
        notifyListeners(HTTP_AUTHORIZATION_EVENT, new JSObject());
    }

    @Override
    public void onError(PluginException e) {
        emitError(e);
    }

    private void emitError(PluginException e) {
        JSObject payload = new JSObject();
        payload.put("code", e.getCode());
        payload.put("message", e.getMessage());
        notifyListeners(ERROR_EVENT, payload);
    }

    private static JSONArray locationsToJsonArray(Collection<BackgroundLocation> locations) throws JSONException {
        JSONArray arr = new JSONArray();
        for (BackgroundLocation location : locations) {
            arr.put(location.toJSONObjectWithId());
        }
        return arr;
    }

    private JSONArray logsToJsonArray(int limit, int offset, String minLevel) throws Exception {
        JSONArray arr = new JSONArray();
        Collection<LogEntry> entries = facade.getLogEntries(limit, offset, minLevel);
        for (LogEntry entry : entries) {
            arr.put(entry.toJSONObject());
        }
        return arr;
    }
}
