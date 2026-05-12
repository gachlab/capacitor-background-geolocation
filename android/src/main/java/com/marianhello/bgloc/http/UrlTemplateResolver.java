package com.marianhello.bgloc.http;

import com.marianhello.bgloc.data.BackgroundLocation;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves placeholders like {lat}, {lon}, {timestamp_iso}, {device_id}, ...
 * in a URL template using a single BackgroundLocation and an optional queryParams map.
 *
 * Placeholders not found in the location/queryParams are left as-is so that
 * partial templates (e.g. only static keys for batch mode) keep working.
 *
 * Usage:
 *   String url = UrlTemplateResolver.resolve(template, location, queryParams);
 *   // location may be null for batch mode (only queryParams keys are resolved).
 */
public final class UrlTemplateResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_]+)\\}");

    private UrlTemplateResolver() { /* no instances */ }

    public static String resolve(String template, BackgroundLocation location, Map<String, ?> queryParams) {
        if (template == null || template.isEmpty()) return template;
        Map<String, String> ctx = buildContext(location, queryParams);
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer(template.length());
        while (m.find()) {
            String key = m.group(1);
            String value = ctx.get(key);
            if (value != null) {
                m.appendReplacement(sb, Matcher.quoteReplacement(urlEncode(value)));
            } else {
                // Leave placeholder as-is if no value available.
                m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    public static boolean hasPlaceholders(String template) {
        if (template == null) return false;
        return PLACEHOLDER.matcher(template).find();
    }

    private static Map<String, String> buildContext(BackgroundLocation loc, Map<String, ?> queryParams) {
        Map<String, String> ctx = new HashMap<String, String>();

        // queryParams first so location-derived values can override if user wants
        if (queryParams != null) {
            for (Map.Entry<String, ?> e : queryParams.entrySet()) {
                if (e.getValue() != null) {
                    ctx.put(e.getKey(), String.valueOf(e.getValue()));
                }
            }
        }

        if (loc != null) {
            ctx.put("latitude", String.valueOf(loc.getLatitude()));
            ctx.put("longitude", String.valueOf(loc.getLongitude()));
            ctx.put("lat", String.valueOf(loc.getLatitude()));
            ctx.put("lon", String.valueOf(loc.getLongitude()));

            long timeMs = loc.getTime();
            ctx.put("time", String.valueOf(timeMs));
            ctx.put("timestamp", String.valueOf(timeMs));
            ctx.put("timestamp_iso", isoUtc(timeMs));

            if (loc.hasSpeed()) ctx.put("speed", String.valueOf(loc.getSpeed()));
            if (loc.hasAltitude()) ctx.put("altitude", String.valueOf(loc.getAltitude()));
            if (loc.hasBearing()) ctx.put("bearing", String.valueOf(loc.getBearing()));
            if (loc.hasAccuracy()) ctx.put("accuracy", String.valueOf(loc.getAccuracy()));

            if (loc.getProvider() != null) ctx.put("provider", loc.getProvider());
            // is_moving derived from speed when available (>0.5 m/s ~ walking pace).
            if (loc.hasSpeed()) {
                ctx.put("is_moving", loc.getSpeed() > 0.5f ? "true" : "false");
            }
            // {activity} is not produced by BackgroundLocation by default; the user can supply
            // a value via queryParams (already populated above).
        }

        return ctx;
    }

    /**
     * URL-encode a placeholder value for use in URL paths and query strings.
     * Spaces become %20, not + (which is form-encoding).
     */
    private static String urlEncode(String s) {
        if (s == null) return "";
        try {
            String enc = java.net.URLEncoder.encode(s, "UTF-8");
            // URLEncoder uses application/x-www-form-urlencoded; convert "+" back to "%20" for URL safety.
            return enc.replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }

    private static String isoUtc(long ms) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(ms));
    }
}
