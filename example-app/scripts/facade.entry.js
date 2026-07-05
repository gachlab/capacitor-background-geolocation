// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 gachlab
//
// Example-app facade entry. esbuild bundles this with @capacitor/core into a
// self-contained IIFE (www/bg-plugin.js) that exposes the REAL v3 facade as
// window.capacitorBackgroundGeolocation — the same object npm consumers import.
//
// Why bundle instead of copying the plugin's dist IIFE: that IIFE externalizes
// @capacitor/core to a `capacitorExports` global that does NOT exist in the Capacitor
// WebView runtime (only window.Capacitor does, and it lacks `registerPlugin`). Bundling
// resolves @capacitor/core so registerPlugin routes to the native bridge on-device.

import * as mod from '@gachlab/capacitor-background-geolocation';

window.capacitorBackgroundGeolocation = mod;
