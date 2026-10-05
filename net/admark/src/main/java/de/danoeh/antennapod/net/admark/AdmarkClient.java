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
        HttpUrl url = buildMarksUrl(item);
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
            payload.put(AdmarkApiPaths.BODY_MEDIA_URL, media.getDownloadUrl());
        }
        if (!TextUtils.isEmpty(item.getItemIdentifier())) {
            payload.put(AdmarkApiPaths.BODY_GUID, item.getItemIdentifier());
        }
        Feed feed = item.getFeed();
        if (feed != null && !TextUtils.isEmpty(feed.getDownloadUrl())) {
            payload.put(AdmarkApiPaths.BODY_FEED_URL, feed.getDownloadUrl());
        }
        if (!TextUtils.isEmpty(item.getTitle())) {
            payload.put(AdmarkApiPaths.BODY_TITLE, item.getTitle());
        }
        RequestBody requestBody = RequestBody.create(payload.toString(), JSON);
        Request.Builder builder = new Request.Builder().url(url).post(requestBody);
        addAuth(builder);
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(builder.build()).execute()) {
            if (response.code() == 404) {
                Log.w(TAG, "enqueueAnalyze path not found (provisional API mismatch?)");
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

    @Nullable
    private HttpUrl buildMarksUrl(@NonNull FeedItem item) {
        String base = AdmarkPreferences.getBaseUrl();
        if (TextUtils.isEmpty(base)) {
            return null;
        }
        HttpUrl parsed = HttpUrl.parse(base + AdmarkApiPaths.MARKS_PATH);
        if (parsed == null) {
            return null;
        }
        HttpUrl.Builder builder = parsed.newBuilder();
        FeedMedia media = item.getMedia();
        if (media != null && !TextUtils.isEmpty(media.getDownloadUrl())) {
            builder.addQueryParameter(AdmarkApiPaths.QUERY_MEDIA_URL, media.getDownloadUrl());
        }
        if (!TextUtils.isEmpty(item.getItemIdentifier())) {
            builder.addQueryParameter(AdmarkApiPaths.QUERY_GUID, item.getItemIdentifier());
        }
        Feed feed = item.getFeed();
        if (feed != null && !TextUtils.isEmpty(feed.getDownloadUrl())) {
            builder.addQueryParameter(AdmarkApiPaths.QUERY_FEED_URL, feed.getDownloadUrl());
        }
        return builder.build();
    }

    private void addAuth(Request.Builder builder) {
        String token = AdmarkPreferences.getToken();
        if (!TextUtils.isEmpty(token)) {
            builder.header("Authorization", "Bearer " + token);
        }
    }
}
