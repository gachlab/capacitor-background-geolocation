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
  target: 'es2022',
  outfile: 'www/bg-plugin.js',
  sourcemap: true,
  logLevel: 'info',
});

console.log('bundled v3 facade -> www/bg-plugin.js');
