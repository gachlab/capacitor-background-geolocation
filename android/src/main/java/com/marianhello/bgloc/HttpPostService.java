package com.marianhello.bgloc;

import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;

import org.json.JSONTokener;

import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;

public class HttpPostService {
    public static final int BUFFER_SIZE = 1024;
    /** Timeout to establish connection (ms). Prevents sync notification from staying stuck. */
    private static final int CONNECT_TIMEOUT_MS = 30_000;
    /** Timeout to read response (ms). Prevents sync notification from staying stuck. */
    private static final int READ_TIMEOUT_MS = 120_000;

    private String mUrl;
    private String mMethod = "POST";
    private HttpURLConnection mHttpURLConnection;

    public interface UploadingProgressListener {
        void onProgress(int progress);
    }

    public HttpPostService(String url) {
        mUrl = url;
    }

    public HttpPostService(String url, String method) {
        mUrl = url;
        mMethod = normalizeMethod(method);
    }

    public HttpPostService(final HttpURLConnection httpURLConnection) {
        mHttpURLConnection = httpURLConnection;
    }

    public void setMethod(String method) {
        mMethod = normalizeMethod(method);
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isEmpty()) return "POST";
        String m = method.trim().toUpperCase();
        if (m.equals("POST") || m.equals("GET") || m.equals("PUT") || m.equals("PATCH")) return m;
        return "POST";
    }

    /** Returns true when the HTTP method has no request body (GET). */
    private boolean isBodyless() {
        return "GET".equals(mMethod);
    }

    private HttpURLConnection openConnection() throws IOException {
        if (mHttpURLConnection == null) {
            mHttpURLConnection = (HttpURLConnection) new URL(mUrl).openConnection();
            mHttpURLConnection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            mHttpURLConnection.setReadTimeout(READ_TIMEOUT_MS);
        }
        return mHttpURLConnection;
    }

    public int postJSON(JSONObject json, Map headers) throws IOException {
        String jsonString = "null";
        if (json != null) {
            jsonString = json.toString();
        }
        return postJSONString(jsonString, headers);
    }

    public int postJSON(JSONArray json, Map headers) throws IOException {
        String jsonString = "null";
        if (json != null) {
            if (json.length() == 1) {
                JSONObject single = json.optJSONObject(0);
                if (single != null) {
                    jsonString = single.toString();
                } else {
                    jsonString = json.toString();
                }
            } else {
                jsonString = json.toString();
            }
        }
        return postJSONString(jsonString, headers);
    }

    public int postJSONString(String body, Map headers) throws IOException {
        if (headers == null) {
            headers = new HashMap();
        }

        String contentType = null;
        for (Object keyObj : headers.keySet()) {
            String key = (String) keyObj;
            if (key.equalsIgnoreCase("Content-Type")) {
                contentType = (String) headers.get(key);
                break;
            }
        }
        if (contentType == null) {
            contentType = "application/json";
        }

        HttpURLConnection conn = this.openConnection();
        conn.setRequestMethod(mMethod);

        // Set headers (including Content-Type) up-front; needed for both bodyless and body requests.
        if (!isBodyless()) {
            conn.setRequestProperty("Content-Type", contentType);
        }
        Iterator<Map.Entry<String, String>> it = headers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> pair = it.next();
            if (!pair.getKey().equalsIgnoreCase("Content-Type")) {
                conn.setRequestProperty(pair.getKey(), pair.getValue());
            }
        }

        // GET: no body; data is expected to live in the URL (URL templating).
        if (isBodyless()) {
            conn.setDoOutput(false);
            return conn.getResponseCode();
        }

        // Prepare body according to Content-Type so header and body always match.
        String finalBody = body;
        if (contentType.equalsIgnoreCase("application/x-www-form-urlencoded")) {
            try {
                finalBody = jsonToUrlEncoded(body);
            } catch (Exception e) {
                finalBody = body;
            }
        }

        // Use byte length, not String.length(), so multi-byte UTF-8 characters
        // (ñ, é, emoji, ...) match the Content-Length the server expects.
        byte[] outputBytes = finalBody.getBytes(StandardCharsets.UTF_8);
        conn.setDoOutput(true);
        conn.setFixedLengthStreamingMode(outputBytes.length);

        java.io.OutputStream os = null;
        try {
            os = conn.getOutputStream();
            os.write(outputBytes);
        } finally {
            if (os != null) {
                os.flush();
                os.close();
            }
        }
        return conn.getResponseCode();
    }
    
    private static String getContentTypeFromHeaders(Map headers) {
        if (headers == null) return null;
        for (Object keyObj : headers.keySet()) {
            String key = (String) keyObj;
            if (key != null && key.equalsIgnoreCase("Content-Type")) {
                return (String) headers.get(key);
            }
        }
        return null;
    }

    /**
     * Converts JSON string (object or array) to application/x-www-form-urlencoded.
     * Object: flat key=value&key2=value2. Array: single key "locations" with URL-encoded JSON array.
     */
    private String jsonToUrlEncoded(String jsonString) throws Exception {
        Object json = new JSONTokener(jsonString).nextValue();
        if (json instanceof JSONArray) {
            return "locations=" + URLEncoder.encode(jsonString, StandardCharsets.UTF_8.name());
        }
        JSONObject jsonObj = (JSONObject) json;
        StringBuilder result = new StringBuilder();
        Iterator<String> keys = jsonObj.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            String value = jsonObj.get(key).toString();
            if (result.length() > 0) {
                result.append("&");
            }
            result.append(URLEncoder.encode(key, StandardCharsets.UTF_8.name()));
            result.append("=");
            result.append(URLEncoder.encode(value, StandardCharsets.UTF_8.name()));
        }
        return result.toString();
    }

    public int postJSONFile(File file, Map headers, UploadingProgressListener listener) throws IOException {
        long fileSize = file.length();
        return postJSONFile(new FileInputStream(file), fileSize, headers, listener);
    }

    public int postJSONFile(InputStream stream, Map headers, UploadingProgressListener listener) throws IOException {
        return postJSONFile(stream, stream.available(), headers, listener);
    }

    public int postJSONFile(InputStream stream, long streamSize, Map headers, UploadingProgressListener listener) throws IOException {
        if (headers == null) {
            headers = new HashMap();
        }
        String contentType = getContentTypeFromHeaders(headers);
        if (contentType == null) {
            contentType = "application/json";
        }
        final boolean isFormUrlEncoded = contentType.equalsIgnoreCase("application/x-www-form-urlencoded");
        // Prepare body according to Content-Type (same as post to url): form body when form-urlencoded, else JSON
        // Read full body so we can convert when needed
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        int bytesRead;
        long progress = 0;
        while ((bytesRead = stream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
            progress += bytesRead;
            if (listener != null && streamSize > 0) {
                int percentage = (int) ((progress * 100L) / streamSize);
                listener.onProgress(percentage);
            }
        }
        stream.close();
        byte[] bodyBytes = baos.toByteArray();
        String jsonString = new String(bodyBytes, StandardCharsets.UTF_8);

        // When form-urlencoded and body is a JSON array, send one POST per location (same flat
        // format as real-time posting) so the same server endpoint accepts both.
        if (isFormUrlEncoded) {
            try {
                Object parsed = new JSONTokener(jsonString).nextValue();
                if (parsed instanceof JSONArray) {
                    JSONArray arr = (JSONArray) parsed;
                    int len = arr.length();
                    if (len == 0) {
                        if (listener != null) listener.onProgress(100);
                        return 200;
                    }
                    for (int i = 0; i < len; i++) {
                        JSONObject item = arr.getJSONObject(i);
                        HttpPostService perRequest = new HttpPostService(mUrl);
                        int code = perRequest.postJSON(item, headers);
                        if (listener != null && len > 0) {
                            listener.onProgress((i + 1) * 100 / len);
                        }
                        if (code < 200 || code >= 300) {
                            return code;
                        }
                    }
                    if (listener != null) {
                        listener.onProgress(100);
                    }
                    return 200;
                }
            } catch (Exception e) {
                // Fall through to single-POST with jsonToUrlEncoded (e.g. array wrap)
            }
        }

        byte[] outputBytes;
        if (isFormUrlEncoded) {
            try {
                String formBody = jsonToUrlEncoded(jsonString);
                outputBytes = formBody.getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                outputBytes = bodyBytes;
            }
        } else {
            outputBytes = bodyBytes;
        }

        HttpURLConnection conn = this.openConnection();
        conn.setRequestMethod(mMethod);
        if (isBodyless()) {
            conn.setDoOutput(false);
            // No headers loop / body for GET; we just consume the response.
            Iterator<Map.Entry<String, String>> hit = headers.entrySet().iterator();
            while (hit.hasNext()) {
                Map.Entry<String, String> pair = hit.next();
                if (!pair.getKey().equalsIgnoreCase("Content-Type")) {
                    conn.setRequestProperty(pair.getKey(), pair.getValue());
                }
            }
            if (listener != null) listener.onProgress(100);
            return conn.getResponseCode();
        }
        conn.setDoInput(false);
        conn.setDoOutput(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            conn.setFixedLengthStreamingMode(outputBytes.length);
        } else {
            conn.setChunkedStreamingMode(0);
        }
        conn.setRequestProperty("Content-Type", contentType);
        Iterator<Map.Entry<String, String>> it = headers.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> pair = it.next();
            if (!pair.getKey().equalsIgnoreCase("Content-Type")) {
                conn.setRequestProperty(pair.getKey(), pair.getValue());
            }
        }

        BufferedOutputStream os = null;
        try {
            os = new BufferedOutputStream(conn.getOutputStream());
            os.write(outputBytes);
            if (listener != null) {
                listener.onProgress(100);
            }
        } finally {
            if (os != null) {
                os.flush();
                os.close();
            }
        }

        return conn.getResponseCode();
    }

    public static int postJSON(String url, JSONObject json, Map headers) throws IOException {
        return postJSON(url, json, headers, "POST");
    }

    public static int postJSON(String url, JSONArray json, Map headers) throws IOException {
        return postJSON(url, json, headers, "POST");
    }

    public static int postJSONFile(String url, File file, Map headers, UploadingProgressListener listener) throws IOException {
        return postJSONFile(url, file, headers, listener, "POST");
    }

    public static int postJSON(String url, JSONObject json, Map headers, String method) throws IOException {
        HttpPostService service = new HttpPostService(url, method);
        return service.postJSON(json, headers);
    }

    public static int postJSON(String url, JSONArray json, Map headers, String method) throws IOException {
        HttpPostService service = new HttpPostService(url, method);
        return service.postJSON(json, headers);
    }

    public static int postJSONFile(String url, File file, Map headers, UploadingProgressListener listener, String method) throws IOException {
        HttpPostService service = new HttpPostService(url, method);
        return service.postJSONFile(file, headers, listener);
    }
}
