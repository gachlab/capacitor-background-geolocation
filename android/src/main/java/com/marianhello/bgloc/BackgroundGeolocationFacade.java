package com.marianhello.bgloc;

import android.Manifest;
import android.accounts.Account;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.TextUtils;

import com.github.jparkie.promise.Promise;
import com.intentfilter.androidpermissions.PermissionManager;
import com.intentfilter.androidpermissions.models.DeniedPermissions;
import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.ConfigurationDAO;
import com.marianhello.bgloc.data.DAOFactory;
import com.marianhello.bgloc.data.LocationDAO;
import com.marianhello.bgloc.data.SessionLocationDAO;
import com.marianhello.bgloc.provider.LocationProvider;
import com.marianhello.bgloc.service.LocationService;
import com.marianhello.bgloc.service.LocationServiceImpl;
import com.marianhello.bgloc.service.LocationServiceProxy;
import com.marianhello.bgloc.data.LocationTransform;
import com.marianhello.bgloc.data.sqlite.SQLiteSessionLocationDAO;
import com.marianhello.bgloc.sync.AccountHelper;
import com.marianhello.bgloc.sync.NotificationHelper;
import com.marianhello.bgloc.sync.SyncService;
import com.marianhello.logging.DBLogReader;
import com.marianhello.logging.LogEntry;
import com.marianhello.logging.LoggerManager;
import com.marianhello.logging.UncaughtExceptionLogger;

import org.json.JSONException;
import org.slf4j.event.Level;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.TimeoutException;

public class BackgroundGeolocationFacade {

    public static final int SERVICE_STARTED = 1;
    public static final int SERVICE_STOPPED = 0;
    public static final int AUTHORIZATION_AUTHORIZED = 1;
    public static final int AUTHORIZATION_DENIED = 0;

    public static final String[] PERMISSIONS = {
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
    };

    private boolean mServiceBroadcastReceiverRegistered = false;
    private boolean mLocationModeChangeReceiverRegistered = false;
    private boolean mIsPaused = false;

    private Config mConfig;
    private final Context mContext;
    private final PluginDelegate mDelegate;
    private final LocationService mService;

    private BackgroundLocation mStationaryLocation;

    private org.slf4j.Logger logger;

    public BackgroundGeolocationFacade(Context context, PluginDelegate delegate) {
        mContext = context;
        mDelegate = delegate;
        mService = new LocationServiceProxy(context);

        UncaughtExceptionLogger.register(context.getApplicationContext());

        logger = LoggerManager.getLogger(BackgroundGeolocationFacade.class);
        LoggerManager.enableDBLogging();

        logger.info("Initializing plugin");

        NotificationHelper.registerAllChannels(getApplicationContext());
    }

    private BroadcastReceiver locationModeChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            logger.debug("Authorization has changed");
            mDelegate.onAuthorizationChanged(getAuthorizationStatus());
        }
    };

    private BroadcastReceiver serviceBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Bundle bundle = intent.getExtras();
            int action = bundle.getInt("action");

            switch (action) {
                case LocationServiceImpl.MSG_ON_LOCATION: {
                    logger.debug("Received MSG_ON_LOCATION");
                    bundle.setClassLoader(LocationServiceImpl.class.getClassLoader());
                    BackgroundLocation location = (BackgroundLocation) bundle.getParcelable("payload");
                    mDelegate.onLocationChanged(location);
                    return;
                }

                case LocationServiceImpl.MSG_ON_STATIONARY: {
                    logger.debug("Received MSG_ON_STATIONARY");
                    bundle.setClassLoader(LocationServiceImpl.class.getClassLoader());
                    BackgroundLocation location = (BackgroundLocation) bundle.getParcelable("payload");
                    mStationaryLocation = location;
                    mDelegate.onStationaryChanged(location);
                    return;
                }

                case LocationServiceImpl.MSG_ON_ACTIVITY: {
                    logger.debug("Received MSG_ON_ACTIVITY");
                    bundle.setClassLoader(LocationServiceImpl.class.getClassLoader());
                    BackgroundActivity activity = (BackgroundActivity) bundle.getParcelable("payload");
                    mDelegate.onActivityChanged(activity);
                    return;
                }

                case LocationServiceImpl.MSG_ON_ERROR: {
                    logger.debug("Received MSG_ON_ERROR");
                    Bundle errorBundle = bundle.getBundle("payload");
                    Integer errorCode = errorBundle.getInt("code");
                    String errorMessage = errorBundle.getString("message");
                    mDelegate.onError(new PluginException(errorMessage, errorCode));
                    return;
                }

                case LocationServiceImpl.MSG_ON_SERVICE_STARTED: {
                    logger.debug("Received MSG_ON_SERVICE_STARTED");
                    mDelegate.onServiceStatusChanged(SERVICE_STARTED);
                    return;
                }

                case LocationServiceImpl.MSG_ON_SERVICE_STOPPED: {
                    logger.debug("Received MSG_ON_SERVICE_STOPPED");
                    mDelegate.onServiceStatusChanged(SERVICE_STOPPED);
                    return;
                }

                case LocationServiceImpl.MSG_ON_ABORT_REQUESTED: {
                    logger.debug("Received MSG_ON_ABORT_REQUESTED");

                    if (mDelegate != null) {
                        // We have a delegate, tell it that there's a request.
                        // It will decide whether to stop or not.
                        mDelegate.onAbortRequested();
                    } else {
                        // No delegate, we may be running in the background.
                        // Let's just stop.
                        stop();
                    }

                    return;
                }

                case LocationServiceImpl.MSG_ON_HTTP_AUTHORIZATION: {
                    logger.debug("Received MSG_ON_HTTP_AUTHORIZATION");

                    if (mDelegate != null) {
                        mDelegate.onHttpAuthorization();
                    }

                    return;
                }

                case LocationServiceImpl.MSG_ON_SYNC_START: {
                    logger.debug("Received MSG_ON_SYNC_START");
                    if (mDelegate != null) mDelegate.onSyncStart();
                    return;
                }

                case LocationServiceImpl.MSG_ON_SYNC_SUCCESS: {
                    int sent = bundle != null ? bundle.getInt("sent", 0) : 0;
                    logger.debug("Received MSG_ON_SYNC_SUCCESS sent={}", sent);
                    if (mDelegate != null) mDelegate.onSyncSuccess(sent);
                    return;
                }

                case LocationServiceImpl.MSG_ON_SYNC_ERROR: {
                    int status = bundle != null ? bundle.getInt("httpStatus", 0) : 0;
                    String msg = bundle != null ? bundle.getString("message", "") : "";
                    logger.debug("Received MSG_ON_SYNC_ERROR status={} message={}", status, msg);
                    if (mDelegate != null) mDelegate.onSyncError(status, msg);
                    return;
                }

                case LocationServiceImpl.MSG_ON_SYNC_PROGRESS: {
                    int progress = bundle != null ? bundle.getInt("progress", 0) : 0;
                    if (mDelegate != null) mDelegate.onSyncProgress(progress);
                    return;
                }

                case LocationServiceImpl.MSG_ON_HEARTBEAT: {
                    if (bundle != null) {
                        bundle.setClassLoader(LocationServiceImpl.class.getClassLoader());
                    }
                    BackgroundLocation hb = bundle != null ? (BackgroundLocation) bundle.getParcelable("payload") : null;
                    if (mDelegate != null) mDelegate.onHeartbeat(hb);
                    return;
                }

                // v4.0 Phase 6: driver-insights events
                case LocationServiceImpl.MSG_ON_TRIP_START:
                case LocationServiceImpl.MSG_ON_TRIP_END:
                case LocationServiceImpl.MSG_ON_MOVING:
                case LocationServiceImpl.MSG_ON_STOPPED:
                case LocationServiceImpl.MSG_ON_SPEEDING:
                case LocationServiceImpl.MSG_ON_SOS: {
                    if (bundle != null) bundle.setClassLoader(LocationServiceImpl.class.getClassLoader());
                    BackgroundLocation loc = bundle != null ? (BackgroundLocation) bundle.getParcelable("payload") : null;
                    if (mDelegate == null) return;
                    switch (action) {
                        case LocationServiceImpl.MSG_ON_TRIP_START:
                            mDelegate.onTripStart(loc); break;
                        case LocationServiceImpl.MSG_ON_TRIP_END:
                            double dist = bundle != null ? bundle.getDouble("distance", 0.0) : 0.0;
                            long durMs = bundle != null ? bundle.getLong("durationMs", 0L) : 0L;
                            mDelegate.onTripEnd(loc, dist, durMs); break;
                        case LocationServiceImpl.MSG_ON_MOVING:
                            mDelegate.onMoving(loc); break;
                        case LocationServiceImpl.MSG_ON_STOPPED:
                            mDelegate.onStopped(loc); break;
                        case LocationServiceImpl.MSG_ON_SPEEDING:
                            double sKmh = bundle != null ? bundle.getDouble("speedKmh", 0.0) : 0.0;
                            double lKmh = bundle != null ? bundle.getDouble("limitKmh", 0.0) : 0.0;
                            mDelegate.onSpeeding(loc, sKmh, lKmh); break;
                        case LocationServiceImpl.MSG_ON_SOS:
                            org.json.JSONObject sosPayload = null;
                            if (bundle != null) {
                                String s = bundle.getString("sosPayload");
                                if (s != null) {
                                    try { sosPayload = new org.json.JSONObject(s); }
                                    catch (org.json.JSONException ignored) { sosPayload = new org.json.JSONObject(); }
                                }
                            }
                            mDelegate.onSOS(loc, sosPayload); break;
                    }
                    return;
                }

                case LocationServiceImpl.MSG_ON_PROVIDER_CHANGE: {
                    String provider = bundle != null ? bundle.getString("provider", "") : "";
                    if (mDelegate != null) mDelegate.onProviderChange(provider);
                    return;
                }

                // v4.1 GPS-derived sensor-like events (and v4.2 sensor-driven possibleCrash)
                case LocationServiceImpl.MSG_ON_HARD_BRAKE:
                case LocationServiceImpl.MSG_ON_RAPID_ACCELERATION:
                case LocationServiceImpl.MSG_ON_SHARP_TURN:
                case LocationServiceImpl.MSG_ON_POSSIBLE_CRASH: {
                    if (bundle != null) bundle.setClassLoader(LocationServiceImpl.class.getClassLoader());
                    BackgroundLocation drvLoc = bundle != null ? (BackgroundLocation) bundle.getParcelable("payload") : null;
                    double drvVal = bundle != null ? bundle.getDouble("value", 0.0) : 0.0;
                    String drvSrc = bundle != null ? bundle.getString("source", "gps") : "gps";
                    if (mDelegate == null) return;
                    switch (action) {
                        case LocationServiceImpl.MSG_ON_HARD_BRAKE:
                            mDelegate.onHardBrake(drvLoc, drvVal); break;
                        case LocationServiceImpl.MSG_ON_RAPID_ACCELERATION:
                            mDelegate.onRapidAcceleration(drvLoc, drvVal); break;
                        case LocationServiceImpl.MSG_ON_SHARP_TURN:
                            mDelegate.onSharpTurn(drvLoc, drvVal); break;
                        case LocationServiceImpl.MSG_ON_POSSIBLE_CRASH:
                            mDelegate.onPossibleCrash(drvLoc, drvVal, drvSrc); break;
                    }
                    return;
                }
                // v4.2 sensor fusion: phone usage while driving
                case LocationServiceImpl.MSG_ON_PHONE_USAGE_WHILE_DRIVING: {
                    if (bundle != null) bundle.setClassLoader(LocationServiceImpl.class.getClassLoader());
                    BackgroundLocation puLoc = bundle != null ? (BackgroundLocation) bundle.getParcelable("payload") : null;
                    if (mDelegate != null) mDelegate.onPhoneUsageWhileDriving(puLoc);
                    return;
                }
                // P2: service restarted by watchdog, OS kill, or boot
                case LocationServiceImpl.MSG_ON_SERVICE_RESTARTED: {
                    String reason = bundle != null ? bundle.getString("reason", "watchdog") : "watchdog";
                    logger.debug("Received MSG_ON_SERVICE_RESTARTED reason={}", reason);
                    if (mDelegate != null) mDelegate.onServiceRestarted(reason);
                    return;
                }
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private synchronized void registerLocationModeChangeReceiver() {
        if (mLocationModeChangeReceiverRegistered) return;

        getContext().registerReceiver(locationModeChangeReceiver, new IntentFilter(android.location.LocationManager.MODE_CHANGED_ACTION));
        mLocationModeChangeReceiverRegistered = true;
    }

    private synchronized void unregisterLocationModeChangeReceiver() {
        if (!mLocationModeChangeReceiverRegistered) return;

        Context context = getContext();
        if (context != null) {
            context.unregisterReceiver(locationModeChangeReceiver);
        }
        mLocationModeChangeReceiverRegistered = false;
    }

    private synchronized void registerServiceBroadcast() {
        if (mServiceBroadcastReceiverRegistered) return;

        LocalBroadcastManager.getInstance(getContext()).registerReceiver(serviceBroadcastReceiver, new IntentFilter(LocationServiceImpl.ACTION_BROADCAST));
        mServiceBroadcastReceiverRegistered = true;
    }

    private synchronized void unregisterServiceBroadcast() {
        if (!mServiceBroadcastReceiverRegistered) return;

        Context context = getContext();
        if (context != null) {
            LocalBroadcastManager.getInstance(context).unregisterReceiver(serviceBroadcastReceiver);
        }

        mServiceBroadcastReceiverRegistered = false;
    }

    public void start() {
        logger.debug("Starting service");

        PermissionManager permissionManager = PermissionManager.getInstance(getContext());
        permissionManager.checkPermissions(Arrays.asList(PERMISSIONS), new PermissionManager.PermissionRequestListener() {
            @Override
            public void onPermissionGranted() {
                logger.info("User granted requested permissions");
                // watch location mode changes
                registerLocationModeChangeReceiver();
                registerServiceBroadcast();
                startBackgroundService();
            }

            @Override
            public void onPermissionDenied(DeniedPermissions deniedPermissions) {
                logger.info("User denied requested permissions");
                if (mDelegate != null) {
                    mDelegate.onAuthorizationChanged(BackgroundGeolocationFacade.AUTHORIZATION_DENIED);
                }
            }
        });
        permissionManager.checkPermissions(Arrays.asList(Manifest.permission.POST_NOTIFICATIONS), new PermissionManager.PermissionRequestListener() {
            @Override
            public void onPermissionGranted() {} // noop

            @Override
            public void onPermissionDenied(DeniedPermissions deniedPermissions) {} // noop
        });
    }

    public void stop() {
        logger.debug("Stopping service");
        unregisterLocationModeChangeReceiver();
        // Note: we cannot unregistered service broadcast here
        // because no stop notification from service will arrive
        // unregisterServiceBroadcast();

        stopBackgroundService();
    }

    public void pause() {
        mIsPaused = true;
        mService.startForeground();
    }

    public void resume() {
        mIsPaused = false;
        mService.stopHeadlessTask();
        if (!getConfig().getStartForeground()) {
            mService.stopForeground();
        }
    }

    public void destroy() {
        logger.info("Destroying plugin");

        unregisterLocationModeChangeReceiver();
        unregisterServiceBroadcast();

        if (getConfig().getStopOnTerminate()) {
            stopBackgroundService();
        } else {
            mService.startHeadlessTask();
        }
    }

    public Collection<BackgroundLocation> getLocations() {
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        return dao.getAllLocations();
    }

    public Collection<BackgroundLocation> getValidLocations() {
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        return dao.getValidLocations();
    }

    public Collection<BackgroundLocation> getValidLocationsAndDelete() {
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        return dao.getValidLocationsAndDelete();
    }

    /** Clear session table and start storing all new locations in session. Call when user starts a route. */
    public void startSession() {
        SessionLocationDAO dao = new SQLiteSessionLocationDAO(getContext());
        dao.startSession();
    }

    /** Return all locations stored in the current session (ordered by time). */
    public Collection<BackgroundLocation> getSessionLocations() {
        SessionLocationDAO dao = new SQLiteSessionLocationDAO(getContext());
        return dao.getSessionLocations();
    }

    /** Clear session table and stop storing. Call when route is finished and sync OK. */
    public void clearSession() {
        SessionLocationDAO dao = new SQLiteSessionLocationDAO(getContext());
        dao.clearSession();
    }

    /** Number of locations in the current session. */
    public int getSessionLocationsCount() {
        SessionLocationDAO dao = new SQLiteSessionLocationDAO(getContext());
        return dao.getSessionLocationsCount();
    }

    public BackgroundLocation getStationaryLocation() {
        return mStationaryLocation;
    }

    public void deleteLocation(Long locationId) {
        logger.info("Deleting location locationId={}", locationId);
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        dao.deleteLocationById(locationId.longValue());
    }

    public void deleteAllLocations() {
        logger.info("Deleting all locations");
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        dao.deleteAllLocations();
    }

    public BackgroundLocation getCurrentLocation(int timeout, long maximumAge, boolean enableHighAccuracy) throws PluginException {
        logger.info("Getting current location with timeout:{} maximumAge:{} enableHighAccuracy:{}", timeout, maximumAge, enableHighAccuracy);

        LocationManager locationManager = LocationManager.getInstance(getContext());
        Promise<Location> promise = locationManager.getCurrentLocation(timeout, maximumAge, enableHighAccuracy);
        try {
            promise.await();
            Location location = promise.get();
            if (location != null) {
                return BackgroundLocation.fromLocation(location);
            }

            Throwable error = promise.getError();
            if (error == null) {
                throw new PluginException("Location not available", 2); // LOCATION_UNAVAILABLE
            }
            if (error instanceof LocationManager.PermissionDeniedException) {
                logger.warn("Getting current location failed due missing permissions");
                throw new PluginException("Permission denied", 1); // PERMISSION_DENIED
            }
            if (error instanceof TimeoutException) {
                throw new PluginException("Location request timed out", 3); // TIME_OUT
            }

            throw new PluginException(error.getMessage(), 2); // LOCATION_UNAVAILABLE
        } catch (InterruptedException e) {
            logger.error("Interrupted while waiting location", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting location", e);
        }
    }

    public void switchMode(final int mode) {
        mService.executeProviderCommand(LocationProvider.CMD_SWITCH_MODE, mode);
    }

    public void sendCommand(final int commandId) {
        mService.executeProviderCommand(commandId, 0);
    }

    public synchronized void configure(Config config) throws PluginException {
        try
        {
            Config newConfig = Config.merge(getStoredConfig(), config);
            persistConfiguration(newConfig);
            logger.debug("Service configured with: {}", newConfig.toString());
            mConfig = newConfig;
            mService.configure(newConfig);
        } catch (Exception e) {
            logger.error("Configuration error: {}", e.getMessage());
            throw new PluginException("Configuration error", e, PluginException.CONFIGURE_ERROR);
        }
    }

    public synchronized Config getConfig() {
        if (mConfig != null) {
            return mConfig;
        }

        try {
            mConfig = getStoredConfig();
        } catch (PluginException e) {
            logger.error("Error getting stored config will use default", e.getMessage());
            mConfig = Config.getDefault();
        }

        return mConfig;
    }

    public synchronized Config getStoredConfig() throws PluginException {
        try {
            ConfigurationDAO dao = DAOFactory.createConfigurationDAO(getContext());
            Config config = dao.retrieveConfiguration();
            if (config == null) {
                config = Config.getDefault();
            }
            return config;
        } catch (JSONException e) {
            logger.error("Error getting stored config: {}", e.getMessage());
            throw new PluginException("Error getting stored config", e, PluginException.JSON_ERROR);
        }
    }

    public Collection<LogEntry> getLogEntries(int limit) {
        DBLogReader logReader = new DBLogReader();
        return logReader.getEntries(limit, 0, Level.DEBUG);
    }

    public Collection<LogEntry> getLogEntries(int limit, int offset, String minLevel) {
        DBLogReader logReader = new DBLogReader();
        return logReader.getEntries(limit, offset, Level.valueOf(minLevel));
    }

    /**
     * Force location sync
     *
     * Method is ignoring syncThreshold and also user sync settings preference
     * and sync locations to defined syncUrl. No-op if sync is disabled in config (sync: false).
     */
    public void forceSync() {
        Config config = getConfig();
        if (!Boolean.TRUE.equals(config.getSyncEnabled())) {
            logger.debug("Sync disabled in config, skipping forceSync");
            return;
        }
        logger.debug("Sync locations forced");
        ResourceResolver resolver = ResourceResolver.newInstance(getContext());
        Account syncAccount = AccountHelper.CreateSyncAccount(getContext(), resolver.getAccountName(),
                resolver.getAccountType());
        SyncService.sync(syncAccount, resolver.getAuthority(), true);
    }

    /**
     * v4.0 Phase 6 — Trigger an SOS event. The plugin emits a single `sos` JS event
     * carrying the latest known location and the user-supplied JSON payload.
     */
    public void triggerSOS(org.json.JSONObject payload) {
        Bundle b = new Bundle();
        b.putInt("action", LocationServiceImpl.MSG_ON_SOS);
        BackgroundLocation last = LocationServiceImpl.getLastReceivedLocation();
        if (last != null) b.putParcelable("payload", last);
        b.putString("sosPayload", payload != null ? payload.toString() : "{}");
        Intent intent = new Intent(LocationServiceImpl.ACTION_BROADCAST);
        intent.putExtras(b);
        androidx.localbroadcastmanager.content.LocalBroadcastManager
                .getInstance(getContext().getApplicationContext()).sendBroadcast(intent);
    }

    /**
     * Returns the number of locations pending to be synced (not yet sent to syncUrl).
     */
    public long getPendingSyncCount() {
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        return dao.getLocationsForSyncCount(Long.MAX_VALUE);
    }

    /**
     * Clear the pending sync queue: mark all locations waiting to be synced as deleted.
     * They will not be sent to syncUrl. Use when the user wants to discard pending locations.
     */
    public void clearSync() {
        LocationDAO dao = DAOFactory.createLocationDAO(getContext());
        int count = dao.deletePendingSyncLocations();
        logger.debug("Cleared {} pending sync locations", count);
    }

    public int getAuthorizationStatus() {
        return hasPermissions() ? AUTHORIZATION_AUTHORIZED : AUTHORIZATION_DENIED;
    }

    public boolean hasPermissions() {
        return hasPermissions(getContext(), PERMISSIONS);
    }

    public boolean locationServicesEnabled() throws PluginException {
        Context context = getContext();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            int locationMode = 0;
            try {
                locationMode = Settings.Secure.getInt(context.getContentResolver(), Settings.Secure.LOCATION_MODE);
                return locationMode != Settings.Secure.LOCATION_MODE_OFF;
            } catch (SettingNotFoundException e) {
                logger.error("Location services check failed", e);
                throw new PluginException("Location services check failed", e, PluginException.SETTINGS_ERROR);
            }
        } else {
            String locationProviders = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
            return !TextUtils.isEmpty(locationProviders);
        }
    }

    public void registerHeadlessTask(final String taskRunnerClass) {
        logger.info("Registering headless task: {}", taskRunnerClass);
        mService.registerHeadlessTask(taskRunnerClass);
    }

    private void startBackgroundService() {
        logger.info("Attempt to start bg service");
        if (mIsPaused) {
            mService.startForegroundService();
        } else {
            mService.start();
        }
    }

    private void stopBackgroundService() {
        logger.info("Attempt to stop bg service");
        mService.stop();
    }

    public boolean isRunning() {
        return ((LocationServiceProxy) mService).isRunning();
    }

    private void persistConfiguration(Config config) throws NullPointerException {
        ConfigurationDAO dao = DAOFactory.createConfigurationDAO(getContext());
        dao.persistConfiguration(config);
    }

    private Context getContext() {
        return mContext;
    }

    private Context getApplicationContext() {
        return mContext.getApplicationContext();
    }

    public static void showAppSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(Uri.parse("package:" + context.getPackageName()));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(intent);
    }

    public static void showLocationSettings(Context context) {
        Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        context.startActivity(intent);
    }

    public static boolean hasPermissions(Context context, String[] permissions) {
        for (String perm: permissions) {
            if (ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sets a transform for each coordinate about to be committed (sent or saved for later sync).
     * You can use this for modifying the coordinates in any way.
     *
     * If the transform returns <code>null</code>, it will prevent the location from being committed.
     * @param transform - the transform listener
     */
    public static void setLocationTransform(LocationTransform transform) {
        LocationServiceImpl.setLocationTransform(transform);
    }

    public static LocationTransform getLocationTransform() {
        return LocationServiceImpl.getLocationTransform();
    }
}
