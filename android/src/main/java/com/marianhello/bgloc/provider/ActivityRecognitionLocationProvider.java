package com.marianhello.bgloc.provider;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.Manifest;
import android.os.Build;
import android.os.Looper;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.location.ActivityRecognition;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.BackgroundActivity;

import java.util.List;


public class ActivityRecognitionLocationProvider extends AbstractLocationProvider {

    private static final String P_NAME = "com.marianhello.bgloc";
    private static final String DETECTED_ACTIVITY_UPDATE = P_NAME + ".DETECTED_ACTIVITY_UPDATE";

    private FusedLocationProviderClient fusedLocationClient;
    private ActivityRecognitionClient activityRecognitionClient;
    private PendingIntent detectedActivitiesPI;

    // Must default to false: onConfigure() is called before onStart() by LocationServiceImpl.
    private boolean isStarted = false;
    private boolean isTracking = false;
    private boolean isWatchingActivity = false;
    private boolean playServicesAvailable = false;
    private boolean activityPermissionErrorEmitted = false;
    private boolean stopOnStillWarningEmitted = false;
    private DetectedActivity lastActivity = new DetectedActivity(DetectedActivity.UNKNOWN, 100);

    // v4.5.2: snapshot of fields that require restarting tracking when they change.
    private Integer prevDesiredAccuracy;
    private Integer prevInterval;
    private Integer prevFastestInterval;
    private Integer prevDistanceFilter;
    private Integer prevActivitiesInterval;
    private Boolean prevStopOnStillActivity;

    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult result) {
            if (result == null) return;
            List<Location> locations = result.getLocations();
            if (locations == null) return;
            for (Location location : locations) {
                onLocationReceived(location);
            }
        }
    };

    public ActivityRecognitionLocationProvider(Context context) {
        super(context);
        PROVIDER_ID = Config.ACTIVITY_PROVIDER;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // v4.5.2: ACTIVITY_PROVIDER strictly depends on Google Play Services
        // (FusedLocationProviderClient + ActivityRecognitionClient). If GPS is
        // missing/outdated we cannot operate — surface a clear error instead of
        // silently failing.
        int gps = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(mContext);
        playServicesAvailable = (gps == ConnectionResult.SUCCESS);
        if (!playServicesAvailable) {
            String msg = "Google Play Services unavailable for ACTIVITY_PROVIDER (code=" + gps + "); provider will be inert.";
            logger.error(msg);
            handleServiceError(msg);
            return;
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(mContext);
        activityRecognitionClient = ActivityRecognition.getClient(mContext);

        Intent detectedActivitiesIntent = new Intent(mContext, DetectedActivitiesReceiver.class);
        detectedActivitiesIntent.setAction(DETECTED_ACTIVITY_UPDATE);

        int updateCurrentFlag = android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
        detectedActivitiesPI = PendingIntent.getBroadcast(mContext, 9002, detectedActivitiesIntent, updateCurrentFlag);
        registerReceiver(detectedActivitiesReceiver, new IntentFilter(DETECTED_ACTIVITY_UPDATE));
    }

    @Override
    public void onStart() {
        logger.info("Start recording");
        this.isStarted = true;

        // v4.5.2: ACTIVITY_PROVIDER hinges on the STILL/ACTIVE state machine.
        // If the host turned that off, the provider degenerates into a tracker
        // that never pauses — warn so it shows up in logcat for the integrator.
        if (mConfig != null && Boolean.FALSE.equals(mConfig.getStopOnStillActivity()) && !stopOnStillWarningEmitted) {
            logger.warn("ACTIVITY_PROVIDER with stopOnStillActivity=false will track continuously; consider DISTANCE_FILTER_PROVIDER or RAW_PROVIDER for that use case.");
            stopOnStillWarningEmitted = true;
        }

        attachRecorder();
    }

    @Override
    public void onStop() {
        logger.info("Stop recording");
        this.isStarted = false;
        detachRecorder();
        stopTracking();
    }

    @Override
    public void onConfigure(Config config) {
        // v4.5.2: only restart tracking if a field that actually affects the
        // LocationRequest / activity-updates subscription has changed. A no-op
        // reconfigure used to drop+re-add the location callback and momentarily
        // leave the service without updates.
        boolean restart = false;
        if (isStarted) {
            restart = !sameValue(prevDesiredAccuracy, config != null ? config.getDesiredAccuracy() : null)
                    || !sameValue(prevInterval, config != null ? config.getInterval() : null)
                    || !sameValue(prevFastestInterval, config != null ? config.getFastestInterval() : null)
                    || !sameValue(prevDistanceFilter, config != null ? config.getDistanceFilter() : null)
                    || !sameValue(prevActivitiesInterval, config != null ? config.getActivitiesInterval() : null)
                    || !sameValue(prevStopOnStillActivity, config != null ? config.getStopOnStillActivity() : null);
        }

        super.onConfigure(config);

        if (restart) {
            logger.debug("Restarting ACTIVITY_PROVIDER tracking after relevant config change.");
            onStop();
            onStart();
        }

        if (config != null) {
            prevDesiredAccuracy = config.getDesiredAccuracy();
            prevInterval = config.getInterval();
            prevFastestInterval = config.getFastestInterval();
            prevDistanceFilter = config.getDistanceFilter();
            prevActivitiesInterval = config.getActivitiesInterval();
            prevStopOnStillActivity = config.getStopOnStillActivity();
        }
    }

    private static boolean sameValue(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    @Override
    public void onCommand(int commandId, int arg1) {
        if (commandId == LocationProvider.CMD_SWITCH_MODE && isStarted) {
            // Foreground: ensure tracking; background: keep current behavior (activity still drives start/stop).
            if (arg1 == LocationProvider.FOREGROUND_MODE) {
                startTracking();
            }
        }
    }

    /**
     * Same logic as former onLocationChanged: STILL -> stationary + stopTracking, else handleLocation.
     */
    private void onLocationReceived(Location location) {
        logger.debug("Location change: {}", location.toString());

        if (lastActivity.getType() == DetectedActivity.STILL) {
            handleStationary(location);
            stopTracking();
            return;
        }

        showDebugToast("acy:" + location.getAccuracy() + ",v:" + location.getSpeed());

        handleLocation(location);
    }

    public void startTracking() {
        if (isTracking) { return; }
        if (fusedLocationClient == null || mConfig == null) { return; }

        int priority = translateDesiredAccuracy(mConfig.getDesiredAccuracy());
        // v3.4: LocationRequest.Builder (play-services-location 21.0.0+) replaces deprecated
        // LocationRequest.create() + setPriority/setInterval/setFastestInterval.
        // v4.5.2: also honor distanceFilter (was ignored on ACTIVITY_PROVIDER), so the
        // FusedLocationProvider can throttle by distance and not just by interval.
        LocationRequest.Builder builder = new LocationRequest.Builder(priority, mConfig.getInterval())
                .setMinUpdateIntervalMillis(mConfig.getFastestInterval())
                .setWaitForAccurateLocation(false);
        Integer distanceFilter = mConfig.getDistanceFilter();
        if (distanceFilter != null && distanceFilter > 0) {
            builder.setMinUpdateDistanceMeters(distanceFilter.floatValue());
        }
        LocationRequest locationRequest = builder.build();

        try {
            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );
            isTracking = true;
            logger.debug("Start tracking with priority={} fastestInterval={} interval={} activitiesInterval={} stopOnStillActivity={}",
                    priority, mConfig.getFastestInterval(), mConfig.getInterval(), mConfig.getActivitiesInterval(), mConfig.getStopOnStillActivity());
        } catch (SecurityException e) {
            logger.error("Security exception: {}", e.getMessage());
            this.handleSecurityException(e);
        }
    }

    public void stopTracking() {
        if (!isTracking) { return; }
        if (fusedLocationClient == null) {
            isTracking = false;
            return;
        }
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        } catch (SecurityException e) {
            logger.warn("Security exception removing location updates: {}", e.getMessage());
        } finally {
            isTracking = false;
        }
    }

    private boolean activityRecognitionPermitted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                || ActivityCompat.checkSelfPermission(mContext, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
    }

    private void attachRecorder() {
        if (fusedLocationClient == null || activityRecognitionClient == null) { return; }
        if (mConfig == null) { return; }

        startTracking();

        if (!isWatchingActivity && mConfig.getStopOnStillActivity()) {
            // v4.5.2: on Android 10+ ACTIVITY_RECOGNITION is a runtime permission.
            // Without it, requestActivityUpdates() silently returns no broadcasts and
            // STILL/ACTIVE never flips — the provider then runs as a continuous tracker
            // by accident. Emit a one-shot error so the host app knows to request it.
            if (!activityRecognitionPermitted()) {
                if (!activityPermissionErrorEmitted) {
                    String msg = "ACTIVITY_RECOGNITION permission denied; ACTIVITY_PROVIDER cannot detect STILL/ACTIVE transitions.";
                    logger.warn(msg);
                    handlePermissionDenied(msg);
                    activityPermissionErrorEmitted = true;
                }
                return;
            }
            activityRecognitionClient.requestActivityUpdates(
                    mConfig.getActivitiesInterval(),
                    detectedActivitiesPI
            ).addOnFailureListener(e -> logger.error("requestActivityUpdates failed: {}", e.getMessage()));
            isWatchingActivity = true;
        }
    }

    private void detachRecorder() {
        if (!isWatchingActivity) { return; }
        if (activityRecognitionClient == null) {
            isWatchingActivity = false;
            return;
        }
        try {
            logger.debug("Detaching recorder");
            activityRecognitionClient.removeActivityUpdates(detectedActivitiesPI)
                    .addOnSuccessListener(v -> logger.debug("removeActivityUpdates ok"))
                    .addOnFailureListener(e -> logger.warn("removeActivityUpdates failed: {}", e.getMessage()));
        } finally {
            isWatchingActivity = false;
        }
    }

    /**
     * Translates a number representing desired accuracy of Geolocation system from set [0, 10, 100, 1000].
     * 0:  most aggressive, most accurate, worst battery drain
     * 1000:  least aggressive, least accurate, best for battery.
     */
    private int translateDesiredAccuracy(Integer accuracy) {
        // v3.4: Priority.* (play-services-location 21.0.0+) replaces deprecated LocationRequest.PRIORITY_*.
        if (accuracy == null) {
            return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        }
        if (accuracy >= 10000) {
            return Priority.PRIORITY_PASSIVE;
        }
        if (accuracy >= 1000) {
            return Priority.PRIORITY_LOW_POWER;
        }
        if (accuracy >= 100) {
            return Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        }
        return Priority.PRIORITY_HIGH_ACCURACY;
    }

    public static DetectedActivity getProbableActivity(List<DetectedActivity> detectedActivities) {
        int highestConfidence = 0;
        DetectedActivity mostLikelyActivity = new DetectedActivity(DetectedActivity.UNKNOWN, 0);

        for (DetectedActivity da : detectedActivities) {
            if (da.getType() != DetectedActivity.TILTING && da.getType() != DetectedActivity.UNKNOWN) {
                if (highestConfidence < da.getConfidence()) {
                    highestConfidence = da.getConfidence();
                    mostLikelyActivity = da;
                }
            }
        }
        return mostLikelyActivity;
    }

    private class DetectedActivitiesReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ActivityRecognitionResult result = ActivityRecognitionResult.extractResult(intent);
            if (result == null) return;
            List<DetectedActivity> detectedActivities = result.getProbableActivities();
            if (detectedActivities == null || detectedActivities.isEmpty()) return;

            DetectedActivity candidate = getProbableActivity(detectedActivities);

            // v4.5.2: skip transitions whose confidence is below the configured
            // threshold (default 50). Prevents jittery STILL/ACTIVE flips when the
            // motion classifier is unsure — which translated into spurious GPS
            // start/stop bursts in earlier versions.
            Integer threshold = mConfig != null ? mConfig.getActivityConfidenceThreshold() : null;
            if (threshold != null && candidate.getConfidence() < threshold) {
                logger.debug("Ignoring low-confidence activity={} confidence={} threshold={}",
                        BackgroundActivity.getActivityString(candidate.getType()),
                        candidate.getConfidence(),
                        threshold);
                return;
            }

            lastActivity = candidate;

            logger.debug("Detected activity={} confidence={}", BackgroundActivity.getActivityString(lastActivity.getType()), lastActivity.getConfidence());

            handleActivity(lastActivity);

            if (lastActivity.getType() == DetectedActivity.STILL) {
                showDebugToast("Detected STILL Activity");
            } else {
                showDebugToast("Detected ACTIVE Activity");
                startTracking();
            }
        }
    }

    private final BroadcastReceiver detectedActivitiesReceiver = new DetectedActivitiesReceiver();

    @Override
    public void onDestroy() {
        logger.info("Destroying ActivityRecognitionLocationProvider");
        onStop();
        try {
            unregisterReceiver(detectedActivitiesReceiver);
        } catch (Exception ignored) {
            // Receiver may not be registered if onCreate/onStart never completed
        }
        super.onDestroy();
    }
}
