package de.danoeh.antennapod.net.admark;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.net.download.serviceinterface.FeedUpdateManager;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.preferences.AdmarkPreferences;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AdmarkService {
    private static final String TAG = "AdmarkService";
    private static final int MAX_POLL_ATTEMPTS = 5;
    private static final long POLL_DELAY_MS = 1500L;
    private static final AdmarkService INSTANCE = new AdmarkService();

    private final AdmarkClient client = new AdmarkClient();
    private final LruCache<String, AdmarkEpisodeMarks> cache = new LruCache<>(64);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Object proxyLock = new Object();
    private Context appContext;

    private AdmarkService() {
    }

    public static AdmarkService getInstance() {
        return INSTANCE;
    }

    public void init(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void enqueueAnalysisIfNeeded(@Nullable FeedItem item) {
        if (item == null || item.getMedia() == null || !AdmarkPreferences.isEnabled()
                || !AdmarkPreferences.isAutoEnqueueEnabled()) {
            return;
        }
        executor.execute(() -> {
            try {
                ensureMarksInternal(item, true, false);
            } catch (Exception e) {
                Log.d(TAG, "enqueueAnalysisIfNeeded failed: " + e.getMessage());
            }
        });
    }

    public void enqueueAnalysisForNewItems(@Nullable List<FeedItem> items) {
        if (items == null || items.isEmpty() || !AdmarkPreferences.isEnabled()
                || !AdmarkPreferences.isAutoEnqueueEnabled()) {
            return;
        }
        for (FeedItem item : items) {
            if (item != null && item.isNew() && item.getMedia() != null) {
                enqueueAnalysisIfNeeded(item);
            }
        }
    }

    public void registerSubscription(@Nullable Feed feed) {
        if (feed == null || feed.isLocalFeed() || !AdmarkPreferences.isEnabled()) {
            return;
        }
        final long feedId = feed.getId();
        final String title = feed.getTitle();
        final String currentUrl = feed.getDownloadUrl();
        executor.execute(() -> proxyFeed(feedId, title, currentUrl, false));
    }

    public void unregisterSubscription(@Nullable Feed feed) {
        if (feed == null || feed.isLocalFeed() || !AdmarkPreferences.isEnabled()) {
            return;
        }
        final long feedId = feed.getId();
        final String originalUrl = AdmarkPreferences.getOriginalFeedUrl(feedId);
        final String feedUrl = !TextUtils.isEmpty(originalUrl) ? originalUrl : feed.getDownloadUrl();
        final String podcastId = AdmarkPreferences.getPodcastId(feedId);
        executor.execute(() -> {
            try {
                client.unregisterSubscription(feedUrl, podcastId);
            } catch (Exception e) {
                Log.d(TAG, "unregisterSubscription failed: " + e.getMessage());
            } finally {
                AdmarkPreferences.clearProxyMapping(feedId);
            }
        });
    }

    public void scheduleMigrationIfNeeded() {
        if (!AdmarkPreferences.isEnabled() || AdmarkPreferences.isMigrationDone() || appContext == null) {
            return;
        }
        AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_PENDING, "Migrating subscriptions");
        AdmarkProxyWorker.enqueueMigrate(appContext);
    }

    public void routeAllViaAdmark() {
        if (!AdmarkPreferences.isEnabled() || appContext == null) {
            return;
        }
        AdmarkPreferences.setMigrationDone(false);
        AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_PENDING, "Routing all feeds via admark");
        AdmarkProxyWorker.enqueueMigrate(appContext);
    }

    public boolean migrateAllSubscriptions(@NonNull Context context) {
        if (!AdmarkPreferences.isEnabled()) {
            return true;
        }
        List<Feed> feeds = DBReader.getFeedList();
        boolean allOk = true;
        int routed = 0;
        for (Feed feed : feeds) {
            if (feed == null || feed.isLocalFeed() || feed.getState() != Feed.STATE_SUBSCRIBED) {
                continue;
            }
            boolean ok = proxyFeed(feed.getId(), feed.getTitle(), feed.getDownloadUrl(), true);
            if (ok) {
                routed++;
            } else {
                allOk = false;
            }
        }
        if (allOk) {
            AdmarkPreferences.setMigrationDone(true);
            AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_OK,
                    routed == 0 ? "No feeds to route" : "Routed " + routed + " feed(s)");
        } else {
            AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_ERROR,
                    "Some feeds could not be routed; retrying");
            AdmarkProxyWorker.enqueueRetry(context);
        }
        return allOk;
    }

    public boolean retryPendingProxies(@NonNull Context context) {
        if (!AdmarkPreferences.isEnabled()) {
            AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_IDLE, null);
            return true;
        }
        Set<Long> pending = AdmarkPreferences.getPendingFeedIds();
        if (pending.isEmpty()) {
            if (!AdmarkPreferences.isMigrationDone()) {
                return migrateAllSubscriptions(context);
            }
            AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_OK, "All feeds routed");
            return true;
        }
        boolean allOk = true;
        for (Long feedId : pending) {
            Feed feed = DBReader.getFeed(feedId, false, 0, 0);
            if (feed == null) {
                AdmarkPreferences.removePendingFeedId(feedId);
                continue;
            }
            boolean ok = proxyFeed(feed.getId(), feed.getTitle(), feed.getDownloadUrl(), true);
            if (!ok) {
                allOk = false;
            }
        }
        if (allOk) {
            AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_OK, "All feeds routed");
            if (!AdmarkPreferences.isMigrationDone()) {
                AdmarkPreferences.setMigrationDone(true);
            }
        } else {
            AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_ERROR,
                    "admark unreachable; will retry");
        }
        return allOk;
    }

    public void onFeedRefreshFailed(@Nullable Feed feed) {
        if (feed == null || !AdmarkPreferences.isEnabled() || !AdmarkPreferences.isProxied(feed.getId())) {
            return;
        }
        executor.execute(() -> revertToOriginal(feed.getId(), feed.getDownloadUrl(), true));
    }

    public void onAudioDownloaded(@Nullable FeedItem item, @Nullable String expectedSha256,
                                  @Nullable String localPath) {
        if (item == null) {
            return;
        }
        String key = shaKey(item);
        if (!TextUtils.isEmpty(expectedSha256)) {
            AdmarkPreferences.putExpectedAudioSha256(key, expectedSha256);
            if (!TextUtils.isEmpty(localPath)) {
                String actual = AdmarkSha256.hexOfFile(localPath);
                if (actual != null && !AdmarkSha256.matches(expectedSha256, actual)) {
                    Log.w(TAG, "audio sha256 mismatch for " + key);
                    AdmarkPreferences.clearExpectedAudioSha256(key);
                }
            }
        }
        enqueueAnalysisIfNeeded(item);
    }

    @NonNull
    public AdmarkEpisodeMarks getMarksForPlayback(@NonNull FeedItem item) {
        if (!AdmarkPreferences.isEnabled()) {
            return AdmarkEpisodeMarks.missing();
        }
        try {
            AdmarkEpisodeMarks marks = ensureMarksInternal(item, true, true);
            if (marks.hasSkippableRanges() && !audioShaMatches(item, marks)) {
                Log.d(TAG, "skip marks withheld: audio_sha256 mismatch");
                return AdmarkEpisodeMarks.missing();
            }
            return marks;
        } catch (Exception e) {
            Log.d(TAG, "getMarksForPlayback failed: " + e.getMessage());
            return AdmarkEpisodeMarks.failed();
        }
    }

    @NonNull
    public List<AdmarkSkipRange> getCachedRanges(@Nullable FeedItem item) {
        if (item == null) {
            return Collections.emptyList();
        }
        AdmarkEpisodeMarks cached = cache.get(cacheKey(item));
        if (cached != null && cached.hasSkippableRanges()) {
            return cached.getRanges();
        }
        return Collections.emptyList();
    }

    public void invalidate(@Nullable FeedItem item) {
        if (item != null) {
            cache.remove(cacheKey(item));
        }
    }

    private boolean proxyFeed(long feedId, @Nullable String title, @Nullable String currentUrl,
                              boolean refreshAfterSwap) {
        synchronized (proxyLock) {
            return proxyFeedLocked(feedId, title, currentUrl, refreshAfterSwap);
        }
    }

    private boolean proxyFeedLocked(long feedId, @Nullable String title, @Nullable String currentUrl,
                                    boolean refreshAfterSwap) {
        if (feedId <= 0 || TextUtils.isEmpty(currentUrl) || currentUrl.startsWith(Feed.PREFIX_LOCAL_FOLDER)) {
            return true;
        }
        String original = AdmarkPreferences.getOriginalFeedUrl(feedId);
        if (TextUtils.isEmpty(original)) {
            original = currentUrl;
        }
        String existingProxy = AdmarkPreferences.getProxiedFeedUrl(feedId);
        if (!TextUtils.isEmpty(existingProxy) && existingProxy.equals(currentUrl)) {
            AdmarkPreferences.removePendingFeedId(feedId);
            return true;
        }
        try {
            AdmarkSubscriptionResult result = client.registerSubscription(original, title);
            if (!result.hasProxiedFeedUrl()) {
                AdmarkPreferences.addPendingFeedId(feedId);
                AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_ERROR,
                        "admark returned no proxied feed URL");
                if (appContext != null) {
                    AdmarkProxyWorker.enqueueRetry(appContext);
                }
                return false;
            }
            String proxied = result.getProxiedFeedUrl();
            if (!TextUtils.isEmpty(result.getOriginalFeedUrl())) {
                original = result.getOriginalFeedUrl();
            }
            AdmarkPreferences.putOriginalFeedUrl(feedId, original);
            AdmarkPreferences.putProxiedFeedUrl(feedId, proxied);
            if (!TextUtils.isEmpty(result.getPodcastId())) {
                AdmarkPreferences.putPodcastId(feedId, result.getPodcastId());
            }
            applyProxiedDownloadUrl(feedId, currentUrl, proxied, refreshAfterSwap);
            AdmarkPreferences.removePendingFeedId(feedId);
            if (!AdmarkPreferences.hasPendingProxies()) {
                AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_OK, "Feeds routed via admark");
            }
            return true;
        } catch (Exception e) {
            Log.w(TAG, "proxyFeed failed: " + e.getMessage());
            AdmarkPreferences.addPendingFeedId(feedId);
            AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_ERROR,
                    "admark unreachable; will retry");
            if (appContext != null) {
                AdmarkProxyWorker.enqueueRetry(appContext);
            }
            if (AdmarkPreferences.isProxied(feedId) && !original.equals(currentUrl)) {
                revertToOriginal(feedId, currentUrl, false);
            }
            return false;
        }
    }

    private void applyProxiedDownloadUrl(long feedId, @Nullable String currentUrl,
                                         @NonNull String proxied, boolean refreshAfterSwap) throws Exception {
        if (TextUtils.isEmpty(currentUrl) || proxied.equals(currentUrl)) {
            return;
        }
        DBWriter.updateFeedDownloadURL(currentUrl, proxied).get();
        if (refreshAfterSwap && appContext != null && FeedUpdateManager.getInstance() != null) {
            Feed refreshed = DBReader.getFeed(feedId, false, 0, 0);
            if (refreshed != null) {
                refreshed.setDownloadUrl(proxied);
                FeedUpdateManager.getInstance().runOnce(appContext, refreshed);
            }
        }
    }

    private void revertToOriginal(long feedId, @Nullable String currentUrl, boolean scheduleRetry) {
        String original = AdmarkPreferences.getOriginalFeedUrl(feedId);
        if (TextUtils.isEmpty(original)) {
            return;
        }
        try {
            if (!TextUtils.isEmpty(currentUrl) && !original.equals(currentUrl)) {
                DBWriter.updateFeedDownloadURL(currentUrl, original).get();
            }
            AdmarkPreferences.clearProxiedFeedUrl(feedId);
            AdmarkPreferences.addPendingFeedId(feedId);
            AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_PENDING,
                    "Reverted to original URL; retrying admark");
            if (scheduleRetry && appContext != null) {
                AdmarkProxyWorker.enqueueRetry(appContext);
            }
        } catch (Exception e) {
            Log.w(TAG, "revertToOriginal failed: " + e.getMessage());
        }
    }

    private AdmarkEpisodeMarks ensureMarksInternal(FeedItem item, boolean enqueueIfMissing, boolean poll)
            throws Exception {
        String key = cacheKey(item);
        AdmarkEpisodeMarks cached = cache.get(key);
        if (cached != null && cached.hasSkippableRanges()) {
            return cached;
        }

        AdmarkEpisodeMarks marks = client.fetchMarks(item);
        if (marks.getStatus() == AdmarkEpisodeMarks.Status.MISSING
                || marks.getStatus() == AdmarkEpisodeMarks.Status.UNKNOWN) {
            if (enqueueIfMissing && AdmarkPreferences.isAutoEnqueueEnabled()) {
                marks = client.enqueueAnalyze(item);
                if (marks.getStatus() == AdmarkEpisodeMarks.Status.READY
                        && !marks.hasSkippableRanges()) {
                    AdmarkEpisodeMarks refreshed = client.fetchMarks(item);
                    if (refreshed.getStatus() == AdmarkEpisodeMarks.Status.READY
                            && refreshed.hasSkippableRanges()) {
                        marks = refreshed;
                    }
                }
            }
        }

        if (poll && marks.getStatus() == AdmarkEpisodeMarks.Status.PENDING) {
            String jobId = marks.getJobId();
            for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
                Thread.sleep(POLL_DELAY_MS);
                if (!TextUtils.isEmpty(jobId)) {
                    marks = client.fetchJob(jobId);
                } else {
                    marks = client.fetchMarks(item);
                }
                if (!TextUtils.isEmpty(marks.getJobId())) {
                    jobId = marks.getJobId();
                }
                if (marks.getStatus() != AdmarkEpisodeMarks.Status.PENDING) {
                    break;
                }
            }
        }

        cache.put(key, marks);
        return marks;
    }

    private boolean audioShaMatches(@NonNull FeedItem item, @NonNull AdmarkEpisodeMarks marks) {
        String expected = marks.getAudioSha256();
        if (TextUtils.isEmpty(expected)) {
            expected = AdmarkPreferences.getExpectedAudioSha256(shaKey(item));
        }
        if (TextUtils.isEmpty(expected)) {
            return true;
        }
        FeedMedia media = item.getMedia();
        if (media != null && media.isDownloaded() && !TextUtils.isEmpty(media.getLocalFileUrl())) {
            String actual = AdmarkSha256.hexOfFile(media.getLocalFileUrl());
            if (actual != null) {
                return AdmarkSha256.matches(expected, actual);
            }
        }
        String fromHeader = AdmarkPreferences.getExpectedAudioSha256(shaKey(item));
        if (!TextUtils.isEmpty(fromHeader)) {
            return AdmarkSha256.matches(expected, fromHeader);
        }
        return true;
    }

    private static String shaKey(@NonNull FeedItem item) {
        if (!TextUtils.isEmpty(item.getItemIdentifier())) {
            return item.getItemIdentifier();
        }
        return "item-" + item.getId();
    }

    private static String cacheKey(FeedItem item) {
        if (!TextUtils.isEmpty(item.getItemIdentifier())) {
            return item.getItemIdentifier();
        }
        if (item.getMedia() != null && item.getMedia().getDownloadUrl() != null) {
            return item.getMedia().getDownloadUrl();
        }
        return "item-" + item.getId();
    }
}
