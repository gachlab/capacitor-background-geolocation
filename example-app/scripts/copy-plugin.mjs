// SPDX-License-Identifier: Apache-2.0
// Copyright (c) 2026 gachlab
//
// Copies the plugin's built IIFE bundle into www/ so the no-bundler example can load
// the real v3 facade via a <script> tag. Run before `cap sync`. The plugin must be
// built first (npm run build in the plugin root → dist/plugin.js).

import { copyFileSync, existsSync } from 'node:fs';

const src = 'node_modules/@gachlab/capacitor-background-geolocation/dist/plugin.js';
const dest = 'www/bg-plugin.js';

if (!existsSync(src)) {
  console.error(`ERROR: ${src} not found. Build the plugin first: (cd .. && npm run build)`);
  process.exit(1);
}

copyFileSync(src, dest);
console.log(`copied ${src} -> ${dest}`);
