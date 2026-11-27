package de.danoeh.antennapod.net.admark;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;
import de.danoeh.antennapod.storage.preferences.AdmarkPreferences;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class AdmarkPlaybackController {
    private static final String TAG = "AdmarkPlayback";
    private static final int MAX_PENDING_POLLS = 3;
    private static final long PENDING_POLL_DELAY_MS = 15000L;

    public interface Host {
        void seekTo(long positionMs);

        long getPositionMs();

        void onSkippedAd();
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicLong activeItemId = new AtomicLong(-1);
    private Host host;
    private List<AdmarkSkipRange> ranges = Collections.emptyList();
    private int pendingPolls = 0;
    private long lastSkipEndMs = 0;
    private long lastSkipAtMs = 0;

    public void attach(Host host) {
        this.host = host;
    }

    public void onPlayableChanged(@Nullable FeedMedia media) {
        mainHandler.removeCallbacksAndMessages(null);
        ranges = Collections.emptyList();
        pendingPolls = 0;
        lastSkipEndMs = 0;
        lastSkipAtMs = 0;
        if (media == null || media.getItem() == null || host == null
                || !AdmarkPreferences.isEnabled() || !AdmarkPreferences.isAutoSkipEnabled()) {
            activeItemId.set(-1);
            return;
        }
        final FeedItem item = media.getItem();
        final long itemId = item.getId();
        activeItemId.set(itemId);
        executor.execute(() -> {
            AdmarkEpisodeMarks marks;
            try {
                marks = AdmarkService.getInstance().getMarksForPlayback(item);
            } catch (Exception e) {
                Log.d(TAG, "load failed: " + e.getMessage());
                return;
            }
            mainHandler.post(() -> {
                if (activeItemId.get() != itemId || host == null) {
                    return;
                }
                if (marks.hasSkippableRanges()) {
                    ranges = marks.getRanges();
                    onPositionMs(host.getPositionMs());
                } else {
                    ranges = Collections.emptyList();
                    if (marks.getStatus() == AdmarkEpisodeMarks.Status.PENDING
                            && pendingPolls < MAX_PENDING_POLLS) {
                        // Analysis still running: back off instead of polling forever
                        pendingPolls++;
                        mainHandler.postDelayed(() -> onPlayableChanged(media),
                                PENDING_POLL_DELAY_MS * pendingPolls);
                    }
                }
            });
        });
    }

    public void onPositionMs(long positionMs) {
        if (host == null || ranges.isEmpty() || !AdmarkPreferences.isEnabled()
                || !AdmarkPreferences.isAutoSkipEnabled()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        long seekTo = AdmarkSkipGuard.decide(ranges, positionMs, lastSkipEndMs, lastSkipAtMs, now);
        if (seekTo < 0) {
            return;
        }
        Log.d(TAG, "skip " + positionMs + " -> " + seekTo);
        lastSkipEndMs = seekTo;
        lastSkipAtMs = now;
        host.seekTo(seekTo);
        host.onSkippedAd();
    }

    public void detach() {
        activeItemId.set(-1);
        ranges = Collections.emptyList();
        pendingPolls = 0;
        lastSkipEndMs = 0;
        lastSkipAtMs = 0;
        host = null;
        mainHandler.removeCallbacksAndMessages(null);
    }
}
