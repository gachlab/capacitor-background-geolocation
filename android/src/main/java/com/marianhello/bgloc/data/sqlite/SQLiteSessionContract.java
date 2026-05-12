package com.marianhello.bgloc.data.sqlite;

import android.provider.BaseColumns;

import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.COMMA_SEP;
import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.INTEGER_TYPE;
import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.REAL_TYPE;
import static com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper.TEXT_TYPE;

/**
 * Contract for the "session" location table.
 * Stores all locations for the current recording session (route).
 * Cleared on startSession() and clearSession(); not cleared when sync succeeds.
 */
public final class SQLiteSessionContract {

    public SQLiteSessionContract() {}

    public static abstract class SessionEntry implements BaseColumns {
        public static final String TABLE_NAME = "location_session";
        public static final String COLUMN_NAME_NULLABLE = "NULLHACK";
        public static final String COLUMN_NAME_TIME = "time";
        public static final String COLUMN_NAME_ACCURACY = "accuracy";
        public static final String COLUMN_NAME_VERTICAL_ACCURACY = "vertical_accuracy";
        public static final String COLUMN_NAME_SPEED = "speed";
        public static final String COLUMN_NAME_BEARING = "bearing";
        public static final String COLUMN_NAME_ALTITUDE = "altitude";
        public static final String COLUMN_NAME_LATITUDE = "latitude";
        public static final String COLUMN_NAME_LONGITUDE = "longitude";
        public static final String COLUMN_NAME_RADIUS = "radius";
        public static final String COLUMN_NAME_HAS_ACCURACY = "has_accuracy";
        public static final String COLUMN_NAME_HAS_VERTICAL_ACCURACY = "has_vertical_accuracy";
        public static final String COLUMN_NAME_HAS_SPEED = "has_speed";
        public static final String COLUMN_NAME_HAS_BEARING = "has_bearing";
        public static final String COLUMN_NAME_HAS_ALTITUDE = "has_altitude";
        public static final String COLUMN_NAME_HAS_RADIUS = "has_radius";
        public static final String COLUMN_NAME_PROVIDER = "provider";
        public static final String COLUMN_NAME_LOCATION_PROVIDER = "service_provider";
        public static final String COLUMN_NAME_STATUS = "valid";
        public static final String COLUMN_NAME_BATCH_START_MILLIS = "batch_start";
        public static final String COLUMN_NAME_MOCK_FLAGS = "mock_flags";

        public static final String SQL_CREATE_SESSION_TABLE =
                "CREATE TABLE " + SessionEntry.TABLE_NAME + " (" +
                        SessionEntry._ID + " INTEGER PRIMARY KEY AUTOINCREMENT," +
                        SessionEntry.COLUMN_NAME_TIME + INTEGER_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_ACCURACY + REAL_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_VERTICAL_ACCURACY + REAL_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_SPEED + REAL_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_BEARING + REAL_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_ALTITUDE + REAL_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_LATITUDE + REAL_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_LONGITUDE + REAL_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_RADIUS + REAL_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_HAS_ACCURACY + INTEGER_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_HAS_VERTICAL_ACCURACY + INTEGER_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_HAS_SPEED + INTEGER_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_HAS_BEARING + INTEGER_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_HAS_ALTITUDE + INTEGER_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_HAS_RADIUS + INTEGER_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_PROVIDER + TEXT_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_LOCATION_PROVIDER + INTEGER_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_STATUS + INTEGER_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_BATCH_START_MILLIS + INTEGER_TYPE + COMMA_SEP +
                        SessionEntry.COLUMN_NAME_MOCK_FLAGS + INTEGER_TYPE +
                        " )";

        public static final String SQL_DROP_SESSION_TABLE =
                "DROP TABLE IF EXISTS " + SessionEntry.TABLE_NAME;

        public static final String SQL_CREATE_SESSION_TABLE_TIME_IDX =
                "CREATE INDEX session_time_idx ON " + SessionEntry.TABLE_NAME + " (" + SessionEntry.COLUMN_NAME_TIME + ")";
    }
}
