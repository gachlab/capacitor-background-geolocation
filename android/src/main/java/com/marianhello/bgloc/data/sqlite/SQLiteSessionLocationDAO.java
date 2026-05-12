package com.marianhello.bgloc.data.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.SessionLocationDAO;

import java.util.ArrayList;
import java.util.Collection;

public class SQLiteSessionLocationDAO implements SessionLocationDAO {

    private static final String PREFS_NAME = "bgloc_session";
    private static final String KEY_SESSION_ACTIVE = "session_active";

    private final SQLiteDatabase db;
    private final Context context;

    public SQLiteSessionLocationDAO(Context context) {
        this.context = context.getApplicationContext();
        SQLiteOpenHelper helper = SQLiteOpenHelper.getHelper(this.context);
        this.db = helper.getWritableDatabase();
    }

    @Override
    public void startSession() {
        db.delete(SQLiteSessionContract.SessionEntry.TABLE_NAME, null, null);
        setSessionActive(true);
    }

    @Override
    public void clearSession() {
        db.delete(SQLiteSessionContract.SessionEntry.TABLE_NAME, null, null);
        setSessionActive(false);
    }

    @Override
    public boolean isSessionActive() {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_SESSION_ACTIVE, false);
    }

    private void setSessionActive(boolean active) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_SESSION_ACTIVE, active)
                .apply();
    }

    @Override
    public void persistSessionLocation(BackgroundLocation location) {
        if (!isSessionActive() || location == null) return;
        ContentValues values = getContentValues(location);
        db.insertOrThrow(SQLiteSessionContract.SessionEntry.TABLE_NAME,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_NULLABLE, values);
    }

    @Override
    public Collection<BackgroundLocation> getSessionLocations() {
        Collection<BackgroundLocation> locations = new ArrayList<>();
        String orderBy = SQLiteSessionContract.SessionEntry.COLUMN_NAME_TIME + " ASC";
        Cursor cursor = null;
        try {
            cursor = db.query(
                    SQLiteSessionContract.SessionEntry.TABLE_NAME,
                    queryColumns(),
                    null, null, null, null, orderBy);
            while (cursor.moveToNext()) {
                locations.add(hydrate(cursor));
            }
        } finally {
            if (cursor != null) cursor.close();
        }
        return locations;
    }

    @Override
    public int getSessionLocationsCount() {
        Cursor cursor = null;
        try {
            cursor = db.rawQuery("SELECT COUNT(*) FROM " + SQLiteSessionContract.SessionEntry.TABLE_NAME, null);
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private BackgroundLocation hydrate(Cursor c) {
        BackgroundLocation l = new BackgroundLocation(c.getString(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_PROVIDER)));
        l.setTime(c.getLong(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_TIME)));
        if (c.getInt(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_ACCURACY)) == 1) {
            l.setAccuracy(c.getFloat(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_ACCURACY)));
        }
        if (c.getInt(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_VERTICAL_ACCURACY)) == 1) {
            l.setVerticalAccuracy(c.getFloat(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_VERTICAL_ACCURACY)));
        }
        if (c.getInt(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_SPEED)) == 1) {
            l.setSpeed(c.getFloat(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_SPEED)));
        }
        if (c.getInt(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_BEARING)) == 1) {
            l.setBearing(c.getFloat(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_BEARING)));
        }
        if (c.getInt(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_ALTITUDE)) == 1) {
            l.setAltitude(c.getDouble(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_ALTITUDE)));
        }
        if (c.getInt(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_RADIUS)) == 1) {
            l.setRadius(c.getFloat(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_RADIUS)));
        }
        l.setLatitude(c.getDouble(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_LATITUDE)));
        l.setLongitude(c.getDouble(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_LONGITUDE)));
        l.setLocationProvider(c.getInt(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_LOCATION_PROVIDER)));
        l.setLocationId(c.getLong(c.getColumnIndex(SQLiteSessionContract.SessionEntry._ID)));
        l.setMockFlags(c.getInt(c.getColumnIndex(SQLiteSessionContract.SessionEntry.COLUMN_NAME_MOCK_FLAGS)));
        return l;
    }

    private ContentValues getContentValues(BackgroundLocation l) {
        ContentValues values = new ContentValues();
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_PROVIDER, l.getProvider());
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_TIME, l.getTime());
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_ACCURACY, l.getAccuracy());
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_VERTICAL_ACCURACY, l.getVerticalAccuracy());
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_SPEED, l.getSpeed());
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_BEARING, l.getBearing());
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_ALTITUDE, l.getAltitude());
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_RADIUS, l.getRadius());
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_LATITUDE, l.getLatitude());
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_LONGITUDE, l.getLongitude());
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_ACCURACY, l.hasAccuracy() ? 1 : 0);
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_VERTICAL_ACCURACY, l.hasVerticalAccuracy() ? 1 : 0);
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_SPEED, l.hasSpeed() ? 1 : 0);
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_BEARING, l.hasBearing() ? 1 : 0);
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_ALTITUDE, l.hasAltitude() ? 1 : 0);
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_RADIUS, l.hasRadius() ? 1 : 0);
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_LOCATION_PROVIDER, l.getLocationProvider());
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_STATUS, 0);
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_BATCH_START_MILLIS, 0L);
        values.put(SQLiteSessionContract.SessionEntry.COLUMN_NAME_MOCK_FLAGS, l.getMockFlags());
        return values;
    }

    private String[] queryColumns() {
        return new String[]{
                SQLiteSessionContract.SessionEntry._ID,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_PROVIDER,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_TIME,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_ACCURACY,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_VERTICAL_ACCURACY,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_SPEED,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_BEARING,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_ALTITUDE,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_RADIUS,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_LATITUDE,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_LONGITUDE,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_ACCURACY,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_VERTICAL_ACCURACY,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_SPEED,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_BEARING,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_ALTITUDE,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_HAS_RADIUS,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_LOCATION_PROVIDER,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_STATUS,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_BATCH_START_MILLIS,
                SQLiteSessionContract.SessionEntry.COLUMN_NAME_MOCK_FLAGS
        };
    }
}
