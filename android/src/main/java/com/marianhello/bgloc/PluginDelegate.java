package com.marianhello.bgloc;

import com.marianhello.bgloc.data.BackgroundActivity;
import com.marianhello.bgloc.data.BackgroundLocation;

/**
 * Created by finch on 27.11.2017.
 */

public interface PluginDelegate {
    void onAuthorizationChanged(int authStatus);
    void onLocationChanged(BackgroundLocation location);
    void onStationaryChanged(BackgroundLocation location);
    void onActivityChanged(BackgroundActivity activity);
    void onServiceStatusChanged(int status);
    void onAbortRequested();
    void onHttpAuthorization();
    void onError(PluginException error);
    /** v3.5 Phase 4: sync queue events. Default no-op so existing implementations keep compiling. */
    default void onSyncStart() {}
    default void onSyncSuccess(int locationsSent) {}
    default void onSyncError(int httpStatus, String message) {}
    default void onSyncProgress(int progress) {}
    default void onHeartbeat(BackgroundLocation location) {}
    // v4.0 Phase 6: driver insights
    default void onTripStart(BackgroundLocation location) {}
    default void onTripEnd(BackgroundLocation location, double distance, long durationMs) {}
    default void onMoving(BackgroundLocation location) {}
    default void onStopped(BackgroundLocation location) {}
    default void onSpeeding(BackgroundLocation location, double speedKmh, double limitKmh) {}
    default void onProviderChange(String provider) {}
    default void onSOS(BackgroundLocation location, org.json.JSONObject payload) {}
    // v4.1 GPS-derived sensor-like events
    default void onHardBrake(BackgroundLocation location, double decelMps2) {}
    default void onRapidAcceleration(BackgroundLocation location, double accelMps2) {}
    default void onSharpTurn(BackgroundLocation location, double degPerSec) {}
    default void onPossibleCrash(BackgroundLocation location, double velocityDropKmh) {}
    /** v4.2: same event, but enriched with the source ("gps" | "sensor") and impact value. */
    default void onPossibleCrash(BackgroundLocation location, double value, String source) {
        // Backward-compat default: forward to the legacy 2-arg overload.
        onPossibleCrash(location, value);
    }
    /** v4.2 sensor fusion: emitted when device interaction is detected during an active trip. */
    default void onPhoneUsageWhileDriving(BackgroundLocation location) {}
}
