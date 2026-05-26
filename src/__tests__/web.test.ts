// node:test unit tests for BackgroundGeolocationWeb (web.ts)
// Run: npm test

import assert from 'node:assert/strict';
import { describe, it, beforeEach, afterEach, mock } from 'node:test';

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
      onWatchSuccess!(makePosition({ latitude: 19.4, longitude: -99.1 }));
      assert.equal(locs.length, 1);
      assert.equal(locs[0].latitude, 19.4);
    });

    it('emits error event when watchPosition reports an error', async () => {
      const errs = collect<{ code: number }>(plugin, 'error');
      await plugin.start();
      onWatchError!(makeGeoError(2, 'Position unavailable'));
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
      onWatchSuccess!(
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
      onWatchSuccess!(
        makePosition({
          speed: null as unknown as number,
          altitude: null as unknown as number,
          heading: null as unknown as number,
        }),
      );

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

  // ── Unimplemented methods ─────────────────────────────────────────────────

  describe('unimplemented methods', () => {
    const cases: [string, unknown[]][] = [
      ['switchMode', [{ mode: 0 }]],
      ['deleteLocation', [{ locationId: 1 }]],
      ['deleteAllLocations', []],
      ['forceSync', []],
      ['clearSync', []],
      ['startSession', []],
      ['clearSession', []],
      ['showAppSettings', []],
      ['openSettings', []],
      ['showLocationSettings', []],
      ['headlessTask', [() => {}]], // eslint-disable-line @typescript-eslint/no-empty-function
    ];

    for (const [method, args] of cases) {
      it(`${method}() throws`, async () => {
        await assert.rejects(
          () => (plugin as unknown as Record<string, (...a: unknown[]) => Promise<unknown>>)[method](...args),
          (err: Error) => {
            assert.ok(err instanceof Error, `expected Error, got ${typeof err}`);
            return true;
          },
        );
      });
    }
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
      onWatchSuccess!(makePosition({ latitude: 19.4, longitude: -99.1 }));
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
      onWatchSuccess!(makePosition());
      await plugin.stop();

      const diag = await plugin.getDiagnostics();
      assert.ok(diag.lastLocationAt != null, 'lastLocationAt should be set');
    });
  });
});
