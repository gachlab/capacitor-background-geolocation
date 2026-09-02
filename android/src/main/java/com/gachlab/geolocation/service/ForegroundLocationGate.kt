// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/**
 * Whether a `location`-typed foreground service may be started at all.
 *
 * Since Android 14 (API 34) `startForeground` on a service declared
 * `foregroundServiceType="location"` — which is what this plugin's manifest
 * declares — throws `SecurityException` unless the app holds ACCESS_FINE_LOCATION
 * or ACCESS_COARSE_LOCATION at that instant. The exception escapes
 * `onStartCommand`, so the platform wraps it as "Unable to start service" and the
 * driver gets the force-close dialog.
 *
 * That is not a hypothetical. Revoking the permission on a phone with an open
 * shift produces, in order: the platform kills the process (its own standard
 * behaviour on a runtime revoke), START_STICKY brings the service back, the
 * service tries to go foreground WITHOUT the permission it just lost, and it
 * crashes. START_STICKY brings it back again. Reproduced on an Android 14
 * emulator against GuestHub's driver app, twice in five seconds with two
 * different PIDs.
 *
 * The check is deliberately OUTSIDE `LocationService` so the reviver can ask the
 * same question. Both are doors into `startForeground`, and a guard on only one
 * of them leaves a fifteen-minute crash loop through the other.
 *
 * COARSE counts. It is degraded tracking, not absent tracking, and the platform
 * accepts either for the foreground-service type — refusing to start on coarse
 * alone would switch off a shift the system was willing to run.
 */
internal object ForegroundLocationGate {

    /**
     * `Context.checkSelfPermission`, not `packageManager.checkPermission`.
     *
     * The first version used the latter, to match `BackgroundGeolocationPlugin`'s
     * own `hasPermission`. Its own test caught the problem: that call answers
     * about the PACKAGE, so under Robolectric it reports every manifest-declared
     * permission as granted and the gate could never close. A guard whose closed
     * state cannot be exercised is a guard nobody can prove.
     *
     * `checkSelfPermission` asks the question the platform actually asks before
     * letting a `location`-typed service go foreground: does this process hold
     * the runtime grant right now. minSdk here is 23, so it is available
     * unconditionally.
     *
     * Never throws. A gate that fails closed on an unexpected error would stop
     * tracking for a reason that has nothing to do with permissions; a gate that
     * fails open is exactly the crash it exists to prevent. The error case is
     * treated as "not granted": the crash is the worse of the two.
     */
    @JvmStatic
    fun canStartLocationService(context: Context): Boolean =
        granted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            granted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun granted(context: Context, permission: String): Boolean = try {
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) {
        false
    }
}
