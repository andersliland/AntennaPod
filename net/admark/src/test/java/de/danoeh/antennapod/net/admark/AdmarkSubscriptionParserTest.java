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
        String json = "{\"podcast_id\":\"pod-1\","
                + "\"proxied_feed_url\":\"https://admark.example/feeds/pod-1.xml\","
                + "\"original_feed_url\":\"https://cdn.example/feed.xml\"}";
        AdmarkSubscriptionResult result = AdmarkResponseParser.parseSubscription(json);
        assertEquals("pod-1", result.getPodcastId());
        assertEquals("https://admark.example/feeds/pod-1.xml", result.getProxiedFeedUrl());
        assertEquals("https://cdn.example/feed.xml", result.getOriginalFeedUrl());
        assertTrue(result.hasProxiedFeedUrl());
    }

    @Test
    public void parseSubscriptionCamelCaseAliases() throws Exception {
        String json = "{\"podcastId\":\"abc\",\"proxiedFeedUrl\":\"https://proxy/x\",\"originalFeedUrl\":\"https://o/x\"}";
        AdmarkSubscriptionResult result = AdmarkResponseParser.parseSubscription(json);
        assertEquals("abc", result.getPodcastId());
        assertEquals("https://proxy/x", result.getProxiedFeedUrl());
        assertEquals("https://o/x", result.getOriginalFeedUrl());
    }

    @Test
    public void parseSubscriptionEmpty() throws Exception {
        AdmarkSubscriptionResult result = AdmarkResponseParser.parseSubscription("");
        assertFalse(result.hasProxiedFeedUrl());
    }
}
