package de.danoeh.antennapod.net.admark;

public class AdmarkSkipRange {
    private final long startMs;
    private final long endMs;
    private final String label;

    public AdmarkSkipRange(long startMs, long endMs, String label) {
        this.startMs = Math.max(0, startMs);
        this.endMs = Math.max(this.startMs, endMs);
        this.label = label;
    }

    public long getStartMs() {
        return startMs;
    }

    public long getEndMs() {
        return endMs;
    }

    public String getLabel() {
        return label;
    }

    public boolean contains(long positionMs) {
        return positionMs >= startMs && positionMs < endMs;
    }

    public boolean isValid() {
        return endMs > startMs;
    }
}
