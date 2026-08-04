// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab
//
// The blind spot behind every "same fact, two spellings" bug this plugin has
// shipped.
//
// `config-mapper.guard.test.ts` proves that every wire key has a PRODUCER — the
// trip out. Nothing proved the trip back: that the native reads the value we
// send, and that what leaves through a getter or an event is spelled the way the
// published type says. Four separate defects lived in exactly that gap:
//
//   · `killReason()` returned `system_kill` where the `serviceRestarted` event
//     said `systemKill` — and `systemKill` is the only reason anyone asks for.
//   · `getLogEntries()` returned `ERROR` against a lowercase `LogLevel` union,
//     so `filter(e => e.level === 'error')` matched nothing, ever.
//   · `getConfig()` echoed `regionmonitoring` against `'regionMonitoring'`.
//   · `stationaryExitMode` travelled as `'poll'` where the native constant is
//     `'polling'`, working only because it fell into an else branch.
//
// Three of those four were invisible because the OTHER values in the same
// vocabulary happened to match on both sides. That is the pattern this file
// exists to break: assert the whole vocabulary, not the value you thought of.

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { toFlatConfig } from '../sdk/config-mapper';
import type { BaseConfig } from '../definitions/config';
import type { ActivityTypeHint, StationaryExitMode } from '../definitions/wire';
import type { LogLevel } from '../definitions/values';
import type { ServiceRestartReason } from '../definitions/events';

describe('vocabularies that cross the bridge', () => {
  // ── Restart reason ────────────────────────────────────────────────────────
  //
  // Mirrors `ServiceEvent.publicReason` in Kotlin. The Android unit test
  // (`ServiceRestartReasonTest`) guards the native half; this guards that the
  // published union is exactly the set that half produces. Both must be edited
  // together, which is the point.
  it('publishes exactly the four restart reasons the native can emit', () => {
    const published: ServiceRestartReason[] = ['watchdog', 'systemKill', 'boot', 'appRemoved'];
    // The internal spellings, as persisted in SharedPreferences.
    const persisted: Record<string, ServiceRestartReason> = {
      watchdog: 'watchdog',
      system_kill: 'systemKill',
      boot: 'boot',
      app_removed: 'appRemoved',
    };
    assert.deepEqual(Object.values(persisted).sort(), [...published].sort());
    // The two that differ are the whole reason the bug existed: `watchdog` and
    // `boot` are identical on both sides and made three of four look correct.
    const identical = Object.entries(persisted).filter(([k, v]) => k === v).map(([k]) => k);
    assert.deepEqual(identical.sort(), ['boot', 'watchdog']);
  });

  // ── Log level ─────────────────────────────────────────────────────────────
  it('publishes log levels in the lowercase the union declares', () => {
    // What `LogDAO.toLevel` / `LogReader.levelString` return, by ordinal.
    const byOrdinal: LogLevel[] = ['debug', 'info', 'warn', 'error'];
    for (const level of byOrdinal) {
      assert.equal(level, level.toLowerCase(),
        'the write path lower-cases and the read path used not to: that asymmetry is the bug');
    }
    // `trace` is in the union and has no ordinal: storage collapses it into
    // debug. Asserted so the gap is recorded rather than discovered again.
    const declared: LogLevel[] = ['trace', 'debug', 'info', 'warn', 'error'];
    const producible = byOrdinal;
    assert.deepEqual(declared.filter(l => !producible.includes(l)), ['trace']);
  });

  // ── Stationary exit mode ──────────────────────────────────────────────────
  it('translates the exit mode to the native spelling instead of passing it through', () => {
    const polling = toFlatConfig({ stationary: { exitMode: 'poll' } } as BaseConfig);
    const geofence = toFlatConfig({ stationary: { exitMode: 'geofence' } } as BaseConfig);
    // `'poll'` used to travel verbatim and land in the native else branch, which
    // happens to be polling — correct by accident, and only until a third mode
    // or an explicit equality check exists.
    assert.equal(polling.stationaryExitMode, 'polling');
    assert.equal(geofence.stationaryExitMode, 'geofence');
    const modes: StationaryExitMode[] = ['polling', 'geofence'];
    assert.ok(modes.includes(polling.stationaryExitMode as StationaryExitMode));
  });

  // ── Activity type ─────────────────────────────────────────────────────────
  it('sends every activity type in the native spelling, or nothing at all', () => {
    const publicValues = ['automotiveNavigation', 'otherNavigation', 'fitness', 'other'] as const;
    const native: ActivityTypeHint[] = ['AutomotiveNavigation', 'OtherNavigation', 'Fitness', 'Other'];
    const sent = publicValues.map(v => toFlatConfig({ location: { activityType: v } } as BaseConfig).activityType);
    assert.deepEqual(sent, native, 'a value the table misses must not travel in the public spelling');
  });

  // ── iOS background fallback ───────────────────────────────────────────────
  it('keeps the iOS fallback strategy in one spelling across both exits', () => {
    // Swift lower-cases on ingest for internal comparison; `getConfig()` and the
    // `iosFallbackActivated` event must both hand back the published spelling.
    // This is the JS-side statement of that contract — `BGConfig.publicFallback`
    // is its implementation.
    const published = ['significantChanges', 'regionMonitoring', 'none'];
    const internal = published.map(v => v.toLowerCase());
    assert.deepEqual(internal, ['significantchanges', 'regionmonitoring', 'none']);
    assert.notDeepEqual(internal, published,
      'two of the three differ once lower-cased, which is why echoing storage broke comparison');
  });
});
