package de.danoeh.antennapod.net.admark;

import androidx.annotation.Nullable;

public final class AdmarkScanSummary {
    public interface Listener {
        void onScan(@Nullable AdmarkScanSummary summary);
    }

    public enum Status {
        READY,
        PENDING,
        FAILED
    }

    private final Status status;
    private final int segmentCount;
    private final long totalAdMs;

    private AdmarkScanSummary(Status status, int segmentCount, long totalAdMs) {
        this.status = status;
        this.segmentCount = segmentCount;
        this.totalAdMs = totalAdMs;
    }

    public Status getStatus() {
        return status;
    }

    public int getSegmentCount() {
        return segmentCount;
    }

    public long getTotalAdMs() {
        return totalAdMs;
    }

    public int getTrafficLightColor() {
        switch (status) {
            case READY:
                return 0xFF2E7D32;
            case PENDING:
                return 0xFFF9A825;
            case FAILED:
                return 0xFFC62828;
            default:
                return 0xFF9E9E9E;
        }
    }

    @Nullable
    public static AdmarkScanSummary fromMarks(@Nullable AdmarkEpisodeMarks marks) {
        if (marks == null) {
            return null;
        }
        Status status = toStatus(marks.getStatus());
        if (status == null) {
            return null;
        }
        return new AdmarkScanSummary(status, marks.getSegmentCount(), marks.getTotalAdMs());
    }

    @Nullable
    public static AdmarkScanSummary parse(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 3) {
            return null;
        }
        Status status;
        try {
            status = Status.valueOf(parts[0]);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
        try {
            int count = Integer.parseInt(parts[1]);
            long total = Long.parseLong(parts[2]);
            return new AdmarkScanSummary(status, Math.max(0, count), Math.max(0, total));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Nullable
    public static String encode(@Nullable AdmarkEpisodeMarks marks) {
        if (marks == null || marks.getStatus() == null
                || marks.getStatus() == AdmarkEpisodeMarks.Status.UNKNOWN) {
            return null;
        }
        return marks.getStatus().name() + "|" + marks.getSegmentCount() + "|" + marks.getTotalAdMs();
    }

    @Nullable
    private static Status toStatus(@Nullable AdmarkEpisodeMarks.Status status) {
        if (status == null) {
            return null;
        }
        switch (status) {
            case READY:
                return Status.READY;
            case PENDING:
                return Status.PENDING;
            case FAILED:
                return Status.FAILED;
            default:
                return null;
        }
    }
}
