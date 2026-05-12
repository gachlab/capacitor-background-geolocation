package com.marianhello.bgloc.sync;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.text.TextUtils;
import android.util.JsonWriter;

import com.marianhello.bgloc.ResourceResolver;
import com.marianhello.bgloc.data.AbstractLocationTemplate;
import com.marianhello.bgloc.data.ArrayListLocationTemplate;
import com.marianhello.bgloc.data.BackgroundLocation;
import com.marianhello.bgloc.data.HashMapLocationTemplate;
import com.marianhello.bgloc.data.LocationTemplate;
import com.marianhello.bgloc.data.LocationTemplateFactory;
import com.marianhello.bgloc.data.provider.LocationContentProvider;
import com.marianhello.bgloc.data.sqlite.SQLiteLocationContract;
import com.marianhello.bgloc.data.sqlite.SQLiteLocationContract.LocationEntry;
import com.marianhello.bgloc.data.sqlite.SQLiteOpenHelper;
import com.marianhello.logging.LoggerManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by finch on 20/07/16.
 */
public class BatchManager {
    private Context context;
    private org.slf4j.Logger logger;

    public BatchManager(Context context) {
        logger = LoggerManager.getLogger(BatchManager.class);
        this.context = context;
    }

    private Uri getLocationContentUri() {
        ResourceResolver resourceResolver = ResourceResolver.newInstance(context);
        String authority = resourceResolver.getAuthority();
        return LocationContentProvider.getContentUri(authority);
    }

    private File createBatchFromTemplate(Long batchStartMillis, Integer syncThreshold, LocationTemplate template) throws IOException {
        logger.info("Creating batch {}", batchStartMillis);

        ContentResolver resolver = context.getContentResolver();
        Uri contentUri = getLocationContentUri();

        String whereClause = TextUtils.join("", new String[]{
                LocationEntry.COLUMN_NAME_STATUS + " = ? AND ( ",
                LocationEntry.COLUMN_NAME_BATCH_START_MILLIS + " IS NULL OR ",
                LocationEntry.COLUMN_NAME_BATCH_START_MILLIS + " < ? )",
        });
        String[] whereArgs = {
                String.valueOf(BackgroundLocation.SYNC_PENDING),
                String.valueOf(batchStartMillis)
        };

        Cursor cursor = null;
        LocationWriter writer = null;
        FileOutputStream fs = null;
        File file = null;
        boolean batchReturned = false;

        try {
            cursor = resolver.query(
                    contentUri,
                    null,
                    whereClause,
                    whereArgs,
                    LocationEntry.COLUMN_NAME_TIME + " ASC"

            );

            if (cursor == null) {
                logger.warn("ContentResolver.query returned null, cannot create batch");
                return null;
            }

            int threshold = (syncThreshold != null && syncThreshold >= 0) ? syncThreshold : 1;
            if (cursor.getCount() < threshold) {
                return null;
            }
            // Never create or send an empty batch (e.g. forceSync with 0 pending locations)
            if (cursor.getCount() == 0) {
                logger.info("No pending locations to sync, skipping batch creation");
                return null;
            }

            file = File.createTempFile("locations", ".json");
            fs = new FileOutputStream(file);
            writer = new LocationWriter(fs, template);

            writer.beginArray();
            while (cursor.moveToNext()) {
                BackgroundLocation location = BackgroundLocation.fromCursor(cursor);
                writer.write(location);
            }

            writer.endArray();
            writer.close();
            writer = null;
            fs.close();
            fs = null;

            // set batchStartMillis for all synced locations
            ContentValues values = new ContentValues();
            values.put(LocationEntry.COLUMN_NAME_BATCH_START_MILLIS, batchStartMillis);
            resolver.update(contentUri, values, whereClause, whereArgs);

            logger.info("Batch file: {} created successfully", file.getName());

            batchReturned = true;
            return file;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    logger.debug("Error closing LocationWriter: {}", e.getMessage());
                }
            }
            if (fs != null) {
                try {
                    fs.close();
                } catch (IOException e) {
                    logger.debug("Error closing FileOutputStream: {}", e.getMessage());
                }
            }
            // if we threw before returning the file, delete temp file to avoid leaving garbage
            if (!batchReturned && file != null && file.exists() && !file.delete()) {
                logger.warn("Could not delete temp batch file: {}", file.getAbsolutePath());
            }
        }
    }

    public File createBatch(Long batchStartMillis, Integer syncThreshold, LocationTemplate template) throws IOException {
        LocationTemplate tpl;
        if (template != null) {
            tpl = template;
        } else {
            tpl = LocationTemplateFactory.getDefault();
        }
        return createBatchFromTemplate(batchStartMillis, syncThreshold, tpl);
    }

    public File createBatch(Long batchStartMillis, Integer syncThreshold) throws IOException {
        return createBatch(batchStartMillis, syncThreshold, null);
    }


    public void setBatchCompleted(Long batchId) {
        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        Uri contentUri = getLocationContentUri();

        String whereClause = LocationEntry.COLUMN_NAME_BATCH_START_MILLIS + " = ?";
        String[] whereArgs = { String.valueOf(batchId) };

        ContentValues values = new ContentValues();
        values.put(LocationEntry.COLUMN_NAME_STATUS, BackgroundLocation.DELETED);
        resolver.update(contentUri, values, whereClause, whereArgs);
    }

    private static class LocationTemplateWriter {
        private BackgroundLocation location;
        private JsonWriter writer;

        public LocationTemplateWriter(JsonWriter writer, BackgroundLocation location) {
            this.writer = writer;
            this.location = location;
        }

        private void writeValue(Object value) throws IOException {
            if (value == null || value == JSONObject.NULL) {
                writer.nullValue();
            } else if (value instanceof String ) {
                writer.value((String) value);
            } else if (value instanceof Map) {
                writeMap((Map) value);
            } else if (value instanceof List) {
                writeList((List) value);
            // v4.5.1 — handle JSONArray / JSONObject so that placeholders that resolve to
            // structured data (@events → JSONArray of events) are written as real JSON arrays /
            // objects, not as escaped strings. Without this, a fix coming out of the sync queue
            // serialised "events" as "[{\"type\":\"hardBrake\"}]" literal.
            } else if (value instanceof org.json.JSONArray) {
                writeJsonArray((org.json.JSONArray) value);
            } else if (value instanceof org.json.JSONObject) {
                writeJsonObject((org.json.JSONObject) value);
            } else if (Integer.class.isInstance(value)) {
                writer.value((Integer) value);
            } else if (Double.class.isInstance(value)) {
                writer.value((Double) value);
            } else if (Float.class.isInstance(value)) {
                writer.value((Float) value);
            } else if (Long.class.isInstance(value)) {
                writer.value((Long) value);
            } else if (Boolean.class.isInstance(value)) {
                writer.value((Boolean) value);
            } else {
                writer.value(String.valueOf(value));
            }
        }

        private void writeJsonArray(org.json.JSONArray arr) throws IOException {
            writer.beginArray();
            for (int i = 0; i < arr.length(); i++) {
                Object v = arr.opt(i);
                writeValue(v == null ? JSONObject.NULL : v);
            }
            writer.endArray();
        }

        private void writeJsonObject(org.json.JSONObject obj) throws IOException {
            writer.beginObject();
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                writer.name(k);
                writeValue(obj.opt(k));
            }
            writer.endObject();
        }

        /** v4.5.1 — resolve a template string. If it is a placeholder ("@foo") and the location
         *  has no value, return JSONObject.NULL so the writer emits `null` instead of the literal
         *  "@foo" string. */
        private Object resolveTemplateValue(Object value) {
            if (value instanceof String) {
                String s = (String) value;
                Object resolved = location.getValueForKey(s);
                if (resolved != null) return resolved;
                if (s.startsWith("@")) return JSONObject.NULL;
            }
            return value;
        }

        public void writeMap(Map values) throws IOException {
            writer.beginObject();
            Iterator<?> it = values.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Object> pair = (Map.Entry) it.next();
                String key = pair.getKey();
                Object value = pair.getValue();
                writer.name(key);
                writeValue(resolveTemplateValue(value));
            }
            writer.endObject();
        }

        public void writeList(List values) throws IOException {
            writer.beginArray();
            Iterator<?> it = values.iterator();
            while (it.hasNext()) {
                Object value = it.next();
                writeValue(resolveTemplateValue(value));
            }
            writer.endArray();
        }
    }

    private static class LocationWriter {
        private JsonWriter writer = null;
        private LocationTemplate template;

        public LocationWriter(FileOutputStream fos, LocationTemplate template) throws IOException {
            writer = new JsonWriter(new OutputStreamWriter(fos, "UTF-8"));
            this.template = template;
        }

        public void beginArray() throws IOException {
            writer.beginArray();
        }

        public void endArray() throws IOException {
            writer.endArray();
        }

        public void close() throws IOException {
            writer.close();
        }

        public void write(BackgroundLocation location) throws IOException {
            LocationTemplateWriter writer = new LocationTemplateWriter(this.writer, location);
            if (template instanceof HashMapLocationTemplate) {
                HashMapLocationTemplate hashTemplate = (HashMapLocationTemplate) template;
                writer.writeMap(hashTemplate.toMap());
            } else if (template instanceof ArrayListLocationTemplate) {
                ArrayListLocationTemplate listTemplate = (ArrayListLocationTemplate) template;
                writer.writeList(listTemplate.toList());
            }
        }
    }
}
