package de.danoeh.antennapod.net.admark;

import androidx.annotation.Nullable;

public final class AdmarkSubscriptionResult {
    @Nullable
    private final String podcastId;
    @Nullable
    private final String proxiedFeedUrl;
    @Nullable
    private final String originalFeedUrl;

    public AdmarkSubscriptionResult(@Nullable String podcastId, @Nullable String proxiedFeedUrl,
                                    @Nullable String originalFeedUrl) {
        this.podcastId = podcastId;
        this.proxiedFeedUrl = proxiedFeedUrl;
        this.originalFeedUrl = originalFeedUrl;
    }

    @Nullable
    public String getPodcastId() {
        return podcastId;
    }

    @Nullable
    public String getProxiedFeedUrl() {
        return proxiedFeedUrl;
    }

    @Nullable
    public String getOriginalFeedUrl() {
        return originalFeedUrl;
    }

    public boolean hasProxiedFeedUrl() {
        return proxiedFeedUrl != null && !proxiedFeedUrl.isEmpty();
    }
}
