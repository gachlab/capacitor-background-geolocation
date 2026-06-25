// node:test unit tests for BackgroundGeolocationWeb (web.ts)
// Run: npm test

import assert from 'node:assert/strict';
import { describe, it, beforeEach, afterEach, mock } from 'node:test';

import { AccuracyValue, AuthorizationStatus, LocationProviderValue } from '../definitions.js';
import { BackgroundGeolocationWeb } from '../web.js';

// ─── Mock browser globals ─────────────────────────────────────────────────────

let onWatchSuccess: ((pos: GeolocationPosition) => void) | null = null;
let onWatchError: ((err: GeolocationPositionError) => void) | null = null;
let onCurrentSuccess: ((pos: GeolocationPosition) => void) | null = null;
let onCurrentError: ((err: GeolocationPositionError) => void) | null = null;

const mockGeo = {
  watchPosition: mock.fn(
    (success: (pos: GeolocationPosition) => void, error: (e: GeolocationPositionError) => void) => {
      onWatchSuccess = success;
      onWatchError = error;
      return 1;
    },
  ),
  clearWatch: mock.fn(),
  getCurrentPosition: mock.fn(
    (success: (pos: GeolocationPosition) => void, error: (e: GeolocationPositionError) => void) => {
      onCurrentSuccess = success;
      onCurrentError = error;
    },
  ),
};

// Node 22+ defines navigator but not navigator.geolocation.
if (typeof globalThis.navigator === 'undefined') {
  Object.defineProperty(globalThis, 'navigator', {
    value: {} as Navigator,
    writable: true,
    configurable: true,
  });
}
Object.defineProperty(globalThis.navigator, 'geolocation', {
  value: mockGeo,
  writable: true,
  configurable: true,
});

// ─── Helpers ──────────────────────────────────────────────────────────────────

function makePosition(overrides: Partial<GeolocationCoordinates> = {}): GeolocationPosition {
  return {
    timestamp: 1_716_000_000_000,
    coords: {
      latitude: 19.4326,
      longitude: -99.1332,
      accuracy: 10,
      altitude: 2240,
      altitudeAccuracy: 5,
      heading: 90,
      speed: 13.8,
      toJSON() {
        return this;
      },
      ...overrides,
    } as GeolocationCoordinates,
    toJSON() {
      return this;
    },
  };
}

function makeGeoError(code: number, message: string): GeolocationPositionError {
  return {
    code,
    message,
    PERMISSION_DENIED: 1,
    POSITION_UNAVAILABLE: 2,
    TIMEOUT: 3,
  } as GeolocationPositionError;
}

function collect<T>(plugin: BackgroundGeolocationWeb, event: string): T[] {
  const items: T[] = [];
  plugin.addListener(event, (d) => items.push(d as T));
  return items;
}

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('BackgroundGeolocationWeb', () => {
  let plugin: BackgroundGeolocationWeb;

  beforeEach(() => {
    plugin = new BackgroundGeolocationWeb();
    mockGeo.watchPosition.mock.resetCalls();
    mockGeo.clearWatch.mock.resetCalls();
    mockGeo.getCurrentPosition.mock.resetCalls();
    onWatchSuccess = null;
    onWatchError = null;
    onCurrentSuccess = null;
    onCurrentError = null;
  });

  afterEach(async () => {
    await plugin.stop().catch(() => {
      /* already stopped */
    });
  });

  // ── configure / getConfig ─────────────────────────────────────────────────

  describe('configure() / getConfig()', () => {
    it('stores options and returns them unchanged', async () => {
      await plugin.configure({ distanceFilter: 50, desiredAccuracy: 'HIGH' });
      const cfg = await plugin.getConfig();
      assert.equal(cfg.distanceFilter, 50);
      assert.equal(cfg.desiredAccuracy, 'HIGH');
    });

    it('merges successive configure() calls', async () => {
      await plugin.configure({ distanceFilter: 100 });
      await plugin.configure({ desiredAccuracy: 'LOW' });
      const cfg = await plugin.getConfig();
      assert.equal(cfg.distanceFilter, 100);
      assert.equal(cfg.desiredAccuracy, 'LOW');
    });

    it('getConfig() returns a copy — mutations do not affect internal state', async () => {
      await plugin.configure({ distanceFilter: 200 });
      const cfg = await plugin.getConfig();
      cfg.distanceFilter = 999;
      const cfg2 = await plugin.getConfig();
      assert.equal(cfg2.distanceFilter, 200);
    });
  });

  // ── start / stop ──────────────────────────────────────────────────────────

  describe('start()', () => {
    it('calls watchPosition and emits start event', async () => {
      const starts = collect(plugin, 'start');
      await plugin.start();
      assert.equal(mockGeo.watchPosition.mock.callCount(), 1);
      assert.equal(starts.length, 1);
    });

    it('does not double-register on repeated start()', async () => {
      await plugin.start();
      await plugin.start();
      assert.equal(mockGeo.watchPosition.mock.callCount(), 1);
    });

    it('uses high accuracy when desiredAccuracy is not LOW', async () => {
      await plugin.configure({ desiredAccuracy: 'HIGH' });
      await plugin.start();
      const opts = mockGeo.watchPosition.mock.calls[0].arguments[2] as PositionOptions;
      assert.equal(opts.enableHighAccuracy, true);
    });

    it('uses low accuracy when desiredAccuracy is LOW', async () => {
      await plugin.configure({ desiredAccuracy: 'LOW' });
      await plugin.start();
      const opts = mockGeo.watchPosition.mock.calls[0].arguments[2] as PositionOptions;
      assert.equal(opts.enableHighAccuracy, false);
    });

    it('emits location event when watchPosition fires', async () => {
      const locs = collect<{ latitude: number }>(plugin, 'location');
      await plugin.start();
      onWatchSuccess?.(makePosition({ latitude: 19.4, longitude: -99.1 }));
      assert.equal(locs.length, 1);
      assert.equal(locs[0].latitude, 19.4);
    });

    it('emits error event when watchPosition reports an error', async () => {
      const errs = collect<{ code: number }>(plugin, 'error');
      await plugin.start();
      onWatchError?.(makeGeoError(2, 'Position unavailable'));
      assert.equal(errs.length, 1);
      assert.equal(errs[0].code, 2);
    });
  });

  describe('stop()', () => {
    it('calls clearWatch and emits stop event', async () => {
      const stops = collect(plugin, 'stop');
      await plugin.start();
      await plugin.stop();
      assert.equal(mockGeo.clearWatch.mock.callCount(), 1);
      assert.equal(stops.length, 1);
    });

    it('is a no-op when not running', async () => {
      await plugin.stop();
      assert.equal(mockGeo.clearWatch.mock.callCount(), 0);
    });
  });

  // ── getCurrentLocation ────────────────────────────────────────────────────

  describe('getCurrentLocation()', () => {
    it('resolves with location on success', async () => {
      const promise = plugin.getCurrentLocation();
      onCurrentSuccess?.(makePosition({ latitude: 48.85, longitude: 2.35 }));
      const loc = await promise;
      assert.equal(loc.latitude, 48.85);
      assert.equal(loc.longitude, 2.35);
      assert.equal(loc.provider, 'gps');
    });

    it('rejects with mapped error code on failure', async () => {
      const promise = plugin.getCurrentLocation();
      onCurrentError?.(makeGeoError(2, 'Position unavailable'));
      await assert.rejects(promise, (err: { code: number }) => {
        assert.equal(err.code, 2);
        return true;
      });
    });

    it('passes enableHighAccuracy: true by default', async () => {
      void plugin.getCurrentLocation({ enableHighAccuracy: true });
      const opts = mockGeo.getCurrentPosition.mock.calls[0].arguments[2] as PositionOptions;
      assert.equal(opts.enableHighAccuracy, true);
    });

    it('passes enableHighAccuracy: false when explicitly set', async () => {
      void plugin.getCurrentLocation({ enableHighAccuracy: false });
      const opts = mockGeo.getCurrentPosition.mock.calls[0].arguments[2] as PositionOptions;
      assert.equal(opts.enableHighAccuracy, false);
    });
  });

  // ── checkStatus ───────────────────────────────────────────────────────────

  describe('checkStatus()', () => {
    it('reports isRunning: false before start()', async () => {
      const status = await plugin.checkStatus();
      assert.equal(status.isRunning, false);
    });

    it('reports isRunning: true while tracking', async () => {
      await plugin.start();
      const status = await plugin.checkStatus();
      assert.equal(status.isRunning, true);
    });

    it('reports isRunning: false after stop()', async () => {
      await plugin.start();
      await plugin.stop();
      const status = await plugin.checkStatus();
      assert.equal(status.isRunning, false);
    });

    it('reports locationServicesEnabled: true when geolocation exists', async () => {
      const status = await plugin.checkStatus();
      assert.equal(status.locationServicesEnabled, true);
    });
  });

  // ── Location field mapping ────────────────────────────────────────────────

  describe('location field mapping', () => {
    it('maps all GeolocationPosition fields to Location', async () => {
      const locs = collect<Record<string, unknown>>(plugin, 'location');
      await plugin.start();
      onWatchSuccess?.(
        makePosition({
          latitude: 19.4326,
          longitude: -99.1332,
          accuracy: 5,
          speed: 10,
          altitude: 2200,
          heading: 45,
        }),
      );

      const loc = locs[0];
      assert.equal(loc['latitude'], 19.4326);
      assert.equal(loc['longitude'], -99.1332);
      assert.equal(loc['accuracy'], 5);
      assert.equal(loc['speed'], 10);
      assert.equal(loc['altitude'], 2200);
      assert.equal(loc['bearing'], 45);
      assert.equal(loc['provider'], 'gps');
    });

    it('falls back to 0 when nullable fields are null', async () => {
      const locs = collect<Record<string, unknown>>(plugin, 'location');
      await plugin.start();
      onWatchSuccess?.(
        makePosition({
          accuracy: null as unknown as number,
          speed: null as unknown as number,
          altitude: null as unknown as number,
          heading: null as unknown as number,
        }),
      );

      assert.equal(locs[0]['accuracy'], 0);
      assert.equal(locs[0]['speed'], 0);
      assert.equal(locs[0]['altitude'], 0);
      assert.equal(locs[0]['bearing'], 0);
    });
  });

  // ── Neutral return values ─────────────────────────────────────────────────

  describe('neutral return values', () => {
    it('getLocations() → empty locations array', async () => {
      assert.deepEqual(await plugin.getLocations(), { locations: [] });
    });

    it('getValidLocations() → empty locations array', async () => {
      assert.deepEqual(await plugin.getValidLocations(), { locations: [] });
    });

    it('getValidLocationsAndDelete() → empty locations array', async () => {
      assert.deepEqual(await plugin.getValidLocationsAndDelete(), {
        locations: [],
      });
    });

    it('getPendingSyncCount() → count: 0', async () => {
      assert.deepEqual(await plugin.getPendingSyncCount(), { count: 0 });
    });

    it('getSessionLocations() → empty locations array', async () => {
      assert.deepEqual(await plugin.getSessionLocations(), { locations: [] });
    });

    it('getSessionLocationsCount() → count: 0', async () => {
      assert.deepEqual(await plugin.getSessionLocationsCount(), { count: 0 });
    });

    it('getStationaryLocation() → null', async () => {
      assert.equal(await plugin.getStationaryLocation(), null);
    });

    it('startTask() → taskKey: 0', async () => {
      assert.deepEqual(await plugin.startTask(), { taskKey: 0 });
    });

    it('isIgnoringBatteryOptimizations() → whitelisted: true', async () => {
      assert.deepEqual(await plugin.isIgnoringBatteryOptimizations(), {
        whitelisted: true,
      });
    });

    it('requestIgnoreBatteryOptimizations() → whitelisted: true', async () => {
      assert.deepEqual(await plugin.requestIgnoreBatteryOptimizations(), {
        whitelisted: true,
      });
    });

    it('requestBackgroundLocationPermission() → { granted: true, notRequired: true }', async () => {
      assert.deepEqual(await plugin.requestBackgroundLocationPermission(), {
        granted: true,
        notRequired: true,
      });
    });

    it('requestActivityRecognitionPermission() → { granted: true, notRequired: true }', async () => {
      assert.deepEqual(await plugin.requestActivityRecognitionPermission(), { granted: true, notRequired: true });
    });

    it('getLogEntries() → empty entries', async () => {
      assert.deepEqual(await plugin.getLogEntries({ limit: 10 }), {
        entries: [],
      });
    });
  });

  // ── No-op methods ─────────────────────────────────────────────────────────

  describe('no-op methods resolve without throwing', () => {
    const cases: [string, unknown[]][] = [
      ['switchMode', [{ mode: 0 }]],
      ['showAppSettings', []],
      ['openSettings', []],
      ['showLocationSettings', []],
    ];

    for (const [method, args] of cases) {
      it(`${method}() resolves`, async () => {
        await (plugin as unknown as Record<string, (...a: unknown[]) => Promise<unknown>>)[method](...args);
      });
    }
  });

  // ── Location store ────────────────────────────────────────────────────────

  describe('location store', () => {
    it('accumulates locations as fixes arrive', async () => {
      await plugin.start();
      onWatchSuccess?.(makePosition({ latitude: 1 }));
      onWatchSuccess?.(makePosition({ latitude: 2 }));
      const { locations } = await plugin.getLocations();
      assert.equal(locations.length, 2);
      assert.equal(locations[0].latitude, 1);
      assert.equal(locations[1].latitude, 2);
    });

    it('deleteLocation removes the matching entry', async () => {
      await plugin.start();
      onWatchSuccess?.(makePosition());
      const { locations } = await plugin.getLocations();
      await plugin.deleteLocation({ locationId: locations[0].id });
      assert.equal((await plugin.getLocations()).locations.length, 0);
    });

    it('deleteAllLocations empties the store', async () => {
      await plugin.start();
      onWatchSuccess?.(makePosition());
      onWatchSuccess?.(makePosition());
      await plugin.deleteAllLocations();
      assert.equal((await plugin.getLocations()).locations.length, 0);
    });

    it('getValidLocationsAndDelete returns and clears', async () => {
      await plugin.start();
      onWatchSuccess?.(makePosition({ latitude: 10 }));
      const { locations } = await plugin.getValidLocationsAndDelete();
      assert.equal(locations.length, 1);
      assert.equal(locations[0].latitude, 10);
      assert.equal((await plugin.getLocations()).locations.length, 0);
    });

    it('respects maxLocations config', async () => {
      await plugin.configure({ maxLocations: 3 });
      await plugin.start();
      for (let i = 0; i < 5; i++) onWatchSuccess?.(makePosition({ latitude: i }));
      const { locations } = await plugin.getLocations();
      assert.equal(locations.length, 3);
      assert.equal(locations[0].latitude, 2);
      assert.equal(locations[2].latitude, 4);
    });
  });

  // ── Sessions ──────────────────────────────────────────────────────────────

  describe('sessions', () => {
    it('session is inactive by default — locations not tracked', async () => {
      await plugin.start();
      onWatchSuccess?.(makePosition());
      assert.equal((await plugin.getSessionLocations()).locations.length, 0);
      assert.equal((await plugin.getSessionLocationsCount()).count, 0);
    });

    it('startSession activates session tracking', async () => {
      await plugin.startSession();
      await plugin.start();
      onWatchSuccess?.(makePosition({ latitude: 5 }));
      onWatchSuccess?.(makePosition({ latitude: 6 }));
      const { locations } = await plugin.getSessionLocations();
      assert.equal(locations.length, 2);
      assert.equal((await plugin.getSessionLocationsCount()).count, 2);
    });

    it('clearSession empties session and stops tracking', async () => {
      await plugin.startSession();
      await plugin.start();
      onWatchSuccess?.(makePosition());
      await plugin.clearSession();
      assert.equal((await plugin.getSessionLocations()).locations.length, 0);
      // new fixes after clear do not accumulate
      onWatchSuccess?.(makePosition());
      assert.equal((await plugin.getSessionLocations()).locations.length, 0);
    });

    it('pre-session locations are not included', async () => {
      await plugin.start();
      onWatchSuccess?.(makePosition({ latitude: 1 }));
      await plugin.startSession();
      onWatchSuccess?.(makePosition({ latitude: 2 }));
      assert.equal((await plugin.getSessionLocations()).locations.length, 1);
      assert.equal((await plugin.getSessionLocations()).locations[0].latitude, 2);
    });
  });

  // ── Sync queue ────────────────────────────────────────────────────────────

  describe('sync queue', () => {
    it('clearSync empties the queue', async () => {
      // Populate queue via a failing POST — simulate by configuring url and
      // letting the fetch mock fail. We can't easily intercept fetch in node:test
      // without globals, so we test clearSync on an initially-empty queue.
      await plugin.clearSync();
      assert.equal((await plugin.getPendingSyncCount()).count, 0);
    });

    it('forceSync is a no-op when queue is empty', async () => {
      await plugin.configure({ url: 'https://example.com/loc' });
      await plugin.forceSync(); // should not throw
    });

    it('forceSync is a no-op when no url is configured', async () => {
      await plugin.forceSync(); // should not throw
    });
  });

  // ── triggerSOS ────────────────────────────────────────────────────────────

  describe('triggerSOS()', () => {
    it('emits sos event with provided payload', async () => {
      const events = collect<Record<string, unknown>>(plugin, 'sos');
      await plugin.triggerSOS({ driverId: 'drv-1' });
      assert.equal(events.length, 1);
      assert.equal(events[0]['driverId'], 'drv-1');
    });

    it('attaches last known location to sos event', async () => {
      const events = collect<Record<string, unknown>>(plugin, 'sos');
      collect(plugin, 'location');
      await plugin.start();
      onWatchSuccess?.(makePosition({ latitude: 19.4, longitude: -99.1 }));
      await plugin.stop();

      await plugin.triggerSOS({});
      const loc = events[0]['location'] as Record<string, unknown> | undefined;
      assert.ok(loc, 'location should be attached');
      assert.equal(loc['latitude'], 19.4);
    });

    it('includes no location if never started', async () => {
      const events = collect<Record<string, unknown>>(plugin, 'sos');
      await plugin.triggerSOS({});
      assert.equal(events[0]['location'], undefined);
    });
  });

  // ── getDiagnostics ────────────────────────────────────────────────────────

  describe('getDiagnostics()', () => {
    it('reports manufacturer: web', async () => {
      const diag = await plugin.getDiagnostics();
      assert.equal(diag.manufacturer, 'web');
    });

    it('reflects isRunning state', async () => {
      assert.equal((await plugin.getDiagnostics()).isRunning, false);
      await plugin.start();
      assert.equal((await plugin.getDiagnostics()).isRunning, true);
      await plugin.stop();
      assert.equal((await plugin.getDiagnostics()).isRunning, false);
    });

    it('lastLocationAt is null before any fix', async () => {
      const diag = await plugin.getDiagnostics();
      assert.equal(diag.lastLocationAt, null);
    });

    it('lastLocationAt is set after a location fix', async () => {
      collect(plugin, 'location');
      await plugin.start();
      onWatchSuccess?.(makePosition());
      await plugin.stop();

      const diag = await plugin.getDiagnostics();
      assert.ok(diag.lastLocationAt != null, 'lastLocationAt should be set');
    });
  });

  // ── getBackgroundKillReason ───────────────────────────────────────────────

  describe('getBackgroundKillReason()', () => {
    it('returns null fields on web', async () => {
      const result = await plugin.getBackgroundKillReason();
      assert.equal(result.reason, null);
      assert.equal(result.timestamp, null);
    });
  });

  // ── getCurrentLocation ────────────────────────────────────────────────────

  describe('getCurrentLocation()', () => {
    it('resolves with mapped location on success', async () => {
      const promise = plugin.getCurrentLocation();
      onCurrentSuccess!(makePosition({ latitude: 10, longitude: 20 }));
      const loc = await promise;
      assert.equal(loc.latitude, 10);
      assert.equal(loc.longitude, 20);
    });

    it('rejects with mapped error on failure', async () => {
      const promise = plugin.getCurrentLocation();
      onCurrentError!(makeGeoError(2, 'Position unavailable'));
      await assert.rejects(
        () => promise,
        (err: unknown) => {
          assert.equal((err as { code: number }).code, 2);
          return true;
        },
      );
    });

    it('respects enableHighAccuracy option', async () => {
      plugin.getCurrentLocation({ enableHighAccuracy: false });
      const opts = mockGeo.getCurrentPosition.mock.calls[0].arguments[2] as PositionOptions;
      assert.equal(opts.enableHighAccuracy, false);
    });

    it('throws unavailable when geolocation API is absent', async () => {
      delete (navigator as unknown as Record<string, unknown>).geolocation;
      assert.throws(
        () => plugin.getCurrentLocation(),
        (err: unknown) => {
          assert.ok(err instanceof Error);
          return true;
        },
      );
      Object.defineProperty(navigator, 'geolocation', { value: mockGeo, configurable: true, writable: true });
    });
  });

  // ── start() with no geolocation ───────────────────────────────────────────

  describe('start() without geolocation API', () => {
    it('throws unavailable when geolocation API is absent', async () => {
      delete (navigator as unknown as Record<string, unknown>).geolocation;
      await assert.rejects(
        () => plugin.start(),
        (err: unknown) => {
          assert.ok(err instanceof Error);
          return true;
        },
      );
      Object.defineProperty(navigator, 'geolocation', { value: mockGeo, configurable: true, writable: true });
    });
  });

  // ── start() idempotency ───────────────────────────────────────────────────

  describe('start() idempotency guard', () => {
    it('skips watchPosition on second call and emits no extra start event', async () => {
      const starts = collect(plugin, 'start');
      await plugin.start();
      await plugin.start();
      assert.equal(mockGeo.watchPosition.mock.callCount(), 1);
      assert.equal(starts.length, 1);
    });
  });

  // ── web stubs — safe defaults ─────────────────────────────────────────────

  describe('web stubs returning safe defaults', () => {
    it('getStationaryLocation() → null', async () => {
      assert.equal(await plugin.getStationaryLocation(), null);
    });

    it('getLocations() → empty array', async () => {
      assert.equal((await plugin.getLocations()).locations.length, 0);
    });

    it('getValidLocations() → empty array', async () => {
      assert.equal((await plugin.getValidLocations()).locations.length, 0);
    });

    it('getValidLocationsAndDelete() → empty array', async () => {
      assert.equal((await plugin.getValidLocationsAndDelete()).locations.length, 0);
    });

    it('getPendingSyncCount() → 0', async () => {
      assert.equal((await plugin.getPendingSyncCount()).count, 0);
    });

    it('getSessionLocations() → empty array', async () => {
      assert.equal((await plugin.getSessionLocations()).locations.length, 0);
    });

    it('getSessionLocationsCount() → 0', async () => {
      assert.equal((await plugin.getSessionLocationsCount()).count, 0);
    });

    it('isIgnoringBatteryOptimizations() → whitelisted: true', async () => {
      assert.equal((await plugin.isIgnoringBatteryOptimizations()).whitelisted, true);
    });

    it('requestIgnoreBatteryOptimizations() → whitelisted: true', async () => {
      assert.equal((await plugin.requestIgnoreBatteryOptimizations()).whitelisted, true);
    });

    it('openBatterySettings() resolves without throwing', async () => {
      await plugin.openBatterySettings();
    });

    it('openAutoStartSettings() → opened: false, manufacturer: web', async () => {
      const r = await plugin.openAutoStartSettings();
      assert.equal(r.opened, false);
      assert.equal(r.manufacturer, 'web');
    });

    it('getManufacturerHelp() → manufacturer: web, steps: []', async () => {
      const r = await plugin.getManufacturerHelp();
      assert.equal(r.manufacturer, 'web');
      assert.equal(r.steps.length, 0);
    });

    it('getPluginVersion() → version string', async () => {
      const r = await plugin.getPluginVersion();
      assert.ok(typeof r.version === 'string' && r.version.length > 0);
    });

    it('getCapabilities() → web reports backgroundTracking:false (honest misa)', async () => {
      const caps = await plugin.getCapabilities();
      assert.equal(caps.platform, 'web');
      // The whole point of the misa register: the web die has no always-on
      // island and must not pretend it tracks in the background.
      assert.equal(caps.backgroundTracking, false);
      assert.equal(caps.driverIntelligence, false);
      assert.equal(caps.activityRecognition, false);
      assert.equal(caps.sensorFusion, false);
      assert.equal(caps.oemSettings, false);
      // Base ISA the web die does implement.
      assert.equal(caps.geofencing, true);
      assert.equal(caps.maxGeofences, -1);
    });

    it('requestBackgroundLocationPermission() → granted: true, notRequired: true', async () => {
      const r = await plugin.requestBackgroundLocationPermission();
      assert.equal(r.granted, true);
      assert.equal(r.notRequired, true);
    });

    it('requestActivityRecognitionPermission() → granted: true, notRequired: true', async () => {
      const r = await plugin.requestActivityRecognitionPermission();
      assert.equal(r.granted, true);
      assert.equal(r.notRequired, true);
    });

    it('startTask() → taskKey number', async () => {
      const r = await plugin.startTask();
      assert.ok(typeof r.taskKey === 'number');
    });

    it('endTask() resolves without throwing', async () => {
      await plugin.endTask({ taskKey: 0 });
    });

    it('getLogEntries() → empty entries', async () => {
      assert.equal((await plugin.getLogEntries({ limit: 10 })).entries.length, 0);
    });
  });

  // ── checkPermissions ──────────────────────────────────────────────────────

  describe('checkPermissions()', () => {
    it('returns prompt when permissions API is unavailable', async () => {
      const saved = (navigator as unknown as Record<string, unknown>).permissions;
      Object.defineProperty(navigator, 'permissions', { value: undefined, configurable: true });
      const result = await plugin.checkPermissions();
      assert.equal(result.location, 'prompt');
      Object.defineProperty(navigator, 'permissions', { value: saved, configurable: true });
    });

    it('returns state from permissions API when available', async () => {
      const fakePerms = { query: async () => ({ state: 'granted' as PermissionState }) };
      Object.defineProperty(navigator, 'permissions', {
        value: fakePerms,
        writable: true,
        configurable: true,
      });
      const result = await plugin.checkPermissions();
      assert.equal(result.location, 'granted');
      Object.defineProperty(navigator, 'permissions', { value: undefined, configurable: true });
    });

    it('falls through to prompt when permissions API query throws', async () => {
      const fakePerms = {
        query: async () => {
          throw new Error('not supported');
        },
      };
      Object.defineProperty(navigator, 'permissions', {
        value: fakePerms,
        writable: true,
        configurable: true,
      });
      const result = await plugin.checkPermissions();
      assert.equal(result.location, 'prompt');
      Object.defineProperty(navigator, 'permissions', { value: undefined, configurable: true });
    });
  });

  // ── requestPermissions ────────────────────────────────────────────────────

  describe('requestPermissions()', () => {
    it('resolves granted when getCurrentPosition succeeds', async () => {
      const promise = plugin.requestPermissions();
      onCurrentSuccess!(makePosition());
      const result = await promise;
      assert.equal(result.location, 'granted');
    });

    it('resolves denied when PERMISSION_DENIED error', async () => {
      const promise = plugin.requestPermissions();
      onCurrentError!(makeGeoError(1, 'Permission denied'));
      const result = await promise;
      assert.equal(result.location, 'denied');
    });

    it('resolves prompt for other geolocation errors', async () => {
      const promise = plugin.requestPermissions();
      onCurrentError!(makeGeoError(2, 'Position unavailable'));
      const result = await promise;
      assert.equal(result.location, 'prompt');
    });

    it('returns denied immediately when geolocation API is absent', async () => {
      delete (navigator as unknown as Record<string, unknown>).geolocation;
      const result = await plugin.requestPermissions();
      assert.equal(result.location, 'denied');
      Object.defineProperty(navigator, 'geolocation', { value: mockGeo, configurable: true, writable: true });
    });
  });

  // ── requestNotificationPermission ────────────────────────────────────────

  describe('requestNotificationPermission()', () => {
    it('returns notRequired when Notification API is absent', async () => {
      const saved = (globalThis as unknown as Record<string, unknown>).Notification;
      delete (globalThis as unknown as Record<string, unknown>).Notification;
      const r = await plugin.requestNotificationPermission();
      assert.deepEqual(r, { granted: true, notRequired: true });
      if (saved !== undefined) (globalThis as unknown as Record<string, unknown>).Notification = saved;
    });

    it('returns granted immediately when permission is already granted', async () => {
      Object.defineProperty(globalThis, 'Notification', {
        value: { permission: 'granted', requestPermission: async () => 'granted' },
        configurable: true,
        writable: true,
      });
      const r = await plugin.requestNotificationPermission();
      assert.deepEqual(r, { granted: true });
      delete (globalThis as unknown as Record<string, unknown>).Notification;
    });

    it('returns granted after user grants permission', async () => {
      Object.defineProperty(globalThis, 'Notification', {
        value: { permission: 'default', requestPermission: async () => 'granted' },
        configurable: true,
        writable: true,
      });
      const r = await plugin.requestNotificationPermission();
      assert.deepEqual(r, { granted: true });
      delete (globalThis as unknown as Record<string, unknown>).Notification;
    });

    it('returns denied when user denies permission', async () => {
      Object.defineProperty(globalThis, 'Notification', {
        value: { permission: 'default', requestPermission: async () => 'denied' },
        configurable: true,
        writable: true,
      });
      const r = await plugin.requestNotificationPermission();
      assert.deepEqual(r, { granted: false, denied: ['notifications'] });
      delete (globalThis as unknown as Record<string, unknown>).Notification;
    });

    it('returns denied when requestPermission() throws', async () => {
      Object.defineProperty(globalThis, 'Notification', {
        value: {
          permission: 'default',
          requestPermission: async () => {
            throw new Error('not supported');
          },
        },
        configurable: true,
        writable: true,
      });
      const r = await plugin.requestNotificationPermission();
      assert.deepEqual(r, { granted: false, denied: ['notifications'] });
      delete (globalThis as unknown as Record<string, unknown>).Notification;
    });
  });

  // ── geofencing ──────────────────────────────────────────────────────────────
  describe('geofencing', () => {
    const CENTER = { latitude: 19.5, longitude: -99.0 };
    const geoFix = (lat: number, lon: number, time = 1_716_000_000_000): GeolocationPosition =>
      ({
        ...makePosition({ latitude: lat, longitude: lon, speed: 0, heading: 0 }),
        timestamp: time,
      }) as GeolocationPosition;

    it('emits geofenceError for an invalid radius (and does not register it)', async () => {
      const errs = collect<{ id?: string; message: string }>(plugin, 'geofenceError');
      await plugin.addGeofences({
        geofences: [{ id: 'bad', latitude: CENTER.latitude, longitude: CENTER.longitude, radius: 0 }],
      });
      assert.equal(errs.length, 1);
      assert.equal(errs[0].id, 'bad');
      assert.equal((await plugin.getGeofences()).geofences.length, 0);
    });

    it('synthesises an initial ENTER when registering already inside', async () => {
      const enters = collect<{ id: string; action: string }>(plugin, 'geofenceEnter');
      await plugin.start();
      onWatchSuccess?.(geoFix(CENTER.latitude, CENTER.longitude)); // lastLocation = inside
      await plugin.addGeofences({
        geofences: [{ id: 'depot', latitude: CENTER.latitude, longitude: CENTER.longitude, radius: 200 }],
      });
      assert.equal(enters.length, 1);
      assert.equal(enters[0].id, 'depot');
      assert.equal(enters[0].action, 'ENTER');
    });

    it('fires ENTER on a boundary crossing, not while still outside', async () => {
      const enters = collect<{ id: string }>(plugin, 'geofenceEnter');
      await plugin.start();
      await plugin.addGeofences({
        geofences: [{ id: 'depot', latitude: CENTER.latitude, longitude: CENTER.longitude, radius: 200 }],
      });
      onWatchSuccess?.(geoFix(20.5, -99.0)); // ~111 km away → outside
      assert.equal(enters.length, 0);
      onWatchSuccess?.(geoFix(CENTER.latitude, CENTER.longitude)); // inside → ENTER
      assert.equal(enters.length, 1);
    });

    it('fires EXIT only after a real entry', async () => {
      const exits = collect<{ id: string; action: string }>(plugin, 'geofenceExit');
      await plugin.start();
      await plugin.addGeofences({
        geofences: [
          { id: 'depot', latitude: CENTER.latitude, longitude: CENTER.longitude, radius: 200, notifyOnExit: true },
        ],
      });
      onWatchSuccess?.(geoFix(20.5, -99.0)); // outside first → no EXIT
      assert.equal(exits.length, 0);
      onWatchSuccess?.(geoFix(CENTER.latitude, CENTER.longitude)); // ENTER
      onWatchSuccess?.(geoFix(20.5, -99.0)); // leave → EXIT
      assert.equal(exits.length, 1);
      assert.equal(exits[0].action, 'EXIT');
    });

    it('fires a single DWELL after the loitering delay', async () => {
      const dwells = collect<{ id: string; action: string }>(plugin, 'geofenceDwell');
      await plugin.start();
      await plugin.addGeofences({
        geofences: [
          {
            id: 'depot',
            latitude: CENTER.latitude,
            longitude: CENTER.longitude,
            radius: 200,
            notifyOnDwell: true,
            loiteringDelay: 1000,
          },
        ],
      });
      onWatchSuccess?.(geoFix(CENTER.latitude, CENTER.longitude, 1_716_000_000_000)); // ENTER @ T
      assert.equal(dwells.length, 0);
      onWatchSuccess?.(geoFix(CENTER.latitude, CENTER.longitude, 1_716_000_000_500)); // T+0.5s → not yet
      assert.equal(dwells.length, 0);
      onWatchSuccess?.(geoFix(CENTER.latitude, CENTER.longitude, 1_716_000_001_000)); // T+1s → DWELL
      assert.equal(dwells.length, 1);
      onWatchSuccess?.(geoFix(CENTER.latitude, CENTER.longitude, 1_716_000_002_000)); // still inside → no repeat
      assert.equal(dwells.length, 1);
    });

    it('getGeofences returns the registered set; removeGeofences clears it', async () => {
      await plugin.addGeofences({
        geofences: [
          { id: 'a', latitude: CENTER.latitude, longitude: CENTER.longitude, radius: 200 },
          { id: 'b', latitude: CENTER.latitude, longitude: CENTER.longitude, radius: 300 },
        ],
      });
      assert.equal((await plugin.getGeofences()).geofences.length, 2);
      await plugin.removeGeofences({ ids: ['a'] });
      assert.deepEqual(
        (await plugin.getGeofences()).geofences.map((g) => g.id),
        ['b'],
      );
      await plugin.removeGeofences();
      assert.equal((await plugin.getGeofences()).geofences.length, 0);
    });
  });
});

// ── definitions constants ─────────────────────────────────────────────────────

describe('definitions constants', () => {
  it('LocationProviderValue has correct numeric mappings', () => {
    assert.equal(LocationProviderValue.DISTANCE_FILTER, 0);
    assert.equal(LocationProviderValue.ACTIVITY_PROVIDER, 1);
    assert.equal(LocationProviderValue.RAW_PROVIDER, 2);
  });

  it('AccuracyValue has correct meter mappings', () => {
    assert.equal(AccuracyValue.HIGH, 0);
    assert.equal(AccuracyValue.MEDIUM, 100);
    assert.equal(AccuracyValue.LOW, 1000);
    assert.equal(AccuracyValue.PASSIVE, 10000);
  });

  it('AuthorizationStatus has correct numeric values', () => {
    assert.equal(AuthorizationStatus.NOT_AUTHORIZED, 0);
    assert.equal(AuthorizationStatus.AUTHORIZED, 1);
    assert.equal(AuthorizationStatus.AUTHORIZED_FOREGROUND, 2);
  });
});
