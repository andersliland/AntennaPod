package de.danoeh.antennapod.net.admark;

import android.util.Log;
import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.storage.preferences.AdmarkPreferences;

import java.util.Collections;
import java.util.List;
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

    private AdmarkService() {
    }

    public static AdmarkService getInstance() {
        return INSTANCE;
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

    @NonNull
    public AdmarkEpisodeMarks getMarksForPlayback(@NonNull FeedItem item) {
        if (!AdmarkPreferences.isEnabled()) {
            return AdmarkEpisodeMarks.missing();
        }
        try {
            return ensureMarksInternal(item, true, true);
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
            }
        }

        if (poll && marks.getStatus() == AdmarkEpisodeMarks.Status.PENDING) {
            for (int attempt = 0; attempt < MAX_POLL_ATTEMPTS; attempt++) {
                Thread.sleep(POLL_DELAY_MS);
                marks = client.fetchMarks(item);
                if (marks.getStatus() != AdmarkEpisodeMarks.Status.PENDING) {
                    break;
                }
            }
        }

        cache.put(key, marks);
        return marks;
    }

    private static String cacheKey(FeedItem item) {
        if (item.getMedia() != null && item.getMedia().getDownloadUrl() != null) {
            return item.getMedia().getDownloadUrl();
        }
        if (item.getItemIdentifier() != null) {
            return item.getItemIdentifier();
        }
        return "item-" + item.getId();
    }
}
