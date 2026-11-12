// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

import CoreLocation
import Foundation

/// Builds every `CLLocationManager` that has a delegate, on the main thread.
///
/// Core Location delivers delegate callbacks on the run loop of the thread that
/// CREATED the manager — not the one that set the delegate or started it. Capacitor
/// runs plugin calls on a background queue whose threads never run their run loop,
/// so a manager built there never hears back: no authorization change, no fix.
///
/// That is how a fresh iOS install hung after the driver granted location: the
/// permission request waited for `locationManagerDidChangeAuthorization`, which
/// had nowhere to arrive. The same shape left the one-shot fix always timing out.
///
/// A manager that is only read (`authorizationStatus`, `accuracyAuthorization`)
/// needs no delegate and may be built anywhere; it does not go through here.
public enum LocationManagerFactory {

    /// Test seam: what actually constructs the manager.
    static var construct: () -> CLLocationManager = { CLLocationManager() }

    /// A manager whose delegate callbacks will arrive.
    ///
    /// Off the main thread this waits for main with `sync`. That is safe for the
    /// callers below because none of them runs while main is waiting on them. The
    /// shared managers are the exception that could deadlock — main and a
    /// background thread both entering the same `static let` — which is why the
    /// plugin builds them on main in `load()`, before any call can race it.
    public static func make() -> CLLocationManager {
        if Thread.isMainThread {
            return construct()
        }
        return DispatchQueue.main.sync { construct() }
    }
}
