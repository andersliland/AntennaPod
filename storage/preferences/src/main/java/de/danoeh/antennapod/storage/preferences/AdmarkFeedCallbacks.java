package de.danoeh.antennapod.storage.preferences;

import androidx.annotation.Nullable;

import de.danoeh.antennapod.model.feed.Feed;

/**
 * Thin callback bridge so storage/database can notify admark without a module cycle.
 * Implemented by {@code AdmarkIntegration} in {@code :net:admark}.
 */
public final class AdmarkFeedCallbacks {
    public interface Listener {
        void onFeedSubscribed(@Nullable Feed feed);

        void onFeedUnsubscribed(@Nullable Feed feed);
    }

    private static Listener listener;

    private AdmarkFeedCallbacks() {
    }

    public static void setListener(@Nullable Listener newListener) {
        listener = newListener;
    }

    public static void notifySubscribed(@Nullable Feed feed) {
        Listener current = listener;
        if (current != null) {
            current.onFeedSubscribed(feed);
        }
    }

    public static void notifyUnsubscribed(@Nullable Feed feed) {
        Listener current = listener;
        if (current != null) {
            current.onFeedUnsubscribed(feed);
        }
    }
}
