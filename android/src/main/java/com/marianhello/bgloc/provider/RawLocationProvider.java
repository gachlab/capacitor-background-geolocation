package com.marianhello.bgloc.provider;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;

import com.marianhello.bgloc.Config;

import java.util.ArrayList;
import java.util.List;

public class RawLocationProvider extends AbstractLocationProvider implements LocationListener {
    private LocationManager locationManager;
    private boolean isStarted = false;
    // v4.5.2: providers we actively subscribed to (so we can unsubscribe cleanly).
    private final List<String> activeProviders = new ArrayList<>(2);

    public RawLocationProvider(Context context) {
        super(context);
        PROVIDER_ID = Config.RAW_PROVIDER;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void onStart() {
        if (isStarted) {
            return;
        }
        if (locationManager == null) {
            logger.error("LocationManager is null");
            return;
        }
        if (mConfig == null) {
            logger.warn("RawLocationProvider started without config");
            return;
        }
        // v4.5.2: honor desiredAccuracy and subscribe to all suitable providers
        // simultaneously (GPS + Network when available). Previously RAW only
        // used GPS-or-Network and ignored desiredAccuracy.
        List<String> providers = pickProviders();
        if (providers.isEmpty()) {
            logger.warn("No location provider available (GPS and Network disabled)");
            return;
        }
        activeProviders.clear();
        for (String provider : providers) {
            try {
                logger.info("Requesting location updates from provider {}", provider);
                locationManager.requestLocationUpdates(provider, mConfig.getInterval(), mConfig.getDistanceFilter(), this);
                activeProviders.add(provider);
            } catch (SecurityException e) {
                logger.error("Security exception requesting {} updates: {}", provider, e.getMessage());
                this.handleSecurityException(e);
            } catch (IllegalArgumentException e) {
                logger.warn("requestLocationUpdates({}) failed: {}", provider, e.getMessage());
            }
        }
        isStarted = !activeProviders.isEmpty();
    }

    /**
     * v4.5.2: choose providers based on desiredAccuracy.
     * <ul>
     *   <li>&lt; 1000 m → include GPS when enabled (HIGH / BALANCED)</li>
     *   <li>≥ 10 m → include Network when enabled (covers indoor and quick fixes)</li>
     *   <li>≥ 1000 m → Network-only (LOW_POWER)</li>
     * </ul>
     * Falls back to whatever is enabled if the preferred set is empty.
     */
    private List<String> pickProviders() {
        List<String> result = new ArrayList<>(2);
        if (locationManager == null) return result;

        Integer da = mConfig != null ? mConfig.getDesiredAccuracy() : null;
        int desired = (da != null) ? da : 100; // default BALANCED

        boolean wantGps = desired < 1000;
        boolean wantNet = desired >= 10;

        boolean gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean netEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        if (wantGps && gpsEnabled) result.add(LocationManager.GPS_PROVIDER);
        if (wantNet && netEnabled) result.add(LocationManager.NETWORK_PROVIDER);

        // Fallback: at least one of the available providers if our preferred set was empty.
        if (result.isEmpty()) {
            if (gpsEnabled) result.add(LocationManager.GPS_PROVIDER);
            else if (netEnabled) result.add(LocationManager.NETWORK_PROVIDER);
        }
        return result;
    }

    /** Backwards-compatible single-provider picker used by onProviderDisabled to check fallback. */
    private String pickProvider() {
        List<String> ps = pickProviders();
        return ps.isEmpty() ? null : ps.get(0);
    }

    @Override
    public void onStop() {
        if (!isStarted) {
            return;
        }
        try {
            // v4.5.2: removeUpdates(this) detaches us from every provider we
            // subscribed to via the same LocationListener.
            locationManager.removeUpdates(this);
        } catch (SecurityException e) {
            logger.error("Security exception: {}", e.getMessage());
            this.handleSecurityException(e);
        } finally {
            activeProviders.clear();
            isStarted = false;
        }
    }

    @Override
    public void onConfigure(Config config) {
        super.onConfigure(config);
        if (isStarted) {
            onStop();
            onStart();
        }
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    @Override
    public void onLocationChanged(Location location) {
        logger.debug("Location change: {}", location.toString());

        showDebugToast("acy:" + location.getAccuracy() + ",v:" + location.getSpeed());
        handleLocation(location);
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle bundle) {
        logger.debug("Provider {} status changed: {}", provider, status);
    }

    @Override
    public void onProviderEnabled(String provider) {
        logger.debug("Provider {} was enabled", provider);
    }

    @Override
    public void onProviderDisabled(String provider) {
        logger.warn("Provider {} was disabled", provider);
        // v4.5.2: emit SERVICE error when no fallback provider is available so
        // the JS layer can re-prompt the user. Matches DISTANCE_FILTER provider
        // behavior.
        if (locationManager != null && pickProvider() == null) {
            handleServiceError("Location provider '" + provider + "' disabled and no fallback available.");
        }
    }

    @Override
    public void onDestroy() {
        logger.debug("Destroying RawLocationProvider");
        this.onStop();
        super.onDestroy();
    }
}
