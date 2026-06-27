// node:test unit tests for the domain/GeoPoint value object.
// Mirrors the Android GeoPointTest and iOS DomainValueObjectsTests so the shared
// vocabulary stays in parity across the three dies.
// Run: npm test

import assert from 'node:assert/strict';
import { describe, it } from 'node:test';

import { GeoPoint } from '../domain/geo-point.js';

describe('GeoPoint — geographic coordinate value object', () => {
  it('distance to itself is zero', () => {
    const p = new GeoPoint(-25.2637, -57.5759); // Asunción
    assert.ok(Math.abs(p.distanceTo(p)) < 1e-6);
  });

  it('distance is symmetric', () => {
    const a = new GeoPoint(-25.2637, -57.5759);
    const b = new GeoPoint(-25.3, -57.6);
    assert.ok(Math.abs(a.distanceTo(b) - b.distanceTo(a)) < 1e-9);
  });

  it('one degree of latitude is about 111 km', () => {
    const d = new GeoPoint(0, 0).distanceTo(new GeoPoint(1, 0));
    assert.ok(Math.abs(d - 111_194.9) < 1.0, `expected ~111195 m, got ${d}`);
  });

  it('matches the legacy web haversine numerics exactly', () => {
    // Reproduce the formula web.ts used inline, byte-for-byte.
    const lat1 = -25.2637,
      lon1 = -57.5759,
      lat2 = -25.3,
      lon2 = -57.6;
    const r = 6_371_000;
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLon = ((lon2 - lon1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) ** 2 +
      Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
    const expected = 2 * r * Math.asin(Math.sqrt(a));
    assert.equal(new GeoPoint(lat1, lon1).distanceTo(new GeoPoint(lat2, lon2)), expected);
  });
});
