package de.danoeh.antennapod.net.admark;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdmarkResponseParser {
    private AdmarkResponseParser() {
    }

    public static AdmarkEpisodeMarks parse(String body) throws JSONException {
        if (body == null || body.trim().isEmpty()) {
            return AdmarkEpisodeMarks.missing();
        }
        JSONObject root = new JSONObject(body);
        String jobId = firstString(root, "job_id", "jobId", "id");
        AdmarkEpisodeMarks.Status status = parseStatus(root.optString("status", ""));
        JSONArray segmentsArray = firstArray(root, "segments");
        List<AdmarkSkipRange> ranges = new ArrayList<>();
        if (segmentsArray != null) {
            for (int i = 0; i < segmentsArray.length(); i++) {
                JSONObject item = segmentsArray.getJSONObject(i);
                AdmarkSkipRange range = parseRange(item);
                if (range != null && range.isValid()) {
                    ranges.add(range);
                }
            }
        }
        if (status == AdmarkEpisodeMarks.Status.UNKNOWN && !ranges.isEmpty()) {
            status = AdmarkEpisodeMarks.Status.READY;
        }
        if (status == AdmarkEpisodeMarks.Status.UNKNOWN && jobId != null && !jobId.isEmpty()) {
            status = AdmarkEpisodeMarks.Status.PENDING;
        }
        if (status == AdmarkEpisodeMarks.Status.READY && ranges.isEmpty()
                && (root.has("start") || root.has("end"))) {
            AdmarkSkipRange single = parseRange(root);
            if (single != null && single.isValid()) {
                ranges.add(single);
            }
        }
        return new AdmarkEpisodeMarks(status, ranges, jobId);
    }

    private static AdmarkSkipRange parseRange(JSONObject item) {
        Double startSec = readSeconds(item, "start", "startTime", "start_time");
        Double endSec = readSeconds(item, "end", "endTime", "end_time");
        if (startSec == null || endSec == null) {
            return null;
        }
        long startMs = Math.round(startSec * 1000.0);
        long endMs = Math.round(endSec * 1000.0);
        String label = item.optString("label", item.optString("title", "ad"));
        return new AdmarkSkipRange(startMs, endMs, label);
    }

    private static Double readSeconds(JSONObject item, String... keys) {
        for (String key : keys) {
            if (item.has(key) && !item.isNull(key)) {
                return item.optDouble(key);
            }
        }
        return null;
    }

    private static JSONArray firstArray(JSONObject root, String... keys) {
        for (String key : keys) {
            JSONArray array = root.optJSONArray(key);
            if (array != null) {
                return array;
            }
        }
        return null;
    }

    private static String firstString(JSONObject root, String... keys) {
        for (String key : keys) {
            if (!root.has(key) || root.isNull(key)) {
                continue;
            }
            String value = root.optString(key, null);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static AdmarkEpisodeMarks.Status parseStatus(String raw) {
        if (raw == null || raw.isEmpty()) {
            return AdmarkEpisodeMarks.Status.UNKNOWN;
        }
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "succeeded":
            case "ready":
            case "complete":
            case "completed":
            case "done":
                return AdmarkEpisodeMarks.Status.READY;
            case "running":
            case "pending":
            case "processing":
            case "queued":
                return AdmarkEpisodeMarks.Status.PENDING;
            case "missing":
            case "not_found":
            case "none":
                return AdmarkEpisodeMarks.Status.MISSING;
            case "failed":
            case "error":
                return AdmarkEpisodeMarks.Status.FAILED;
            default:
                return AdmarkEpisodeMarks.Status.UNKNOWN;
        }
    }
}
