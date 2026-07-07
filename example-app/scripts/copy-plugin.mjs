// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 gachlab
//
// Bundles the plugin's v3 facade + @capacitor/core into www/bg-plugin.js so the
// no-bundler example WebView can load the REAL facade via a <script> tag. Run before
// `cap sync`. The plugin must be built first (npm run build in the plugin root → dist/).
//
// esbuild (not a plain copy of dist/plugin.js): the plugin's published IIFE externalizes
// @capacitor/core to a `capacitorExports` global that does not exist in the Capacitor
// WebView runtime — loading it raw throws `capacitorExports is not defined`. Bundling
// resolves @capacitor/core so registerPlugin routes to the native bridge on-device.

import { existsSync } from 'node:fs';

import { build } from 'esbuild';

const facadeDist = 'node_modules/@gachlab/capacitor-background-geolocation/dist/esm/index.js';
if (!existsSync(facadeDist)) {
  console.error(`ERROR: ${facadeDist} not found. Build the plugin first: (cd .. && npm run build)`);
  process.exit(1);
}

await build({
  entryPoints: ['scripts/facade.entry.js'],
  bundle: true,
  format: 'iife',
  platform: 'browser',
  // The API-30 e2e emulator ships Android System WebView 83 (Chromium 83). Logical
  // assignment (`??=`/`||=`/`&&=`, ES2021) landed in Chromium 85, so an es2022 bundle
  // throws "Unexpected token '='" at load and the plugin global never registers. Target
  // chrome83 so esbuild transpiles those down while keeping `?.`/`??` (Chromium 80).
  target: 'chrome83',
  outfile: 'www/bg-plugin.js',
  sourcemap: true,
  logLevel: 'info',
});

console.log('bundled v3 facade -> www/bg-plugin.js');
