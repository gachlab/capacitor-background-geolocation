/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.marianhello.bgloc.service;

import android.accounts.Account;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.Manifest;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.ConnectivityListener;
import com.marianhello.bgloc.sync.NotificationHelper;
import com.marianhello.bgloc.PluginException;
import com.marianhello.bgloc.PostLocationTask;
import com.marianhello.bgloc.ResourceResolver;
import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.ConfigurationDAO;
import com.marianhello.bgloc.data.DAOFactory;
import com.marianhello.bgloc.data.LocationDAO;
import com.marianhello.bgloc.data.SessionLocationDAO;
import com.marianhello.bgloc.data.LocationTransform;
import com.marianhello.bgloc.data.sqlite.SQLiteSessionLocationDAO;
import com.marianhello.bgloc.headless.AbstractTaskRunner;
import com.marianhello.bgloc.headless.ActivityTask;
import com.marianhello.bgloc.headless.LocationTask;
import com.marianhello.bgloc.headless.StationaryTask;
import com.marianhello.bgloc.headless.Task;
import com.marianhello.bgloc.headless.TaskRunner;
import com.marianhello.bgloc.headless.TaskRunnerFactory;
import com.marianhello.bgloc.provider.LocationProvider;
import com.marianhello.bgloc.provider.LocationProviderFactory;
import com.marianhello.bgloc.provider.ProviderDelegate;
import com.marianhello.bgloc.sync.AccountHelper;
import com.marianhello.bgloc.sync.SyncService;
import com.marianhello.logging.LoggerManager;
import com.marianhello.logging.UncaughtExceptionLogger;

import org.chromium.content.browser.ThreadUtils;
import org.json.JSONException;

import java.util.Locale;

import static com.marianhello.bgloc.service.LocationServiceIntentBuilder.containsCommand;
import static com.marianhello.bgloc.service.LocationServiceIntentBuilder.containsMessage;
import static com.marianhello.bgloc.service.LocationServiceIntentBuilder.getCommand;
import static com.marianhello.bgloc.service.LocationServiceIntentBuilder.getMessage;

public class LocationServiceImpl extends Service implements ProviderDelegate, LocationService {

    public static final String ACTION_BROADCAST = ".broadcast";

    /**
     * CommandId sent by the service to
     * any registered clients with error.
     */
    public static final int MSG_ON_ERROR = 100;

    /**
     * CommandId sent by the service to
     * any registered clients with the new position.
     */
    public static final int MSG_ON_LOCATION = 101;

    /**
     * CommandId sent by the service to
     * any registered clients whenever the devices enters "stationary-mode"
     */
    public static final int MSG_ON_STATIONARY = 102;

    /**
     * CommandId sent by the service to
     * any registered clients with new detected activity.
     */
    public static final int MSG_ON_ACTIVITY = 103;

    public static final int MSG_ON_SERVICE_STARTED = 104;

    public static final int MSG_ON_SERVICE_STOPPED = 105;

    public static final int MSG_ON_ABORT_REQUESTED = 106;

    public static final int MSG_ON_HTTP_AUTHORIZATION = 107;

    /** v3.5 Phase 4: sync queue events. */
    public static final int MSG_ON_SYNC_START = 108;
    public static final int MSG_ON_SYNC_SUCCESS = 109;
    public static final int MSG_ON_SYNC_ERROR = 110;
    public static final int MSG_ON_SYNC_PROGRESS = 111;
    public static final int MSG_ON_HEARTBEAT = 112;
    /** v4.0 Phase 6 — driver insight events. */
    public static final int MSG_ON_TRIP_START      = 113;
    public static final int MSG_ON_TRIP_END        = 114;
    public static final int MSG_ON_MOVING          = 115;
    public static final int MSG_ON_STOPPED         = 116;
    public static final int MSG_ON_SPEEDING        = 117;
    public static final int MSG_ON_PROVIDER_CHANGE = 118;
    public static final int MSG_ON_SOS             = 119;
    /** v4.1 — sensor-like GPS-derived driving events. */
    public static final int MSG_ON_HARD_BRAKE          = 120;
    public static final int MSG_ON_RAPID_ACCELERATION  = 121;
    public static final int MSG_ON_SHARP_TURN          = 122;
    public static final int MSG_ON_POSSIBLE_CRASH      = 123;
    /** v4.2 — sensor-fusion-only events. {@code MSG_ON_POSSIBLE_CRASH} is reused
     *  by the sensor pipeline; phone-usage is a brand-new event. */
    public static final int MSG_ON_PHONE_USAGE_WHILE_DRIVING = 124;
    /** P2: service was restarted by the OS watchdog, system kill, or boot. */
    public static final int MSG_ON_SERVICE_RESTARTED = 125;

    /** notification id */
    private static int NOTIFICATION_ID = 1;

    private ResourceResolver mResolver;
    private Config mConfig;
    private LocationProvider mProvider;
    private Account mSyncAccount;

    private org.slf4j.Logger logger;

    private final IBinder mBinder = new LocalBinder();
    private HandlerThread mHandlerThread;
    private ServiceHandler mServiceHandler;
    private LocationDAO mLocationDAO;
    private SessionLocationDAO mSessionDAO;
    private PostLocationTask mPostLocationTask;
    private String mHeadlessTaskRunnerClass;
    private TaskRunner mHeadlessTaskRunner;

    private long mServiceId = -1;
    private static boolean sIsRunning = false;
    private boolean mIsInForeground = false;

    private PowerManager.WakeLock mWakeLock;
    private static final String WAKE_LOCK_TAG = "com.marianhello.bgloc:LocationServiceWakeLock";

    /** Last time we received a location (for watchdog). */
    private volatile long mLastLocationTime = 0L;
    /** v3.5 Phase 4: latest received location, used as heartbeat payload. */
    private volatile BackgroundLocation mLastReceivedLocation;
    /** v4.0 Phase 6: static accessor for {@link com.marianhello.bgloc.BackgroundGeolocationFacade#triggerSOS}. */
    private static volatile BackgroundLocation sLastReceivedLocation;
    public static BackgroundLocation getLastReceivedLocation() { return sLastReceivedLocation; }
    /** v3.5 Phase 4: heartbeat scheduler. */
    private java.util.concurrent.ScheduledExecutorService mHeartbeatExecutor;
    private java.util.concurrent.ScheduledFuture<?> mHeartbeatTask;

    /** v4.0 Phase 6: driver-insights detector. Created lazily when config has drivingEvents.enabled. */
    private com.marianhello.bgloc.driving.DrivingEventsDetector mDrivingDetector;
    /** v4.2 Phase 8: real sensor-fusion detector. Created when drivingEvents.sensorFusion=true. */
    private com.marianhello.bgloc.sensor.SensorFusionDetector mSensorFusion;
    /** v4.2 Phase 8: cached tripActive state so hot-reload can re-inject it. */
    private volatile boolean mDrivingTripActive = false;
    /** v4.3: events fired without a simultaneous fix (providerChange, sensor crash, phone usage,
     *  manual SOS) buffered here and flushed onto the next location's `events` array.
     *  v4.4.1: capped at PENDING_DRIVING_EVENTS_MAX entries (oldest evicted) and entries older
     *  than PENDING_DRIVING_EVENTS_TTL_MS are dropped at flush time. */
    private final org.json.JSONArray mPendingDrivingEvents = new org.json.JSONArray();
    private static final int  PENDING_DRIVING_EVENTS_MAX = 20;
    private static final long PENDING_DRIVING_EVENTS_TTL_MS = 60_000L;
    private static final long WATCHDOG_INTERVAL_MS = 60_000L;
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    private long effectiveWatchdogIntervalMs() {
        if (mConfig != null && mConfig.getWatchdogIntervalMs() != null) {
            return mConfig.getWatchdogIntervalMs();
        }
        return WATCHDOG_INTERVAL_MS;
    }

    private final Runnable mWatchdogRunnable = new Runnable() {
        @Override
        public void run() {
            if (!sIsRunning || mProvider == null || mConfig == null) return;
            if (!Boolean.TRUE.equals(mConfig.getEnableWatchdog())) return;
            long interval = effectiveWatchdogIntervalMs();
            long now = System.currentTimeMillis();
            // Fire when: (a) we got at least one fix but it's stale, OR
            //            (b) the service started but never received any fix.
            boolean stale = (mLastLocationTime > 0 && (now - mLastLocationTime) > interval)
                    || (mLastLocationTime == 0 && (now - mSessionStartTime) > interval);
            if (stale) {
                // v4.5.1: when drivingEvents is enabled, treat "no fixes" while NOT tripActive as
                // intentional stationary → don't restart (saves battery). When drivingEvents is
                // disabled (the plugin has no notion of "trip"), keep the legacy behaviour of
                // restarting on every stale window.
                Config.DrivingEventsOptions de = mConfig.getDrivingEvents();
                boolean drivingEnabled = de != null && de.enabled;
                boolean shouldRestart = !drivingEnabled || mDrivingTripActive;
                if (shouldRestart) {
                    logger.info("Location watchdog: no update in {}s, restarting provider", interval / 1000);
                    broadcastServiceRestarted("watchdog");
                    try {
                        mProvider.onStop();
                        mProvider.onStart();
                    } catch (Exception e) {
                        logger.warn("Watchdog restart failed", e);
                    }
                } else {
                    logger.debug("Location watchdog: stationary (no active trip); skipping restart");
                }
            }
            mMainHandler.postDelayed(this, interval);
        }
    };

    /** Session start time for notification elapsed time (showTime). */
    private volatile long mSessionStartTime = 0L;
    /** Accumulated distance in meters for notification (showDistance). */
    private volatile double mSessionDistanceMeters = 0.0;
    private volatile double mLastLat = 0.0;
    private volatile double mLastLon = 0.0;
    private volatile boolean mHasLastLocation = false;
    private static final long NOTIFICATION_UPDATE_INTERVAL_MS = 1000L;
    private final Runnable mNotificationUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            if (!sIsRunning || !mIsInForeground || mConfig == null) {
                return;
            }
            boolean showTime = Boolean.TRUE.equals(mConfig.getShowTime());
            boolean showDistance = Boolean.TRUE.equals(mConfig.getShowDistance());
            if (!showTime && !showDistance) {
                return;
            }
            updateForegroundNotification();
            mMainHandler.postDelayed(this, NOTIFICATION_UPDATE_INTERVAL_MS);
        }
    };

    private static LocationTransform sLocationTransform;
    private static LocationProviderFactory sLocationProviderFactory;

    private class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    }

    /**
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        logger.debug("Client binds to service");
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        logger.debug("Client rebinds to service");
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        // All clients have unbound with unbindService()
        logger.debug("All clients have been unbound from service");

        return true; // Ensures onRebind() is called when a client re-binds.
    }

    @Override
    public void onCreate() {
        super.onCreate();

        sIsRunning = false;

        UncaughtExceptionLogger.register(this);

        logger = LoggerManager.getLogger(LocationServiceImpl.class);
        logger.info("Creating LocationServiceImpl");

        mServiceId = System.currentTimeMillis();

        // Start up the thread running the service.  Note that we create a
        // separate thread because the service normally runs in the process's
        // main thread, which we don't want to block.  We also make it
        // background priority so CPU-intensive work will not disrupt our UI.
        if (mHandlerThread == null) {
            mHandlerThread = new HandlerThread("LocationServiceImpl.Thread", Process.THREAD_PRIORITY_BACKGROUND);
        }
        mHandlerThread.start();
        // An Android service handler is a handler running on a specific background thread.
        mServiceHandler = new ServiceHandler(mHandlerThread.getLooper());

        mResolver = ResourceResolver.newInstance(this);

        mSyncAccount = AccountHelper.CreateSyncAccount(this, mResolver.getAccountName(),
                mResolver.getAccountType());

        String authority = mResolver.getAuthority();
        ContentResolver.setIsSyncable(mSyncAccount, authority, 1);
        ContentResolver.setSyncAutomatically(mSyncAccount, authority, true);

        mLocationDAO = DAOFactory.createLocationDAO(this);
        mSessionDAO = new SQLiteSessionLocationDAO(this);

        mPostLocationTask = new PostLocationTask(mLocationDAO, mSessionDAO,
                new PostLocationTask.PostLocationTaskListener() {
                    @Override
                    public void onRequestedAbortUpdates() {
                        handleRequestedAbortUpdates();
                    }

                    @Override
                    public void onHttpAuthorizationUpdates() {
                        handleHttpAuthorizationUpdates();
                    }

                    @Override
                    public void onSyncRequested() {
                        SyncService.sync(mSyncAccount, mResolver.getAuthority(), false);
                    }
                }, new ConnectivityListener() {
            @Override
            public boolean hasConnectivity() {
                return isNetworkAvailable();
            }
        });

        registerReceiver(connectivityChangeReceiver, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));
        NotificationHelper.registerServiceChannel(this);
    }

    @Override
    public void onDestroy() {
        logger.info("Destroying LocationServiceImpl");

        // workaround for issue #276
        if (mProvider != null) {
            mProvider.onDestroy();
        }

        if (mHandlerThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mHandlerThread.quitSafely();
            } else {
                mHandlerThread.quit(); //sorry
            }
        }

        if (mPostLocationTask != null) {
            mPostLocationTask.shutdown();
        }


        unregisterReceiver(connectivityChangeReceiver);

        sIsRunning = false;
        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        logger.debug("Task has been removed");
        // workaround for issue #276
        Config config = getConfig();
        if (config.getStopOnTerminate()) {
            logger.info("Stopping self");
            stopSelf();
        } else {
            logger.info("Continue running in background");
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // Service was killed by the OS and is being restarted (START_STICKY).
            broadcastServiceRestarted("system_kill");
            start();
            return START_STICKY;
        }

        boolean containsCommand = containsCommand(intent);
        logger.debug(
                String.format("Service in [%s] state. cmdId: [%s]. startId: [%d]",
                        sIsRunning ? "STARTED" : "NOT STARTED",
                        containsCommand ? getCommand(intent).getId() : "N/A",
                        startId)
        );

        if (containsCommand) {
            LocationServiceIntentBuilder.Command cmd = getCommand(intent);
            processCommand(cmd.getId(), cmd.getArgument());
        } else {
            // Could be a BOOT-event (BootCompletedReceiver puts "config" extra) or
            // the OS just randomly restarted the service without a specific command.
            if (intent.hasExtra("config")) {
                broadcastServiceRestarted("boot");
            }
            startForegroundService();
        }

        if (containsMessage(intent)) {
            processMessage(getMessage(intent));
        }

        return START_STICKY;
    }

    private void processMessage(String message) {
        // currently we do not process any message
    }

    private void processCommand(int command, Object arg) {
        try {
            switch (command) {
                case CommandId.START:
                    start();
                    break;
                case CommandId.START_FOREGROUND_SERVICE:
                    startForegroundService();
                    break;
                case CommandId.STOP:
                    stop();
                    break;
                case CommandId.CONFIGURE:
                    configure((Config) arg);
                    break;
                case CommandId.STOP_FOREGROUND:
                    stopForeground();
                    break;
                case CommandId.START_FOREGROUND:
                    startForeground();
                    break;
                case CommandId.REGISTER_HEADLESS_TASK:
                    registerHeadlessTask((String) arg);
                    break;
                case CommandId.START_HEADLESS_TASK:
                    startHeadlessTask();
                    break;
                case CommandId.STOP_HEADLESS_TASK:
                    stopHeadlessTask();
                    break;
            }
        } catch (Exception e) {
            logger.error("processCommand: exception", e);
        }
    }

    @Override
    public synchronized void start() {
        if (sIsRunning) {
            return;
        }

        if (mConfig == null) {
            logger.warn("Attempt to start unconfigured service. Will use stored or default.");
            mConfig = getConfig();
            // TODO: throw JSONException if config cannot be obtained from db
        }

        logger.debug("Will start service with: {}", mConfig.toString());

        if (!hasLocationPermission()) {
            logger.warn("Cannot start location service: ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION not granted");
            stopSelf();
            return;
        }

        mPostLocationTask.setConfig(mConfig);
        mPostLocationTask.clearQueue();

        LocationProviderFactory spf = sLocationProviderFactory != null
                ? sLocationProviderFactory : new LocationProviderFactory(this);
        mProvider = spf.getInstance(mConfig.getLocationProvider());
        mProvider.setDelegate(this);
        mProvider.onCreate();
        mProvider.onConfigure(mConfig);

        sIsRunning = true;

        if (mWakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
            }
        }
        // v4.5.1: only hold a permanent CPU wake lock when wakeLockMode == 'always'.
        // Default 'posting' acquires only briefly during onLocation/post; 'none' never.
        String wlMode = mConfig.getWakeLockMode() != null ? mConfig.getWakeLockMode() : "posting";
        if ("always".equals(wlMode) && mWakeLock != null && !mWakeLock.isHeld()) {
            mWakeLock.acquire();
            logger.debug("Wake lock acquired (mode=always)");
        }

        if (Boolean.TRUE.equals(mConfig.getEnableWatchdog())) {
            mMainHandler.removeCallbacks(mWatchdogRunnable);
            mMainHandler.postDelayed(mWatchdogRunnable, effectiveWatchdogIntervalMs());
        }

        mSessionStartTime = System.currentTimeMillis();
        mSessionDistanceMeters = 0.0;
        mLastLat = 0.0;
        mLastLon = 0.0;
        mHasLastLocation = false;

        ThreadUtils.runOnUiThreadBlocking(new Runnable() {
            @Override
            public void run() {
                mProvider.onStart();
                if (mConfig.getStartForeground()) {
                    startForeground();
                }
            }
        });

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_SERVICE_STARTED);
        bundle.putLong("serviceId", mServiceId);
        broadcastMessage(bundle);

        // v3.5 Phase 4: kick off heartbeat scheduler when the service starts.
        scheduleHeartbeat();

        // v4.0 Phase 6: build driver-insights detector if enabled in config.
        configureDrivingDetector();
    }

    /** v4.0 Phase 6: instantiate / reconfigure the GPS-based driver-insights detector. */
    private void configureDrivingDetector() {
        if (mConfig == null) return;
        com.marianhello.bgloc.Config.DrivingEventsOptions opts = mConfig.getDrivingEvents();
        if (opts == null || !opts.enabled) {
            if (mDrivingDetector != null) mDrivingDetector.reset();
            mDrivingDetector = null;
            return;
        }
        com.marianhello.bgloc.driving.DrivingEventsDetector.Config c =
                new com.marianhello.bgloc.driving.DrivingEventsDetector.Config();
        c.enabled = true;
        c.speedLimitKmh     = opts.speedLimitKmh;
        c.minMovingSpeedMps = opts.minMovingSpeedMps;
        c.stoppedDurationMs = opts.stoppedDurationMs;
        c.minTripSpeedMps   = opts.minTripSpeedMps;
        c.minTripDurationMs = opts.minTripDurationMs;

        mDrivingDetector = new com.marianhello.bgloc.driving.DrivingEventsDetector(
                new com.marianhello.bgloc.driving.DrivingEventsDetector.Listener() {
                    @Override public void onMoving(BackgroundLocation l) {
                        attachDrivingEvent(l, "moving", null);
                        Bundle b = new Bundle();
                        b.putInt("action", MSG_ON_MOVING);
                        if (l != null) b.putParcelable("payload", l);
                        broadcastMessage(b);
                    }
                    @Override public void onStopped(BackgroundLocation l) {
                        attachDrivingEvent(l, "stopped", null);
                        Bundle b = new Bundle();
                        b.putInt("action", MSG_ON_STOPPED);
                        if (l != null) b.putParcelable("payload", l);
                        broadcastMessage(b);
                    }
                    @Override public void onTripStart(BackgroundLocation l) {
                        attachDrivingEvent(l, "tripStart", null);
                        Bundle b = new Bundle();
                        b.putInt("action", MSG_ON_TRIP_START);
                        if (l != null) b.putParcelable("payload", l);
                        broadcastMessage(b);
                        mDrivingTripActive = true;
                        if (mSensorFusion != null) mSensorFusion.setTripActive(true);
                    }
                    @Override public void onTripEnd(BackgroundLocation l, double distance, long durationMs) {
                        org.json.JSONObject extra = new org.json.JSONObject();
                        try { extra.put("distance", distance); extra.put("durationMs", durationMs); } catch (org.json.JSONException ignored) {}
                        attachDrivingEvent(l, "tripEnd", extra);
                        Bundle b = new Bundle();
                        b.putInt("action", MSG_ON_TRIP_END);
                        if (l != null) b.putParcelable("payload", l);
                        b.putDouble("distance", distance);
                        b.putLong("durationMs", durationMs);
                        broadcastMessage(b);
                        mDrivingTripActive = false;
                        if (mSensorFusion != null) mSensorFusion.setTripActive(false);
                    }
                    @Override public void onSpeeding(BackgroundLocation l, double speedKmh, double limitKmh) {
                        org.json.JSONObject extra = new org.json.JSONObject();
                        try { extra.put("speedKmh", speedKmh); extra.put("limitKmh", limitKmh); } catch (org.json.JSONException ignored) {}
                        attachDrivingEvent(l, "speeding", extra);
                        Bundle b = new Bundle();
                        b.putInt("action", MSG_ON_SPEEDING);
                        if (l != null) b.putParcelable("payload", l);
                        b.putDouble("speedKmh", speedKmh);
                        b.putDouble("limitKmh", limitKmh);
                        broadcastMessage(b);
                    }
                    @Override public void onProviderChange(String provider) {
                        // No location associated; buffer for next fix.
                        org.json.JSONObject ev = new org.json.JSONObject();
                        try { ev.put("type", "providerChange"); ev.put("provider", provider != null ? provider : ""); ev.put("time", System.currentTimeMillis()); } catch (org.json.JSONException ignored) {}
                        enqueuePendingDrivingEvent(ev);
                        Bundle b = new Bundle();
                        b.putInt("action", MSG_ON_PROVIDER_CHANGE);
                        b.putString("provider", provider != null ? provider : "");
                        broadcastMessage(b);
                    }
                    @Override public void onHardBrake(BackgroundLocation l, double decelMps2) {
                        org.json.JSONObject extra = new org.json.JSONObject();
                        try { extra.put("value", decelMps2); } catch (org.json.JSONException ignored) {}
                        attachDrivingEvent(l, "hardBrake", extra);
                        Bundle b = new Bundle();
                        b.putInt("action", MSG_ON_HARD_BRAKE);
                        if (l != null) b.putParcelable("payload", l);
                        b.putDouble("value", decelMps2);
                        broadcastMessage(b);
                    }
                    @Override public void onRapidAcceleration(BackgroundLocation l, double accelMps2) {
                        org.json.JSONObject extra = new org.json.JSONObject();
                        try { extra.put("value", accelMps2); } catch (org.json.JSONException ignored) {}
                        attachDrivingEvent(l, "rapidAcceleration", extra);
                        Bundle b = new Bundle();
                        b.putInt("action", MSG_ON_RAPID_ACCELERATION);
                        if (l != null) b.putParcelable("payload", l);
                        b.putDouble("value", accelMps2);
                        broadcastMessage(b);
                    }
                    @Override public void onSharpTurn(BackgroundLocation l, double degPerSec) {
                        org.json.JSONObject extra = new org.json.JSONObject();
                        try { extra.put("value", degPerSec); } catch (org.json.JSONException ignored) {}
                        attachDrivingEvent(l, "sharpTurn", extra);
                        Bundle b = new Bundle();
                        b.putInt("action", MSG_ON_SHARP_TURN);
                        if (l != null) b.putParcelable("payload", l);
                        b.putDouble("value", degPerSec);
                        broadcastMessage(b);
                    }
                    @Override public void onPossibleCrash(BackgroundLocation l, double velocityDropKmh) {
                        org.json.JSONObject extra = new org.json.JSONObject();
                        try { extra.put("value", velocityDropKmh); extra.put("source", "gps"); } catch (org.json.JSONException ignored) {}
                        attachDrivingEvent(l, "possibleCrash", extra);
                        Bundle b = new Bundle();
                        b.putInt("action", MSG_ON_POSSIBLE_CRASH);
                        if (l != null) b.putParcelable("payload", l);
                        b.putDouble("value", velocityDropKmh);
                        b.putString("source", "gps");
                        broadcastMessage(b);
                    }
                });
        // Pass v4.1 thresholds from app config (with defaults from c).
        com.marianhello.bgloc.Config.DrivingEventsOptions optsRef = mConfig.getDrivingEvents();
        if (optsRef != null) {
            c.hardBrakeMps2     = optsRef.hardBrakeMps2;
            c.rapidAccelMps2    = optsRef.rapidAccelMps2;
            c.sharpTurnDegPerSec = optsRef.sharpTurnDegPerSec;
            c.crashImpactKmh    = optsRef.crashImpactKmh;
            c.crashWindowMs     = optsRef.crashWindowMs;
        }
        mDrivingDetector.setConfig(c);
        configureSensorFusion();
    }

    /** v4.2 Phase 8: instantiate / reconfigure the real sensor-fusion detector. */
    private void configureSensorFusion() {
        if (mConfig == null) {
            if (mSensorFusion != null) { mSensorFusion.stop(); mSensorFusion = null; }
            return;
        }
        com.marianhello.bgloc.Config.DrivingEventsOptions opts = mConfig.getDrivingEvents();
        boolean wantSF = opts != null && opts.enabled && opts.sensorFusion;
        if (!wantSF) {
            if (mSensorFusion != null) { mSensorFusion.stop(); mSensorFusion = null; }
            return;
        }

        com.marianhello.bgloc.sensor.SensorFusionDetector.Listener l =
                new com.marianhello.bgloc.sensor.SensorFusionDetector.Listener() {
                    @Override public void onSensorCrash(BackgroundLocation lastLocation, double impactG) {
                        // Buffer for next fix (sensor events fire async to GPS).
                        try {
                            org.json.JSONObject ev = new org.json.JSONObject();
                            ev.put("type", "possibleCrash");
                            ev.put("value", impactG);
                            ev.put("source", "sensor");
                            ev.put("time", System.currentTimeMillis());
                            enqueuePendingDrivingEvent(ev);
                        } catch (org.json.JSONException ignored) {}
                        Bundle b = new Bundle();
                        b.putInt("action", MSG_ON_POSSIBLE_CRASH);
                        if (lastLocation != null) b.putParcelable("payload", lastLocation);
                        b.putDouble("value", impactG);
                        b.putString("source", "sensor");
                        broadcastMessage(b);
                    }
                    @Override public void onPhoneUsageWhileDriving(BackgroundLocation lastLocation) {
                        try {
                            org.json.JSONObject ev = new org.json.JSONObject();
                            ev.put("type", "phoneUsageWhileDriving");
                            ev.put("time", System.currentTimeMillis());
                            enqueuePendingDrivingEvent(ev);
                        } catch (org.json.JSONException ignored) {}
                        Bundle b = new Bundle();
                        b.putInt("action", MSG_ON_PHONE_USAGE_WHILE_DRIVING);
                        if (lastLocation != null) b.putParcelable("payload", lastLocation);
                        broadcastMessage(b);
                    }
                };

        if (mSensorFusion == null) {
            mSensorFusion = new com.marianhello.bgloc.sensor.SensorFusionDetector(this, l);
        }
        com.marianhello.bgloc.sensor.SensorFusionDetector.Config sfc =
                new com.marianhello.bgloc.sensor.SensorFusionDetector.Config();
        sfc.enabled = true;
        sfc.crashImpactG          = opts.crashImpactG;
        sfc.crashCooldownMs       = opts.sensorCrashCooldownMs;
        sfc.phoneUsageWindowMs    = opts.phoneUsageWindowMs;
        sfc.phoneUsageCooldownMs  = opts.phoneUsageCooldownMs;
        mSensorFusion.setConfig(sfc);
        // v4.2 hot-reload: re-inject current tripActive state and last location so the
        // sensor pipeline starts in the right mode (e.g. config arrives mid-trip).
        mSensorFusion.setTripActive(mDrivingTripActive);
        if (mLastReceivedLocation != null) {
            mSensorFusion.setLastLocation(mLastReceivedLocation);
        }
        if (sIsRunning) mSensorFusion.start();
    }

    /** v4.3 — append a {type, time, ...extra} entry to the location's events array. */
    private void attachDrivingEvent(BackgroundLocation loc, String type, org.json.JSONObject extra) {
        if (loc == null || type == null) return;
        try {
            org.json.JSONObject ev = new org.json.JSONObject();
            ev.put("type", type);
            ev.put("time", System.currentTimeMillis());
            if (extra != null) {
                java.util.Iterator<String> keys = extra.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    ev.put(k, extra.opt(k));
                }
            }
            loc.addDrivingEvent(ev);
        } catch (org.json.JSONException ignored) {}
    }

    /** v4.5.1 — acquire a short, time-bounded wake lock when wakeLockMode is 'posting'. */
    private void acquireWakeLockForPosting() {
        if (mWakeLock == null || mConfig == null) return;
        String mode = mConfig.getWakeLockMode() != null ? mConfig.getWakeLockMode() : "posting";
        if (!"posting".equals(mode)) return;
        try {
            // Bounded: SQLite write + HTTP POST should finish well within 30 s.
            if (!mWakeLock.isHeld()) mWakeLock.acquire(30_000L);
        } catch (Throwable ignored) { /* best-effort */ }
    }

    /** v4.4 — read current device battery via sticky broadcast and stamp it onto the location.
     *  No permission required. Sticky broadcast returns instantly without blocking.
     *  v4.4.1: route through the application context to bypass our own registerReceiver()
     *  override (which forces RECEIVER_NOT_EXPORTED + handler — incompatible with sticky-only reads). */
    private void attachBatterySnapshot(BackgroundLocation loc) {
        if (loc == null) return;
        try {
            android.content.IntentFilter filter = new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
            android.content.Intent batteryStatus = getApplicationContext().registerReceiver(null, filter);
            if (batteryStatus == null) return;
            int level = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1);
            if (level >= 0 && scale > 0) {
                loc.setBatteryLevel((int) Math.round(level * 100.0 / scale));
            }
            int status = batteryStatus.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            boolean charging = (status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                    || status == android.os.BatteryManager.BATTERY_STATUS_FULL);
            loc.setCharging(charging);
        } catch (Throwable ignored) { /* best-effort; never fail the fix */ }
    }

    /** v4.3 — drain pending events (those fired without a simultaneous fix) onto this location.
     *  v4.4.1: drop entries older than PENDING_DRIVING_EVENTS_TTL_MS so we don't anexar an event
     *  whose context (location, speed, etc.) is no longer relevant. */
    private void flushPendingDrivingEvents(BackgroundLocation loc) {
        if (loc == null) return;
        long now = System.currentTimeMillis();
        synchronized (mPendingDrivingEvents) {
            int n = mPendingDrivingEvents.length();
            if (n == 0) return;
            for (int i = 0; i < n; i++) {
                org.json.JSONObject ev = mPendingDrivingEvents.optJSONObject(i);
                if (ev == null) continue;
                long t = ev.optLong("time", now);
                if (now - t <= PENDING_DRIVING_EVENTS_TTL_MS) {
                    loc.addDrivingEvent(ev);
                }
            }
            for (int i = n - 1; i >= 0; i--) mPendingDrivingEvents.remove(i);
        }
    }

    /** v4.4.1 — append to pending events with cap (oldest evicted). */
    private void enqueuePendingDrivingEvent(org.json.JSONObject ev) {
        if (ev == null) return;
        synchronized (mPendingDrivingEvents) {
            while (mPendingDrivingEvents.length() >= PENDING_DRIVING_EVENTS_MAX) {
                mPendingDrivingEvents.remove(0);
            }
            mPendingDrivingEvents.put(ev);
        }
    }

    @Override
    public synchronized void startForegroundService() {
        start();
        startForeground();
    }

    @Override
    public synchronized void stop() {
        if (!sIsRunning) {
            return;
        }

        mMainHandler.removeCallbacks(mWatchdogRunnable);
        mMainHandler.removeCallbacks(mNotificationUpdateRunnable);

        if (mWakeLock != null && mWakeLock.isHeld()) {
            try {
                mWakeLock.release();
                logger.debug("Wake lock released");
            } catch (Exception e) {
                logger.warn("Wake lock release failed", e);
            }
        }

        if (mProvider != null) {
            mProvider.onStop();
        }

        mIsInForeground = false;
        stopForeground(true);
        stopSelf();

        // v3.5 Phase 4: stop heartbeat scheduler.
        cancelHeartbeat();
        // v4.0 Phase 6: reset driver-insights state machine.
        if (mDrivingDetector != null) mDrivingDetector.reset();
        // v4.2 Phase 8: stop sensor fusion sampling.
        mDrivingTripActive = false;
        if (mSensorFusion != null) {
            mSensorFusion.setTripActive(false);
            mSensorFusion.stop();
        }

        broadcastMessage(MSG_ON_SERVICE_STOPPED);
        sIsRunning = false;
    }

    /**
     * Returns true if the app has at least one of the location runtime permissions.
     * Required before starting a location foreground service on API 34+.
     */
    private boolean hasLocationPermission() {
        // v4.5.1 — ContextCompat handles API < 23 (permissions granted at install time).
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Reads this service's foregroundServiceType from the merged AndroidManifest (API 34+).
     * Uses ComponentInfoFlags.of(0) (not GET_META_DATA) so getServiceInfo returns complete ServiceInfo.
     * Returns the real value; never invents a hardcoded type. If unknown, returns 0 so callers must not call startForeground.
     * Requires compileSdk 33+ (ComponentInfoFlags); 34+ for ServiceInfo.foregroundServiceType.
     */
    private int getManifestForegroundServiceType() {
        if (Build.VERSION.SDK_INT < 34) return 0;

        try {
            ComponentName cn = new ComponentName(this, LocationServiceImpl.class);

            ServiceInfo si;
            if (Build.VERSION.SDK_INT >= 33) {
                si = getPackageManager().getServiceInfo(
                        cn,
                        PackageManager.ComponentInfoFlags.of(0)
                );
            } else {
                si = getPackageManager().getServiceInfo(cn, 0);
            }

            int t = getForegroundServiceTypeFromServiceInfo(si);
            logger.info("Manifest foregroundServiceType=0x{}", Integer.toHexString(t));
            return t;
        } catch (Throwable e) {
            logger.warn("getManifestForegroundServiceType failed: {}", e.getMessage());
            return 0;
        }
    }

    /** Read foregroundServiceType from ServiceInfo (field exists in API 34; use reflection to compile with compileSdk 33). */
    private int getForegroundServiceTypeFromServiceInfo(ServiceInfo si) {
        try {
            java.lang.reflect.Field f = ServiceInfo.class.getField("foregroundServiceType");
            Object v = f.get(si);
            return (v instanceof Integer) ? (Integer) v : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    @Override
    public void startForeground() {
        if (sIsRunning && !mIsInForeground) {
            if (!hasLocationPermission()) {
                logger.warn("Cannot start foreground: location permission not granted");
                return;
            }
            Config config = getConfig();
            String contentText = buildNotificationContentText(config);
            Notification notification = new NotificationHelper.NotificationFactory(this).getNotification(
                    config.getNotificationTitle(),
                    contentText,
                    config.getLargeNotificationIcon(),
                    config.getSmallNotificationIcon(),
                    config.getNotificationIconColor());

            if (mProvider != null) {
                mProvider.onCommand(LocationProvider.CMD_SWITCH_MODE,
                        LocationProvider.FOREGROUND_MODE);
            }
            // Android 14+ (API 34): type is required. Android 12-13 (API 31-33): type accepted (preferred).
            // FOREGROUND_SERVICE_TYPE_LOCATION = 0x00000008. Resolve from merged manifest first;
            // if reflection fails or manifest merge missed the attribute, fall back to LOCATION
            // hardcoded so the FGS still promotes (otherwise: no notification, no background tracking).
            try {
                if (Build.VERSION.SDK_INT >= 30) {
                    int type = getManifestForegroundServiceType();
                    if (type == 0) {
                        // Defensive fallback: every consumer of this plugin requires location FGS.
                        // Logging at warn so the failure is visible without breaking the service.
                        logger.warn("Manifest foregroundServiceType unreadable; defaulting to LOCATION (0x8). "
                                + "Verify merged AndroidManifest has foregroundServiceType=\"location\"." );
                        type = 0x00000008; // ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                    }
                    super.startForeground(NOTIFICATION_ID, notification, type);
                } else {
                    super.startForeground(NOTIFICATION_ID, notification);
                }
            } catch (Throwable t) {
                logger.error("startForeground threw {}; retrying without type", t.getMessage());
                try {
                    super.startForeground(NOTIFICATION_ID, notification);
                } catch (Throwable t2) {
                    logger.error("startForeground retry failed: {}", t2.getMessage());
                    return;
                }
            }
            mIsInForeground = true;
            scheduleNotificationUpdater();
        }
    }

    @Override
    public synchronized void stopForeground() {
        if (sIsRunning && mIsInForeground) {
            mMainHandler.removeCallbacks(mNotificationUpdateRunnable);
            stopForeground(true);
            if (mProvider != null) {
                mProvider.onCommand(LocationProvider.CMD_SWITCH_MODE,
                        LocationProvider.BACKGROUND_MODE);
            }
            mIsInForeground = false;
        }
    }

    /** Resource names for optional app-localized notification labels (showTime / showDistance). */
    private static final String RES_NOTIFICATION_TIME_LABEL = "plugin_bgloc_notification_time_label";
    private static final String RES_NOTIFICATION_DISTANCE_LABEL = "plugin_bgloc_notification_distance_label";

    private String getNotificationLabel(String resourceName, String defaultValue) {
        Context app = getApplicationContext();
        int id = app.getResources().getIdentifier(resourceName, "string", app.getPackageName());
        return (id != 0) ? app.getString(id) : defaultValue;
    }

    private String buildNotificationContentText(Config config) {
        String base = config.getNotificationText() != null ? config.getNotificationText() : "ENABLED";
        if (Boolean.TRUE.equals(config.getShowTime())) {
            String timeLabel = getNotificationLabel(RES_NOTIFICATION_TIME_LABEL, "Time");
            base += "\n" + timeLabel + ": " + formatElapsed(mSessionStartTime);
        }
        if (Boolean.TRUE.equals(config.getShowDistance())) {
            String distanceLabel = getNotificationLabel(RES_NOTIFICATION_DISTANCE_LABEL, "Distance");
            base += "\n" + distanceLabel + ": " + formatDistance(mSessionDistanceMeters);
        }
        return base;
    }

    private static String formatElapsed(long startTimeMs) {
        long elapsed = Math.max(0L, System.currentTimeMillis() - startTimeMs);
        long s = (elapsed / 1000L) % 60L;
        long m = (elapsed / 60000L) % 60L;
        long h = elapsed / 3600000L;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, s);
    }

    private static String formatDistance(double meters) {
        return String.format(Locale.US, "%.2f km", meters / 1000.0);
    }

    private void updateForegroundNotification() {
        if (!sIsRunning || !mIsInForeground || mConfig == null) {
            return;
        }
        String contentText = buildNotificationContentText(mConfig);
        Notification notification = new NotificationHelper.NotificationFactory(this).getNotification(
                mConfig.getNotificationTitle(),
                contentText,
                mConfig.getLargeNotificationIcon(),
                mConfig.getSmallNotificationIcon(),
                mConfig.getNotificationIconColor());
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID, notification);
        }
    }

    private void scheduleNotificationUpdater() {
        mMainHandler.removeCallbacks(mNotificationUpdateRunnable);
        if (mConfig == null) {
            return;
        }
        boolean showTime = Boolean.TRUE.equals(mConfig.getShowTime());
        boolean showDistance = Boolean.TRUE.equals(mConfig.getShowDistance());
        if ((showTime || showDistance) && sIsRunning && mIsInForeground) {
            mMainHandler.postDelayed(mNotificationUpdateRunnable, NOTIFICATION_UPDATE_INTERVAL_MS);
        }
    }

    @Override
    public synchronized void configure(Config config) {
        if (mConfig == null) {
            mConfig = config;
            return;
        }

        final Config currentConfig = mConfig;
        mConfig = config;

        mPostLocationTask.setConfig(mConfig);

        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (sIsRunning) {
                    if (currentConfig.getStartForeground() == true && mConfig.getStartForeground() == false) {
                        stopForeground();
                    }

                    if (mConfig.getStartForeground() == true) {
                        if (currentConfig.getStartForeground() == false) {
                            // was not running in foreground, so start in foreground
                            startForeground();
                        } else {
                            // was running in foreground, so just update existing notification
                            String contentText = buildNotificationContentText(mConfig);
                            Notification notification = new NotificationHelper.NotificationFactory(LocationServiceImpl.this).getNotification(
                                    mConfig.getNotificationTitle(),
                                    contentText,
                                    mConfig.getLargeNotificationIcon(),
                                    mConfig.getSmallNotificationIcon(),
                                    mConfig.getNotificationIconColor());

                            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                            notificationManager.notify(NOTIFICATION_ID, notification);
                            scheduleNotificationUpdater();
                        }
                    }
                }

                if (currentConfig.getLocationProvider() != mConfig.getLocationProvider()) {
                    boolean shouldStart = mProvider.isStarted();
                    mProvider.onDestroy();
                    LocationProviderFactory spf = new LocationProviderFactory(LocationServiceImpl.this);
                    mProvider = spf.getInstance(mConfig.getLocationProvider());
                    mProvider.setDelegate(LocationServiceImpl.this);
                    mProvider.onCreate();
                    mProvider.onConfigure(mConfig);
                    if (shouldStart) {
                        mProvider.onStart();
                    }
                } else {
                    mProvider.onConfigure(mConfig);
                }

                // v4.1: re-evaluate hot-reload features when config changes while service is running.
                if (sIsRunning) {
                    Integer prevHb = currentConfig.getHeartbeatInterval();
                    Integer newHb = mConfig.getHeartbeatInterval();
                    if (prevHb == null) prevHb = 0;
                    if (newHb == null) newHb = 0;
                    if (!prevHb.equals(newHb)) {
                        scheduleHeartbeat(); // cancels and reschedules with the new interval (or stops if 0)
                    }
                    // Driver-insights detector: rebuild if the config dict changed.
                    Config.DrivingEventsOptions prevDe = currentConfig.getDrivingEvents();
                    Config.DrivingEventsOptions newDe = mConfig.getDrivingEvents();
                    if (!equalsDrivingEvents(prevDe, newDe)) {
                        configureDrivingDetector();
                    }
                    // v4.5.1 — hot-reload wakeLockMode: when transitioning between always /
                    // posting / none, the existing permanent lock (if any) must be released,
                    // or a new permanent lock acquired. Without this, switching mode at runtime
                    // either leaked CPU or left the service running without the requested lock.
                    String prevWl = currentConfig.getWakeLockMode() != null ? currentConfig.getWakeLockMode() : "posting";
                    String newWl  = mConfig.getWakeLockMode()      != null ? mConfig.getWakeLockMode()      : "posting";
                    if (!prevWl.equals(newWl) && mWakeLock != null) {
                        if ("always".equals(newWl)) {
                            if (!mWakeLock.isHeld()) {
                                try { mWakeLock.acquire(); logger.debug("Wake lock acquired (hot-reload → always)"); }
                                catch (Throwable t) { logger.warn("Wake lock acquire failed", t); }
                            }
                        } else {
                            // 'posting' or 'none' — release any permanent lock; per-fix lock continues to work via acquireWakeLockForPosting().
                            if (mWakeLock.isHeld()) {
                                try { mWakeLock.release(); logger.debug("Wake lock released (hot-reload → {})", newWl); }
                                catch (Throwable t) { logger.warn("Wake lock release failed", t); }
                            }
                        }
                    }
                }
            }
        });
    }

    /** Shallow value equality for DrivingEventsOptions; avoids needless detector rebuilds. */
    private static boolean equalsDrivingEvents(Config.DrivingEventsOptions a, Config.DrivingEventsOptions b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.enabled == b.enabled
                && a.speedLimitKmh == b.speedLimitKmh
                && a.minMovingSpeedMps == b.minMovingSpeedMps
                && a.stoppedDurationMs == b.stoppedDurationMs
                && a.minTripSpeedMps == b.minTripSpeedMps
                && a.minTripDurationMs == b.minTripDurationMs
                && a.hardBrakeMps2 == b.hardBrakeMps2
                && a.rapidAccelMps2 == b.rapidAccelMps2
                && a.sharpTurnDegPerSec == b.sharpTurnDegPerSec
                && a.crashImpactKmh == b.crashImpactKmh
                && a.crashWindowMs == b.crashWindowMs
                && a.sensorFusion == b.sensorFusion
                && a.crashImpactG == b.crashImpactG
                && a.sensorCrashCooldownMs == b.sensorCrashCooldownMs
                && a.phoneUsageWindowMs == b.phoneUsageWindowMs
                && a.phoneUsageCooldownMs == b.phoneUsageCooldownMs;
    }

    @Override
    public synchronized void registerHeadlessTask(String taskRunnerClass) {
        logger.debug("Registering headless task");
        mHeadlessTaskRunnerClass = taskRunnerClass;
    }

    @Override
    public synchronized void startHeadlessTask() {
        if (mHeadlessTaskRunnerClass != null) {
            TaskRunnerFactory trf = new TaskRunnerFactory();
            try {
                mHeadlessTaskRunner = trf.getTaskRunner(mHeadlessTaskRunnerClass);
                ((AbstractTaskRunner) mHeadlessTaskRunner).setContext(this);
            } catch (Exception e) {
                logger.error("Headless task start failed: {}", e.getMessage());
            }
        }
    }

    @Override
    public synchronized void stopHeadlessTask() {
        mHeadlessTaskRunner = null;
    }

    @Override
    public synchronized void executeProviderCommand(final int command, final int arg1) {
        if (mProvider == null) {
            return;
        }

        ThreadUtils.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProvider.onCommand(command, arg1);
            }
        });
    }

    @Override
    public void onLocation(BackgroundLocation location) {
        // v4.5.1: in 'posting' wake-lock mode, hold the CPU briefly so SQLite writes + HTTP
        // POST finish before the system returns to deep sleep. 30s ceiling — plenty for a fix.
        acquireWakeLockForPosting();
        mLastLocationTime = System.currentTimeMillis();
        mLastReceivedLocation = location;
        sLastReceivedLocation = location;

        // v4.0 Phase 6: feed the driver-insights state machine on the *raw* location so speed/bearing
        // come straight from the sensors. Listener attaches events to this same instance.
        if (mDrivingDetector != null) {
            mDrivingDetector.onLocation(location);
        }
        // v4.2 Phase 8: keep sensor pipeline aware of the latest raw fix.
        if (mSensorFusion != null) {
            mSensorFusion.setLastLocation(location);
        }
        if (Boolean.TRUE.equals(mConfig != null ? mConfig.getShowDistance() : null)) {
            double lat = location.getLatitude();
            double lon = location.getLongitude();
            if (mHasLastLocation) {
                float[] dist = new float[1];
                Location.distanceBetween(mLastLat, mLastLon, lat, lon, dist);
                mSessionDistanceMeters += (double) dist[0];
            }
            mLastLat = lat;
            mLastLon = lon;
            mHasLastLocation = true;
            if (mIsInForeground && mConfig != null && (Boolean.TRUE.equals(mConfig.getShowTime()) || Boolean.TRUE.equals(mConfig.getShowDistance()))) {
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        updateForegroundNotification();
                    }
                });
            }
        }
        logger.debug("New location {}", location.toString());

        // v4.5.1 — events were attached to the RAW location above (so detector heuristics see real
        // speed/bearing). If transformLocation() returns a NEW instance, we'd lose those events
        // and the battery snapshot. Solution: copy them across to the transformed instance below.
        org.json.JSONArray rawEvents = location.getDrivingEvents();
        Integer rawBatteryLevel = null;
        Boolean rawIsCharging = null;

        location = transformLocation(location);
        if (location == null) {
            logger.debug("Skipping location as requested by the locationTransform");
            return;
        }

        // Re-attach events to the transformed location if the transform produced a new instance.
        if (rawEvents != null && rawEvents.length() > 0 && location.getDrivingEvents() != rawEvents) {
            try {
                org.json.JSONArray copy = new org.json.JSONArray(rawEvents.toString());
                for (int i = 0; i < copy.length(); i++) {
                    org.json.JSONObject ev = copy.optJSONObject(i);
                    if (ev != null) location.addDrivingEvent(ev);
                }
            } catch (org.json.JSONException ignored) {}
        }
        // v4.3: drain pending events (providerChange/sensor crash/phone usage) onto the post-transform
        // instance so they always reach the backend.
        flushPendingDrivingEvents(location);
        // v4.4: stamp device battery snapshot onto the *transformed* location so it survives any
        // user-supplied locationTransform that creates a new instance.
        if (mConfig == null || !Boolean.FALSE.equals(mConfig.getIncludeBattery())) {
            attachBatterySnapshot(location);
        }

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_LOCATION);
        bundle.putParcelable("payload", location);
        broadcastMessage(bundle);

        runHeadlessTask(new LocationTask(location) {
            @Override
            public void onError(String errorMessage) {
                logger.error("Location task error: {}", errorMessage);
            }

            @Override
            public void onResult(String value) {
                logger.debug("Location task result: {}", value);
            }
        });

        postLocation(location);
    }

    @Override
    public void onStationary(BackgroundLocation location) {
        logger.debug("New stationary {}", location.toString());

        location = transformLocation(location);
        if (location == null) {
            logger.debug("Skipping location as requested by the locationTransform");
            return;
        }
        // v4.5.1 — same enrichment as regular fixes: drain pending events and stamp battery.
        flushPendingDrivingEvents(location);
        if (mConfig == null || !Boolean.FALSE.equals(mConfig.getIncludeBattery())) {
            attachBatterySnapshot(location);
        }

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_STATIONARY);
        bundle.putParcelable("payload", location);
        broadcastMessage(bundle);

        runHeadlessTask(new StationaryTask(location){
            @Override
            public void onError(String errorMessage) {
                logger.error("Stationary task error: {}", errorMessage);
            }

            @Override
            public void onResult(String value) {
                logger.debug("Stationary task result: {}", value);
            }
        });

        postLocation(location);
    }

    @Override
    public void onActivity(BackgroundActivity activity) {
        logger.debug("New activity {}", activity.toString());

        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_ACTIVITY);
        bundle.putParcelable("payload", activity);
        broadcastMessage(bundle);

        runHeadlessTask(new ActivityTask(activity){
            @Override
            public void onError(String errorMessage) {
                logger.error("Activity task error: {}", errorMessage);
            }

            @Override
            public void onResult(String value) {
                logger.debug("Activity task result: {}", value);
            }
        });
    }

    @Override
    public void onError(PluginException error) {
        Bundle bundle = new Bundle();
        bundle.putInt("action", MSG_ON_ERROR);
        bundle.putBundle("payload", error.toBundle());
        broadcastMessage(bundle);
    }

    private void broadcastMessage(int msgId) {
        Bundle bundle = new Bundle();
        bundle.putInt("action", msgId);
        broadcastMessage(bundle);
    }

    private void broadcastServiceRestarted(String reason) {
        Bundle b = new Bundle();
        b.putInt("action", MSG_ON_SERVICE_RESTARTED);
        b.putString("reason", reason);
        broadcastMessage(b);
    }

    private void broadcastMessage(Bundle bundle) {
        Intent intent = new Intent(ACTION_BROADCAST);
        intent.putExtras(bundle);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
    }

    /** v3.5 Phase 4: schedule periodic heartbeat broadcasts using {@link Config#getHeartbeatInterval()}. */
    private void scheduleHeartbeat() {
        cancelHeartbeat();
        if (mConfig == null) return;
        Integer interval = mConfig.getHeartbeatInterval();
        if (interval == null || interval <= 0) return;
        logger.debug("Scheduling heartbeat every {} ms", interval);
        mHeartbeatExecutor = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();
        mHeartbeatTask = mHeartbeatExecutor.scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                try {
                    Bundle b = new Bundle();
                    b.putInt("action", MSG_ON_HEARTBEAT);
                    BackgroundLocation last = mLastReceivedLocation;
                    if (last != null) b.putParcelable("payload", last);
                    broadcastMessage(b);
                } catch (Throwable t) {
                    logger.warn("Heartbeat tick failed: {}", t.getMessage());
                }
            }
        }, interval, interval, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private void cancelHeartbeat() {
        if (mHeartbeatTask != null) {
            mHeartbeatTask.cancel(false);
            mHeartbeatTask = null;
        }
        if (mHeartbeatExecutor != null) {
            mHeartbeatExecutor.shutdownNow();
            mHeartbeatExecutor = null;
        }
    }

    @Override
    public Intent registerReceiver(BroadcastReceiver receiver, IntentFilter filter) {
        // v4.5.1 — RECEIVER_NOT_EXPORTED flag is required on Android 13+ (API 33) for non-system
        // broadcasts and the 5-arg overload exists only from API 26. Guard for older OSs.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return super.registerReceiver(receiver, filter, null, mServiceHandler, Context.RECEIVER_NOT_EXPORTED);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return super.registerReceiver(receiver, filter, null, mServiceHandler);
        }
        return super.registerReceiver(receiver, filter);
    }

    @Override
    public void unregisterReceiver(BroadcastReceiver receiver) {
        try {
            super.unregisterReceiver(receiver);
        } catch (IllegalArgumentException ex) {
            // if was not registered ignore exception
        }
    }

    public Config getConfig() {
        Config config = mConfig;
        if (config == null) {
            ConfigurationDAO dao = DAOFactory.createConfigurationDAO(this);
            try {
                config = dao.retrieveConfiguration();
            } catch (JSONException e) {
                logger.error("Config exception: {}", e.getMessage());
            }
        }

        if (config == null) {
            config = Config.getDefault();
        }

        mConfig = config;
        return mConfig;
    }

    public static void setLocationProviderFactory(LocationProviderFactory factory) {
        sLocationProviderFactory = factory;
    }

    private void runHeadlessTask(Task task) {
        if (mHeadlessTaskRunner == null) {
            return;
        }

        logger.debug("Running headless task: {}", task);
        mHeadlessTaskRunner.runTask(task);
    }

    /**
     * Class used for the client Binder.  Since this service runs in the same process as its
     * clients, we don't need to deal with IPC.
     */
    public class LocalBinder extends Binder {
        public LocationServiceImpl getService() {
            return LocationServiceImpl.this;
        }
    }

    private BackgroundLocation transformLocation(BackgroundLocation location) {
        if (sLocationTransform != null) {
            return sLocationTransform.transformLocationBeforeCommit(this, location);
        }

        return location;
    }

    private void postLocation(BackgroundLocation location) {
        mPostLocationTask.add(location);
    }

    public void handleRequestedAbortUpdates() {
        broadcastMessage(MSG_ON_ABORT_REQUESTED);
    }

    public void handleHttpAuthorizationUpdates() {
        broadcastMessage(MSG_ON_HTTP_AUTHORIZATION);
    }

    /**
     * Broadcast receiver which detects connectivity change condition
     */
    private BroadcastReceiver connectivityChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean hasConnectivity = isNetworkAvailable();
            mPostLocationTask.setHasConnectivity(hasConnectivity);
            logger.info("Network condition changed has connectivity: {}", hasConnectivity);
        }
    };

    private boolean isNetworkAvailable() {
        ConnectivityManager cm =
                (ConnectivityManager) this.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    public long getServiceId() {
        return mServiceId;
    }

    public boolean isBound() {
        LocationServiceInfo info = new LocationServiceInfoImpl(this);
        return info.isBound();
    }

    public static boolean isRunning() {
        return sIsRunning;
    }

    public static void setLocationTransform(@Nullable LocationTransform transform) {
        sLocationTransform = transform;
    }

    public static @Nullable LocationTransform getLocationTransform() {
        return sLocationTransform;
    }
}
