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
        AdmarkEpisodeMarks.Status status = parseStatus(root.optString("status", "ready"));
        JSONArray rangesArray = firstArray(root, "ranges", "ads", "skips", "marks");
        List<AdmarkSkipRange> ranges = new ArrayList<>();
        if (rangesArray != null) {
            for (int i = 0; i < rangesArray.length(); i++) {
                JSONObject item = rangesArray.getJSONObject(i);
                AdmarkSkipRange range = parseRange(item);
                if (range != null && range.isValid()) {
                    ranges.add(range);
                }
            }
        }
        if (status == AdmarkEpisodeMarks.Status.READY && ranges.isEmpty()
                && root.has("start_ms") && root.has("end_ms")) {
            AdmarkSkipRange single = parseRange(root);
            if (single != null && single.isValid()) {
                ranges.add(single);
            }
        }
        return new AdmarkEpisodeMarks(status, ranges);
    }

    private static AdmarkSkipRange parseRange(JSONObject item) {
        Long startMs = readMillis(item, "start_ms", "startMs", "start_time_ms");
        Long endMs = readMillis(item, "end_ms", "endMs", "end_time_ms");
        if (startMs == null || endMs == null) {
            Double startSec = readSeconds(item, "start", "startTime", "start_time");
            Double endSec = readSeconds(item, "end", "endTime", "end_time");
            if (startSec == null || endSec == null) {
                return null;
            }
            startMs = Math.round(startSec * 1000.0);
            endMs = Math.round(endSec * 1000.0);
        }
        String label = item.optString("label", item.optString("title", "ad"));
        return new AdmarkSkipRange(startMs, endMs, label);
    }

    private static Long readMillis(JSONObject item, String... keys) {
        for (String key : keys) {
            if (item.has(key) && !item.isNull(key)) {
                return item.optLong(key);
            }
        }
        return null;
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

    private static AdmarkEpisodeMarks.Status parseStatus(String raw) {
        if (raw == null || raw.isEmpty()) {
            return AdmarkEpisodeMarks.Status.READY;
        }
        switch (raw.toLowerCase(Locale.ROOT)) {
            case "ready":
            case "complete":
            case "completed":
            case "done":
                return AdmarkEpisodeMarks.Status.READY;
            case "pending":
            case "processing":
            case "queued":
            case "running":
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
