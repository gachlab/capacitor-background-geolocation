// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.domain

import kotlin.math.abs

/**
 * A compass heading in degrees — the directional twin of [GeoPoint], for the `domain/`
 * layer (Roadmap Fase 1). The detector computed the angular difference between two
 * bearings inline and identically in two places (sharp-turn and phone-usage jitter),
 * and tracked the previous bearing as two loose mutable fields (prevBearingDeg +
 * hasPrevBearing). Modelling a heading as one value object moves the wrap-around delta
 * into the shared domain vocabulary — pure, no Android, trivially testable.
 */
@JvmInline
value class Heading(val degrees: Double) {
    /**
     * Smallest absolute angular difference to [other], in degrees (0..180). Handles the
     * 0/360 wrap-around, so e.g. 350° and 10° are 20° apart. Symmetric. Preserves the
     * legacy detector computation exactly (`abs(a - b)`, folded above 180°).
     */
    fun deltaTo(other: Heading): Double {
        var d = abs(other.degrees - degrees)
        if (d > 180) d = 360 - d
        return d
    }
}
