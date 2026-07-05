// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation

/**
 * Maps Android runtime location-permission state to the cross-platform
 * `AuthorizationStatus` contract (v3 `AuthorizationStatus` string union):
 *
 *  - "notAuthorized"        (no foreground location permission)
 *  - "authorized"           (foreground + background — iOS "authorizedAlways")
 *  - "authorizedForeground" (foreground only         — iOS "authorizedWhenInUse")
 *
 * `status()` keeps the internal 0/1/2 ordering; `text()` is the clean output the v3
 * bridge emits. Pure functions, unit-testable on the JVM without the Android framework.
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

    /** v3 clean output: the `AuthorizationStatus` string emitted over the bridge. */
    fun text(foregroundGranted: Boolean, backgroundGranted: Boolean): String =
        when (status(foregroundGranted, backgroundGranted)) {
            AUTHORIZED            -> "authorized"
            AUTHORIZED_FOREGROUND -> "authorizedForeground"
            else                  -> "notAuthorized"
        }
}
