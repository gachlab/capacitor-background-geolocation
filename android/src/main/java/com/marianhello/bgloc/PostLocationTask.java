package com.marianhello.bgloc;

import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.LocationDAO;
import com.marianhello.bgloc.data.SessionLocationDAO;
import com.marianhello.bgloc.http.UrlTemplateResolver;
import com.marianhello.logging.LoggerManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * Location task to post/sync locations from location providers
 *
 * All locations updates are recorded in local db at all times.
 * Also location is also send to all messenger clients.
 *
 * If option.url is defined, each location is also immediately posted.
 * If post is successful, the location is deleted from local db.
 * All failed to post locations are coalesced and send in some time later in one single batch.
 * Batch sync takes place only when number of failed to post locations reaches syncTreshold.
 *
 * If only option.syncUrl is defined, locations are send only in single batch,
 * when number of locations reaches syncTreshold.
 *
 */
public class PostLocationTask {
    private final LocationDAO mLocationDAO;
    private final SessionLocationDAO mSessionDAO;
    private final PostLocationTaskListener mTaskListener;
    private final ConnectivityListener mConnectivityListener;

    private final ExecutorService mExecutor;

    private volatile boolean mHasConnectivity = true;
    private volatile Config mConfig;

    private org.slf4j.Logger logger;

    public interface PostLocationTaskListener
    {
        void onSyncRequested();
        void onRequestedAbortUpdates();
        void onHttpAuthorizationUpdates();
    }

    public PostLocationTask(LocationDAO dao, PostLocationTaskListener taskListener,
                            ConnectivityListener connectivityListener) {
        this(dao, null, taskListener, connectivityListener);
    }

    public PostLocationTask(LocationDAO dao, SessionLocationDAO sessionDAO,
                            PostLocationTaskListener taskListener,
                            ConnectivityListener connectivityListener) {
        logger = LoggerManager.getLogger(PostLocationTask.class);
        logger.info("Creating PostLocationTask");

        mLocationDAO = dao;
        mSessionDAO = sessionDAO;
        mTaskListener = taskListener;
        mConnectivityListener = connectivityListener;

        mExecutor = Executors.newSingleThreadExecutor();
    }

    public void setConfig(Config config) {
        mConfig = config;
    }

    public void setHasConnectivity(boolean hasConnectivity) {
        mHasConnectivity = hasConnectivity;
    }

    public void clearQueue() {
        mExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mLocationDAO.deleteUnpostedLocations();
            }
        });
    }

    public void add(final BackgroundLocation location) {
        if (mConfig == null) {
            logger.warn("PostLocationTask has no config. Did you called setConfig? Skipping location.");
            return;
        }

        // v3.5 Phase 4: mock location policy. Detection is already in BackgroundLocation
        // (isFromMockProvider). Here we apply the policy.
        if (location != null && location.isFromMockProvider()) {
            String policy = mConfig.getMockLocationPolicy(); // "allow" | "flag" | "drop"
            if ("drop".equals(policy)) {
                logger.info("Mock location dropped (mockLocationPolicy=drop)");
                return;
            }
            // "flag": leave it but caller can read isFromMockProvider() / mocked field.
            // "allow": no-op.
        }

        long locationId = mLocationDAO.persistLocation(location);
        location.setLocationId(locationId);

        if (mSessionDAO != null && mSessionDAO.isSessionActive()) {
            mSessionDAO.persistSessionLocation(location);
        }

        try {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    post(location);
                }
            });
        } catch (RejectedExecutionException ex) {
            mLocationDAO.updateLocationForSync(locationId);
        }
    }

    public void shutdown() {
        shutdown(60);
    }

    public void shutdown(int waitSeconds) {
        mExecutor.shutdown();
        try {
            if (!mExecutor.awaitTermination(waitSeconds, TimeUnit.SECONDS)) {
                mExecutor.shutdownNow();
                mLocationDAO.deleteUnpostedLocations();
            }
        } catch (InterruptedException e) {
            mExecutor.shutdownNow();
        }
    }

    private void post(final BackgroundLocation location) {
        long locationId = location.getLocationId();

        if (mHasConnectivity && mConfig.hasValidUrl()) {
            if (postLocation(location)) {
                mLocationDAO.deleteLocationById(locationId);

                return; // if posted successfully do nothing more
            } else {
                mLocationDAO.updateLocationForSync(locationId);
            }
        } else {
            mLocationDAO.updateLocationForSync(locationId);
        }

        if (mConfig.hasValidSyncUrl()) {
            Integer configThreshold = mConfig.getSyncThreshold();
            int threshold = (configThreshold != null) ? configThreshold : 100;
            long syncLocationsCount = mLocationDAO.getLocationsForSyncCount(System.currentTimeMillis());
            if (syncLocationsCount >= threshold) {
                logger.debug("Attempt to sync locations: {} threshold: {}", syncLocationsCount, threshold);
                mTaskListener.onSyncRequested();
            }
        }
    }

    private boolean postLocation(BackgroundLocation location) {
        logger.debug("Executing PostLocationTask#postLocation");

        // LocationTemplate.locationToJson returns Object (JSONObject for HashMapLocationTemplate,
        // JSONArray for ArrayListLocationTemplate). Resolve to the concrete type before calling
        // the matching HttpPostService.postJSON overload.
        Object jsonLocation;
        try {
            jsonLocation = mConfig.getTemplate().locationToJson(location);
        } catch (JSONException e) {
            logger.warn("Location to json failed: {}", location.toString());
            return false;
        }

        String urlTemplate = mConfig.getUrl();
        // URL templating: substitute {lat}, {lon}, {timestamp_iso}, {device_id}, ... using the
        // current location plus any static queryParams. For "single" mode this is per-location;
        // for "batch" mode only static queryParams placeholders apply (location-derived ones
        // would not make sense for an array).
        String resolvedUrl = UrlTemplateResolver.resolve(urlTemplate, location, mConfig.getQueryParams());

        String method = mConfig.getHttpMethod();
        String mode = mConfig.getHttpMode();
        logger.debug("Posting to url: {} method: {} mode: {} headers: {}",
                resolvedUrl, method, mode, mConfig.getHttpHeaders());
        int responseCode;

        try {
            if ("single".equals(mode) || "GET".equals(method)) {
                // GET cannot carry a JSON array body; force per-location request.
                if (jsonLocation instanceof JSONArray) {
                    responseCode = HttpPostService.postJSON(resolvedUrl, (JSONArray) jsonLocation, mConfig.getHttpHeaders(), method);
                } else {
                    responseCode = HttpPostService.postJSON(resolvedUrl, (JSONObject) jsonLocation, mConfig.getHttpHeaders(), method);
                }
            } else {
                JSONArray jsonLocations = new JSONArray();
                jsonLocations.put(jsonLocation);
                responseCode = HttpPostService.postJSON(resolvedUrl, jsonLocations, mConfig.getHttpHeaders(), method);
            }
        } catch (Exception e) {
            mHasConnectivity = mConnectivityListener.hasConnectivity();
            logger.warn("Error while posting locations: {}", e.getMessage());
            return false;
        }

        if (responseCode == 285) {
            // Okay, but we don't need to continue sending these

            logger.debug("Location was sent to the server, and received an \"HTTP 285 Updates Not Required\"");

            if (mTaskListener != null)
                mTaskListener.onRequestedAbortUpdates();
        }

        if (responseCode == 401) {
            if (mTaskListener != null)
                mTaskListener.onHttpAuthorizationUpdates();
        }

        // All 2xx statuses are okay
        boolean isStatusOkay = responseCode >= 200 && responseCode < 300;

        if (!isStatusOkay) {
            logger.warn("Server error while posting locations responseCode: {}", responseCode);
            return false;
        }

        return true;
    }
}
