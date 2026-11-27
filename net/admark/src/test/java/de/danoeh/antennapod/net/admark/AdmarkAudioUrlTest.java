package de.danoeh.antennapod.net.admark;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import okhttp3.HttpUrl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class AdmarkAudioUrlTest {
    @Test
    public void preservesBasePathAndEncodesEpisodeIdentifiers() {
        String raw = AdmarkAudioUrl.build("https://admark.example/prefix", "pod", "episode/?#", "feed-token");
        HttpUrl url = HttpUrl.parse(raw);
        assertEquals("/prefix/audio/pod/episode%2F%3F%23.mp3", url.encodedPath());
        assertEquals("feed-token", url.queryParameter("token"));
    }

    @Test
    public void missingIdentityCannotDownloadUnverifiedAudio() {
        assertNull(AdmarkAudioUrl.build("https://admark.example", "pod", "", "feed-token"));
        assertNull(AdmarkAudioUrl.build("https://admark.example", "pod", "episode", null));
    }
}
