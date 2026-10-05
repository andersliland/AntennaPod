package de.danoeh.antennapod.net.admark;

import android.content.Context;

import androidx.annotation.Nullable;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.preferences.AdmarkPreferences;

import java.util.List;

public final class AdmarkIntegration {
    private AdmarkIntegration() {
    }

    public static void init(Context context) {
        AdmarkPreferences.init(context);
    }

    public static void onNewFeedItems(@Nullable List<FeedItem> items) {
        AdmarkService.getInstance().enqueueAnalysisForNewItems(items);
    }

    public static void onMediaDownloaded(@Nullable FeedItem item) {
        AdmarkService.getInstance().enqueueAnalysisIfNeeded(item);
    }

    public static void onFeedSubscribed(@Nullable Feed feed) {
        AdmarkService.getInstance().registerSubscription(feed);
    }

    public static AdmarkPlaybackController createPlaybackController() {
        return new AdmarkPlaybackController();
    }
}
