package de.danoeh.antennapod.net.admark;

import java.util.Collections;
import java.util.List;

public class AdmarkEpisodeMarks {
    public enum Status {
        READY,
        PENDING,
        MISSING,
        FAILED,
        UNKNOWN
    }

    private final Status status;
    private final List<AdmarkSkipRange> ranges;
    private final String jobId;
    private final String audioSha256;

    public AdmarkEpisodeMarks(Status status, List<AdmarkSkipRange> ranges) {
        this(status, ranges, null, null);
    }

    public AdmarkEpisodeMarks(Status status, List<AdmarkSkipRange> ranges, String jobId) {
        this(status, ranges, jobId, null);
    }

    public AdmarkEpisodeMarks(Status status, List<AdmarkSkipRange> ranges, String jobId, String audioSha256) {
        this.status = status == null ? Status.UNKNOWN : status;
        this.ranges = ranges == null ? Collections.emptyList() : ranges;
        this.jobId = jobId;
        this.audioSha256 = audioSha256;
    }

    public Status getStatus() {
        return status;
    }

    public List<AdmarkSkipRange> getRanges() {
        return ranges;
    }

    public String getJobId() {
        return jobId;
    }

    public String getAudioSha256() {
        return audioSha256;
    }

    public boolean hasSkippableRanges() {
        return status == Status.READY && !ranges.isEmpty();
    }

    public static AdmarkEpisodeMarks missing() {
        return new AdmarkEpisodeMarks(Status.MISSING, Collections.emptyList());
    }

    public static AdmarkEpisodeMarks failed() {
        return new AdmarkEpisodeMarks(Status.FAILED, Collections.emptyList());
    }
}
