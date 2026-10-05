package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

public abstract class AdmarkPreferences {
    public static final String PREF_NAME = "admark";
    public static final String PREF_ENABLED = "prefAdmarkEnabled";
    public static final String PREF_BASE_URL = "prefAdmarkBaseUrl";
    public static final String PREF_TOKEN = "prefAdmarkToken";
    public static final String PREF_AUTO_SKIP = "prefAdmarkAutoSkip";
    public static final String PREF_AUTO_ENQUEUE = "prefAdmarkAutoEnqueue";
    public static final String DEFAULT_BASE_URL = "https://admark.liland.xyz";

    private static SharedPreferences prefs;

    private AdmarkPreferences() {
    }

    public static void init(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isEnabled() {
        return prefs.getBoolean(PREF_ENABLED, false) && !TextUtils.isEmpty(getBaseUrl());
    }

    public static void setEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();
    }

    public static String getBaseUrl() {
        String url = prefs.getString(PREF_BASE_URL, DEFAULT_BASE_URL);
        if (TextUtils.isEmpty(url)) {
            url = DEFAULT_BASE_URL;
        }
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static void setBaseUrl(String baseUrl) {
        prefs.edit().putString(PREF_BASE_URL, baseUrl == null ? "" : baseUrl.trim()).apply();
    }

    public static String getToken() {
        String token = prefs.getString(PREF_TOKEN, "");
        return token == null ? "" : token;
    }

    public static void setToken(String token) {
        prefs.edit().putString(PREF_TOKEN, token == null ? "" : token.trim()).apply();
    }

    public static boolean isAutoSkipEnabled() {
        return prefs.getBoolean(PREF_AUTO_SKIP, true);
    }

    public static void setAutoSkipEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_SKIP, enabled).apply();
    }

    public static boolean isAutoEnqueueEnabled() {
        return prefs.getBoolean(PREF_AUTO_ENQUEUE, true);
    }

    public static void setAutoEnqueueEnabled(boolean enabled) {
        prefs.edit().putBoolean(PREF_AUTO_ENQUEUE, enabled).apply();
    }
}
