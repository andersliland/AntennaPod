package de.danoeh.antennapod.net.admark;

/**
 * Decides when a proxied feed may fall back to its original URL. Feed
 * refreshes fail for many transient reasons (airplane mode, captive portal,
 * server blip); reverting on the first failure makes the feed URL flap and
 * spam re-subscriptions. Require several consecutive failures instead.
 */
public final class AdmarkRevertPolicy {

    public static final int CONSECUTIVE_FAILURES_TO_REVERT = 3;

    private AdmarkRevertPolicy() {
    }

    public static boolean shouldRevert(int consecutiveFailures) {
        return consecutiveFailures >= CONSECUTIVE_FAILURES_TO_REVERT;
    }
}
