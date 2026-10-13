package de.danoeh.antennapod.net.admark;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.preferences.AdmarkFeedCallbacks;
import de.danoeh.antennapod.storage.preferences.AdmarkPreferences;

import java.util.List;

public final class AdmarkIntegration {
    private AdmarkIntegration() {
    }

    public static void init(Context context) {
        AdmarkPreferences.init(context);
        AdmarkService.getInstance().init(context);
        AdmarkFeedCallbacks.setListener(new AdmarkFeedCallbacks.Listener() {
            @Override
            public void onFeedSubscribed(@Nullable Feed feed) {
                AdmarkService.getInstance().registerSubscription(feed);
            }

            @Override
            public void onFeedUnsubscribed(@Nullable Feed feed) {
                AdmarkService.getInstance().unregisterSubscription(feed);
            }
        });
    }

    public static void onAppReady() {
        AdmarkService.getInstance().scheduleMigrationIfNeeded();
    }

    public static void onNewFeedItems(@Nullable List<FeedItem> items) {
        AdmarkService.getInstance().enqueueAnalysisForNewItems(items);
    }

    public static void onMediaDownloaded(@Nullable FeedItem item) {
        AdmarkService.getInstance().enqueueAnalysisIfNeeded(item);
    }

    public static void onMediaDownloaded(@Nullable FeedItem item, @Nullable DownloadRequest request) {
        String sha = null;
        String path = null;
        if (request != null) {
            if (request.getArguments() != null) {
                sha = request.getArguments().getString(AdmarkApiPaths.REQUEST_ARG_AUDIO_SHA256);
            }
            path = request.getDestination();
        }
        AdmarkService.getInstance().onAudioDownloaded(item, sha, path);
    }

    public static void onFeedSubscribed(@Nullable Feed feed) {
        AdmarkService.getInstance().registerSubscription(feed);
    }

    public static void onFeedUnsubscribed(@Nullable Feed feed) {
        AdmarkService.getInstance().unregisterSubscription(feed);
    }

    public static void onFeedRefreshFailed(@Nullable Feed feed) {
        AdmarkService.getInstance().onFeedRefreshFailed(feed);
    }

    public static void routeAllViaAdmark() {
        AdmarkService.getInstance().routeAllViaAdmark();
    }

    @NonNull
    public static String getDisplayFeedUrl(@Nullable Feed feed) {
        if (feed == null) {
            return "";
        }
        return AdmarkPreferences.getDisplayFeedUrl(feed.getId(), feed.getDownloadUrl());
    }

    @Nullable
    public static String getOriginalFeedUrl(@Nullable Feed feed) {
        if (feed == null) {
            return null;
        }
        String original = AdmarkPreferences.getOriginalFeedUrl(feed.getId());
        if (!TextUtils.isEmpty(original)) {
            return original;
        }
        return feed.getDownloadUrl();
    }

    public static AdmarkPlaybackController createPlaybackController() {
        return new AdmarkPlaybackController();
    }
}
