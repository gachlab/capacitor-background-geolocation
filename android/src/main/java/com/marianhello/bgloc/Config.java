/*
According to apache license

This is fork of christocracy cordova-plugin-background-geolocation plugin
https://github.com/christocracy/cordova-plugin-background-geolocation

This is a new class
*/

package com.marianhello.bgloc;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.Nullable;

import com.marianhello.bgloc.data.AbstractLocationTemplate;
import com.marianhello.bgloc.data.LocationTemplate;
import com.marianhello.bgloc.data.LocationTemplateFactory;
import com.marianhello.utils.CloneHelper;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;

/**
 * Config class
 */
public class Config implements Parcelable
{
    public static final String BUNDLE_KEY = "config";

    public static final int DISTANCE_FILTER_PROVIDER = 0;
    public static final int ACTIVITY_PROVIDER = 1;
    public static final int RAW_PROVIDER = 2;

    // NULL string config option to distinguish between java null
    public static final String NullString = new String();

    private Float stationaryRadius;
    private Integer distanceFilter;
    private Integer desiredAccuracy;
    private Boolean debug;
    private String notificationTitle;
    private String notificationText;
    private String notificationSyncTitle;
    private String notificationSyncText;
    private String notificationSyncCompletedText;
    private String notificationSyncFailedText;
    private String notificationIconLarge;
    private String notificationIconSmall;
    private String notificationIconColor;
    private Integer locationProvider;
    private Integer interval; //milliseconds
    private Integer fastestInterval; //milliseconds
    private Integer activitiesInterval; //milliseconds
    private Boolean stopOnTerminate;
    private Boolean startOnBoot;
    private Boolean startForeground;
    private Boolean notificationsEnabled;
    private Boolean stopOnStillActivity;
    private String url;
    private String syncUrl;
    private Integer syncThreshold;
    private Boolean syncEnabled;
    private HashMap httpHeaders;
    private Integer maxLocations;
    private LocationTemplate template;
    private Boolean enableWatchdog;
    /** Watchdog check interval (ms). {@code null} = use the 60 s default. */
    private Long watchdogIntervalMs;
    private Boolean showTime;
    private Boolean showDistance;
    // v3.3 (Phase 2): backend-agnostic HTTP transport
    private String httpMethod;       // POST | GET | PUT | PATCH (default POST)
    private String syncHttpMethod;   // POST | GET | PUT | PATCH (default POST)
    private String httpMode;         // batch | single (default batch)
    private String syncMode;         // batch | single (default batch)
    private HashMap queryParams;     // static placeholder values for URL templating
    // v3.5 (Phase 4): diagnostics
    private Integer heartbeatInterval;    // ms; 0 disables heartbeat events
    private String mockLocationPolicy;    // allow | flag | drop (default allow)
    // v4.0 (Phase 6): driver insights
    private DrivingEventsOptions drivingEvents;
    // v4.4: include device battery in every location payload (default true).
    private Boolean includeBattery;
    // v4.5.1: battery-saving knobs.
    /** WakeLock policy: 'none' | 'posting' | 'always'. Default 'posting'. */
    private String wakeLockMode;
    /** ms before declaring stationary. DistanceFilterLocationProvider default 5*60_000. */
    private Integer stationaryTimeout;
    /** Lazy poll interval while stationary (ms). Default 3*60_000. */
    private Integer stationaryPollInterval;
    /** Aggressive poll interval while stationary (ms). Default 60_000. */
    private Integer stationaryPollFast;
    // v4.5.2 — provider hardening
    /** 0-100. Activity-recognition transitions below this confidence are ignored. Default 50. */
    private Integer activityConfidenceThreshold;
    /** Discard fixes whose `accuracy` (m) is worse than this. `null` (default) disables the filter. */
    private Float maxAcceptedAccuracy;

    /** v4.0 Phase 6 + v4.1: driver-insights configuration. Plain holder; no Parcelable to keep this class diff small. */
    public static class DrivingEventsOptions {
        public boolean enabled = false;
        public double speedLimitKmh = 0;
        public double minMovingSpeedMps = 1.0;
        public long stoppedDurationMs = 60_000L;
        public double minTripSpeedMps = 3.0;
        public long minTripDurationMs = 30_000L;
        // v4.1 GPS-derived sensor-like events. 0 disables each one.
        public double hardBrakeMps2 = 3.5;
        public double rapidAccelMps2 = 3.5;
        public double sharpTurnDegPerSec = 30;
        public double crashImpactKmh = 25;
        public long   crashWindowMs = 2_000L;
        // v4.2 sensor fusion (real accelerometer + gyroscope).
        public boolean sensorFusion = false;
        public double  crashImpactG = 3.0;          // |a| threshold for sensor crash, in g
        public long    sensorCrashCooldownMs = 10_000L;
        public long    phoneUsageWindowMs = 4_000L;
        public long    phoneUsageCooldownMs = 60_000L;
    }

    public Config () {
    }

    // Copy constructor
    public Config(Config config) {
        this.stationaryRadius = config.stationaryRadius;
        this.distanceFilter = config.distanceFilter;
        this.desiredAccuracy = config.desiredAccuracy;
        this.debug = config.debug;
        this.notificationTitle = config.notificationTitle;
        this.notificationText = config.notificationText;
        this.notificationSyncTitle = config.notificationSyncTitle;
        this.notificationSyncText = config.notificationSyncText;
        this.notificationSyncCompletedText = config.notificationSyncCompletedText;
        this.notificationSyncFailedText = config.notificationSyncFailedText;
        this.notificationIconLarge = config.notificationIconLarge;
        this.notificationIconSmall = config.notificationIconSmall;
        this.notificationIconColor = config.notificationIconColor;
        this.locationProvider = config.locationProvider;
        this.interval = config.interval;
        this.fastestInterval = config.fastestInterval;
        this.activitiesInterval = config.activitiesInterval;
        this.stopOnTerminate = config.stopOnTerminate;
        this.startOnBoot = config.startOnBoot;
        this.startForeground = config.startForeground;
        this.notificationsEnabled = config.notificationsEnabled;
        this.stopOnStillActivity = config.stopOnStillActivity;
        this.url = config.url;
        this.syncUrl = config.syncUrl;
        this.syncThreshold = config.syncThreshold;
        this.syncEnabled = config.syncEnabled;
        this.httpHeaders = CloneHelper.deepCopy(config.httpHeaders);
        this.maxLocations = config.maxLocations;
        this.enableWatchdog = config.enableWatchdog;
        this.watchdogIntervalMs = config.watchdogIntervalMs;
        this.showTime = config.showTime;
        this.showDistance = config.showDistance;
        this.httpMethod = config.httpMethod;
        this.syncHttpMethod = config.syncHttpMethod;
        this.httpMode = config.httpMode;
        this.syncMode = config.syncMode;
        this.queryParams = CloneHelper.deepCopy(config.queryParams);
        this.heartbeatInterval = config.heartbeatInterval;
        this.mockLocationPolicy = config.mockLocationPolicy;
        this.includeBattery = config.includeBattery;
        this.wakeLockMode = config.wakeLockMode;
        this.stationaryTimeout = config.stationaryTimeout;
        this.stationaryPollInterval = config.stationaryPollInterval;
        this.stationaryPollFast = config.stationaryPollFast;
        this.activityConfidenceThreshold = config.activityConfidenceThreshold;
        this.maxAcceptedAccuracy = config.maxAcceptedAccuracy;
        if (config.drivingEvents != null) {
            DrivingEventsOptions de = new DrivingEventsOptions();
            de.enabled            = config.drivingEvents.enabled;
            de.speedLimitKmh      = config.drivingEvents.speedLimitKmh;
            de.minMovingSpeedMps  = config.drivingEvents.minMovingSpeedMps;
            de.stoppedDurationMs  = config.drivingEvents.stoppedDurationMs;
            de.minTripSpeedMps    = config.drivingEvents.minTripSpeedMps;
            de.minTripDurationMs  = config.drivingEvents.minTripDurationMs;
            de.hardBrakeMps2      = config.drivingEvents.hardBrakeMps2;
            de.rapidAccelMps2     = config.drivingEvents.rapidAccelMps2;
            de.sharpTurnDegPerSec = config.drivingEvents.sharpTurnDegPerSec;
            de.crashImpactKmh     = config.drivingEvents.crashImpactKmh;
            de.crashWindowMs      = config.drivingEvents.crashWindowMs;
            de.sensorFusion          = config.drivingEvents.sensorFusion;
            de.crashImpactG          = config.drivingEvents.crashImpactG;
            de.sensorCrashCooldownMs = config.drivingEvents.sensorCrashCooldownMs;
            de.phoneUsageWindowMs    = config.drivingEvents.phoneUsageWindowMs;
            de.phoneUsageCooldownMs  = config.drivingEvents.phoneUsageCooldownMs;
            this.drivingEvents = de;
        }
        if (config.template instanceof AbstractLocationTemplate) {
            this.template = ((AbstractLocationTemplate)config.template).clone();
        }
    }

    private Config(Parcel in) {
        setStationaryRadius(in.readFloat());
        setDistanceFilter(in.readInt());
        setDesiredAccuracy(in.readInt());
        setDebugging((Boolean) in.readValue(null));
        setNotificationTitle(in.readString());
        setNotificationText(in.readString());
        setNotificationSyncTitle(in.readString());
        setNotificationSyncText(in.readString());
        setNotificationSyncCompletedText(in.readString());
        setNotificationSyncFailedText(in.readString());
        setLargeNotificationIcon(in.readString());
        setSmallNotificationIcon(in.readString());
        setNotificationIconColor(in.readString());
        setStopOnTerminate((Boolean) in.readValue(null));
        setStartOnBoot((Boolean) in.readValue(null));
        setStartForeground((Boolean) in.readValue(null));
        setNotificationsEnabled((Boolean) in.readValue(null));
        setLocationProvider(in.readInt());
        setInterval(in.readInt());
        setFastestInterval(in.readInt());
        setActivitiesInterval(in.readInt());
        setStopOnStillActivity((Boolean) in.readValue(null));
        setUrl(in.readString());
        setSyncUrl(in.readString());
        setSyncThreshold(in.readInt());
        setSyncEnabled((Boolean) in.readValue(null));
        setMaxLocations(in.readInt());
        setEnableWatchdog((Boolean) in.readValue(null));
        setShowTime((Boolean) in.readValue(null));
        setShowDistance((Boolean) in.readValue(null));
        setHttpMethod(in.readString());
        setSyncHttpMethod(in.readString());
        setHttpMode(in.readString());
        setSyncMode(in.readString());
        setHeartbeatInterval((Integer) in.readValue(null));
        setMockLocationPolicy(in.readString());
        // v4.0 + v4.1: driver-insights options serialised as primitives.
        boolean deEnabled = in.readInt() != 0;
        double deSpeedLimit = in.readDouble();
        double deMinMove = in.readDouble();
        long   deStoppedDur = in.readLong();
        double deMinTrip = in.readDouble();
        long   deMinTripDur = in.readLong();
        // v4.1
        double deHardBrake = in.readDouble();
        double deRapidAccel = in.readDouble();
        double deSharpTurn = in.readDouble();
        double deCrashKmh = in.readDouble();
        long   deCrashWin = in.readLong();
        // v4.2 sensor fusion
        boolean deSensorFusion = in.readInt() != 0;
        double  deCrashImpactG = in.readDouble();
        long    deSensorCrashCooldown = in.readLong();
        long    dePhoneUsageWindow = in.readLong();
        long    dePhoneUsageCooldown = in.readLong();
        boolean deHasOptions = in.readInt() != 0;
        if (deHasOptions) {
            DrivingEventsOptions de = new DrivingEventsOptions();
            de.enabled = deEnabled;
            de.speedLimitKmh = deSpeedLimit;
            de.minMovingSpeedMps = deMinMove;
            de.stoppedDurationMs = deStoppedDur;
            de.minTripSpeedMps = deMinTrip;
            de.minTripDurationMs = deMinTripDur;
            de.hardBrakeMps2 = deHardBrake;
            de.rapidAccelMps2 = deRapidAccel;
            de.sharpTurnDegPerSec = deSharpTurn;
            de.crashImpactKmh = deCrashKmh;
            de.crashWindowMs = deCrashWin;
            de.sensorFusion = deSensorFusion;
            de.crashImpactG = deCrashImpactG;
            de.sensorCrashCooldownMs = deSensorCrashCooldown;
            de.phoneUsageWindowMs = dePhoneUsageWindow;
            de.phoneUsageCooldownMs = dePhoneUsageCooldown;
            this.drivingEvents = de;
        }
        // v4.4: includeBattery
        setIncludeBattery((Boolean) in.readValue(null));
        // v4.5.1: battery-saving knobs
        setWakeLockMode(in.readString());
        setStationaryTimeout((Integer) in.readValue(null));
        setStationaryPollInterval((Integer) in.readValue(null));
        setStationaryPollFast((Integer) in.readValue(null));
        // v4.5.2 provider hardening
        setActivityConfidenceThreshold((Integer) in.readValue(null));
        setMaxAcceptedAccuracy((Float) in.readValue(null));
        // P2: configurable watchdog interval
        setWatchdogIntervalMs((Long) in.readValue(null));
        // v4.5.1 — pass the plugin's classloader so getSerializable() can deserialize
        // LocationTemplate / HashMap subclasses across IPC boundaries (e.g. SyncService :sync process).
        Bundle bundle = in.readBundle(Config.class.getClassLoader());
        setHttpHeaders((HashMap<String, String>) bundle.getSerializable("httpHeaders"));
        setQueryParams((HashMap<String, String>) bundle.getSerializable("queryParams"));
        setTemplate((LocationTemplate) bundle.getSerializable(AbstractLocationTemplate.BUNDLE_KEY));
    }

    public static Config getDefault() {
        Config config = new Config();
        config.stationaryRadius = 50f;
        config.distanceFilter = 500;
        config.desiredAccuracy = 100;
        config.debug = false;
        config.notificationTitle = "Background tracking";
        config.notificationText = "ENABLED";
        config.notificationSyncTitle = "Syncing locations";
        config.notificationSyncText = "Sync in progress";
        config.notificationSyncCompletedText = "Sync completed";
        config.notificationSyncFailedText = "Sync failed";
        config.notificationIconLarge = "";
        config.notificationIconSmall = "";
        config.notificationIconColor = "";
        config.locationProvider = DISTANCE_FILTER_PROVIDER;
        config.interval = 600000; //milliseconds
        config.fastestInterval = 120000; //milliseconds
        config.activitiesInterval = 10000; //milliseconds
        config.stopOnTerminate = true;
        config.startOnBoot = false;
        config.startForeground = true;
        config.notificationsEnabled = true;
        config.stopOnStillActivity = true;
        config.url = "";
        config.syncUrl = "";
        config.syncThreshold = 100;
        config.syncEnabled = true;
        config.httpHeaders = null;
        config.maxLocations = 10000;
        config.template = null;
        config.enableWatchdog = false;
        config.showTime = false;
        config.showDistance = false;
        config.httpMethod = "POST";
        config.syncHttpMethod = "POST";
        config.httpMode = "batch";
        config.syncMode = "batch";
        config.queryParams = null;
        config.heartbeatInterval = 0;
        config.mockLocationPolicy = "allow";
        config.includeBattery = true; // v4.4: on by default
        config.wakeLockMode = "posting";  // v4.5.1: hold wake lock only while posting/syncing
        config.stationaryTimeout = 5 * 60 * 1000;
        config.stationaryPollInterval = 3 * 60 * 1000;
        config.stationaryPollFast = 60 * 1000;
        config.activityConfidenceThreshold = 50; // v4.5.2: ignore <50% confidence transitions
        config.maxAcceptedAccuracy = null;       // v4.5.2: off by default (no JS regression)

        return config;
    }

    public int describeContents() {
        return 0;
    }

    // write your object's data to the passed-in Parcel
    public void writeToParcel(Parcel out, int flags) {
        out.writeFloat(getStationaryRadius());
        out.writeInt(getDistanceFilter());
        out.writeInt(getDesiredAccuracy());
        out.writeValue(isDebugging());
        out.writeString(getNotificationTitle());
        out.writeString(getNotificationText());
        out.writeString(getNotificationSyncTitle());
        out.writeString(getNotificationSyncText());
        out.writeString(getNotificationSyncCompletedText());
        out.writeString(getNotificationSyncFailedText());
        out.writeString(getLargeNotificationIcon());
        out.writeString(getSmallNotificationIcon());
        out.writeString(getNotificationIconColor());
        out.writeValue(getStopOnTerminate());
        out.writeValue(getStartOnBoot());
        out.writeValue(getStartForeground());
        out.writeValue(getNotificationsEnabled());
        out.writeInt(getLocationProvider());
        out.writeInt(getInterval());
        out.writeInt(getFastestInterval());
        out.writeInt(getActivitiesInterval());
        out.writeValue(getStopOnStillActivity());
        out.writeString(getUrl());
        out.writeString(getSyncUrl());
        out.writeInt(getSyncThreshold());
        out.writeValue(getSyncEnabled());
        out.writeInt(getMaxLocations());
        out.writeValue(getEnableWatchdog());
        out.writeValue(getShowTime());
        out.writeValue(getShowDistance());
        out.writeString(getHttpMethod());
        out.writeString(getSyncHttpMethod());
        out.writeString(getHttpMode());
        out.writeString(getSyncMode());
        out.writeValue(getHeartbeatInterval());
        out.writeString(getMockLocationPolicy());
        // v4.0 + v4.1: drivingEvents primitives (always written; "hasOptions" flag at end).
        DrivingEventsOptions de = drivingEvents;
        out.writeInt(de != null && de.enabled ? 1 : 0);
        out.writeDouble(de != null ? de.speedLimitKmh     : 0.0);
        out.writeDouble(de != null ? de.minMovingSpeedMps : 1.0);
        out.writeLong  (de != null ? de.stoppedDurationMs : 60_000L);
        out.writeDouble(de != null ? de.minTripSpeedMps   : 3.0);
        out.writeLong  (de != null ? de.minTripDurationMs : 30_000L);
        // v4.1
        out.writeDouble(de != null ? de.hardBrakeMps2     : 3.5);
        out.writeDouble(de != null ? de.rapidAccelMps2    : 3.5);
        out.writeDouble(de != null ? de.sharpTurnDegPerSec: 30.0);
        out.writeDouble(de != null ? de.crashImpactKmh    : 25.0);
        out.writeLong  (de != null ? de.crashWindowMs     : 2_000L);
        // v4.2 sensor fusion
        out.writeInt   (de != null && de.sensorFusion ? 1 : 0);
        out.writeDouble(de != null ? de.crashImpactG          : 3.0);
        out.writeLong  (de != null ? de.sensorCrashCooldownMs : 10_000L);
        out.writeLong  (de != null ? de.phoneUsageWindowMs    : 4_000L);
        out.writeLong  (de != null ? de.phoneUsageCooldownMs  : 60_000L);
        out.writeInt   (de != null ? 1 : 0);
        // v4.4: includeBattery
        out.writeValue(getIncludeBattery());
        // v4.5.1
        out.writeString(getWakeLockMode());
        out.writeValue(getStationaryTimeout());
        out.writeValue(getStationaryPollInterval());
        out.writeValue(getStationaryPollFast());
        // v4.5.2
        out.writeValue(getActivityConfidenceThreshold());
        out.writeValue(getMaxAcceptedAccuracy());
        // P2: configurable watchdog interval
        out.writeValue(getWatchdogIntervalMs());
        Bundle bundle = new Bundle();
        bundle.putSerializable("httpHeaders", getHttpHeaders());
        bundle.putSerializable("queryParams", getQueryParams());
        bundle.putSerializable(AbstractLocationTemplate.BUNDLE_KEY, (AbstractLocationTemplate) getTemplate());
        out.writeBundle(bundle);
    }

    public static final Parcelable.Creator<Config> CREATOR
            = new Parcelable.Creator<Config>() {
        public Config createFromParcel(Parcel in) {
            return new Config(in);
        }

        public Config[] newArray(int size) {
            return new Config[size];
        }
    };

    public boolean hasStationaryRadius() {
        return stationaryRadius != null;
    }

    public Float getStationaryRadius() {
        return stationaryRadius;
    }

    public void setStationaryRadius(float stationaryRadius) {
        this.stationaryRadius = stationaryRadius;
    }

    public void setStationaryRadius(double stationaryRadius) {
        this.stationaryRadius = (float) stationaryRadius;
    }

    public boolean hasDesiredAccuracy() {
        return desiredAccuracy != null;
    }

    public Integer getDesiredAccuracy() {
        return desiredAccuracy;
    }

    public void setDesiredAccuracy(Integer desiredAccuracy) {
        this.desiredAccuracy = desiredAccuracy;
    }

    public boolean hasDistanceFilter() {
        return distanceFilter != null;
    }

    public Integer getDistanceFilter() {
        return distanceFilter;
    }

    public void setDistanceFilter(Integer distanceFilter) {
        this.distanceFilter = distanceFilter;
    }

    public boolean hasDebug() {
        return debug != null;
    }

    public Boolean isDebugging() {
        return debug != null && debug;
    }

    public void setDebugging(Boolean debug) {
        this.debug = debug;
    }

    public boolean hasNotificationIconColor() {
        return notificationIconColor != null && !notificationIconColor.isEmpty();
    }

    public String getNotificationIconColor() {
        return notificationIconColor;
    }

    public void setNotificationIconColor(String notificationIconColor) {
        this.notificationIconColor = notificationIconColor;
    }

    public boolean hasNotificationTitle() {
        return notificationTitle != null;
    }

    public String getNotificationTitle() {
        return notificationTitle;
    }

    public void setNotificationTitle(String notificationTitle) {
        this.notificationTitle = notificationTitle;
    }

    public boolean hasNotificationText() {
        return notificationText != null;
    }

    public String getNotificationText() {
        return notificationText;
    }

    public void setNotificationText(String notificationText) {
        this.notificationText = notificationText;
    }

    public String getNotificationSyncTitle() {
        return notificationSyncTitle != null ? notificationSyncTitle : "Syncing locations";
    }

    public void setNotificationSyncTitle(String notificationSyncTitle) {
        this.notificationSyncTitle = notificationSyncTitle;
    }

    public String getNotificationSyncText() {
        return notificationSyncText != null ? notificationSyncText : "Sync in progress";
    }

    public void setNotificationSyncText(String notificationSyncText) {
        this.notificationSyncText = notificationSyncText;
    }

    public String getNotificationSyncCompletedText() {
        return notificationSyncCompletedText != null ? notificationSyncCompletedText : "Sync completed";
    }

    public void setNotificationSyncCompletedText(String notificationSyncCompletedText) {
        this.notificationSyncCompletedText = notificationSyncCompletedText;
    }

    public String getNotificationSyncFailedText() {
        return notificationSyncFailedText != null ? notificationSyncFailedText : "Sync failed";
    }

    public void setNotificationSyncFailedText(String notificationSyncFailedText) {
        this.notificationSyncFailedText = notificationSyncFailedText;
    }

    public boolean hasLargeNotificationIcon() {
        return notificationIconLarge != null && !notificationIconLarge.isEmpty();
    }

    public String getLargeNotificationIcon () {
        return notificationIconLarge;
    }

    public void setLargeNotificationIcon (String icon) {
        this.notificationIconLarge = icon;
    }

    public boolean hasSmallNotificationIcon() {
        return notificationIconSmall != null && !notificationIconSmall.isEmpty();
    }

    public String getSmallNotificationIcon () {
        return notificationIconSmall;
    }

    public void setSmallNotificationIcon (String icon) {
        this.notificationIconSmall = icon;
    }

    public boolean hasStopOnTerminate() {
        return stopOnTerminate != null;
    }

    public Boolean getStopOnTerminate() {
        return stopOnTerminate;
    }

    public void setStopOnTerminate(Boolean stopOnTerminate) {
        this.stopOnTerminate = stopOnTerminate;
    }

    public boolean hasStartOnBoot() {
        return startOnBoot != null;
    }

    public Boolean getStartOnBoot() {
        return startOnBoot;
    }

    public void setStartOnBoot(Boolean startOnBoot) {
        this.startOnBoot = startOnBoot;
    }

    public boolean hasStartForeground() {
        return startForeground != null;
    }

    public Boolean getStartForeground() {
        return startForeground;
    }

    public void setStartForeground(Boolean startForeground) {
        this.startForeground = startForeground;
    }

    public boolean hasNotificationsEnabled() {
        return notificationsEnabled != null;
    }

    @Nullable
    public Boolean getNotificationsEnabled() {
        return notificationsEnabled;
    }

    public void setNotificationsEnabled(@Nullable Boolean notificationsEnabled) {
        this.notificationsEnabled = notificationsEnabled;
    }

    public boolean hasLocationProvider() {
        return locationProvider != null;
    }

    public Integer getLocationProvider() {
        return locationProvider;
    }

    public void setLocationProvider(Integer locationProvider) {
        this.locationProvider = locationProvider;
    }

    public boolean hasInterval() {
        return interval != null;
    }

    public Integer getInterval() {
        return interval;
    }

    public void setInterval(Integer interval) {
        this.interval = interval;
    }

    public boolean hasFastestInterval() {
        return fastestInterval != null;
    }

    public Integer getFastestInterval() {
        return fastestInterval;
    }

    public void setFastestInterval(Integer fastestInterval) {
        this.fastestInterval = fastestInterval;
    }

    public boolean hasActivitiesInterval() {
        return activitiesInterval != null;
    }

    public Integer getActivitiesInterval() {
        return activitiesInterval;
    }

    public void setActivitiesInterval(Integer activitiesInterval) {
        this.activitiesInterval = activitiesInterval;
    }

    public boolean hasStopOnStillActivity() {
        return stopOnStillActivity != null;
    }

    public Boolean getStopOnStillActivity() {
        return stopOnStillActivity;
    }

    public void setStopOnStillActivity(Boolean stopOnStillActivity) {
        this.stopOnStillActivity = stopOnStillActivity;
    }

    public boolean hasUrl() {
        return url != null;
    }
    public boolean hasValidUrl() {
        return url != null && !url.isEmpty();
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean hasSyncUrl() {
        return syncUrl != null;
    }
    public boolean hasValidSyncUrl() {
        return syncUrl != null && !syncUrl.isEmpty();
    }

    public String getSyncUrl() {
        return syncUrl;
    }

    public void setSyncUrl(String syncUrl) {
        this.syncUrl = syncUrl;
    }

    public boolean hasSyncThreshold() {
        return syncThreshold != null;
    }

    public Integer getSyncThreshold() {
        return syncThreshold;
    }

    public void setSyncThreshold(Integer syncThreshold) {
        this.syncThreshold = syncThreshold;
    }

    public boolean hasSyncEnabled() {
        return syncEnabled != null;
    }

    /** Whether synchronization to syncUrl is enabled. Default true. */
    @Nullable
    public Boolean getSyncEnabled() {
        return syncEnabled != null ? syncEnabled : true;
    }

    public void setSyncEnabled(@Nullable Boolean syncEnabled) {
        this.syncEnabled = syncEnabled;
    }

    public boolean hasHttpHeaders() {
        return httpHeaders != null;
    }

    public HashMap<String, String> getHttpHeaders() {
        if (!hasHttpHeaders()) {
            httpHeaders = new HashMap<String, String>();
        }

        return httpHeaders;
    }

    public void setHttpHeaders(HashMap httpHeaders) {
        this.httpHeaders = httpHeaders;
    }

    public void setHttpHeaders(JSONObject httpHeaders) throws JSONException {
        // intentionally set httpHeaders to empty hash map
        // this allows to reset headers in .fromJSONArray providing empty httpHeaders JSONObject
        this.httpHeaders = new HashMap<String, String>();
        if (httpHeaders == null) {
            return;
        }
        Iterator<?> it = httpHeaders.keys();
        while (it.hasNext()) {
            String key = (String) it.next();
            this.httpHeaders.put(key, httpHeaders.getString(key));
        }
    }

    public boolean hasMaxLocations() {
        return maxLocations != null;
    }

    public Integer getMaxLocations() {
        return maxLocations;
    }

    public void setMaxLocations(Integer maxLocations) {
        this.maxLocations = maxLocations;
    }

    public boolean hasTemplate() {
        return template != null;
    }

    public LocationTemplate getTemplate() {
        if (!hasTemplate()) {
            template = LocationTemplateFactory.getDefault();
        }
        return template;
    }

    public void setTemplate(LocationTemplate template) {
        this.template = template;
    }

    public boolean hasEnableWatchdog() {
        return enableWatchdog != null;
    }

    @Nullable
    public Boolean getEnableWatchdog() {
        return enableWatchdog;
    }

    public void setEnableWatchdog(Boolean enableWatchdog) {
        this.enableWatchdog = enableWatchdog;
    }

    @Nullable
    public Long getWatchdogIntervalMs() {
        return watchdogIntervalMs;
    }

    public void setWatchdogIntervalMs(Long watchdogIntervalMs) {
        this.watchdogIntervalMs = watchdogIntervalMs;
    }

    @Nullable
    public Boolean getIncludeBattery() {
        return includeBattery;
    }

    public void setIncludeBattery(Boolean includeBattery) {
        this.includeBattery = includeBattery;
    }

    @Nullable public String getWakeLockMode() { return wakeLockMode; }
    public void setWakeLockMode(String mode) { this.wakeLockMode = mode; }
    @Nullable public Integer getStationaryTimeout() { return stationaryTimeout; }
    public void setStationaryTimeout(Integer ms) { this.stationaryTimeout = ms; }
    @Nullable public Integer getStationaryPollInterval() { return stationaryPollInterval; }
    public void setStationaryPollInterval(Integer ms) { this.stationaryPollInterval = ms; }
    @Nullable public Integer getStationaryPollFast() { return stationaryPollFast; }
    public void setStationaryPollFast(Integer ms) { this.stationaryPollFast = ms; }
    @Nullable public Integer getActivityConfidenceThreshold() { return activityConfidenceThreshold; }
    public void setActivityConfidenceThreshold(Integer v) { this.activityConfidenceThreshold = v; }
    @Nullable public Float getMaxAcceptedAccuracy() { return maxAcceptedAccuracy; }
    public void setMaxAcceptedAccuracy(Float v) { this.maxAcceptedAccuracy = v; }

    public boolean hasShowTime() {
        return showTime != null;
    }

    @Nullable
    public Boolean getShowTime() {
        return showTime;
    }

    public void setShowTime(Boolean showTime) {
        this.showTime = showTime;
    }

    public boolean hasShowDistance() {
        return showDistance != null;
    }

    @Nullable
    public Boolean getShowDistance() {
        return showDistance;
    }

    public void setShowDistance(Boolean showDistance) {
        this.showDistance = showDistance;
    }

    /** HTTP method for the main `url`. Default POST. */
    @Nullable
    public String getHttpMethod() {
        return httpMethod != null ? httpMethod : "POST";
    }

    public void setHttpMethod(@Nullable String httpMethod) {
        this.httpMethod = (httpMethod == null || httpMethod.isEmpty()) ? null : httpMethod.toUpperCase(Locale.US);
    }

    /** HTTP method for the `syncUrl`. Default POST. */
    @Nullable
    public String getSyncHttpMethod() {
        return syncHttpMethod != null ? syncHttpMethod : "POST";
    }

    public void setSyncHttpMethod(@Nullable String syncHttpMethod) {
        this.syncHttpMethod = (syncHttpMethod == null || syncHttpMethod.isEmpty()) ? null : syncHttpMethod.toUpperCase(Locale.US);
    }

    /** Real-time post mode. "batch" (default) or "single". */
    @Nullable
    public String getHttpMode() {
        return httpMode != null ? httpMode : "batch";
    }

    public void setHttpMode(@Nullable String httpMode) {
        this.httpMode = (httpMode == null || httpMode.isEmpty()) ? null : httpMode.toLowerCase(Locale.US);
    }

    /** Sync queue mode. "batch" (default) or "single". */
    @Nullable
    public String getSyncMode() {
        return syncMode != null ? syncMode : "batch";
    }

    public void setSyncMode(@Nullable String syncMode) {
        this.syncMode = (syncMode == null || syncMode.isEmpty()) ? null : syncMode.toLowerCase(Locale.US);
    }

    public boolean hasQueryParams() {
        return queryParams != null && !queryParams.isEmpty();
    }

    public HashMap<String, String> getQueryParams() {
        return queryParams;
    }

    public void setQueryParams(HashMap queryParams) {
        this.queryParams = queryParams;
    }

    public void setQueryParams(JSONObject queryParams) throws JSONException {
        this.queryParams = new HashMap<String, String>();
        if (queryParams == null) return;
        Iterator<?> it = queryParams.keys();
        while (it.hasNext()) {
            String key = (String) it.next();
            // queryParams accepts string | number (per d.ts). Convert numbers to string.
            Object value = queryParams.get(key);
            this.queryParams.put(key, value == null || value == JSONObject.NULL ? "" : String.valueOf(value));
        }
    }

    /** Heartbeat emit interval in ms. 0 disables. */
    @Nullable
    public Integer getHeartbeatInterval() {
        return heartbeatInterval != null ? heartbeatInterval : 0;
    }

    public void setHeartbeatInterval(Integer heartbeatInterval) {
        this.heartbeatInterval = heartbeatInterval;
    }

    /** Mock location policy: "allow" | "flag" | "drop". Default "allow". */
    @Nullable
    public String getMockLocationPolicy() {
        return mockLocationPolicy != null ? mockLocationPolicy : "allow";
    }

    public void setMockLocationPolicy(@Nullable String mockLocationPolicy) {
        this.mockLocationPolicy = (mockLocationPolicy == null || mockLocationPolicy.isEmpty())
                ? null
                : mockLocationPolicy.toLowerCase(Locale.US);
    }

    /** v4.0 Phase 6: driver-insights options. */
    @Nullable
    public DrivingEventsOptions getDrivingEvents() {
        return drivingEvents;
    }

    public void setDrivingEvents(@Nullable DrivingEventsOptions drivingEvents) {
        this.drivingEvents = drivingEvents;
    }

    @Override
    public String toString () {
        return new StringBuffer()
                .append("Config[distanceFilter=").append(getDistanceFilter())
                .append(" stationaryRadius=").append(getStationaryRadius())
                .append(" desiredAccuracy=").append(getDesiredAccuracy())
                .append(" interval=").append(getInterval())
                .append(" fastestInterval=").append(getFastestInterval())
                .append(" activitiesInterval=").append(getActivitiesInterval())
                .append(" isDebugging=").append(isDebugging())
                .append(" stopOnTerminate=" ).append(getStopOnTerminate())
                .append(" stopOnStillActivity=").append(getStopOnStillActivity())
                .append(" startOnBoot=").append(getStartOnBoot())
                .append(" startForeground=").append(getStartForeground())
                .append(" notificationsEnabled=").append(getNotificationsEnabled())
                .append(" locationProvider=").append(getLocationProvider())
                .append(" nTitle=").append(getNotificationTitle())
                .append(" nText=").append(getNotificationText())
                .append(" nIconLarge=").append(getLargeNotificationIcon())
                .append(" nIconSmall=").append(getSmallNotificationIcon())
                .append(" nIconColor=").append(getNotificationIconColor())
                .append(" url=").append(getUrl())
                .append(" syncUrl=").append(getSyncUrl())
                .append(" syncThreshold=").append(getSyncThreshold())
                .append(" syncEnabled=").append(getSyncEnabled())
                .append(" httpHeaders=").append(getHttpHeaders().toString())
                .append(" maxLocations=").append(getMaxLocations())
                .append(" postTemplate=").append(hasTemplate() ? getTemplate().toString() : null)
                .append(" showTime=").append(getShowTime())
                .append(" showDistance=").append(getShowDistance())
                .append(" httpMethod=").append(getHttpMethod())
                .append(" syncHttpMethod=").append(getSyncHttpMethod())
                .append(" httpMode=").append(getHttpMode())
                .append(" syncMode=").append(getSyncMode())
                .append(" queryParams=").append(hasQueryParams() ? getQueryParams().toString() : null)
                .append("]")
                .toString();
    }

    public Parcel toParcel () {
        Parcel parcel = Parcel.obtain();
        this.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        return parcel;
    }

    public Bundle toBundle () {
        Bundle bundle = new Bundle();
        bundle.putParcelable(BUNDLE_KEY, this);
        return bundle;
    }

    public static Config merge(Config config1, Config config2) {
        Config merger = new Config(config1);

        if (config2.hasStationaryRadius()) {
            merger.setStationaryRadius(config2.getStationaryRadius());
        }
        if (config2.hasDistanceFilter()) {
            merger.setDistanceFilter(config2.getDistanceFilter());
        }
        if (config2.hasDesiredAccuracy()) {
            merger.setDesiredAccuracy(config2.getDesiredAccuracy());
        }
        if (config2.hasDebug()) {
            merger.setDebugging(config2.isDebugging());
        }
        if (config2.hasNotificationTitle()) {
            merger.setNotificationTitle(config2.getNotificationTitle());
        }
        if (config2.hasNotificationText()) {
            merger.setNotificationText(config2.getNotificationText());
        }
        if (config2.notificationSyncTitle != null) {
            merger.setNotificationSyncTitle(config2.getNotificationSyncTitle());
        }
        if (config2.notificationSyncText != null) {
            merger.setNotificationSyncText(config2.getNotificationSyncText());
        }
        if (config2.notificationSyncCompletedText != null) {
            merger.setNotificationSyncCompletedText(config2.getNotificationSyncCompletedText());
        }
        if (config2.notificationSyncFailedText != null) {
            merger.setNotificationSyncFailedText(config2.getNotificationSyncFailedText());
        }
        if (config2.hasStopOnTerminate()) {
            merger.setStopOnTerminate(config2.getStopOnTerminate());
        }
        if (config2.hasStartOnBoot()) {
            merger.setStartOnBoot(config2.getStartOnBoot());
        }
        if (config2.hasLocationProvider()) {
            merger.setLocationProvider(config2.getLocationProvider());
        }
        if (config2.hasInterval()) {
            merger.setInterval(config2.getInterval());
        }
        if (config2.hasFastestInterval()) {
            merger.setFastestInterval(config2.getFastestInterval());
        }
        if (config2.hasActivitiesInterval()) {
            merger.setActivitiesInterval(config2.getActivitiesInterval());
        }
        if (config2.hasNotificationIconColor()) {
            merger.setNotificationIconColor(config2.getNotificationIconColor());
        }
        if (config2.hasLargeNotificationIcon()) {
            merger.setLargeNotificationIcon(config2.getLargeNotificationIcon());
        }
        if (config2.hasSmallNotificationIcon()) {
            merger.setSmallNotificationIcon(config2.getSmallNotificationIcon());
        }
        if (config2.hasStartForeground()) {
            merger.setStartForeground(config2.getStartForeground());
        }
        if (config2.hasNotificationsEnabled()) {
            merger.setNotificationsEnabled(config2.getNotificationsEnabled());
        }
        if (config2.hasStopOnStillActivity()) {
            merger.setStopOnStillActivity(config2.getStopOnStillActivity());
        }
        if (config2.hasUrl()) {
            merger.setUrl(config2.getUrl());
        }
        if (config2.hasSyncUrl()) {
            merger.setSyncUrl(config2.getSyncUrl());
        }
        if (config2.hasSyncThreshold()) {
            merger.setSyncThreshold(config2.getSyncThreshold());
        }
        if (config2.hasSyncEnabled()) {
            merger.setSyncEnabled(config2.getSyncEnabled());
        }
        if (config2.hasHttpHeaders()) {
            merger.setHttpHeaders(config2.getHttpHeaders());
        }
        if (config2.hasMaxLocations()) {
            merger.setMaxLocations(config2.getMaxLocations());
        }
        if (config2.hasTemplate()) {
            merger.setTemplate(config2.getTemplate());
        }
        if (config2.hasShowTime()) {
            merger.setShowTime(config2.getShowTime());
        }
        if (config2.hasShowDistance()) {
            merger.setShowDistance(config2.getShowDistance());
        }
        if (config2.httpMethod != null) {
            merger.setHttpMethod(config2.getHttpMethod());
        }
        if (config2.syncHttpMethod != null) {
            merger.setSyncHttpMethod(config2.getSyncHttpMethod());
        }
        if (config2.httpMode != null) {
            merger.setHttpMode(config2.getHttpMode());
        }
        if (config2.syncMode != null) {
            merger.setSyncMode(config2.getSyncMode());
        }
        if (config2.hasQueryParams()) {
            merger.setQueryParams(config2.getQueryParams());
        }
        if (config2.heartbeatInterval != null) {
            merger.setHeartbeatInterval(config2.getHeartbeatInterval());
        }
        if (config2.mockLocationPolicy != null) {
            merger.setMockLocationPolicy(config2.getMockLocationPolicy());
        }
        if (config2.drivingEvents != null) {
            merger.setDrivingEvents(config2.drivingEvents);
        }
        // v4.4.1 — was missing: configure({includeBattery: false}) was being ignored.
        if (config2.includeBattery != null) {
            merger.setIncludeBattery(config2.getIncludeBattery());
        }
        // v4.5.1 — battery-saving knobs.
        if (config2.wakeLockMode != null) merger.setWakeLockMode(config2.wakeLockMode);
        if (config2.stationaryTimeout != null) merger.setStationaryTimeout(config2.stationaryTimeout);
        if (config2.stationaryPollInterval != null) merger.setStationaryPollInterval(config2.stationaryPollInterval);
        if (config2.stationaryPollFast != null) merger.setStationaryPollFast(config2.stationaryPollFast);
        // v4.5.2
        if (config2.activityConfidenceThreshold != null) merger.setActivityConfidenceThreshold(config2.activityConfidenceThreshold);
        if (config2.maxAcceptedAccuracy != null) merger.setMaxAcceptedAccuracy(config2.maxAcceptedAccuracy);
        // P2
        if (config2.watchdogIntervalMs != null) merger.setWatchdogIntervalMs(config2.watchdogIntervalMs);

        return merger;
    }

    public static Config fromByteArray (byte[] byteArray) {
        Parcel parcel = Parcel.obtain();
        parcel.unmarshall(byteArray, 0, byteArray.length);
        parcel.setDataPosition(0);
        return Config.CREATOR.createFromParcel(parcel);
    }
}
