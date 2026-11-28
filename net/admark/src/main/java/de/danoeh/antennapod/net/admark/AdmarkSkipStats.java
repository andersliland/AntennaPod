package de.danoeh.antennapod.net.admark;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Running total of skipped ads: how many ranges were skipped and how much
 * playback time that saved. Encoded compactly for persistence as "count|ms".
 */
public final class AdmarkSkipStats {
    private final int count;
    private final long savedMs;

    public AdmarkSkipStats(int count, long savedMs) {
        this.count = Math.max(0, count);
        this.savedMs = Math.max(0, savedMs);
    }

    public int getCount() {
        return count;
    }

    public long getSavedMs() {
        return savedMs;
    }

    public AdmarkSkipStats withSkip(long skippedMs) {
        if (skippedMs <= 0) {
            return this;
        }
        return new AdmarkSkipStats(count + 1, savedMs + skippedMs);
    }

    /** "2h 04m" above an hour, "12m 30s" above a minute, "45s" below. */
    @NonNull
    public static String formatSavedTime(long savedMs) {
        long totalSeconds = Math.max(0, savedMs) / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%dh %02dm", hours, minutes);
        }
        if (minutes > 0) {
            return String.format("%dm %02ds", minutes, seconds);
        }
        return String.format("%ds", seconds);
    }

    @Nullable
    public static AdmarkSkipStats parse(@Nullable String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 2) {
            return null;
        }
        try {
            return new AdmarkSkipStats(Integer.parseInt(parts[0]), Long.parseLong(parts[1]));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @NonNull
    public String encode() {
        return count + "|" + savedMs;
    }
}
