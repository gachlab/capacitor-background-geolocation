// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.service

import com.gachlab.geolocation.domain.TripConfig

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.buffer.PositionBuffer
import com.gachlab.geolocation.BGLocation
import com.gachlab.geolocation.BGLog
import com.gachlab.geolocation.DrivingEventsDetector
import com.gachlab.geolocation.NotificationHelper
import com.gachlab.geolocation.SensorFusionDetector
import com.gachlab.geolocation.ServiceEvent
import com.gachlab.geolocation.persistence.ConfigDAO
import com.gachlab.geolocation.persistence.LocationDAO
import com.gachlab.geolocation.persistence.SessionDAO
import com.gachlab.geolocation.network.BackgroundSync
import com.gachlab.geolocation.network.PostLocationTask
import com.gachlab.geolocation.ports.ConfigRepository
import com.gachlab.geolocation.ports.LocationPublisher
import com.gachlab.geolocation.network.PrioritySyncManager
import com.gachlab.geolocation.network.ShiftGoneDetector
import com.gachlab.geolocation.provider.AbstractLocationProvider
import com.gachlab.geolocation.provider.ActivityLocationProvider
import com.gachlab.geolocation.provider.BGException
import com.gachlab.geolocation.provider.DistanceFilterLocationProvider
import com.gachlab.geolocation.provider.RawLocationProvider

class LocationService : Service() {

    companion object {
        // Numeric MSG constants — kept for external consumers / documentation parity.
        const val MSG_ON_ERROR                      = 100
        const val MSG_ON_LOCATION                   = 101
        const val MSG_ON_STATIONARY                 = 102
        const val MSG_ON_ACTIVITY                   = 103
        const val MSG_ON_SERVICE_STARTED            = 104
        const val MSG_ON_SERVICE_STOPPED            = 105
        const val MSG_ON_ABORT_REQUESTED            = 106
        const val MSG_ON_HTTP_AUTHORIZATION         = 107
        const val MSG_ON_SYNC_START                 = 108
        const val MSG_ON_SYNC_SUCCESS               = 109
        const val MSG_ON_SYNC_ERROR                 = 110
        const val MSG_ON_SYNC_PROGRESS              = 111
        const val MSG_ON_HEARTBEAT                  = 112
        const val MSG_ON_TRIP_START                 = 113
        const val MSG_ON_TRIP_END                   = 114
        const val MSG_ON_MOVING                     = 115
        const val MSG_ON_STOPPED                    = 116
        const val MSG_ON_SPEEDING                   = 117
        const val MSG_ON_PROVIDER_CHANGE            = 118
        const val MSG_ON_SOS                        = 119
        const val MSG_ON_HARD_BRAKE                 = 120
        const val MSG_ON_RAPID_ACCELERATION         = 121
        const val MSG_ON_SHARP_TURN                 = 122
        const val MSG_ON_POSSIBLE_CRASH             = 123
        const val MSG_ON_PHONE_USAGE_WHILE_DRIVING  = 124
        const val MSG_ON_SERVICE_RESTARTED          = 125

        /**
         * Direct same-process event listener. Set by BGFacade before starting the service.
         * Avoids LocalBroadcastManager (deprecated) and Bundle/Parcelable overhead.
         */
        @Volatile var eventListener: ((ServiceEvent) -> Unit)? = null

        /** Live service instance for same-process method calls. Null when service is stopped. */
        @Volatile var instance: LocationService? = null

        const val EXTRA_START_REASON = "start_reason"

        /**
         * Retire a shift the server has forgotten (#63).
         *
         * Two callers, one of which has no service to talk to: `PostLocationTask`
         * runs inside the service, while `BackgroundSync` is a WorkManager worker
         * that may run when nothing is started. Both must produce the SAME end
         * state, so it lives here rather than at either call site.
         *
         * `stop()` already does the right thing when there is an instance: it
         * stands the reviver down, cancels its work, tears the provider down and
         * retires the foreground notification. The branch below it is the one
         * that matters — with no instance, clearing `shouldBeRunning` by hand is
         * the only thing standing between us and the reviver starting the shift
         * again in fifteen minutes to be 404ed some more. That is the same
         * "second door" that made #59 a loop.
         *
         * The reason is persisted BEFORE stopping, because `stop()` fires
         * `ServiceStopped` and a listener reading the reason must find it there.
         */
        /**
         * Record that a start was refused for want of the location permission.
         *
         * For the callers that never reach the service at all. The guard inside
         * `start()` cannot help them: by the time it runs, `startForegroundService`
         * has already been called and the app is committed to going foreground
         * within a few seconds — a promise a service without the permission
         * cannot keep either way (#59).
         *
         * `shouldBeRunning` is deliberately untouched. The shift is still a shift
         * that should be running; what changed is that it cannot be right now, and
         * clearing the flag would leave the driver with a shift nothing resumes
         * when the permission comes back.
         */
        @JvmStatic
        fun recordPermissionLost(context: Context) {
            val app = context.applicationContext
            app.getSharedPreferences(PREFS_DIAG, MODE_PRIVATE).edit()
                .putString(KEY_KILL_REASON, ServiceEvent.REASON_PERMISSION_LOST)
                .putLong(KEY_KILL_AT, System.currentTimeMillis())
                .apply()
            BGLog.w("location permission is gone — refusing to start the service")
            eventListener?.invoke(ServiceEvent.ServiceRestarted(ServiceEvent.REASON_PERMISSION_LOST))
        }

        @JvmStatic
        fun retireShiftGone(context: Context) {
            val app = context.applicationContext
            app.getSharedPreferences(PREFS_DIAG, MODE_PRIVATE).edit()
                .putString(KEY_KILL_REASON, ServiceEvent.REASON_SHIFT_GONE)
                .putLong(KEY_KILL_AT, System.currentTimeMillis())
                .apply()
            BGLog.w("server reports the shift no longer exists — retiring tracking")
            eventListener?.invoke(ServiceEvent.ServiceRestarted(ServiceEvent.REASON_SHIFT_GONE))

            val live = instance
            if (live != null) {
                live.stop()
            } else {
                // No service to stop, but the net is still armed. Leaving it is
                // what would turn "the shift is gone" into a fifteen-minute loop.
                ServiceReviver.setShouldBeRunning(app, false)
                ServiceReviver.cancel(app)
            }
        }

        private const val TAG             = "LocationService"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG   = "gachlab:bgloc"
        private const val PREFS_DIAG      = "bgloc_diagnostics"
        private const val KEY_KILL_REASON = "last_kill_reason"
        private const val KEY_KILL_AT     = "last_kill_at"
        // Stamped while the service is alive, so a death that never reached a
        // shutdown path can still be dated by the last moment we know it ran.
        private const val KEY_ALIVE_AT    = "last_alive_at"
    }

    private var config: BGConfig? = null
    private var provider: AbstractLocationProvider? = null
    private var postTask: LocationPublisher? = null
    private var prioritySyncManager: PrioritySyncManager? = null
    private var drivingDetector: DrivingEventsDetector? = null
    private var sensorDetector: SensorFusionDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile var isRunning = false
        private set
    private val buffer = PositionBuffer.shared

    private lateinit var locationDAO: LocationDAO
    private lateinit var sessionDAO: SessionDAO
    private lateinit var configDAO: ConfigRepository
    private lateinit var mainHandler: Handler

    private val watchdogRunnable = Runnable { checkWatchdog() }
    private val heartbeatRunnable = Runnable { fireHeartbeat() }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance    = this
        buffer.clear()   // fresh lifecycle starts with no cached fix (matches the old per-instance field)
        BGLog.init(applicationContext)
        locationDAO = LocationDAO(applicationContext)
        sessionDAO  = SessionDAO(applicationContext)
        configDAO   = ConfigDAO(applicationContext)
        mainHandler = Handler(Looper.getMainLooper())
        NotificationHelper.registerAllChannels(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when {
            intent == null -> {
                // OS restarted us via START_STICKY after a kill.
                restartedByOs = true
                persistKillReason(ServiceEvent.REASON_SYSTEM_KILL)
                fire(ServiceEvent.ServiceRestarted(ServiceEvent.REASON_SYSTEM_KILL))
            }
            intent.getStringExtra(EXTRA_START_REASON) == ServiceEvent.REASON_BOOT -> {
                restartedByOs = true
                persistKillReason(ServiceEvent.REASON_BOOT)
                fire(ServiceEvent.ServiceRestarted(ServiceEvent.REASON_BOOT))
            }
        }
        if (!isRunning) start()
        // START_STICKY is what the platform honours, so returning it while the
        // permission is gone asks to be restarted into the same SecurityException.
        // `isRunning` is the reading that matters and not the gate again: start()
        // has just run and left it false for exactly one reason.
        if (!isRunning) return START_NOT_STICKY
        val cfg = config ?: configDAO.retrieveConfig() ?: BGConfig.getDefault()
        return if (cfg.restartOnKill != false) START_STICKY else START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stop()
        instance = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val cfg = config
        if (cfg?.stopOnTerminate == true) { stop(); stopSelf() }
        else {
            // Persisted as well as fired. The event goes to a WebView that has
            // just been destroyed with the task, so nobody receives it — which
            // meant `appRemoved` was one of four published reasons that
            // `killReason()` could never return. The reason is only useful to
            // whoever opens the app NEXT, and that is the door that reads
            // preferences.
            persistKillReason(ServiceEvent.REASON_APP_REMOVED)
            fire(ServiceEvent.ServiceRestarted(ServiceEvent.REASON_APP_REMOVED))
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** True while the current start came from a restart path that already
     *  recorded why the previous run ended. */
    private var restartedByOs = false

    /**
     * When this run began, used by the watchdog as the baseline before the first
     * fix exists — and moved forward on every watchdog restart so the restart
     * itself becomes what is being timed.
     */
    private var runStartedAtMs = 0L

    @Synchronized
    fun start() {
        if (isRunning) return

        // BEFORE anything else, and in particular before `setShouldBeRunning`
        // below (#59). Going foreground with type `location` and no location
        // permission throws SecurityException out of `onStartCommand`, which the
        // platform turns into the driver's force-close dialog.
        //
        // Placing the check further down — say, just above `startForeground` —
        // would stop the crash and leave the loop: by then the service has
        // already told the reviver it SHOULD be running, so the out-of-process
        // net would keep bringing it back every fifteen minutes to crash again.
        // The order is the fix, not the condition.
        //
        // `shouldBeRunning` is deliberately left as it is rather than cleared:
        // the shift was supposed to be running and still is. What changed is
        // that it cannot be, and the reviver asks this same gate before acting,
        // so nothing spins. Re-granting the permission puts the driver straight
        // back to work.
        if (!ForegroundLocationGate.canStartLocationService(applicationContext)) {
            persistKillReason(ServiceEvent.REASON_PERMISSION_LOST)
            fire(ServiceEvent.ServiceRestarted(ServiceEvent.REASON_PERMISSION_LOST))
            BGLog.w("location permission is gone — not starting the foreground service")
            Log.w(TAG, "start() refused: no location permission")
            stopSelf()
            return
        }

        val cfg = configDAO.retrieveConfig() ?: BGConfig.getDefault()
        config = cfg
        isRunning = true
        // A new shift must never inherit the previous one's run of 404s (#63).
        // The detector is in-memory and process-wide, so this is the one place
        // that can honestly say "that evidence belonged to something else".
        ShiftGoneDetector.reset()
        // Only on a CLEAN start. `onStartCommand` writes the reason and then calls
        // start(), so clearing unconditionally here would wipe the very record it
        // had just made — the death would be forgotten a millisecond after being
        // observed. A restart path owns its reason; a user-initiated start is the
        // moment the previous one stops being news.
        runStartedAtMs = System.currentTimeMillis()
        // Arm the out-of-process net and record that tracking is SUPPOSED to be
        // on. That second fact never existed anywhere: `startOnBoot` was standing
        // in for it, which is why a reboot after a driver stopped tracking used to
        // start reporting the location of someone off duty.
        ServiceReviver.setShouldBeRunning(applicationContext, true)
        ServiceReviver.schedule(applicationContext)
        if (!restartedByOs) clearKillReason()
        restartedByOs = false
        markAlive()

        if (cfg.startForeground == true) {
            startForeground(NOTIFICATION_ID,
                NotificationHelper.buildServiceNotification(applicationContext, cfg))
        }

        if (cfg.wakeLockMode == "always") acquireWakeLock()

        val p = createProvider(cfg)
        p.onCreate(); p.onConfigure(cfg); p.setDelegate(providerDelegate)
        provider = p; p.onStart()

        postTask = makePostTask(cfg)
        configurePrioritySync(cfg)
        configureDrivingDetector(cfg)
        scheduleWatchdog(cfg)
        scheduleHeartbeat(cfg)

        fire(ServiceEvent.ServiceStarted)
        Log.i(TAG, "LocationService started (provider=${cfg.locationProvider})")
    }

    @Synchronized
    fun stop() {
        if (!isRunning) return
        isRunning = false
        // Stood down deliberately: the net must not resurrect a shift the driver
        // ended. This is the difference between "it died" and "it was stopped",
        // and it is the whole reason the reviver reads a state flag rather than a
        // configuration preference.
        ServiceReviver.setShouldBeRunning(applicationContext, false)
        ServiceReviver.cancel(applicationContext)

        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.removeCallbacks(heartbeatRunnable)

        provider?.onStop(); provider?.onDestroy(); provider = null
        postTask?.shutdown(); postTask = null
        prioritySyncManager?.destroy(); prioritySyncManager = null
        drivingDetector?.reset(); drivingDetector = null
        sensorDetector?.stop(); sensorDetector = null

        releaseWakeLock()
        @Suppress("DEPRECATION") stopForeground(true)

        fire(ServiceEvent.ServiceStopped)
        Log.i(TAG, "LocationService stopped")
    }

    @Synchronized
    fun configure(newConfig: BGConfig) {
        val merged = BGConfig.merge(config ?: BGConfig.getDefault(), newConfig)
        config = merged
        configDAO.persistConfig(merged)
        provider?.onConfigure(merged)
        // Swap FIRST, shut the old one down after. `configure()` runs on the
        // Capacitor bridge thread while `handleLocation()` reads `postTask` on the
        // main looper without synchronisation, so a fix landing between the
        // shutdown and the reassignment used to hit `submit` on a terminated
        // executor: `RejectedExecutionException`, uncaught, on the main looper —
        // process dead. Reversing the order means the worst case is a fix handed
        // to the outgoing task, which still has a live executor.
        val previousTask = postTask
        postTask = makePostTask(merged)
        previousTask?.shutdown()
        configurePrioritySync(merged)
        configureDrivingDetector(merged)
        // Re-armed here, exactly like the heartbeat below. Without it the
        // watchdog could only ever be turned on by a restart of the service.
        scheduleWatchdog(merged)

        when (merged.wakeLockMode) {
            "always" -> { if (wakeLock?.isHeld != true) acquireWakeLock() }
            "none"   -> releaseWakeLock()
        }
        mainHandler.removeCallbacks(heartbeatRunnable)
        scheduleHeartbeat(merged)
    }

    fun switchMode(mode: Int) { provider?.onCommand(AbstractLocationProvider.CMD_SWITCH_MODE, mode) }

    fun triggerSync(forced: Boolean) {
        val work = OneTimeWorkRequestBuilder<BackgroundSync>()
            .setInputData(Data.Builder().putBoolean(BackgroundSync.KEY_FORCED, forced).build())
            .addTag(BackgroundSync.WORK_TAG)
            // Do not run a batch upload with no connectivity: without this every
            // enqueued worker wakes, reads the whole pending table into memory and
            // fails, which is the most expensive possible way to discover there is
            // no network.
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // UNIQUE, and existing work wins.
        //
        // `PostLocationTask` calls `checkSyncThreshold()` on every failed and every
        // offline POST, so once `pending >= syncThreshold` the condition holds
        // permanently: one worker enqueued PER FIX. With the backend down for
        // forty minutes and a driver reporting every fifteen seconds that is ~160
        // workers, and each one reads the same ~160 pending rows and POSTs the
        // entire batch — the server receives the same batch dozens of times, and
        // every worker hydrates up to `maxLocations` rows into memory to do it.
        //
        // KEEP rather than REPLACE: a flush already in flight is doing exactly the
        // work we want, and cancelling it to start again is how a queue never
        // drains.
        WorkManager.getInstance(applicationContext)
            .enqueueUniqueWork(BackgroundSync.WORK_TAG, ExistingWorkPolicy.KEEP, work)
    }

    // The last known fix rides along: the service is the only place that has it,
    // and without it the alert reaches the dispatcher with no coordinates.
    fun triggerSOS(locationId: Long?, payload: org.json.JSONObject? = null) {
        fire(ServiceEvent.Sos(locationId, payload, buffer.lastFix))
    }

    // ── Provider delegate ─────────────────────────────────────────────────────

    private val providerDelegate = object : AbstractLocationProvider.Delegate {
        override fun onLocation(location: BGLocation) = handleLocation(location)
        override fun onStationary(location: BGLocation, radius: Float) = handleStationary(location, radius)
        override fun onError(error: BGException) = fire(ServiceEvent.Error(error.message ?: "", error.code))
    }

    private fun handleLocation(loc: BGLocation) {
        val prev = buffer.lastFix
        buffer.record(loc, System.currentTimeMillis())

        // Emulators (`adb emu geo fix`) and some low-end chipsets report speed=0
        // with hasSpeed=true, or omit speed/bearing entirely. Derive both from
        // consecutive-fix displacement so the driving detector works correctly.
        if (prev != null) {
            val prevL = prev.getLocation()
            val currL = loc.getLocation()
            val dt = (loc.time - prev.time) / 1000.0
            if (dt in 0.5..30.0) {
                val dist = prevL.distanceTo(currL)
                if (!loc.hasSpeed || loc.speed == 0f)
                    loc.speed = (dist / dt).toFloat()
                if ((!loc.hasBearing || loc.bearing == 0f) && dist > 1.0f)
                    loc.bearing = prevL.bearingTo(currL)
            }
        }

        drivingDetector?.onLocation(loc)
        sensorDetector?.lastLocation = loc
        attachBattery(loc)

        if (config?.wakeLockMode == "posting") acquirePostingWakeLock()

        fire(ServiceEvent.Location(loc))
        postTask?.add(loc)
    }

    private fun handleStationary(loc: BGLocation, radius: Float) {
        attachBattery(loc)
        fire(ServiceEvent.Stationary(loc, radius))
    }

    // ── Driving detector ──────────────────────────────────────────────────────

    private fun configureDrivingDetector(cfg: BGConfig) {
        val opts = cfg.drivingEvents ?: run {
            drivingDetector?.reset(); drivingDetector = null
            sensorDetector?.stop(); sensorDetector = null
            return
        }
        // Sensor-based crash detection (TYPE_LINEAR_ACCELERATION), fused with the GPS
        // crash path below — both can fire, distinguished by PossibleCrash.source.
        if (opts.enabled) {
            val sd = sensorDetector ?: SensorFusionDetector(applicationContext).also {
                it.listener = sensorCrashListener; sensorDetector = it
            }
            sd.crashImpactG    = opts.crashImpactG
            sd.crashCooldownMs = opts.sensorCrashCooldownMs
            // Phone-usage by sensor runs only under sensorFusion; the GPS bearing-jitter
            // path in DrivingEventsDetector owns it otherwise (gated on !sensorFusion).
            sd.sensorFusion        = opts.sensorFusion
            sd.phoneUsageWindowMs  = opts.phoneUsageWindowMs
            sd.phoneUsageCooldownMs = opts.phoneUsageCooldownMs
            sd.lastLocation    = buffer.lastFix
            sd.start()
        } else {
            sensorDetector?.stop(); sensorDetector = null
        }
        val detectorCfg = TripConfig(
            enabled              = opts.enabled,
            speedLimitKmh        = opts.speedLimitKmh,
            minMovingSpeedMps    = opts.minMovingSpeedMps,
            stoppedDurationMs    = opts.stoppedDurationMs,
            minTripSpeedMps      = opts.minTripSpeedMps,
            minTripDurationMs    = opts.minTripDurationMs,
            hardBrakeMps2        = opts.hardBrakeMps2,
            rapidAccelMps2       = opts.rapidAccelMps2,
            sharpTurnDegPerSec   = opts.sharpTurnDegPerSec,
            crashImpactKmh       = opts.crashImpactKmh,
            crashWindowMs        = opts.crashWindowMs,
            idleThresholdMs      = opts.idleThresholdMs,
            idleEndThresholdMs   = opts.idleEndThresholdMs,
            scoringWeights       = opts.scoringWeights,
            crashConfirmWindowMs = opts.crashConfirmWindowMs,
            sensorFusion         = opts.sensorFusion,
            phoneUsageWindowMs   = opts.phoneUsageWindowMs,
            phoneUsageCooldownMs = opts.phoneUsageCooldownMs,
        )
        if (drivingDetector == null) drivingDetector = DrivingEventsDetector(drivingListener)
        drivingDetector!!.setConfig(detectorCfg)
    }

    private val drivingListener = object : DrivingEventsDetector.Listener {
        override fun onMoving(loc: BGLocation)        { Log.i(TAG, "driving-event: moving");   fire(ServiceEvent.Moving(loc)) }
        override fun onStopped(loc: BGLocation)       { Log.i(TAG, "driving-event: stopped");  fire(ServiceEvent.Stopped(loc)) }
        override fun onTripStart(loc: BGLocation)     {
            Log.i(TAG, "driving-event: tripStart"); sensorDetector?.tripActive = true
            fire(ServiceEvent.TripStart(loc))
        }
        override fun onTripEnd(loc: BGLocation, journey: com.gachlab.geolocation.domain.Journey) {
            Log.i(TAG, "driving-event: tripEnd dist=${journey.distanceMeters.toInt()}m dur=${journey.durationMs}ms")
            sensorDetector?.tripActive = false
            fire(ServiceEvent.TripEnd(loc, journey))
        }
        override fun onIdleStart(loc: BGLocation, startedAt: Long) =
            fire(ServiceEvent.IdleStart(loc, startedAt))
        override fun onIdleEnd(loc: BGLocation, durationMs: Long, startedAt: Long) =
            fire(ServiceEvent.IdleEnd(loc, durationMs, startedAt))
        override fun onSpeeding(loc: BGLocation, speedKmh: Double, limitKmh: Double) =
            fire(ServiceEvent.Speeding(loc, speedKmh, limitKmh))
        override fun onProviderChange(provider: String) = fire(ServiceEvent.ProviderChange(provider))
        override fun onHardBrake(loc: BGLocation, decelMps2: Double) {
            loc.addDrivingEvent("hardBrake"); fire(ServiceEvent.HardBrake(loc, decelMps2))
        }
        override fun onRapidAcceleration(loc: BGLocation, accelMps2: Double) {
            loc.addDrivingEvent("rapidAcceleration"); fire(ServiceEvent.RapidAcceleration(loc, accelMps2))
        }
        override fun onSharpTurn(loc: BGLocation, degPerSec: Double) {
            loc.addDrivingEvent("sharpTurn"); fire(ServiceEvent.SharpTurn(loc, degPerSec))
        }
        override fun onPossibleCrash(loc: BGLocation, velocityDropKmh: Double) {
            Log.i(TAG, "driving-event: possibleCrash drop=${velocityDropKmh.toInt()}kmh")
            loc.addDrivingEvent("possibleCrash"); fire(ServiceEvent.PossibleCrash(loc, velocityDropKmh, "gps"))
        }
        override fun onPhoneUsageWhileDriving(loc: BGLocation) {
            Log.i(TAG, "driving-event: phoneUsageWhileDriving")
            loc.addDrivingEvent("phoneUsageWhileDriving"); fire(ServiceEvent.PhoneUsageWhileDriving(loc))
        }
    }

    /** Sensor-based crash + phone usage. Crash is fused with the GPS path via source;
     *  phone usage mirrors iOS (sensorFusion only) — emits the event, like its GPS twin. */
    private val sensorCrashListener = object : SensorFusionDetector.Listener {
        override fun onCrash(impactG: Double, location: BGLocation?) {
            val loc = location ?: buffer.lastFix ?: return
            Log.i(TAG, "sensor-event: possibleCrash impactG=${"%.1f".format(impactG)}")
            loc.addDrivingEvent("possibleCrash")
            fire(ServiceEvent.PossibleCrash(loc, impactG, "sensor"))
        }
        override fun onPhoneUsageWhileDriving(location: BGLocation?) {
            val loc = location ?: buffer.lastFix ?: return
            Log.i(TAG, "sensor-event: phoneUsageWhileDriving")
            loc.addDrivingEvent("phoneUsageWhileDriving")
            // Feed the trip score (the GPS jitter path is gated off under sensorFusion,
            // so this is the only phone-usage source when sensor fusion is on).
            drivingDetector?.recordExternalPhoneUsage(loc)
            fire(ServiceEvent.PhoneUsageWhileDriving(loc))
        }
    }

    // ── Watchdog ──────────────────────────────────────────────────────────────

    /**
     * (Re)arm the watchdog. Safe to call at any time, including from `configure()`.
     *
     * It used to be reachable only from `start()`, so `configure({ enableWatchdog:
     * true })` on a running service was accepted, persisted and returned by
     * `getConfig()` while scheduling nothing — for the life of that run. Turning
     * it off at runtime did not work either, because `checkWatchdog` never re-read
     * the flag.
     */
    private fun scheduleWatchdog(cfg: BGConfig) {
        mainHandler.removeCallbacks(watchdogRunnable)
        if (cfg.enableWatchdog != true) return
        mainHandler.postDelayed(watchdogRunnable, cfg.watchdogIntervalMs ?: 60_000L)
    }

    private fun checkWatchdog() {
        val cfg = config ?: return
        if (!isRunning) return
        // Re-read the flag every tick, so disabling it at runtime actually stops it.
        if (cfg.enableWatchdog != true) return
        val interval = cfg.watchdogIntervalMs ?: 60_000L

        // `since` is the last fix OR, when there has never been one, the moment
        // this run began.
        //
        // The old guard was `ts > 0`, and `onCreate()` clears the buffer on every
        // service lifecycle — including a START_STICKY restart. A service that
        // came back and never received a single fix therefore had `ts == 0` and
        // the watchdog never fired, which is precisely the situation that
        // justifies having one. Blind in the worst case.
        val ts       = buffer.lastFixAtMs   // snapshot once: a concurrent record() between
        val since    = if (ts > 0) ts else runStartedAtMs
        val elapsed  = System.currentTimeMillis() - since  // the two reads could pass a stale elapsed

        if (since > 0 && elapsed > interval) {
            val p = provider
            if (p != null) {
                Log.i(TAG, "Watchdog: no update in ${elapsed / 1000}s — restarting provider")
                p.onStop(); p.onStart()
                persistKillReason(ServiceEvent.REASON_WATCHDOG)
                fire(ServiceEvent.ServiceRestarted(ServiceEvent.REASON_WATCHDOG))
                // The restart is now the thing being timed. Without moving this
                // mark the next tick sees the same elapsed time and restarts the
                // provider again — every interval, indefinitely, writing a kill
                // reason and firing a `serviceRestarted` each round. The watchdog
                // becomes the outage it was watching for.
                runStartedAtMs = System.currentTimeMillis()
            }
        }
        mainHandler.postDelayed(watchdogRunnable, interval)
    }

    // ── Heartbeat ─────────────────────────────────────────────────────────────

    private fun scheduleHeartbeat(cfg: BGConfig) {
        val interval = cfg.heartbeatInterval?.toLong()?.takeIf { it > 0 } ?: return
        mainHandler.postDelayed(heartbeatRunnable, interval)
    }

    private fun fireHeartbeat() {
        val cfg = config ?: return
        val interval = cfg.heartbeatInterval?.toLong()?.takeIf { it > 0 } ?: return
        markAlive()
        fire(ServiceEvent.Heartbeat(buffer.lastFix))
        mainHandler.postDelayed(heartbeatRunnable, interval)
    }

    // ── Battery (sticky broadcast read — not a persistent receiver) ───────────

    private fun attachBattery(loc: BGLocation) {
        if (config?.includeBattery != true) return
        try {
            val status = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
            val level  = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale  = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) loc.batteryLevel = level * 100 / scale
            val st = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            loc.isCharging = st == BatteryManager.BATTERY_STATUS_CHARGING ||
                             st == BatteryManager.BATTERY_STATUS_FULL
        } catch (_: Exception) {}
    }

    // ── Wake lock ─────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).also {
            it.setReferenceCounted(false); it.acquire()
        }
    }

    private fun acquirePostingWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$WAKE_LOCK_TAG:post")
            .acquire(10_000L) // auto-release after 10 s
    }

    private fun releaseWakeLock() {
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
    }

    // ── Provider factory ──────────────────────────────────────────────────────

    private fun createProvider(cfg: BGConfig): AbstractLocationProvider =
        when (cfg.locationProvider ?: BGConfig.DISTANCE_FILTER_PROVIDER) {
            BGConfig.ACTIVITY_PROVIDER -> ActivityLocationProvider(applicationContext)
            BGConfig.RAW_PROVIDER      -> RawLocationProvider(applicationContext)
            else                       -> DistanceFilterLocationProvider(applicationContext)
        }

    // ── PostLocationTask factory ──────────────────────────────────────────────

    private fun makePostTask(cfg: BGConfig) = PostLocationTask(
        applicationContext, cfg, locationDAO, sessionDAO,
        object : PostLocationTask.Callbacks {
            override fun onSyncRequested()          = triggerSync(false)
            override fun onRequestedAbortUpdates()  = fire(ServiceEvent.AbortRequested)
            override fun onHttpAuthorizationFailed() = fire(ServiceEvent.HttpAuthorization)
        }
    )

    // ── Priority sync ─────────────────────────────────────────────────────────

    private fun configurePrioritySync(cfg: BGConfig) {
        prioritySyncManager?.destroy()
        val hasUrl = !cfg.prioritySyncUrl.isNullOrEmpty() || !cfg.url.isNullOrEmpty()
        prioritySyncManager = if (hasUrl) PrioritySyncManager(applicationContext, cfg, ::fire) else null
    }

    private fun maybePrioritySync(event: ServiceEvent) {
        val mgr = prioritySyncManager ?: return
        val allowed = config?.prioritySyncEvents ?: PrioritySyncManager.DEFAULT_EVENTS
        val (type, payload) = buildPriorityPayload(event, allowed) ?: return
        mgr.submit(type, payload)
    }

    private fun buildPriorityPayload(event: ServiceEvent, allowed: List<String>): Pair<String, org.json.JSONObject>? {
        return when {
            event is ServiceEvent.PossibleCrash && "possibleCrash" in allowed ->
                Pair("possibleCrash", org.json.JSONObject().apply {
                    put("type", "possibleCrash")
                    put("timestamp", event.loc.time)
                    put("location", locationToCoords(event.loc))
                    put("source", event.source)
                })
            event is ServiceEvent.Sos && "sos" in allowed ->
                Pair("sos", org.json.JSONObject().apply {
                    put("type", "sos")
                    put("timestamp", System.currentTimeMillis())
                    buffer.lastFix?.let { put("location", locationToCoords(it)) }
                    event.payload?.let { p -> p.keys().forEach { k -> put(k, p.get(k)) } }
                })
            event is ServiceEvent.HardBrake && "hardBrake" in allowed ->
                Pair("hardBrake", org.json.JSONObject().apply {
                    put("type", "hardBrake")
                    put("timestamp", event.loc.time)
                    put("location", locationToCoords(event.loc))
                    put("source", "gps")
                })
            event is ServiceEvent.Speeding && "speeding" in allowed ->
                Pair("speeding", org.json.JSONObject().apply {
                    put("type", "speeding")
                    put("timestamp", event.loc.time)
                    put("location", locationToCoords(event.loc))
                    put("speedKmh", event.speedKmh)
                    put("limitKmh", event.limitKmh)
                    put("source", "gps")
                })
            else -> null
        }
    }

    private fun locationToCoords(loc: BGLocation): org.json.JSONObject =
        org.json.JSONObject().apply {
            put("latitude",  loc.latitude)
            put("longitude", loc.longitude)
        }

    // ── Event dispatch ────────────────────────────────────────────────────────

    private fun fire(event: ServiceEvent) {
        logEvent(event)
        maybePrioritySync(event)
        eventListener?.invoke(event)
    }

    /** Persist a diagnostic line for notable (low-volume) lifecycle/error events. */
    private fun logEvent(event: ServiceEvent) {
        when (event) {
            is ServiceEvent.ServiceStarted   -> BGLog.i("Service started")
            is ServiceEvent.ServiceStopped   -> BGLog.i("Service stopped")
            is ServiceEvent.ServiceRestarted -> BGLog.w("Service restarted: ${event.reason}")
            is ServiceEvent.Error            -> BGLog.e(event.message)
            is ServiceEvent.AbortRequested   -> BGLog.w("Server requested abort (285)")
            is ServiceEvent.HttpAuthorization -> BGLog.w("HTTP authorization failed (401)")
            is ServiceEvent.Sos              -> BGLog.w("SOS triggered")
            else -> Unit
        }
    }

    // ── Kill diagnostics ──────────────────────────────────────────────────────

    /**
     * Record why the service went down, dated when it went down.
     *
     * Two things were wrong. The timestamp was `System.currentTimeMillis()` at
     * the moment of the RESTART, not of the death — for a 611-minute gap the two
     * differ by 611 minutes and the wrong one was reported. And a service that
     * died and never came back wrote nothing at all, because every call site is
     * on the way back up, so the one case the API exists for was the one it could
     * not describe.
     *
     * `KEY_ALIVE_AT` closes that: the service stamps it while running, so the
     * last heartbeat before silence dates the death even when nothing survived to
     * report it. When a restart path knows the moment, it passes it; otherwise
     * the last known alive time is the best honest answer.
     */
    private fun persistKillReason(reason: String, diedAt: Long? = null) {
        val prefs = getSharedPreferences(PREFS_DIAG, MODE_PRIVATE)
        val at = diedAt ?: prefs.getLong(KEY_ALIVE_AT, 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
        prefs.edit()
            .putString(KEY_KILL_REASON, reason)
            .putLong(KEY_KILL_AT, at)
            .apply()
    }

    /** Stamp "still alive" so a death with no exit path can still be dated. */
    private fun markAlive() {
        getSharedPreferences(PREFS_DIAG, MODE_PRIVATE).edit()
            .putLong(KEY_ALIVE_AT, System.currentTimeMillis())
            .apply()
    }

    /**
     * Forget the previous death once we are cleanly up again.
     *
     * Nothing ever cleared this preference: three writes, one read, no removal.
     * Once written it was returned for the life of the install, so a `boot` from
     * three weeks ago was reported on every shift and every consumer had to
     * deduplicate by timestamp to avoid acting on it. A clean start is exactly
     * the moment the previous death stops being news.
     */
    private fun clearKillReason() {
        getSharedPreferences(PREFS_DIAG, MODE_PRIVATE).edit()
            .remove(KEY_KILL_REASON)
            .remove(KEY_KILL_AT)
            .apply()
    }
}
