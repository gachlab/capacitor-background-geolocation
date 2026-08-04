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
    assert.deepEqual(sent, native);
    // The assertion that was missing: the message claimed to cover a value the
    // table misses, and the test never passed one — so restoring the old
    // `?? loc.activityType` fallback left it green. Every public value IS in the
    // table, which is exactly why the gap was invisible.
    const unmapped = toFlatConfig({ location: { activityType: 'walking' } } as unknown as BaseConfig);
    assert.equal(unmapped.activityType, undefined,
      'an unmapped value must not travel in the public spelling the native cannot read');
  });

  // The iOS fallback strategy was asserted here and has been REMOVED, for the
  // same reason as the other three: it compared `['significantChanges', …]
  // .map(v => v.toLowerCase())` against a hand-written lowercase list, executing
  // no production code at all. It is Swift, and unreachable from here.
  //
  // The comment that replaced the first three claimed iOS had no unit-test
  // harness in this repo. That was wrong and unchecked: `Package.swift` declares
  // a test target, `ios/Tests/BackgroundGeolocationPluginTests/` holds several
  // XCTest files, and CI runs `xcodebuild test` on every PR. The fallback
  // translation, its round trip and the `queryParams` coercion are guarded there
  // now, in `VocabularyTests.swift`.
});
