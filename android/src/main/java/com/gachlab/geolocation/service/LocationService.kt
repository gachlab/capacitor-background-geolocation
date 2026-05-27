// SPDX-License-Identifier: MIT
// Copyright (c) 2026 gachlab

package com.gachlab.geolocation.service

import android.app.Service
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
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gachlab.geolocation.BGConfig
import com.gachlab.geolocation.BGLocation
import com.gachlab.geolocation.DrivingEventsDetector
import com.gachlab.geolocation.NotificationHelper
import com.gachlab.geolocation.ServiceEvent
import com.gachlab.geolocation.persistence.ConfigDAO
import com.gachlab.geolocation.persistence.LocationDAO
import com.gachlab.geolocation.persistence.SessionDAO
import com.gachlab.geolocation.network.BackgroundSync
import com.gachlab.geolocation.network.PostLocationTask
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

        private const val TAG             = "LocationService"
        private const val NOTIFICATION_ID = 1
        private const val WAKE_LOCK_TAG   = "gachlab:bgloc"
        private const val PREFS_DIAG      = "bgloc_diagnostics"
        private const val KEY_KILL_REASON = "last_kill_reason"
        private const val KEY_KILL_AT     = "last_kill_at"
    }

    private var config: BGConfig? = null
    private var provider: AbstractLocationProvider? = null
    private var postTask: PostLocationTask? = null
    private var drivingDetector: DrivingEventsDetector? = null
    private var wakeLock: PowerManager.WakeLock? = null

    @Volatile var isRunning = false
        private set
    @Volatile private var lastLocationTime = 0L
    @Volatile private var latestLocation: BGLocation? = null

    private lateinit var locationDAO: LocationDAO
    private lateinit var sessionDAO: SessionDAO
    private lateinit var configDAO: ConfigDAO
    private lateinit var mainHandler: Handler

    private val watchdogRunnable = Runnable { checkWatchdog() }
    private val heartbeatRunnable = Runnable { fireHeartbeat() }

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance    = this
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
                persistKillReason(ServiceEvent.REASON_SYSTEM_KILL)
                fire(ServiceEvent.ServiceRestarted(ServiceEvent.REASON_SYSTEM_KILL))
            }
            intent.getStringExtra(EXTRA_START_REASON) == ServiceEvent.REASON_BOOT -> {
                persistKillReason(ServiceEvent.REASON_BOOT)
                fire(ServiceEvent.ServiceRestarted(ServiceEvent.REASON_BOOT))
            }
        }
        if (!isRunning) start()
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
        else fire(ServiceEvent.ServiceRestarted(ServiceEvent.REASON_APP_REMOVED))
    }

    // ── Public API ────────────────────────────────────────────────────────────

    @Synchronized
    fun start() {
        if (isRunning) return
        val cfg = configDAO.retrieveConfig() ?: BGConfig.getDefault()
        config = cfg
        isRunning = true

        if (cfg.startForeground == true) {
            startForeground(NOTIFICATION_ID,
                NotificationHelper.buildServiceNotification(applicationContext, cfg))
        }

        if (cfg.wakeLockMode == "always") acquireWakeLock()

        val p = createProvider(cfg)
        p.onCreate(); p.onConfigure(cfg); p.setDelegate(providerDelegate)
        provider = p; p.onStart()

        postTask = makePostTask(cfg)
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

        mainHandler.removeCallbacks(watchdogRunnable)
        mainHandler.removeCallbacks(heartbeatRunnable)

        provider?.onStop(); provider?.onDestroy(); provider = null
        postTask?.shutdown(); postTask = null
        drivingDetector?.reset(); drivingDetector = null

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
        postTask?.shutdown()
        postTask = makePostTask(merged)
        configureDrivingDetector(merged)

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
            .build()
        WorkManager.getInstance(applicationContext).enqueue(work)
    }

    fun triggerSOS(locationId: Long?) { fire(ServiceEvent.Sos(locationId)) }

    // ── Provider delegate ─────────────────────────────────────────────────────

    private val providerDelegate = object : AbstractLocationProvider.Delegate {
        override fun onLocation(location: BGLocation) = handleLocation(location)
        override fun onStationary(location: BGLocation, radius: Float) = handleStationary(location, radius)
        override fun onError(error: BGException) = fire(ServiceEvent.Error(error.message ?: ""))
    }

    private fun handleLocation(loc: BGLocation) {
        lastLocationTime = System.currentTimeMillis()
        latestLocation   = loc

        drivingDetector?.onLocation(loc)
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
            drivingDetector?.reset(); drivingDetector = null; return
        }
        val detectorCfg = DrivingEventsDetector.Config(
            enabled            = opts.enabled,
            speedLimitKmh      = opts.speedLimitKmh,
            minMovingSpeedMps  = opts.minMovingSpeedMps,
            stoppedDurationMs  = opts.stoppedDurationMs,
            minTripSpeedMps    = opts.minTripSpeedMps,
            minTripDurationMs  = opts.minTripDurationMs,
            hardBrakeMps2      = opts.hardBrakeMps2,
            rapidAccelMps2     = opts.rapidAccelMps2,
            sharpTurnDegPerSec = opts.sharpTurnDegPerSec,
            crashImpactKmh     = opts.crashImpactKmh,
            crashWindowMs      = opts.crashWindowMs
        )
        if (drivingDetector == null) drivingDetector = DrivingEventsDetector(drivingListener)
        drivingDetector!!.setConfig(detectorCfg)
    }

    private val drivingListener = object : DrivingEventsDetector.Listener {
        override fun onMoving(loc: BGLocation)        = fire(ServiceEvent.Moving(loc))
        override fun onStopped(loc: BGLocation)       = fire(ServiceEvent.Stopped(loc))
        override fun onTripStart(loc: BGLocation)     = fire(ServiceEvent.TripStart(loc))
        override fun onTripEnd(loc: BGLocation, distanceMeters: Double, durationMs: Long) =
            fire(ServiceEvent.TripEnd(loc, distanceMeters, durationMs))
        override fun onSpeeding(loc: BGLocation, speedKmh: Double, limitKmh: Double) =
            fire(ServiceEvent.Speeding(loc, speedKmh, limitKmh))
        override fun onProviderChange(provider: String) = fire(ServiceEvent.ProviderChange(provider))
        override fun onHardBrake(loc: BGLocation, decelMps2: Double) {
            loc.addDrivingEvent("hardBrake"); fire(ServiceEvent.HardBrake(loc))
        }
        override fun onRapidAcceleration(loc: BGLocation, accelMps2: Double) {
            loc.addDrivingEvent("rapidAcceleration"); fire(ServiceEvent.RapidAcceleration(loc))
        }
        override fun onSharpTurn(loc: BGLocation, degPerSec: Double) {
            loc.addDrivingEvent("sharpTurn"); fire(ServiceEvent.SharpTurn(loc))
        }
        override fun onPossibleCrash(loc: BGLocation, velocityDropKmh: Double) {
            loc.addDrivingEvent("possibleCrash"); fire(ServiceEvent.PossibleCrash(loc))
        }
    }

    // ── Watchdog ──────────────────────────────────────────────────────────────

    private fun scheduleWatchdog(cfg: BGConfig) {
        if (cfg.enableWatchdog != true) return
        mainHandler.postDelayed(watchdogRunnable, cfg.watchdogIntervalMs ?: 60_000L)
    }

    private fun checkWatchdog() {
        val cfg = config ?: return
        if (!isRunning) return
        val interval = cfg.watchdogIntervalMs ?: 60_000L
        val elapsed  = System.currentTimeMillis() - lastLocationTime
        if (lastLocationTime > 0 && elapsed > interval) {
            val p = provider
            if (p != null && p.isStarted()) {
                Log.i(TAG, "Watchdog: no update in ${elapsed / 1000}s — restarting provider")
                p.onStop(); p.onStart()
                persistKillReason(ServiceEvent.REASON_WATCHDOG)
                fire(ServiceEvent.ServiceRestarted(ServiceEvent.REASON_WATCHDOG))
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
        fire(ServiceEvent.Heartbeat(latestLocation))
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

    // ── Event dispatch ────────────────────────────────────────────────────────

    private fun fire(event: ServiceEvent) { eventListener?.invoke(event) }

    // ── Kill diagnostics ──────────────────────────────────────────────────────

    private fun persistKillReason(reason: String) {
        getSharedPreferences(PREFS_DIAG, MODE_PRIVATE).edit()
            .putString(KEY_KILL_REASON, reason)
            .putLong(KEY_KILL_AT, System.currentTimeMillis())
            .apply()
    }
}
