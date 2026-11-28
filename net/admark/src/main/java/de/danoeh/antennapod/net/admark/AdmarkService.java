package de.danoeh.antennapod.net.admark;

import android.content.Context;
import android.os.SystemClock;
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

import okhttp3.HttpUrl;
import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AdmarkService {
    private static final String TAG = "AdmarkService";
    private static final int MAX_POLL_ATTEMPTS = 5;
    private static final long POLL_DELAY_MS = 1500L;
    private static final long STREAM_VERIFY_TTL_MS = 10 * 60 * 1000L;
    private static final int SCAN_SUMMARY_MAX_ENTRIES = 250;
    private static final AdmarkService INSTANCE = new AdmarkService();

    private final AdmarkClient client = new AdmarkClient();
    private final Map<Long, String> verifiedAudio = new ConcurrentHashMap<>();
    private final Map<Long, Long> streamVerifiedAt = new ConcurrentHashMap<>();
    private final Map<Long, List<AdmarkSkipRange>> playbackRanges = new ConcurrentHashMap<>();
    private final LruCache<String, AdmarkEpisodeMarks> cache = new LruCache<>(64);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService scanExecutor = Executors.newFixedThreadPool(2);
    private final Map<String, List<AdmarkScanSummary.Listener>> scanListeners = new HashMap<>();
    private final Map<String, Long> scanAttempts = new ConcurrentHashMap<>();
    private final Set<String> autoEnqueueInFlight = ConcurrentHashMap.newKeySet();
    private final Object proxyLock = new Object();
    private Context appContext;

    private AdmarkService() {
    }

    public static AdmarkService getInstance() {
        return INSTANCE;
    }

    public void init(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        pruneScanSummaries();
    }

    /** Caps persisted per-episode scan summaries so prefs cannot grow forever. */
    private void pruneScanSummaries() {
        try {
            java.util.List<String> keys = AdmarkPreferences.getScanSummaryKeys();
            if (keys.size() <= SCAN_SUMMARY_MAX_ENTRIES) {
                return;
            }
            StringBuilder order = new StringBuilder(AdmarkPreferences.getScanOrder());
            for (String key : keys) {
                if (order.indexOf(key) < 0) {
                    if (order.length() > 0) {
                        order.append(',');
                    }
                    order.append(key);
                }
            }
            AdmarkKeyLru.Result result = AdmarkKeyLru.push(order.toString(), "", SCAN_SUMMARY_MAX_ENTRIES);
            AdmarkPreferences.putScanOrder(result.order);
            for (String evicted : result.evicted) {
                AdmarkPreferences.removeScanSummary(evicted);
            }
            Log.i(TAG, "pruned " + result.evicted.size() + " old scan summaries");
        } catch (Exception e) {
            Log.d(TAG, "scan summary prune failed: " + e.getMessage());
        }
    }

    public void enqueueAnalysisIfNeeded(@Nullable FeedItem item) {
        if (!canTrack(item) || !AdmarkPreferences.isAutoEnqueueEnabled()) {
            return;
        }
        final String key = cacheKey(item);
        // Bulk feed refreshes enqueue many episodes; keep them off the proxy
        // executor and skip episodes that already have a scan in flight.
        if (!autoEnqueueInFlight.add(key)) {
            return;
        }
        scanExecutor.execute(() -> {
            try {
                ensureMarksInternal(item, true, false);
            } catch (Exception e) {
                Log.d(TAG, "enqueueAnalysisIfNeeded failed: " + e.getMessage());
            } finally {
                autoEnqueueInFlight.remove(key);
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
        if (feed == null || feed.isLocalFeed() || !AdmarkPreferences.isEnabled()
                || !AdmarkPreferences.isFeedIncluded(feed.getId())) {
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
            if (feed == null || feed.isLocalFeed() || feed.getState() != Feed.STATE_SUBSCRIBED
                    || !AdmarkPreferences.isFeedIncluded(feed.getId())) {
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
        int failures = AdmarkPreferences.getRefreshFailureCount(feed.getId()) + 1;
        AdmarkPreferences.setRefreshFailureCount(feed.getId(), failures);
        if (!AdmarkRevertPolicy.shouldRevert(failures)) {
            Log.d(TAG, "refresh failed " + failures + "x; keeping proxied feed");
            return;
        }
        executor.execute(() -> revertToOriginal(feed.getId(), feed.getDownloadUrl(), true));
    }

    public void onFeedRefreshed(@Nullable Feed feed) {
        if (feed == null) {
            return;
        }
        AdmarkPreferences.setRefreshFailureCount(feed.getId(), 0);
    }

    /**
     * Deletes AdMark subscriptions whose podcast is no longer in the local
     * library (unsubscribe attempts that failed while offline, reinstalls).
     * Reports the outcome via the proxy status shown on the settings screen.
     */
    public void reconcileSubscriptions() {
        executor.execute(() -> {
            if (!AdmarkPreferences.isEnabled() || !AdmarkAuth.isSignedIn(AdmarkPreferences.getContext())) {
                return;
            }
            try {
                java.util.Set<String> localOriginals = new java.util.HashSet<>();
                for (Feed feed : DBReader.getFeedList()) {
                    if (feed == null || feed.isLocalFeed()) {
                        continue;
                    }
                    String original = AdmarkPreferences.getOriginalFeedUrl(feed.getId());
                    localOriginals.add(!TextUtils.isEmpty(original) ? original : feed.getDownloadUrl());
                }
                java.util.List<AdmarkSubscriptionResult> remote = client.fetchSubscriptions();
                int deleted = 0;
                if (remote != null) {
                    for (AdmarkSubscriptionResult subscription : remote) {
                        String original = !TextUtils.isEmpty(subscription.getFeedUrl())
                                ? subscription.getFeedUrl() : null;
                        if (original == null || localOriginals.contains(original)) {
                            continue;
                        }
                        try {
                            client.unregisterSubscription(original, subscription.getId());
                            deleted++;
                        } catch (IOException e) {
                            Log.d(TAG, "reconcile delete failed: " + e.getMessage());
                        }
                    }
                }
                AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_OK,
                        deleted == 0 ? "Subscriptions in sync" : "Removed " + deleted + " stale subscription(s)");
            } catch (Exception e) {
                Log.d(TAG, "reconcileSubscriptions failed: " + e.getMessage());
                AdmarkPreferences.setProxyStatus(AdmarkPreferences.STATUS_ERROR,
                        "Reconcile failed: " + e.getMessage());
            }
        });
    }

    public void recordSkip(long skippedMs) {
        if (skippedMs <= 0) {
            return;
        }
        AdmarkSkipStats stats = AdmarkSkipStats.parse(AdmarkPreferences.getSkipStats());
        if (stats == null) {
            stats = new AdmarkSkipStats(0, 0);
        }
        AdmarkPreferences.setSkipStats(stats.withSkip(skippedMs).encode());
    }

    @Nullable
    public AdmarkSkipStats getSkipStats() {
        return AdmarkSkipStats.parse(AdmarkPreferences.getSkipStats());
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
                String actual = memoizedAudioSha(item);
                if (actual != null && !AdmarkSha256.matches(expectedSha256, actual)) {
                    Log.w(TAG, "audio sha256 mismatch for " + key);
                    AdmarkPreferences.clearExpectedAudioSha256(key);
                }
            }
        }
        invalidate(item);
        enqueueAnalysisIfNeeded(item);
    }

    public void setFeedIncluded(@Nullable Feed feed, boolean included) {
        if (feed == null || feed.getId() <= 0 || feed.isLocalFeed()) {
            return;
        }
        AdmarkPreferences.setFeedIncluded(feed.getId(), included);
        if (!AdmarkPreferences.isEnabled()) {
            return;
        }
        if (included) {
            registerSubscription(feed);
            return;
        }
        final long feedId = feed.getId();
        final String currentUrl = feed.getDownloadUrl();
        final String originalUrl = AdmarkPreferences.getOriginalFeedUrl(feedId);
        final String podcastId = AdmarkPreferences.getPodcastId(feedId);
        final String feedUrl = !TextUtils.isEmpty(originalUrl) ? originalUrl : currentUrl;
        executor.execute(() -> {
            boolean reverted = true;
            try {
                if (!TextUtils.isEmpty(originalUrl) && !TextUtils.isEmpty(currentUrl)
                        && !originalUrl.equals(currentUrl)) {
                    DBWriter.updateFeedDownloadURL(currentUrl, originalUrl).get();
                }
                try {
                    client.unregisterSubscription(feedUrl, podcastId);
                } catch (Exception e) {
                    Log.d(TAG, "unregister on exclude failed: " + e.getMessage());
                }
            } catch (Exception e) {
                reverted = false;
                Log.d(TAG, "exclude feed failed: " + e.getMessage());
            }
            if (reverted) {
                AdmarkPreferences.clearProxyMapping(feedId);
            }
        });
    }

    @NonNull
    public String scanKey(@NonNull FeedItem item) {
        return cacheKey(item);
    }

    @Nullable
    public AdmarkScanSummary getStoredScan(@NonNull FeedItem item) {
        AdmarkEpisodeMarks marks = cache.get(cacheKey(item));
        AdmarkScanSummary summary = marks == null
                ? AdmarkScanSummary.parse(AdmarkPreferences.getScanSummary(cacheKey(item)))
                : AdmarkScanSummary.fromMarks(marks);
        String signature = audioSignature(item);
        return summary == null ? null : summary.withAudioReady(
                signature != null && signature.equals(verifiedAudio.get(item.getId())));
    }

    @Nullable
    private static String audioSignature(FeedItem item) {
        FeedMedia media = item.getMedia();
        if (media == null || !media.isDownloaded() || TextUtils.isEmpty(media.getLocalFileUrl())) {
            return null;
        }
        File file = new File(media.getLocalFileUrl());
        return file.isFile() ? file.getPath() + "|" + file.length() + "|" + file.lastModified() : null;
    }

    @Nullable
    private AdmarkScanSummary summaryFor(FeedItem item, AdmarkEpisodeMarks marks) {
        AdmarkScanSummary summary = AdmarkScanSummary.fromMarks(marks);
        if (summary != null && summary.getStatus() == AdmarkScanSummary.Status.READY) {
            String signature = audioSignature(item);
            boolean ready = signature != null && audioShaMatches(item, marks);
            if (ready) {
                verifiedAudio.put(item.getId(), signature);
            } else {
                verifiedAudio.remove(item.getId());
            }
            return summary.withAudioReady(ready);
        }
        return summary;
    }

    public void refreshStoredScan(@NonNull FeedItem item, @Nullable AdmarkScanSummary.Listener listener) {
        if (!canTrack(item)) {
            dispatch(listener, null);
            return;
        }
        String key = cacheKey(item);
        AdmarkScanSummary storedSummary = getStoredScan(item);
        if (storedSummary != null && storedSummary.getStatus() == AdmarkScanSummary.Status.READY
                && storedSummary.isAudioReady()) {
            dispatch(listener, storedSummary);
            return;
        }
        synchronized (scanListeners) {
            Long lastAttempt = scanAttempts.get(key);
            if (lastAttempt != null && SystemClock.elapsedRealtime() - lastAttempt < 10000
                    && !scanListeners.containsKey(key)) {
                dispatch(listener, storedSummary);
                return;
            }
            List<AdmarkScanSummary.Listener> listeners = scanListeners.get(key);
            boolean start = listeners == null;
            if (start) {
                listeners = new ArrayList<>();
                scanListeners.put(key, listeners);
                scanAttempts.put(key, SystemClock.elapsedRealtime());
            }
            if (listener != null) {
                listeners.add(listener);
            }
            if (!start) {
                return;
            }
        }
        scanExecutor.execute(() -> {
            AdmarkScanSummary summary = storedSummary;
            try {
                summary = summaryFor(item, ensureMarksInternal(item, false, false));
            } catch (Exception e) {
                Log.d(TAG, "refreshStoredScan failed: " + e.getMessage());
            }
            List<AdmarkScanSummary.Listener> listeners;
            synchronized (scanListeners) {
                listeners = scanListeners.remove(key);
            }
            if (listeners != null) {
                for (AdmarkScanSummary.Listener pending : listeners) {
                    dispatch(pending, summary);
                }
            }
        });
    }

    @Nullable
    public String getCanonicalAudioUrl(@Nullable FeedItem item) {
        if (!canTrack(item) || item.getFeed() == null || TextUtils.isEmpty(item.getItemIdentifier())) {
            return null;
        }
        String base = AdmarkPreferences.getBaseUrl();
        String podcastId = AdmarkPreferences.getPodcastId(item.getFeedId());
        String proxied = AdmarkPreferences.getProxiedFeedUrl(item.getFeedId());
        HttpUrl proxyUrl = proxied == null ? null : HttpUrl.parse(proxied);
        HttpUrl baseUrl = HttpUrl.parse(base);
        String token = proxyUrl == null ? null : proxyUrl.queryParameter("token");
        if (proxyUrl == null || baseUrl == null || !proxyUrl.host().equals(baseUrl.host())
                || !proxyUrl.scheme().equals(baseUrl.scheme()) || proxyUrl.port() != baseUrl.port()
                || TextUtils.isEmpty(token) || TextUtils.isEmpty(podcastId)) {
            try {
                String original = AdmarkIntegration.getOriginalFeedUrl(item.getFeed());
                if (TextUtils.isEmpty(original)) {
                    return null;
                }
                AdmarkSubscriptionResult result = client.registerSubscription(original, item.getFeed().getTitle());
                podcastId = result.getPodcastId();
                token = result.getAccessToken();
                if (TextUtils.isEmpty(token) && result.getProxiedFeedUrl() != null) {
                    HttpUrl registered = HttpUrl.parse(result.getProxiedFeedUrl());
                    token = registered == null ? null : registered.queryParameter("token");
                }
            } catch (IOException | JSONException e) {
                Log.d(TAG, "canonical audio unavailable");
                return null;
            }
        }
        return AdmarkAudioUrl.build(base, podcastId, item.getItemIdentifier(), token);
    }

    public void analyzeEpisode(@NonNull FeedItem item, @Nullable AdmarkScanSummary.Listener listener) {
        if (!canTrack(item)) {
            dispatch(listener, null);
            return;
        }
        scanExecutor.execute(() -> {
            AdmarkEpisodeMarks marks;
            try {
                marks = client.enqueueAnalyze(item);
                if (marks.getStatus() == AdmarkEpisodeMarks.Status.READY) {
                    marks = client.fetchMarks(item);
                }
            } catch (IOException | JSONException e) {
                marks = AdmarkEpisodeMarks.failed();
            }
            String key = cacheKey(item);
            cache.put(key, marks);
            persistScan(key, marks);
            scanAttempts.remove(key);
            dispatch(listener, summaryFor(item, marks));
        });
    }

    @NonNull
    public List<AdmarkSkipRange> getPlaybackRanges(@Nullable FeedItem item) {
        if (!canTrack(item)) {
            return Collections.emptyList();
        }
        List<AdmarkSkipRange> ranges = playbackRanges.get(item.getId());
        return ranges == null ? Collections.emptyList() : ranges;
    }

    @NonNull
    public AdmarkEpisodeMarks getMarksForPlayback(@NonNull FeedItem item) {
        if (!canTrack(item)) {
            return AdmarkEpisodeMarks.missing();
        }
        playbackRanges.remove(item.getId());
        try {
            AdmarkEpisodeMarks marks = ensureMarksInternal(item, true, true);
            if (marks.hasSkippableRanges() && !audioVerified(item, marks)) {
                Log.d(TAG, "skip marks withheld: audio not verified");
                return AdmarkEpisodeMarks.missing();
            }
            summaryFor(item, marks);
            if (marks.hasSkippableRanges()) {
                playbackRanges.put(item.getId(), marks.getRanges());
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
            scanAttempts.remove(cacheKey(item));
            verifiedAudio.remove(item.getId());
            streamVerifiedAt.remove(item.getId());
            playbackRanges.remove(item.getId());
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
        if (!AdmarkPreferences.isFeedIncluded(feedId)) {
            AdmarkPreferences.removePendingFeedId(feedId);
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
            persistScan(key, cached);
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
        persistScan(key, marks);
        return marks;
    }

    private static void persistScan(@NonNull String key, @Nullable AdmarkEpisodeMarks marks) {
        String encoded = AdmarkScanSummary.encode(marks);
        if (encoded != null) {
            AdmarkPreferences.putScanSummary(key, encoded);
            AdmarkKeyLru.Result result = AdmarkKeyLru.push(
                    AdmarkPreferences.getScanOrder(), key, SCAN_SUMMARY_MAX_ENTRIES);
            AdmarkPreferences.putScanOrder(result.order);
            for (String evicted : result.evicted) {
                AdmarkPreferences.removeScanSummary(evicted);
            }
        }
    }

    private static boolean canTrack(@Nullable FeedItem item) {
        if (item == null || item.getMedia() == null || !AdmarkPreferences.isEnabled()) {
            return false;
        }
        if (item.getFeed() != null && item.getFeed().isLocalFeed()) {
            return false;
        }
        return AdmarkPreferences.isFeedIncluded(item.getFeedId());
    }

    private static void dispatch(@Nullable AdmarkScanSummary.Listener listener,
                                 @Nullable AdmarkScanSummary summary) {
        if (listener != null) {
            listener.onScan(summary);
        }
    }

    static boolean audioShaMatches(@NonNull FeedItem item, @NonNull AdmarkEpisodeMarks marks) {
        String expected = marks.getAudioSha256();
        if (TextUtils.isEmpty(expected)) {
            return false;
        }
        FeedMedia media = item.getMedia();
        if (media != null && media.isDownloaded() && !TextUtils.isEmpty(media.getLocalFileUrl())) {
            String actual = memoizedAudioSha(item);
            return AdmarkSha256.matches(expected, actual);
        }
        return false;
    }

    /**
     * Verifies streamed audio: a minimal ranged request to the canonical audio
     * URL must report the expected hash via X-Admark-Audio-SHA256. A 302
     * "pending" response carries no hash and fails verification, per contract.
     */
    private boolean streamingAudioVerified(@NonNull FeedItem item, @NonNull AdmarkEpisodeMarks marks) {
        Long verifiedAt = streamVerifiedAt.get(item.getId());
        if (verifiedAt != null && SystemClock.elapsedRealtime() - verifiedAt < STREAM_VERIFY_TTL_MS) {
            return true;
        }
        try {
            boolean verified = client.verifyStreamSha(getCanonicalAudioUrl(item), marks.getAudioSha256());
            if (verified) {
                streamVerifiedAt.put(item.getId(), SystemClock.elapsedRealtime());
            }
            return verified;
        } catch (Exception e) {
            Log.d(TAG, "stream sha verify failed: " + e.getMessage());
            return false;
        }
    }

    private boolean audioVerified(@NonNull FeedItem item, @NonNull AdmarkEpisodeMarks marks) {
        FeedMedia media = item.getMedia();
        if (media != null && media.isDownloaded()) {
            return audioShaMatches(item, marks);
        }
        return streamingAudioVerified(item, marks);
    }

    /** Hashes the downloaded file at most once per path|size|mtime signature. */
    @Nullable
    private static String memoizedAudioSha(@NonNull FeedItem item) {
        String signature = audioSignature(item);
        if (signature == null) {
            return null;
        }
        String cached = AdmarkPreferences.getCachedAudioSha(signature);
        if (!TextUtils.isEmpty(cached)) {
            return cached;
        }
        FeedMedia media = item.getMedia();
        String hex = AdmarkSha256.hexOfFile(media.getLocalFileUrl());
        if (!TextUtils.isEmpty(hex)) {
            AdmarkPreferences.putCachedAudioSha(signature, hex);
        }
        return hex;
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
