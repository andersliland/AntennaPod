package de.danoeh.antennapod.storage.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public abstract class AdmarkPreferences {
    public static final String PREF_NAME = "admark";
    public static final String PREF_ENABLED = "prefAdmarkEnabled";
    public static final String PREF_BASE_URL = "prefAdmarkBaseUrl";
    public static final String PREF_TOKEN = "prefAdmarkToken";
    public static final String PREF_AUTO_SKIP = "prefAdmarkAutoSkip";
    public static final String PREF_AUTO_ENQUEUE = "prefAdmarkAutoEnqueue";
    public static final String PREF_PROXY_STATUS = "prefAdmarkProxyStatus";
    public static final String PREF_ROUTE_ALL = "prefAdmarkRouteAll";
    public static final String DEFAULT_BASE_URL = "https://admark.liland.xyz";

    public static final String STATUS_IDLE = "idle";
    public static final String STATUS_OK = "ok";
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ERROR = "error";

    private static final String KEY_ORIGINAL_URL_PREFIX = "origUrl.";
    private static final String KEY_PODCAST_ID_PREFIX = "podcastId.";
    private static final String KEY_PROXIED_URL_PREFIX = "proxiedUrl.";
    private static final String KEY_MEDIA_SHA_PREFIX = "mediaSha.";
    private static final String KEY_PENDING_FEED_IDS = "pendingProxyFeedIds";
    private static final String KEY_PROXY_STATUS = "proxyStatus";
    private static final String KEY_PROXY_STATUS_DETAIL = "proxyStatusDetail";
    private static final String KEY_MIGRATION_DONE = "proxyMigrationDone";

    private static SharedPreferences prefs;

    private AdmarkPreferences() {
    }

    public static void init(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    private static boolean ready() {
        return prefs != null;
    }

    public static boolean isEnabled() {
        return ready() && prefs.getBoolean(PREF_ENABLED, false) && !TextUtils.isEmpty(getBaseUrl());
    }

    public static void setEnabled(boolean enabled) {
        if (!ready()) {
            return;
        }
        prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();
    }

    public static String getBaseUrl() {
        if (!ready()) {
            return DEFAULT_BASE_URL;
        }
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
        if (!ready()) {
            return;
        }
        prefs.edit().putString(PREF_BASE_URL, baseUrl == null ? "" : baseUrl.trim()).apply();
    }

    public static String getToken() {
        if (!ready()) {
            return "";
        }
        String token = prefs.getString(PREF_TOKEN, "");
        return token == null ? "" : token;
    }

    public static void setToken(String token) {
        if (!ready()) {
            return;
        }
        prefs.edit().putString(PREF_TOKEN, token == null ? "" : token.trim()).apply();
    }

    public static boolean isAutoSkipEnabled() {
        return !ready() || prefs.getBoolean(PREF_AUTO_SKIP, true);
    }

    public static void setAutoSkipEnabled(boolean enabled) {
        if (!ready()) {
            return;
        }
        prefs.edit().putBoolean(PREF_AUTO_SKIP, enabled).apply();
    }

    public static boolean isAutoEnqueueEnabled() {
        return !ready() || prefs.getBoolean(PREF_AUTO_ENQUEUE, true);
    }

    public static void setAutoEnqueueEnabled(boolean enabled) {
        if (!ready()) {
            return;
        }
        prefs.edit().putBoolean(PREF_AUTO_ENQUEUE, enabled).apply();
    }

    public static void putOriginalFeedUrl(long feedId, @Nullable String originalUrl) {
        if (!ready() || feedId <= 0 || TextUtils.isEmpty(originalUrl)) {
            return;
        }
        prefs.edit().putString(KEY_ORIGINAL_URL_PREFIX + feedId, originalUrl).apply();
    }

    @Nullable
    public static String getOriginalFeedUrl(long feedId) {
        if (!ready() || feedId <= 0) {
            return null;
        }
        String value = prefs.getString(KEY_ORIGINAL_URL_PREFIX + feedId, null);
        return TextUtils.isEmpty(value) ? null : value;
    }

    public static void putPodcastId(long feedId, @Nullable String podcastId) {
        if (!ready() || feedId <= 0 || TextUtils.isEmpty(podcastId)) {
            return;
        }
        prefs.edit().putString(KEY_PODCAST_ID_PREFIX + feedId, podcastId).apply();
    }

    @Nullable
    public static String getPodcastId(long feedId) {
        if (!ready() || feedId <= 0) {
            return null;
        }
        String value = prefs.getString(KEY_PODCAST_ID_PREFIX + feedId, null);
        return TextUtils.isEmpty(value) ? null : value;
    }

    public static void putProxiedFeedUrl(long feedId, @Nullable String proxiedUrl) {
        if (!ready() || feedId <= 0 || TextUtils.isEmpty(proxiedUrl)) {
            return;
        }
        prefs.edit().putString(KEY_PROXIED_URL_PREFIX + feedId, proxiedUrl).apply();
    }

    @Nullable
    public static String getProxiedFeedUrl(long feedId) {
        if (!ready() || feedId <= 0) {
            return null;
        }
        String value = prefs.getString(KEY_PROXIED_URL_PREFIX + feedId, null);
        return TextUtils.isEmpty(value) ? null : value;
    }

    public static boolean isProxied(long feedId) {
        return getProxiedFeedUrl(feedId) != null && getOriginalFeedUrl(feedId) != null;
    }

    public static void clearProxiedFeedUrl(long feedId) {
        if (!ready() || feedId <= 0) {
            return;
        }
        prefs.edit().remove(KEY_PROXIED_URL_PREFIX + feedId).apply();
    }

    public static void clearProxyMapping(long feedId) {
        if (!ready() || feedId <= 0) {
            return;
        }
        prefs.edit()
                .remove(KEY_ORIGINAL_URL_PREFIX + feedId)
                .remove(KEY_PODCAST_ID_PREFIX + feedId)
                .remove(KEY_PROXIED_URL_PREFIX + feedId)
                .apply();
        removePendingFeedId(feedId);
    }

    @NonNull
    public static String getDisplayFeedUrl(long feedId, @Nullable String downloadUrl) {
        String original = getOriginalFeedUrl(feedId);
        if (!TextUtils.isEmpty(original)) {
            return original;
        }
        return downloadUrl == null ? "" : downloadUrl;
    }

    public static void addPendingFeedId(long feedId) {
        if (!ready() || feedId <= 0) {
            return;
        }
        Set<String> pending = new HashSet<>(prefs.getStringSet(KEY_PENDING_FEED_IDS, Collections.emptySet()));
        pending.add(String.valueOf(feedId));
        prefs.edit().putStringSet(KEY_PENDING_FEED_IDS, pending).apply();
    }

    public static void removePendingFeedId(long feedId) {
        if (!ready() || feedId <= 0) {
            return;
        }
        Set<String> pending = new HashSet<>(prefs.getStringSet(KEY_PENDING_FEED_IDS, Collections.emptySet()));
        if (pending.remove(String.valueOf(feedId))) {
            prefs.edit().putStringSet(KEY_PENDING_FEED_IDS, pending).apply();
        }
    }

    @NonNull
    public static Set<Long> getPendingFeedIds() {
        if (!ready()) {
            return Collections.emptySet();
        }
        Set<String> pending = prefs.getStringSet(KEY_PENDING_FEED_IDS, Collections.emptySet());
        Set<Long> ids = new HashSet<>();
        for (String value : pending) {
            try {
                ids.add(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return ids;
    }

    public static boolean hasPendingProxies() {
        return !getPendingFeedIds().isEmpty();
    }

    public static void setProxyStatus(@NonNull String status, @Nullable String detail) {
        if (!ready()) {
            return;
        }
        prefs.edit()
                .putString(KEY_PROXY_STATUS, status)
                .putString(KEY_PROXY_STATUS_DETAIL, detail == null ? "" : detail)
                .apply();
    }

    @NonNull
    public static String getProxyStatus() {
        if (!ready()) {
            return STATUS_IDLE;
        }
        String status = prefs.getString(KEY_PROXY_STATUS, STATUS_IDLE);
        return status == null ? STATUS_IDLE : status;
    }

    @NonNull
    public static String getProxyStatusDetail() {
        if (!ready()) {
            return "";
        }
        String detail = prefs.getString(KEY_PROXY_STATUS_DETAIL, "");
        return detail == null ? "" : detail;
    }

    public static boolean isMigrationDone() {
        return ready() && prefs.getBoolean(KEY_MIGRATION_DONE, false);
    }

    public static void setMigrationDone(boolean done) {
        if (!ready()) {
            return;
        }
        prefs.edit().putBoolean(KEY_MIGRATION_DONE, done).apply();
    }

    public static void putExpectedAudioSha256(@NonNull String key, @Nullable String sha256) {
        if (!ready() || TextUtils.isEmpty(key) || TextUtils.isEmpty(sha256)) {
            return;
        }
        prefs.edit().putString(KEY_MEDIA_SHA_PREFIX + key, sha256.trim().toLowerCase(Locale.ROOT)).apply();
    }

    @Nullable
    public static String getExpectedAudioSha256(@Nullable String key) {
        if (!ready() || TextUtils.isEmpty(key)) {
            return null;
        }
        String value = prefs.getString(KEY_MEDIA_SHA_PREFIX + key, null);
        return TextUtils.isEmpty(value) ? null : value;
    }

    public static void clearExpectedAudioSha256(@Nullable String key) {
        if (!ready() || TextUtils.isEmpty(key)) {
            return;
        }
        prefs.edit().remove(KEY_MEDIA_SHA_PREFIX + key).apply();
    }
}
