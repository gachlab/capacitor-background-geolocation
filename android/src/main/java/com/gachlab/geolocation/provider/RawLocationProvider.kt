// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.provider

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.util.Log
import com.gachlab.geolocation.BGConfig

internal class RawLocationProvider(context: Context) :
    AbstractLocationProvider(context), LocationListener {

    private val locationManager by lazy {
        mContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
    // TWO facts, deliberately not one flag.
    //
    // `activeProviders` is what we are REGISTERED for; `isStarted` is whether any
    // of those can actually deliver right now. They used to be the same thing,
    // and separating them is the whole point of subscribing through a disabled
    // provider — but the first attempt kept every guard hanging off `isStarted`
    // while changing what it meant, which broke three of them at once: `onStop()`
    // stopped unregistering, `onConfigure()` stopped applying, and the flag could
    // never climb back to true. Registration guards read `activeProviders`;
    // only "can it deliver" reads `isStarted`.
    private var isStarted = false
    private val activeProviders = mutableListOf<String>()

    /** Registered with the system, regardless of whether anything is delivering. */
    private val registered: Boolean get() = activeProviders.isNotEmpty()

    /**
     * Set by `onStop()` so a provider callback already queued on the looper
     * cannot re-register after the service let go of us.
     *
     * `isStarted` cannot do this job any more: it is false whenever every
     * provider is switched off, which is a perfectly live state we must be able
     * to recover from. `LocationService.stop()` drops its provider reference
     * immediately after stopping, so anything that re-registers past that point
     * keeps the GPS on with nothing left that could ever turn it off.
     */
    private var stopped = false

    override fun onCreate() {
        super.onCreate()
        PROVIDER_ID = BGConfig.RAW_PROVIDER
    }

    override fun onStart() {
        // Guarded on REGISTRATION, not on delivery. With `isStarted` here, a
        // shift opened while location was off looked "not started" and every
        // later call would tear down and rebuild a subscription that was already
        // in place.
        if (registered) return
        val cfg = mConfig ?: run { Log.w(TAG, "Started without config"); return }
        stopped = false
        // Subscribe to what we WANT, not to what happens to be switched on right
        // now. `requestLocationUpdates` on a provider that exists but is disabled
        // is legal: it delivers nothing until the provider comes up, and then
        // calls `onProviderEnabled` on this very listener.
        //
        // Filtering by `isProviderEnabled` here was the trap. A driver opening
        // the shift with GPS off got no GPS subscription, and with everything off
        // got no listener at all — so `onProviderEnabled` could never fire and
        // there was nothing left alive to notice the provider coming back. The
        // tracker stayed silent for the whole shift and only recovered when the
        // service itself restarted, which for this app means when a human opened
        // it hours later.
        val providers = desiredProviders()
        if (providers.isEmpty()) {
            Log.w(TAG, "No location provider available")
            return
        }
        activeProviders.clear()
        providers.forEach { subscribe(it, cfg) }
        if (pickProviders().isEmpty()) {
            // Subscribed, but nothing is switched on yet. Say so: a shift that
            // starts with location services off must not look healthy.
            Log.w(TAG, "Subscribed but every provider is currently disabled")
        }
        // `isStarted` means SOME provider can actually deliver, not "we managed
        // to register". Now that we subscribe through disabled providers, the two
        // are different — and `LocationService.checkWatchdog` gates its restart on
        // this flag, so reporting true with everything switched off turns the
        // watchdog into an infinite restart loop: restart, still no fix, gate
        // still open, restart. Once a minute, forever, each round overwriting the
        // persisted kill reason and firing a `serviceRestarted` at JavaScript.
        isStarted = pickProviders().isNotEmpty()
    }

    override fun onStop() {
        stopped = true
        // Unregister whenever we ARE registered. Hanging this off `isStarted`
        // meant that a shift opened with location switched off never released the
        // system listener: the driver closed the shift, the service dropped its
        // reference, and the GPS stayed on with a listener nobody could reach —
        // one more of them per shift.
        if (!registered) return
        try { locationManager.removeUpdates(this) }
        catch (e: SecurityException) { handleSecurityException(e) }
        finally { activeProviders.clear(); isStarted = false }
    }

    override fun onDestroy() { onStop() }

    override fun isStarted() = isStarted

    override fun onConfigure(config: BGConfig) {
        super.onConfigure(config)
        // On REGISTRATION again: a new interval or accuracy has to reach the
        // system whether or not a provider happens to be delivering. Gated on
        // `isStarted`, a shift running with location off silently ignored every
        // reconfiguration for the rest of the shift, leaving `mConfig` and the
        // live subscription describing different things.
        if (registered) { onStop(); onStart() }
    }

    override fun onLocationChanged(location: Location) = handleLocation(location)
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    /**
     * Re-subscribe when a provider comes back. This used to only log, and that
     * one line was a silent, permanent blackout.
     *
     * `pickProviders()` filters by `isProviderEnabled` and was evaluated ONCE,
     * in `onStart()`. Anything switched off at that moment — the driver opening
     * the shift with GPS disabled, a few seconds of airplane mode, the OS
     * dropping a provider — was never subscribed, and nothing here ever tried
     * again. Worse, when every provider was off at start, `activeProviders` came
     * back empty and `isStarted` stayed false, so the early return at the top of
     * `onStart()` did not even protect the retry: there was no retry.
     *
     * From the outside that is a tracker that never reports and only recovers
     * when the service itself restarts — which, for a shuttle app, means when a
     * human opens the app hours later. EB Miami went 611 minutes exactly like
     * that on 2026-08-04.
     *
     * It registers only the provider that was missing — never a tear-down and
     * rebuild, which from inside a callback re-enters. `subscribe()` is
     * idempotent, so a duplicate callback cannot stack two registrations.
     */
    override fun onProviderEnabled(provider: String) {
        // Only when this provider is not already registered. Android calls
        // `onProviderEnabled` for providers that are ALREADY on at the moment we
        // register, so acting unconditionally meant subscribe → callback →
        // resubscribe → callback: an unbounded loop. It showed up first as an
        // OOM in the unit-test JVM, which is a far cheaper place to find it than
        // a driver's phone.
        // Not after `onStop()`. `removeUpdates` does not un-post callbacks already
        // on the looper, and `LocationService.stop()` drops its provider reference
        // right afterwards — so a late callback that re-registered here would keep
        // the GPS on with nothing left able to turn it off.
        if (stopped) return

        // The flag is recalculated FIRST, before the idempotence return.
        //
        // This ordering is the fix. `onStart()` registers every desired provider,
        // so `provider in activeProviders` is true for essentially every callback
        // that can arrive — which meant the recalculation below the return was
        // unreachable, and a shift opened with location switched off could never
        // report itself as delivering again. The body was dead exactly when it
        // had work to do.
        isStarted = pickProviders().isNotEmpty()

        if (provider in activeProviders) return
        val cfg = mConfig ?: return
        Log.i(TAG, "$provider enabled — subscribing")
        subscribe(provider, cfg)
        isStarted = pickProviders().isNotEmpty()
    }

    override fun onProviderDisabled(provider: String) {
        Log.w(TAG, "$provider disabled")
        // Keep the flag honest in this direction too. Only `onStart()` used to
        // maintain it, so a provider going down mid-shift left `isStarted` true
        // with nothing able to deliver — the same lie as before, mirrored.
        isStarted = pickProviders().isNotEmpty()
        if (pickProviders().isEmpty()) handleServiceError("Location provider disabled and no fallback.")
    }

    /**
     * Register ONE provider, the only place that decides how.
     *
     * Both the initial subscription and the recovery go through here so the
     * interval, the distance filter and the error handling cannot drift between
     * "the first time" and "after the provider came back".
     *
     * Deliberately NOT a tear-down-and-rebuild: `onStop()` + `onStart()` from
     * inside a provider callback re-enters, because registering can deliver
     * `onProviderEnabled` synchronously. The first attempt at this fix did
     * exactly that and blew the unit-test JVM's heap — a far cheaper place to
     * find it than a driver's phone.
     */
    private fun subscribe(provider: String, cfg: BGConfig) {
        if (provider in activeProviders) return
        // Marked BEFORE the call, not after. `requestLocationUpdates` can deliver
        // `onProviderEnabled` synchronously, and with the mark set afterwards the
        // re-entrancy guard is not armed yet when that callback lands — which is
        // the unbounded recursion this method's shape exists to prevent. Removed
        // again in the catch blocks so a failed registration leaves no ghost.
        activeProviders += provider
        try {
            locationManager.requestLocationUpdates(
                provider,
                cfg.interval?.toLong() ?: BGConfig.DEFAULT_INTERVAL.toLong(),
                (cfg.distanceFilter ?: BGConfig.DEFAULT_DISTANCE_FILTER).toFloat(),
                this,
                // EXPLICIT, and the whole of #60. The four-argument overload binds
                // delivery to `Looper.myLooper()` — the CALLING thread — so the
                // subscription lived exactly as long as whoever happened to
                // subscribe. `onConfigure()` re-subscribes, host apps call
                // `configure()` from JavaScript, and Capacitor runs a
                // `@PluginMethod` off the main thread: swiping the app out of
                // recents took that thread down and every fix afterwards died at
                // the executor boundary with `RejectedExecutionException` — a
                // service that looked perfectly healthy and measured nothing.
                //
                // The MAIN looper because the service owns it, not the caller: it
                // outlives every bridge thread, `LocationService` already runs its
                // watchdog and heartbeat there, and both GMS providers already
                // pass it explicitly. So this is also what stops one provider out
                // of three from inheriting the lifetime of an accident.
                Looper.getMainLooper(),
            )
        } catch (e: SecurityException) {
            activeProviders -= provider
            handleSecurityException(e)
        } catch (e: IllegalArgumentException) {
            activeProviders -= provider
            Log.w(TAG, "requestLocationUpdates($provider) failed: ${e.message}")
        }
    }

    /**
     * The providers this configuration wants and the device actually has,
     * regardless of whether they are switched on at this instant.
     *
     * Existence is checked against `allProviders` because
     * `requestLocationUpdates` throws `IllegalArgumentException` for a provider
     * the device does not have — that one is a real error, while "disabled right
     * now" is a temporary state we want to subscribe through.
     */
    private fun desiredProviders(): List<String> {
        val desired = mConfig?.desiredAccuracy ?: 100
        // Null-safe AND throw-safe: on the unit-test android.jar these members
        // are stubs that may return null or throw, and a provider that cannot
        // enumerate the device must degrade to "subscribe to nothing", never to
        // an exception escaping into the service's start path.
        val existing: List<String> =
            try { locationManager.allProviders ?: emptyList() } catch (_: Throwable) { emptyList() }
        val result = mutableListOf<String>()
        if (desired < 1000 && LocationManager.GPS_PROVIDER in existing) result += LocationManager.GPS_PROVIDER
        if (desired >= 10 && LocationManager.NETWORK_PROVIDER in existing) result += LocationManager.NETWORK_PROVIDER
        // Same last-resort widening as `pickProviders`: better an unwanted
        // provider than no position at all.
        if (result.isEmpty()) {
            if (LocationManager.GPS_PROVIDER in existing) result += LocationManager.GPS_PROVIDER
            else if (LocationManager.NETWORK_PROVIDER in existing) result += LocationManager.NETWORK_PROVIDER
        }
        return result
    }

    /** Which of the desired providers are enabled RIGHT NOW. Only for reporting
     *  and for the no-fallback error — never for deciding what to subscribe to. */
    private fun pickProviders(): List<String> {
        val desired = mConfig?.desiredAccuracy ?: 100
        val wantGps = desired < 1000
        val wantNet = desired >= 10
        val gpsOn = try { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
        val netOn = try { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { false }
        val result = mutableListOf<String>()
        if (wantGps && gpsOn) result += LocationManager.GPS_PROVIDER
        if (wantNet && netOn) result += LocationManager.NETWORK_PROVIDER
        if (result.isEmpty()) {
            if (gpsOn) result += LocationManager.GPS_PROVIDER
            else if (netOn) result += LocationManager.NETWORK_PROVIDER
        }
        return result
    }

    companion object {
        private const val TAG = "RawLocationProvider"
    }
}
