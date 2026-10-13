package de.danoeh.antennapod.net.admark;

import androidx.annotation.Nullable;

public final class AdmarkSubscriptionResult {
    @Nullable
    private final String id;
    @Nullable
    private final String proxiedFeedUrl;
    @Nullable
    private final String feedUrl;
    @Nullable
    private final String accessToken;

    public AdmarkSubscriptionResult(@Nullable String id, @Nullable String proxiedFeedUrl,
                                    @Nullable String feedUrl) {
        this(id, proxiedFeedUrl, feedUrl, null);
    }

    public AdmarkSubscriptionResult(@Nullable String id, @Nullable String proxiedFeedUrl,
                                    @Nullable String feedUrl, @Nullable String accessToken) {
        this.id = id;
        this.proxiedFeedUrl = proxiedFeedUrl;
        this.feedUrl = feedUrl;
        this.accessToken = accessToken;
    }

    @Nullable
    public String getId() {
        return id;
    }

    @Nullable
    public String getPodcastId() {
        return id;
    }

    @Nullable
    public String getProxiedFeedUrl() {
        return proxiedFeedUrl;
    }

    @Nullable
    public String getFeedUrl() {
        return feedUrl;
    }

    @Nullable
    public String getOriginalFeedUrl() {
        return feedUrl;
    }

    @Nullable
    public String getAccessToken() {
        return accessToken;
    }

    public boolean hasProxiedFeedUrl() {
        return proxiedFeedUrl != null && !proxiedFeedUrl.isEmpty();
    }
}
