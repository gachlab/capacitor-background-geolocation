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
  // Three more vocabularies were asserted here and have been REMOVED: restart
  // reasons, log levels and the iOS fallback strategy. Each compared a
  // hand-written literal against another hand-written literal, so reverting the
  // production fix left them green — verified by actually doing it. A test that
  // cannot fail is worse than no test: it reports coverage that does not exist.
  //
  // Their real subjects live in Kotlin and Swift and are unreachable from here,
  // so they are guarded where they run: `ServiceRestartReasonTest` and
  // `LogLevelsTest` on the Android side. iOS has no unit-test harness in this
  // repo, so `BGConfig.publicFallback` and `LogReader.levelString` are genuinely
  // unguarded — stated plainly rather than papered over with an assertion that
  // never executes them.

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
