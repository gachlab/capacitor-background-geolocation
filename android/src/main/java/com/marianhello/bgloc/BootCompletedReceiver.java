/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.marianhello.bgloc;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.marianhello.bgloc.data.ConfigurationDAO;
import com.marianhello.bgloc.data.DAOFactory;
import com.marianhello.bgloc.service.LocationServiceImpl;

import org.json.JSONException;

/**
 * BootCompletedReceiver class
 */
public class BootCompletedReceiver extends BroadcastReceiver {
    private static final String TAG = BootCompletedReceiver.class.getName();

    @Override
     public void onReceive(Context context, Intent intent) {
        String action = intent != null ? intent.getAction() : null;

        // v4.5.2 — hardening: ignore arbitrary broadcasts directed at this
        // receiver. Without this, any explicit intent (e.g. a malicious app
        // targeting our package) could trigger the service auto-start path.
        // Accept only the canonical boot/package-replaced actions plus the
        // OEM-specific quick-boot variants used by HTC and Samsung.
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !"android.intent.action.QUICKBOOT_POWERON".equals(action)
                && !"com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Log.w(TAG, "Ignoring unsupported broadcast: " + action);
            return;
        }

        Log.d(TAG, "Received boot/replace broadcast: " + action);
        ConfigurationDAO dao = DAOFactory.createConfigurationDAO(context);
        Config config = null;

        try {
            config = dao.retrieveConfiguration();
        } catch (JSONException e) {
            //noop
        }

        if (config == null) { return; }

        Log.d(TAG, "Boot/replace handler " + config.toString());

        if (!config.getStartOnBoot()) { return; }

        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Skipping start on boot: ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION not granted");
            return;
        }
        if (Build.VERSION.SDK_INT >= 29 && !hasBackgroundLocationPermission(context)) {
            Log.w(TAG, "Skipping start on boot: ACCESS_BACKGROUND_LOCATION not granted (Android 10+)");
            return;
        }

        Log.i(TAG, "Starting service after boot/replace");
        Intent locationServiceIntent = new Intent(context, LocationServiceImpl.class);
        locationServiceIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
        locationServiceIntent.putExtra("config", config);

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(locationServiceIntent);
            } else {
                context.startService(locationServiceIntent);
            }
        } catch (Exception e) {
            // Android 12+ may throw ForegroundServiceStartNotAllowedException.
            // Log and exit; do NOT fall back to a non-foreground service for tracking.
            Log.e(TAG, "Start on boot blocked: " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
     }

    private static boolean hasLocationPermission(Context context) {
        // v4.5.1 — ContextCompat handles API < 23 (always granted at install time).
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean hasBackgroundLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }
}
