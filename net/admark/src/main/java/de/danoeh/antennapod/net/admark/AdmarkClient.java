package de.danoeh.antennapod.net.admark;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.storage.preferences.AdmarkPreferences;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class AdmarkClient {
    private static final String TAG = "AdmarkClient";
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public AdmarkEpisodeMarks fetchMarks(@NonNull FeedItem item) throws IOException, JSONException {
        HttpUrl url = buildLookupUrl(item);
        if (url == null) {
            return AdmarkEpisodeMarks.missing();
        }
        Request.Builder builder = new Request.Builder().url(url).get();
        addAuth(builder);
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(builder.build()).execute()) {
            if (response.code() == 404) {
                return AdmarkEpisodeMarks.missing();
            }
            if (!response.isSuccessful()) {
                Log.w(TAG, "fetchMarks HTTP " + response.code());
                return AdmarkEpisodeMarks.failed();
            }
            String body = response.body() == null ? "" : response.body().string();
            return AdmarkResponseParser.parse(body);
        }
    }

    public AdmarkEpisodeMarks enqueueAnalyze(@NonNull FeedItem item) throws IOException, JSONException {
        String base = AdmarkPreferences.getBaseUrl();
        if (TextUtils.isEmpty(base)) {
            return AdmarkEpisodeMarks.missing();
        }
        HttpUrl url = HttpUrl.parse(base + AdmarkApiPaths.ANALYZE_PATH);
        if (url == null) {
            return AdmarkEpisodeMarks.failed();
        }
        JSONObject payload = new JSONObject();
        FeedMedia media = item.getMedia();
        if (media != null && !TextUtils.isEmpty(media.getDownloadUrl())) {
            payload.put(AdmarkApiPaths.BODY_ENCLOSURE_URL, media.getDownloadUrl());
        }
        if (!TextUtils.isEmpty(item.getItemIdentifier())) {
            payload.put(AdmarkApiPaths.BODY_EPISODE_GUID, item.getItemIdentifier());
        }
        String feedUrl = resolveOriginalFeedUrl(item.getFeed());
        if (!TextUtils.isEmpty(feedUrl)) {
            payload.put(AdmarkApiPaths.BODY_FEED_URL, feedUrl);
        }
        Feed feed = item.getFeed();
        if (feed != null && !TextUtils.isEmpty(feed.getTitle())) {
            payload.put(AdmarkApiPaths.BODY_PODCAST_TITLE, feed.getTitle());
        }
        if (!TextUtils.isEmpty(item.getTitle())) {
            payload.put(AdmarkApiPaths.BODY_EPISODE_TITLE, item.getTitle());
        }
        RequestBody requestBody = RequestBody.create(payload.toString(), JSON);
        Request.Builder builder = new Request.Builder().url(url).post(requestBody);
        addAuth(builder);
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(builder.build()).execute()) {
            if (response.code() == 404) {
                Log.w(TAG, "enqueueAnalyze path not found");
                return AdmarkEpisodeMarks.failed();
            }
            if (!response.isSuccessful() && response.code() != 202) {
                Log.w(TAG, "enqueueAnalyze HTTP " + response.code());
                return AdmarkEpisodeMarks.failed();
            }
            String body = response.body() == null ? "" : response.body().string();
            if (TextUtils.isEmpty(body)) {
                return new AdmarkEpisodeMarks(AdmarkEpisodeMarks.Status.PENDING, null);
            }
            return AdmarkResponseParser.parse(body);
        }
    }

    public AdmarkEpisodeMarks fetchJob(@NonNull String jobId) throws IOException, JSONException {
        String base = AdmarkPreferences.getBaseUrl();
        if (TextUtils.isEmpty(base) || TextUtils.isEmpty(jobId)) {
            return AdmarkEpisodeMarks.missing();
        }
        HttpUrl url = HttpUrl.parse(base + AdmarkApiPaths.JOBS_PATH + jobId);
        if (url == null) {
            return AdmarkEpisodeMarks.failed();
        }
        Request.Builder builder = new Request.Builder().url(url).get();
        addAuth(builder);
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(builder.build()).execute()) {
            if (response.code() == 404) {
                return AdmarkEpisodeMarks.missing();
            }
            if (!response.isSuccessful()) {
                Log.w(TAG, "fetchJob HTTP " + response.code());
                return AdmarkEpisodeMarks.failed();
            }
            String body = response.body() == null ? "" : response.body().string();
            return AdmarkResponseParser.parse(body);
        }
    }

    @NonNull
    public AdmarkSubscriptionResult registerSubscription(@NonNull String feedUrl, @Nullable String podcastTitle)
            throws IOException, JSONException {
        String base = AdmarkPreferences.getBaseUrl();
        if (TextUtils.isEmpty(base) || TextUtils.isEmpty(feedUrl)) {
            return new AdmarkSubscriptionResult(null, null, feedUrl);
        }
        HttpUrl url = HttpUrl.parse(base + AdmarkApiPaths.SUBSCRIPTIONS_PATH);
        if (url == null) {
            throw new IOException("Invalid admark subscriptions URL");
        }
        JSONObject payload = new JSONObject();
        payload.put(AdmarkApiPaths.BODY_FEED_URL, feedUrl);
        if (!TextUtils.isEmpty(podcastTitle)) {
            payload.put(AdmarkApiPaths.BODY_PODCAST_TITLE, podcastTitle);
        }
        RequestBody requestBody = RequestBody.create(payload.toString(), JSON);
        Request.Builder builder = new Request.Builder().url(url).post(requestBody);
        addAuth(builder);
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("registerSubscription HTTP " + response.code());
            }
            String body = response.body() == null ? "" : response.body().string();
            AdmarkSubscriptionResult result = AdmarkResponseParser.parseSubscription(body);
            if (TextUtils.isEmpty(result.getFeedUrl())) {
                return new AdmarkSubscriptionResult(result.getId(), result.getProxiedFeedUrl(),
                        feedUrl, result.getAccessToken());
            }
            return result;
        }
    }

    public void unregisterSubscription(@Nullable String feedUrl, @Nullable String podcastId)
            throws IOException {
        String base = AdmarkPreferences.getBaseUrl();
        if (TextUtils.isEmpty(base)) {
            return;
        }
        HttpUrl parsed = HttpUrl.parse(base + AdmarkApiPaths.SUBSCRIPTIONS_PATH);
        if (parsed == null) {
            return;
        }
        HttpUrl.Builder urlBuilder = parsed.newBuilder();
        if (!TextUtils.isEmpty(podcastId)) {
            urlBuilder.addQueryParameter(AdmarkApiPaths.QUERY_PODCAST_ID, podcastId);
        } else if (!TextUtils.isEmpty(feedUrl)) {
            urlBuilder.addQueryParameter(AdmarkApiPaths.QUERY_FEED_URL, feedUrl);
        } else {
            return;
        }
        Request.Builder builder = new Request.Builder().url(urlBuilder.build()).delete();
        addAuth(builder);
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(builder.build()).execute()) {
            if (!response.isSuccessful() && response.code() != 404) {
                Log.w(TAG, "unregisterSubscription HTTP " + response.code());
                throw new IOException("unregisterSubscription HTTP " + response.code());
            }
        }
    }

    @Nullable
    private HttpUrl buildLookupUrl(@NonNull FeedItem item) {
        String base = AdmarkPreferences.getBaseUrl();
        if (TextUtils.isEmpty(base)) {
            return null;
        }
        HttpUrl parsed = HttpUrl.parse(base + AdmarkApiPaths.LOOKUP_PATH);
        if (parsed == null) {
            return null;
        }
        HttpUrl.Builder builder = parsed.newBuilder();
        if (!TextUtils.isEmpty(item.getItemIdentifier())) {
            builder.addQueryParameter(AdmarkApiPaths.QUERY_EPISODE_GUID, item.getItemIdentifier());
        }
        String feedUrl = resolveOriginalFeedUrl(item.getFeed());
        if (!TextUtils.isEmpty(feedUrl)) {
            builder.addQueryParameter(AdmarkApiPaths.QUERY_FEED_URL, feedUrl);
        }
        return builder.build();
    }

    @Nullable
    static String resolveOriginalFeedUrl(@Nullable Feed feed) {
        if (feed == null) {
            return null;
        }
        String original = AdmarkPreferences.getOriginalFeedUrl(feed.getId());
        if (!TextUtils.isEmpty(original)) {
            return original;
        }
        return feed.getDownloadUrl();
    }

    private void addAuth(Request.Builder builder) {
        String token = AdmarkPreferences.getToken();
        if (!TextUtils.isEmpty(token)) {
            builder.header(AdmarkApiPaths.AUTH_HEADER, token);
        }
    }
}
