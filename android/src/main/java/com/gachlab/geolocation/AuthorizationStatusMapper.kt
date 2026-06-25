// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

/**
 * Maps Android runtime location-permission state to the cross-platform
 * `AuthorizationStatus` contract (see `src/definitions.ts`):
 *
 *  - 0 = NOT_AUTHORIZED         (no foreground location permission)
 *  - 1 = AUTHORIZED            (foreground + background — iOS "authorizedAlways")
 *  - 2 = AUTHORIZED_FOREGROUND (foreground only    — iOS "authorizedWhenInUse")
 *
 * Kept as a pure function so it is unit-testable on the JVM without the Android
 * framework.
 */
internal object AuthorizationStatusMapper {

    const val NOT_AUTHORIZED = 0
    const val AUTHORIZED = 1
    const val AUTHORIZED_FOREGROUND = 2

    fun status(foregroundGranted: Boolean, backgroundGranted: Boolean): Int = when {
        !foregroundGranted -> NOT_AUTHORIZED
        backgroundGranted  -> AUTHORIZED
        else               -> AUTHORIZED_FOREGROUND
    }
}
