package de.danoeh.antennapod.net.admark;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import de.danoeh.antennapod.model.feed.FeedItem;
import de.danoeh.antennapod.model.feed.FeedMedia;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AdmarkSha256Test {
    @Test
    public void hashesFileContents() throws Exception {
        File file = new File(RuntimeEnvironment.getApplication().getCacheDir(), "admark-sha.bin");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("hello-admark".getBytes(StandardCharsets.UTF_8));
        }
        String hex = AdmarkSha256.hexOfFile(file.getAbsolutePath());
        assertEquals("abfe82e8463f018db7f9e9d77a2e9f9f0558a0170f74a6b01d8ed5f7a79728b8", hex);
        assertTrue(AdmarkSha256.matches(hex, hex.toUpperCase()));
        assertFalse(AdmarkSha256.matches(hex, "00"));
    }

    @Test
    public void skipsOnlyWhenDownloadedAudioMatchesAnalysis() throws Exception {
        File file = new File(RuntimeEnvironment.getApplication().getCacheDir(), "admark-playback.bin");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("episode-audio".getBytes(StandardCharsets.UTF_8));
        }
        FeedItem item = new FeedItem();
        FeedMedia media = new FeedMedia(item, "https://example.com/episode.mp3", 0, "audio/mpeg");
        item.setMedia(media);
        media.setLocalFileUrl(file.getAbsolutePath());
        media.setDownloaded(true, System.currentTimeMillis());
        String hash = AdmarkSha256.hexOfFile(file.getAbsolutePath());

        assertTrue(AdmarkService.audioShaMatches(item, marks(hash)));
        assertFalse(AdmarkService.audioShaMatches(item, marks(null)));
        assertFalse(AdmarkService.audioShaMatches(item, marks("different-hash")));

        media.setLocalFileUrl(new File(file.getParentFile(), "missing-audio.mp3").getAbsolutePath());
        assertFalse(AdmarkService.audioShaMatches(item, marks(hash)));
        media.setLocalFileUrl(file.getAbsolutePath());
        media.setDownloaded(false, 0);
        assertFalse(AdmarkService.audioShaMatches(item, marks(hash)));
    }

    private static AdmarkEpisodeMarks marks(String hash) {
        return new AdmarkEpisodeMarks(AdmarkEpisodeMarks.Status.READY, Collections.emptyList(), null, hash);
    }
}
