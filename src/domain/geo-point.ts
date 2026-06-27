// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

/**
 * A geographic coordinate — a pure, immutable value object, the web twin of the Android
 * `domain/GeoPoint` and the iOS `Domain/GeoPoint` (Roadmap Fase 1). The great-circle
 * distance was previously a free `distanceMeters` helper in `web.ts`; modelling the
 * point as one value object moves the geometry into the shared domain vocabulary, in
 * parity with the two natives.
 *
 * The web die has no driving detector (`driverIntelligence: false`), so `GeoPoint` is
 * the only domain value object with a real consumer here: the JS geofencing engine's
 * inside-region test.
 */
export class GeoPoint {
  /** Mean earth radius, matching the legacy `web.ts` constant and the natives. */
  static readonly EARTH_RADIUS_METERS = 6_371_000;

  constructor(
    readonly latitude: number,
    readonly longitude: number,
  ) {}

  /**
   * Great-circle distance in metres to `other` via the haversine formula. Symmetric and
   * zero for identical points. Preserves the legacy `distanceMeters` numerics exactly.
   */
  distanceTo(other: GeoPoint): number {
    const dLat = ((other.latitude - this.latitude) * Math.PI) / 180;
    const dLon = ((other.longitude - this.longitude) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) ** 2 +
      Math.cos((this.latitude * Math.PI) / 180) * Math.cos((other.latitude * Math.PI) / 180) * Math.sin(dLon / 2) ** 2;
    return 2 * GeoPoint.EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
  }
}
