package de.danoeh.antennapod.net.admark;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AdmarkSubscriptionParserTest {
    @Test
    public void parseSubscriptionContract() throws Exception {
        String json = "{"
                + "\"id\":\"a1b2c3d4\","
                + "\"feed_url\":\"https://feeds.megaphone.fm/history102\","
                + "\"podcast_title\":null,"
                + "\"active\":true,"
                + "\"priority\":\"hot_new\","
                + "\"access_token\":\"tok-xyz\","
                + "\"proxied_feed_url\":\"https://admark.liland.xyz/feeds/a1b2c3d4.xml?token=tok-xyz\","
                + "\"last_checked\":null,"
                + "\"last_episode_guid\":null,"
                + "\"created_at\":\"2026-10-13T00:00:00Z\""
                + "}";
        AdmarkSubscriptionResult result = AdmarkResponseParser.parseSubscription(json);
        assertEquals("a1b2c3d4", result.getId());
        assertEquals("https://admark.liland.xyz/feeds/a1b2c3d4.xml?token=tok-xyz", result.getProxiedFeedUrl());
        assertEquals("https://feeds.megaphone.fm/history102", result.getFeedUrl());
        assertEquals("tok-xyz", result.getAccessToken());
        assertTrue(result.hasProxiedFeedUrl());
    }

    @Test
    public void parseSubscriptionEmpty() throws Exception {
        AdmarkSubscriptionResult result = AdmarkResponseParser.parseSubscription("");
        assertFalse(result.hasProxiedFeedUrl());
    }
}
