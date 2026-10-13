package de.danoeh.antennapod.net.admark;

public final class AdmarkApiPaths {
    public static final String LOOKUP_PATH = "/api/lookup";
    public static final String ANALYZE_PATH = "/api/analyze";
    public static final String SUBSCRIPTIONS_PATH = "/api/subscriptions";
    public static final String JOBS_PATH = "/api/jobs/";

    public static final String QUERY_EPISODE_GUID = "episode_guid";
    public static final String QUERY_FEED_URL = "feed_url";
    public static final String QUERY_PODCAST_ID = "podcast_id";

    public static final String BODY_ENCLOSURE_URL = "enclosure_url";
    public static final String BODY_EPISODE_GUID = "episode_guid";
    public static final String BODY_FEED_URL = "feed_url";
    public static final String BODY_PODCAST_TITLE = "podcast_title";
    public static final String BODY_EPISODE_TITLE = "episode_title";

    public static final String JSON_ID = "id";
    public static final String JSON_FEED_URL = "feed_url";
    public static final String JSON_PROXIED_FEED_URL = "proxied_feed_url";
    public static final String JSON_ACCESS_TOKEN = "access_token";
    public static final String JSON_AUDIO_SHA256 = "audio_sha256";

    public static final String AUTH_HEADER = "X-Alto-Token";
    public static final String AUDIO_SHA256_HEADER = "X-Admark-Audio-SHA256";
    public static final String CANONICAL_HEADER = "X-Admark-Canonical";
    public static final String REQUEST_ARG_AUDIO_SHA256 = "admark_audio_sha256";

    private AdmarkApiPaths() {
    }
}
