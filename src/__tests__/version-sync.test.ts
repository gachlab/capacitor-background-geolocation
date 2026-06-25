// Guard test: the native version constants drift from package.json over time
// (they were stuck at "1.5.0" while the package shipped 2.0.0). This locks all
// three dies — web, Android, iOS — to package.json `version` so any future bump
// that forgets a surface fails CI here instead of shipping a wrong version.
// Run: npm test

import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { describe, it } from 'node:test';

import { BackgroundGeolocationWeb } from '../web.js';

const repoRoot = fileURLToPath(new URL('../../', import.meta.url));
const read = (rel: string): string => readFileSync(repoRoot + rel, 'utf8');

const pkgVersion = (JSON.parse(read('package.json')) as { version: string }).version;

describe('plugin version is in sync across all dies', () => {
  it('package.json has a semver version', () => {
    assert.match(pkgVersion, /^\d+\.\d+\.\d+/);
  });

  it('web getPluginVersion() matches package.json', async () => {
    const { version } = await new BackgroundGeolocationWeb().getPluginVersion();
    assert.equal(version, pkgVersion);
  });

  it('Android PLUGIN_VERSION matches package.json', () => {
    const kt = read('android/src/main/java/com/gachlab/capacitor/backgroundgeolocation/BackgroundGeolocationPlugin.kt');
    const match = kt.match(/PLUGIN_VERSION\s*=\s*"([^"]+)"/);
    assert.ok(match, 'PLUGIN_VERSION constant not found in Android plugin');
    assert.equal(match[1], pkgVersion);
  });

  it('iOS pluginVersion matches package.json', () => {
    const swift = read('ios/Sources/BackgroundGeolocationPlugin/BackgroundGeolocationPlugin.swift');
    const match = swift.match(/pluginVersion\s*=\s*"([^"]+)"/);
    assert.ok(match, 'pluginVersion constant not found in iOS plugin');
    assert.equal(match[1], pkgVersion);
  });
});
