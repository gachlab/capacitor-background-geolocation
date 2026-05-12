package com.marianhello.bgloc.oem;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * v3.6 Phase 5: Battery / OEM helpers.
 *
 * Standard Android Doze whitelist:
 *   - {@link #isIgnoringBatteryOptimizations(Context)}
 *   - {@link #requestIgnoreBatteryOptimizations(Activity)}
 *   - {@link #openBatterySettings(Activity)}
 *
 * OEM-specific "auto-start" / "background activity" screens (Xiaomi MIUI, Huawei
 * EMUI, Oppo ColorOS, Vivo FunTouch, Samsung One UI). These cannot be granted
 * programmatically — the user must toggle them in Settings.
 */
public final class BatteryOemHelper {

    private BatteryOemHelper() { /* no instances */ }

    public static boolean isIgnoringBatteryOptimizations(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true;
        try {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Opens the system battery-optimisation prompt for the app.
     * Does not throw; logs and silently returns if the system dialog is missing.
     */
    public static void requestIgnoreBatteryOptimizations(Activity activity) {
        if (activity == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + activity.getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception ignored) {
            openBatterySettings(activity);
        }
    }

    public static void openBatterySettings(Activity activity) {
        if (activity == null) return;
        // Try the per-app battery usage screen first; fall back to app-info.
        try {
            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            try {
                Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                fallback.setData(Uri.parse("package:" + activity.getPackageName()));
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(fallback);
            } catch (Exception ignored) {}
        }
    }

    /**
     * Open the OEM-specific "auto-start" / "background activity" screen.
     * Returns a JSON object describing what was opened so the JS layer can show
     * appropriate copy: { opened, manufacturer, screen }.
     */
    public static JSONObject openAutoStartSettings(Activity activity) throws JSONException {
        JSONObject out = new JSONObject();
        String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        out.put("manufacturer", manufacturer);
        out.put("opened", false);
        out.put("screen", "");

        if (activity == null) return out;

        ComponentName component = autoStartComponent(manufacturer);
        if (component != null) {
            try {
                Intent intent = new Intent();
                intent.setComponent(component);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                out.put("opened", true);
                out.put("screen", component.flattenToShortString());
                return out;
            } catch (Exception ignored) { /* fall through to app-info */ }
        }

        // Fallback: standard application details page so the user can toggle background restrictions.
        try {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            fallback.setData(Uri.parse("package:" + activity.getPackageName()));
            fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(fallback);
            out.put("opened", true);
            out.put("screen", "android.settings.APPLICATION_DETAILS_SETTINGS");
        } catch (Exception ignored) {}
        return out;
    }

    /** Known OEM auto-start screen components. Verified against AOSP / OEM forks; may vary by ROM. */
    private static ComponentName autoStartComponent(String manufacturer) {
        if (manufacturer == null) return null;
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
            return new ComponentName("com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity");
        }
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            return new ComponentName("com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity");
        }
        if (manufacturer.contains("oppo")) {
            return new ComponentName("com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity");
        }
        if (manufacturer.contains("vivo")) {
            return new ComponentName("com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity");
        }
        if (manufacturer.contains("samsung")) {
            // Samsung does not expose a stable component for "Sleeping apps"; return null and
            // let the caller fall back to app-info, where the user can disable battery optimisation.
            return null;
        }
        if (manufacturer.contains("oneplus")) {
            return new ComponentName("com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity");
        }
        if (manufacturer.contains("asus")) {
            return new ComponentName("com.asus.mobilemanager",
                    "com.asus.mobilemanager.entry.FunctionActivity");
        }
        return null;
    }

    /**
     * Returns OEM-specific guidance steps. The caller renders them as a help screen.
     */
    public static JSONObject getManufacturerHelp() throws JSONException {
        JSONObject out = new JSONObject();
        String m = Build.MANUFACTURER != null ? Build.MANUFACTURER.toLowerCase() : "";
        out.put("manufacturer", m);
        out.put("steps", new JSONArray(stepsFor(m)));
        return out;
    }

    private static String[] stepsFor(String manufacturer) {
        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco")) {
            return new String[] {
                "Settings → Apps → Manage apps → [your app] → Autostart → enable.",
                "Settings → Apps → Manage apps → [your app] → Battery saver → No restrictions.",
                "Settings → Apps → Manage apps → [your app] → Other permissions → Display pop-up windows while running in the background → Allow.",
                "Lock the app in Recents (drag it down to keep it in memory)."
            };
        }
        if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            return new String[] {
                "Settings → Apps → [your app] → Battery → App launch → switch off Manage automatically and enable Auto-launch + Run in background.",
                "Settings → Battery → App launch → [your app] → manage manually."
            };
        }
        if (manufacturer.contains("oppo")) {
            return new String[] {
                "Settings → Battery → Power Consumption Protection → [your app] → Allow.",
                "Settings → Apps → App management → [your app] → Permissions → Auto-start → Allow.",
                "Settings → Privacy permissions → Startup manager → [your app] → enable."
            };
        }
        if (manufacturer.contains("vivo")) {
            return new String[] {
                "Settings → Battery → High background power consumption → [your app] → Allow.",
                "Settings → More settings → Permission management → Auto-start → [your app] → enable."
            };
        }
        if (manufacturer.contains("samsung")) {
            return new String[] {
                "Settings → Apps → [your app] → Battery → Unrestricted.",
                "Settings → Battery and device care → Battery → Background usage limits → Sleeping apps → make sure [your app] is NOT listed.",
                "Settings → Battery and device care → Battery → Background usage limits → Never sleeping apps → add [your app]."
            };
        }
        if (manufacturer.contains("oneplus")) {
            return new String[] {
                "Settings → Battery → Battery optimisation → [your app] → Don't optimise.",
                "Settings → Apps → [your app] → Battery → Background activity → Allow."
            };
        }
        if (manufacturer.contains("asus")) {
            return new String[] {
                "Settings → Apps → [your app] → Battery → Battery saver → Off.",
                "Mobile Manager → Auto-start manager → [your app] → enable."
            };
        }
        // Generic Android.
        return new String[] {
            "Settings → Apps → [your app] → Battery → Unrestricted.",
            "Settings → Apps → [your app] → Permissions → Location → Allow all the time.",
            "Disable battery optimisation for [your app] in Settings → Battery."
        };
    }
}
