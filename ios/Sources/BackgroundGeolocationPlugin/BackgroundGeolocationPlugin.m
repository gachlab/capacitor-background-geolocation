// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 JosueLMM

#import <Foundation/Foundation.h>
#import <Capacitor/Capacitor.h>

CAP_PLUGIN(BackgroundGeolocationPlugin, "BackgroundGeolocation",
    CAP_PLUGIN_METHOD(configure, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(start, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(stop, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getCurrentLocation, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getStationaryLocation, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getLocations, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getValidLocations, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getValidLocationsAndDelete, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getConfig, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(deleteLocation, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(deleteAllLocations, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(isLocationEnabled, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(showAppSettings, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(showLocationSettings, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(openSettings, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(watchLocationMode, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(stopWatchingLocationMode, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getLogEntries, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(checkStatus, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getDiagnostics, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getPluginVersion, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(switchMode, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(startTask, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(endTask, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(forceSync, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(clearSync, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getPendingSyncCount, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(startSession, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(clearSession, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getSessionLocations, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getSessionLocationsCount, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(triggerSOS, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(isIgnoringBatteryOptimizations, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(requestIgnoreBatteryOptimizations, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(openBatterySettings, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(openAutoStartSettings, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(getManufacturerHelp, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(requestBackgroundLocationPermission, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(requestActivityRecognitionPermission, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(requestNotificationPermission, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(checkPermissions, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(requestPermissions, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(registerHeadlessTask, CAPPluginReturnPromise);
    CAP_PLUGIN_METHOD(removeAllListeners, CAPPluginReturnPromise);
)
