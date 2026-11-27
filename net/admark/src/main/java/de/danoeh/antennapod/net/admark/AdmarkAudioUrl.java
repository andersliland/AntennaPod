package de.danoeh.antennapod.net.admark;

import androidx.annotation.Nullable;
import okhttp3.HttpUrl;

public final class AdmarkAudioUrl {
    private AdmarkAudioUrl() {
    }

    @Nullable
    public static String build(String base, String podcastId, String episodeGuid, String token) {
        HttpUrl url = HttpUrl.parse(base);
        if (url == null || podcastId == null || podcastId.isEmpty()
                || episodeGuid == null || episodeGuid.isEmpty() || token == null || token.isEmpty()) {
            return null;
        }
        return url.newBuilder().addPathSegment("audio").addPathSegment(podcastId)
                .addPathSegment(episodeGuid + ".mp3").addQueryParameter("token", token).build().toString();
    }
}
