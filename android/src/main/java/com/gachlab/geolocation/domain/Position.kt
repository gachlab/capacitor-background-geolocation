// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

/**
 * A single location fix as the `domain/` layer sees it (Roadmap Fase 1) — a pure,
 * immutable kinematic snapshot decoupled from the platform `BGLocation`.
 *
 * The detector previously read the platform fix directly, decoding the
 * `hasSpeed`/`hasBearing` + sentinel pattern at every call site. Position models the
 * *absence* of a reading honestly as `null` ([speedMps]/[bearingDeg]) so domain logic
 * reasons about typed nullable kinematics instead of platform accessors. The
 * platform→domain mapping stays out of here (it would pull Android into the pure layer);
 * each die builds a Position from its own location type — which is what lets the web die
 * (Fase 4) reuse the same domain over its own fixes.
 */
data class Position(
    val point: GeoPoint,
    val speedMps: Double? = null,
    val bearingDeg: Double? = null,
    val provider: String? = null,
) {
    /** Speed in m/s, treating an absent reading as stationary (0.0). */
    val speedMpsOrZero: Double get() = speedMps ?: 0.0
}
