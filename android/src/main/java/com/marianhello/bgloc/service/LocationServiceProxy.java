package com.marianhello.bgloc.service;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.marianhello.bgloc.Config;

public class LocationServiceProxy implements LocationService, LocationServiceInfo {
    private static final String TAG = LocationServiceProxy.class.getSimpleName();
    private final Context mContext;
    private final LocationServiceIntentBuilder mIntentBuilder;

    public LocationServiceProxy(Context context) {
        mContext = context;
        mIntentBuilder = new LocationServiceIntentBuilder(context);
    }

    @Override
    public void configure(Config config) {
        // do not start service if it was not already started
        // FIXES:
        // https://github.com/mauron85/react-native-background-geolocation/issues/360
        // https://github.com/mauron85/cordova-plugin-background-geolocation/issues/551
        // https://github.com/mauron85/cordova-plugin-background-geolocation/issues/552
        if (!isStarted()) { return; }

        Intent intent = mIntentBuilder
                .setCommand(CommandId.CONFIGURE, config)
                .build();
        executeIntentCommand(intent);
    }

    @Override
    public void registerHeadlessTask(String taskRunnerClass) {
        Intent intent = mIntentBuilder
                .setCommand(CommandId.REGISTER_HEADLESS_TASK, taskRunnerClass)
                .build();
        executeIntentCommand(intent);
    }

    @Override
    public void startHeadlessTask() {
        if (!isStarted()) { return; }

        Intent intent = mIntentBuilder
                .setCommand(CommandId.START_HEADLESS_TASK)
                .build();
        executeIntentCommand(intent);
    }

    @Override
    public void stopHeadlessTask() {
        if (!isStarted()) { return; }

        Intent intent = mIntentBuilder
                .setCommand(CommandId.STOP_HEADLESS_TASK)
                .build();
        executeIntentCommand(intent);
    }

    @Override
    public void executeProviderCommand(int command, int arg) {
        // TODO
    }

    @Override
    public void start() {
        Intent intent = mIntentBuilder.setCommand(CommandId.START).build();
//        intent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        // start service to keep service running even if no clients are bound to it
        executeIntentCommand(intent);
    }

    @Override
    public void startForegroundService() {
        Intent intent = mIntentBuilder.setCommand(CommandId.START_FOREGROUND_SERVICE).build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!hasLocationPermission()) {
                // Do NOT fall back to startService(): would create a non-foreground service that crashes
                // on first location update. Caller must request the permission first.
                Log.w(TAG, "Cannot start foreground service: ACCESS_FINE_LOCATION/COARSE_LOCATION not granted");
                return;
            }
            // Note: ACCESS_BACKGROUND_LOCATION is only required when the service is started from
            // background (e.g. BootCompletedReceiver). When called from foreground, the OS allows
            // a location-typed FGS to run with only fine/coarse location and inherit "while-in-use".
            try {
                mContext.startForegroundService(intent);
            } catch (Exception e) {
                // Android 12+ may throw ForegroundServiceStartNotAllowedException.
                Log.e(TAG, "startForegroundService blocked: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
            }
        } else {
            mContext.startService(intent);
        }
    }

    private boolean hasLocationPermission() {
        // v4.5.1 — ContextCompat handles API < 23 safely.
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void stop() {
        if (!isStarted()) { return; }

        Intent intent = mIntentBuilder.setCommand(CommandId.STOP).build();
        executeIntentCommand(intent);
    }

    @Override
    public void stopForeground() {
        if (!isStarted()) { return; }

        Intent intent = mIntentBuilder.setCommand(CommandId.STOP_FOREGROUND).build();
        executeIntentCommand(intent);
    }

    @Override
    public void startForeground() {
        if (!isStarted()) { return; }

        Intent intent = mIntentBuilder.setCommand(CommandId.START_FOREGROUND).build();
        executeIntentCommand(intent);
    }

    @Override
    public boolean isStarted() {
        LocationServiceInfo serviceInfo = new LocationServiceInfoImpl(mContext);
        return serviceInfo.isStarted();
    }

    public boolean isRunning() {
        if (isStarted()) {
            return LocationServiceImpl.isRunning();
        }
        return false;
    }

    @Override
    public boolean isBound() {
        LocationServiceInfo serviceInfo = new LocationServiceInfoImpl(mContext);
        return serviceInfo.isBound();
    }

    private void executeIntentCommand(Intent intent) {
        mContext.startService(intent);
    }
}
