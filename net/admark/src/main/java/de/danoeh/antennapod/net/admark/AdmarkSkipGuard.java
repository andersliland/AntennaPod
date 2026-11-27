package de.danoeh.antennapod.net.admark;

import androidx.annotation.Nullable;

import java.util.List;

/**
 * Pure decision helper for ad-range skipping. Keeps the skip loop honest:
 * when a seek does not take effect (buffering, slow player response), the
 * position tick stays inside the same range and must not spam seekTo() and
 * the skip snackbar on every tick.
 */
public final class AdmarkSkipGuard {

    public static final long DEFAULT_REPEAT_INTERVAL_MS = 2000L;

    private AdmarkSkipGuard() {
    }

    /**
     * @param ranges        skip ranges for the current episode
     * @param positionMs    current playback position
     * @param lastSkipEndMs end position of the range skipped last (0 = none)
     * @param lastSkipAtMs  elapsedRealtime timestamp of the last skip (0 = none)
     * @param nowMs         current elapsedRealtime
     * @return end position to seek to, or -1 when no skip should happen now
     */
    public static long decide(@Nullable List<AdmarkSkipRange> ranges, long positionMs,
                              long lastSkipEndMs, long lastSkipAtMs, long nowMs) {
        return decide(ranges, positionMs, lastSkipEndMs, lastSkipAtMs, nowMs,
                DEFAULT_REPEAT_INTERVAL_MS);
    }

    public static long decide(@Nullable List<AdmarkSkipRange> ranges, long positionMs,
                              long lastSkipEndMs, long lastSkipAtMs, long nowMs,
                              long repeatIntervalMs) {
        if (ranges == null || ranges.isEmpty()) {
            return -1;
        }
        for (AdmarkSkipRange range : ranges) {
            if (range.contains(positionMs)) {
                boolean sameRangeJustSkipped = range.getEndMs() == lastSkipEndMs
                        && lastSkipAtMs > 0
                        && nowMs - lastSkipAtMs < repeatIntervalMs;
                if (sameRangeJustSkipped) {
                    return -1;
                }
                return range.getEndMs();
            }
        }
        return -1;
    }
}
