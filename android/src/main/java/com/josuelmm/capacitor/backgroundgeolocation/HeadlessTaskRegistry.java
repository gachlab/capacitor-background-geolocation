// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// Holds the JS function body registered via `BackgroundGeolocation.headlessTask(fn)`.
// The native `JsEvaluatorTaskRunner` reads this string when the Android service
// fires a location/stationary/activity event while the host activity is killed.
// Direct port of `com.marianhello.bgloc.cordova.PluginRegistry` from the
// upstream Cordova plugin (Apache-2.0), renamed for clarity.

package com.josuelmm.capacitor.backgroundgeolocation;

public final class HeadlessTaskRegistry {

    private String headlessTaskJs;

    private static HeadlessTaskRegistry instance;

    private HeadlessTaskRegistry() {}

    public synchronized String getHeadlessTask() {
        return headlessTaskJs;
    }

    public synchronized void registerHeadlessTask(String headlessTaskJs) {
        this.headlessTaskJs = headlessTaskJs;
    }

    public static synchronized HeadlessTaskRegistry getInstance() {
        if (instance == null) {
            instance = new HeadlessTaskRegistry();
        }
        return instance;
    }
}
