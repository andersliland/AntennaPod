package de.danoeh.antennapod.net.admark;

import android.os.Handler;
import android.os.Looper;
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

    public void attach(Host host) {
        this.host = host;
    }

    public void onPlayableChanged(@Nullable FeedMedia media) {
        ranges = Collections.emptyList();
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
                }
            });
        });
    }

    public void onPositionMs(long positionMs) {
        if (host == null || ranges.isEmpty() || !AdmarkPreferences.isEnabled()
                || !AdmarkPreferences.isAutoSkipEnabled()) {
            return;
        }
        for (AdmarkSkipRange range : ranges) {
            if (range.contains(positionMs)) {
                Log.d(TAG, "skip " + range.getStartMs() + "-" + range.getEndMs());
                host.seekTo(range.getEndMs());
                host.onSkippedAd();
                return;
            }
        }
    }

    public void detach() {
        activeItemId.set(-1);
        ranges = Collections.emptyList();
        host = null;
        mainHandler.removeCallbacksAndMessages(null);
    }
}
