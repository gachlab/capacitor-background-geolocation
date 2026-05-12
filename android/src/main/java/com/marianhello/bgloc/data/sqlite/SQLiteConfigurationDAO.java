package com.marianhello.bgloc.data.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import org.json.JSONObject;
import org.json.JSONException;

import com.marianhello.bgloc.Config;
import com.marianhello.bgloc.data.ConfigJsonMapper;
import com.marianhello.bgloc.data.ConfigurationDAO;
import com.marianhello.bgloc.data.LocationTemplateFactory;
import com.marianhello.bgloc.data.sqlite.SQLiteConfigurationContract.ConfigurationEntry;

public class SQLiteConfigurationDAO implements ConfigurationDAO {
  private static final String TAG = SQLiteConfigurationDAO.class.getName();

    private SQLiteDatabase db;

  public SQLiteConfigurationDAO(Context context) {
    SQLiteOpenHelper helper = SQLiteOpenHelper.getHelper(context);
    this.db = helper.getWritableDatabase();
  }

  public SQLiteConfigurationDAO(SQLiteDatabase db) {
    this.db = db;
  }

  public Config retrieveConfiguration() throws JSONException {
    Cursor cursor = null;

    String[] columns = {
      ConfigurationEntry._ID,
      ConfigurationEntry.COLUMN_NAME_RADIUS,
      ConfigurationEntry.COLUMN_NAME_DISTANCE_FILTER,
      ConfigurationEntry.COLUMN_NAME_DESIRED_ACCURACY,
      ConfigurationEntry.COLUMN_NAME_DEBUG,
      ConfigurationEntry.COLUMN_NAME_NOTIF_TITLE,
      ConfigurationEntry.COLUMN_NAME_NOTIF_TEXT,
      ConfigurationEntry.COLUMN_NAME_NOTIF_SYNC_TITLE,
      ConfigurationEntry.COLUMN_NAME_NOTIF_SYNC_TEXT,
      ConfigurationEntry.COLUMN_NAME_NOTIF_SYNC_COMPLETED,
      ConfigurationEntry.COLUMN_NAME_NOTIF_SYNC_FAILED,
      ConfigurationEntry.COLUMN_NAME_NOTIF_ICON_LARGE,
      ConfigurationEntry.COLUMN_NAME_NOTIF_ICON_SMALL,
      ConfigurationEntry.COLUMN_NAME_NOTIF_COLOR,
      ConfigurationEntry.COLUMN_NAME_STOP_TERMINATE,
      ConfigurationEntry.COLUMN_NAME_STOP_ON_STILL,
      ConfigurationEntry.COLUMN_NAME_START_BOOT,
      ConfigurationEntry.COLUMN_NAME_START_FOREGROUND,
      ConfigurationEntry.COLUMN_NAME_NOTIFICATIONS_ENABLED,
      ConfigurationEntry.COLUMN_NAME_LOCATION_PROVIDER,
      ConfigurationEntry.COLUMN_NAME_INTERVAL,
      ConfigurationEntry.COLUMN_NAME_FASTEST_INTERVAL,
      ConfigurationEntry.COLUMN_NAME_ACTIVITIES_INTERVAL,
      ConfigurationEntry.COLUMN_NAME_URL,
      ConfigurationEntry.COLUMN_NAME_SYNC_URL,
      ConfigurationEntry.COLUMN_NAME_SYNC_THRESHOLD,
      ConfigurationEntry.COLUMN_NAME_SYNC_ENABLED,
      ConfigurationEntry.COLUMN_NAME_HEADERS,
      ConfigurationEntry.COLUMN_NAME_MAX_LOCATIONS,
      ConfigurationEntry.COLUMN_NAME_TEMPLATE,
      ConfigurationEntry.COLUMN_NAME_SHOW_TIME,
      ConfigurationEntry.COLUMN_NAME_SHOW_DISTANCE,
      ConfigurationEntry.COLUMN_NAME_CONFIG_JSON
    };

    String whereClause = null;
    String[] whereArgs = null;
    String groupBy = null;
    String having = null;
    String orderBy = null;

    Config config = null;
    try {
      cursor = db.query(
          ConfigurationEntry.TABLE_NAME,  // The table to query
          columns,                   // The columns to return
          whereClause,               // The columns for the WHERE clause
          whereArgs,                 // The values for the WHERE clause
          groupBy,                   // don't group the rows
          having,                    // don't filter by row groups
          orderBy                    // The sort order
      );
      if (cursor.moveToFirst()) {
        config = hydrate(cursor);
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
    return config;
  }

  public boolean persistConfiguration(Config config) throws NullPointerException {
    long rowId = db.replace(ConfigurationEntry.TABLE_NAME, ConfigurationEntry.COLUMN_NAME_NULLABLE, getContentValues(config));
    Log.d(TAG, "Configuration persisted with rowId = " + rowId);
    if (rowId > -1) {
      return true;
    } else {
      return false;
    }
  }

  private Config hydrate(Cursor c) throws JSONException {
    // v4.4.1: prefer the full JSON blob if present (covers all keys, including post-3.2 ones).
    // Note: `template` (postTemplate) lives in its dedicated column — restore it after the
    // JSON deserialization so it's not lost when the config arrived via config_json only.
    int idxJson = c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_CONFIG_JSON);
    if (idxJson >= 0 && !c.isNull(idxJson)) {
      String json = c.getString(idxJson);
      if (json != null && !json.isEmpty()) {
        try {
          Config restored = ConfigJsonMapper.fromJSONObject(new JSONObject(json));
          int idxTpl = c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_TEMPLATE);
          if (idxTpl >= 0 && !c.isNull(idxTpl)) {
            restored.setTemplate(LocationTemplateFactory.fromJSONString(c.getString(idxTpl)));
          }
          return restored;
        } catch (JSONException ex) {
          Log.w(TAG, "config_json parse failed; falling back to legacy columns: " + ex.getMessage());
        }
      }
    }
    // Legacy hydration (DBs upgraded from v20 or earlier where config_json is still NULL).
    Config config = Config.getDefault();
    config.setStationaryRadius(c.getFloat(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_RADIUS)));
    config.setDistanceFilter(c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_DISTANCE_FILTER)));
    config.setDesiredAccuracy(c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_DESIRED_ACCURACY)));
    config.setDebugging( (c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_DEBUG)) == 1) ? true : false );
    config.setNotificationTitle(c.getString(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_NOTIF_TITLE)));
    config.setNotificationText(c.getString(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_NOTIF_TEXT)));
    int idxSyncTitle = c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_NOTIF_SYNC_TITLE);
    if (idxSyncTitle >= 0) {
      if (!c.isNull(idxSyncTitle)) config.setNotificationSyncTitle(c.getString(idxSyncTitle));
      int idxSyncText = c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_NOTIF_SYNC_TEXT);
      if (idxSyncText >= 0 && !c.isNull(idxSyncText)) config.setNotificationSyncText(c.getString(idxSyncText));
      int idxSyncCompleted = c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_NOTIF_SYNC_COMPLETED);
      if (idxSyncCompleted >= 0 && !c.isNull(idxSyncCompleted)) config.setNotificationSyncCompletedText(c.getString(idxSyncCompleted));
      int idxSyncFailed = c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_NOTIF_SYNC_FAILED);
      if (idxSyncFailed >= 0 && !c.isNull(idxSyncFailed)) config.setNotificationSyncFailedText(c.getString(idxSyncFailed));
    }
    config.setSmallNotificationIcon(c.getString(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_NOTIF_ICON_SMALL)));
    config.setLargeNotificationIcon(c.getString(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_NOTIF_ICON_LARGE)));
    config.setNotificationIconColor(c.getString(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_NOTIF_COLOR)));
    config.setStopOnTerminate( (c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_STOP_TERMINATE)) == 1) ? true : false );
    config.setStopOnStillActivity( (c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_STOP_ON_STILL)) == 1) ? true : false );
    config.setStartOnBoot( (c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_START_BOOT)) == 1) ? true : false );
    config.setStartForeground( (c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_START_FOREGROUND)) == 1) ? true : false );
    config.setNotificationsEnabled( (c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_NOTIFICATIONS_ENABLED)) == 1) ? true : false );
    config.setLocationProvider(c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_LOCATION_PROVIDER)));
    config.setInterval(c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_INTERVAL)));
    config.setFastestInterval(c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_FASTEST_INTERVAL)));
    config.setActivitiesInterval(c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_ACTIVITIES_INTERVAL)));
    config.setUrl(c.getString(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_URL)));
    config.setSyncUrl(c.getString(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_SYNC_URL)));
    config.setSyncThreshold(c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_SYNC_THRESHOLD)));
    int idxSyncEnabled = c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_SYNC_ENABLED);
    if (idxSyncEnabled >= 0 && !c.isNull(idxSyncEnabled)) {
      config.setSyncEnabled(c.getInt(idxSyncEnabled) == 1);
    }
    config.setHttpHeaders(new JSONObject(c.getString(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_HEADERS))));
    config.setMaxLocations(c.getInt(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_MAX_LOCATIONS)));
    config.setTemplate(LocationTemplateFactory.fromJSONString(c.getString(c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_TEMPLATE))));
    int idxShowTime = c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_SHOW_TIME);
    if (idxShowTime >= 0 && !c.isNull(idxShowTime)) {
      config.setShowTime(c.getInt(idxShowTime) == 1);
    }
    int idxShowDistance = c.getColumnIndex(ConfigurationEntry.COLUMN_NAME_SHOW_DISTANCE);
    if (idxShowDistance >= 0 && !c.isNull(idxShowDistance)) {
      config.setShowDistance(c.getInt(idxShowDistance) == 1);
    }

    return config;
  }

  private ContentValues getContentValues(Config config) throws NullPointerException {
    ContentValues values = new ContentValues();
    values.put(ConfigurationEntry._ID, 1);
    values.put(ConfigurationEntry.COLUMN_NAME_RADIUS, config.getStationaryRadius());
    values.put(ConfigurationEntry.COLUMN_NAME_DISTANCE_FILTER, config.getDistanceFilter());
    values.put(ConfigurationEntry.COLUMN_NAME_DESIRED_ACCURACY, config.getDesiredAccuracy());
    values.put(ConfigurationEntry.COLUMN_NAME_DEBUG, (config.isDebugging() == true) ? 1 : 0);
    values.put(ConfigurationEntry.COLUMN_NAME_NOTIF_TITLE, config.getNotificationTitle());
    values.put(ConfigurationEntry.COLUMN_NAME_NOTIF_TEXT, config.getNotificationText());
    values.put(ConfigurationEntry.COLUMN_NAME_NOTIF_SYNC_TITLE, config.getNotificationSyncTitle());
    values.put(ConfigurationEntry.COLUMN_NAME_NOTIF_SYNC_TEXT, config.getNotificationSyncText());
    values.put(ConfigurationEntry.COLUMN_NAME_NOTIF_SYNC_COMPLETED, config.getNotificationSyncCompletedText());
    values.put(ConfigurationEntry.COLUMN_NAME_NOTIF_SYNC_FAILED, config.getNotificationSyncFailedText());
    values.put(ConfigurationEntry.COLUMN_NAME_NOTIF_ICON_SMALL, config.getSmallNotificationIcon());
    values.put(ConfigurationEntry.COLUMN_NAME_NOTIF_ICON_LARGE, config.getLargeNotificationIcon());
    values.put(ConfigurationEntry.COLUMN_NAME_NOTIF_COLOR, config.getNotificationIconColor());
    values.put(ConfigurationEntry.COLUMN_NAME_STOP_TERMINATE, (config.getStopOnTerminate() == true) ? 1 : 0);
    values.put(ConfigurationEntry.COLUMN_NAME_STOP_ON_STILL, (config.getStopOnStillActivity() == true) ? 1 : 0);
    values.put(ConfigurationEntry.COLUMN_NAME_START_BOOT, (config.getStartOnBoot() == true) ? 1 : 0);
    values.put(ConfigurationEntry.COLUMN_NAME_START_FOREGROUND, (config.getStartForeground() == true) ? 1 : 0);
    values.put(ConfigurationEntry.COLUMN_NAME_NOTIFICATIONS_ENABLED, (config.getNotificationsEnabled() == true) ? 1 : 0);
    values.put(ConfigurationEntry.COLUMN_NAME_LOCATION_PROVIDER, config.getLocationProvider());
    values.put(ConfigurationEntry.COLUMN_NAME_INTERVAL, config.getInterval());
    values.put(ConfigurationEntry.COLUMN_NAME_FASTEST_INTERVAL, config.getFastestInterval());
    values.put(ConfigurationEntry.COLUMN_NAME_ACTIVITIES_INTERVAL, config.getActivitiesInterval());
    values.put(ConfigurationEntry.COLUMN_NAME_URL, config.getUrl());
    values.put(ConfigurationEntry.COLUMN_NAME_SYNC_URL, config.getSyncUrl());
    values.put(ConfigurationEntry.COLUMN_NAME_SYNC_THRESHOLD, config.getSyncThreshold());
    values.put(ConfigurationEntry.COLUMN_NAME_SYNC_ENABLED, Boolean.TRUE.equals(config.getSyncEnabled()) ? 1 : 0);
    values.put(ConfigurationEntry.COLUMN_NAME_HEADERS, new JSONObject(config.getHttpHeaders()).toString());
    values.put(ConfigurationEntry.COLUMN_NAME_MAX_LOCATIONS, config.getMaxLocations());
    values.put(ConfigurationEntry.COLUMN_NAME_TEMPLATE, config.hasTemplate() ? config.getTemplate().toString() : null);
    values.put(ConfigurationEntry.COLUMN_NAME_SHOW_TIME, Boolean.TRUE.equals(config.getShowTime()) ? 1 : 0);
    values.put(ConfigurationEntry.COLUMN_NAME_SHOW_DISTANCE, Boolean.TRUE.equals(config.getShowDistance()) ? 1 : 0);
    // v4.4.1: persist the full Config as JSON so post-3.2 fields (httpMethod, queryParams,
    // drivingEvents, includeBattery, mockLocationPolicy, heartbeatInterval, ...) survive
    // a reboot / startOnBoot. Legacy columns are kept populated above for backward compat.
    try {
      values.put(ConfigurationEntry.COLUMN_NAME_CONFIG_JSON,
              ConfigJsonMapper.toJSONObject(config).toString());
    } catch (JSONException e) {
      Log.w(TAG, "config_json serialize failed: " + e.getMessage());
    }

    return values;
  }
}
