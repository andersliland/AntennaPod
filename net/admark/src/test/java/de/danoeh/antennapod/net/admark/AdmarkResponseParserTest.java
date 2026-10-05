package de.danoeh.antennapod.net.admark;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AdmarkResponseParserTest {
    @Test
    public void parseReadyRangesMillis() throws Exception {
        String json = "{\"status\":\"ready\",\"ranges\":[{\"start_ms\":1000,\"end_ms\":5000,\"label\":\"ad\"}]}";
        AdmarkEpisodeMarks marks = AdmarkResponseParser.parse(json);
        assertEquals(AdmarkEpisodeMarks.Status.READY, marks.getStatus());
        assertEquals(1, marks.getRanges().size());
        assertEquals(1000, marks.getRanges().get(0).getStartMs());
        assertEquals(5000, marks.getRanges().get(0).getEndMs());
        assertTrue(marks.getRanges().get(0).contains(2500));
    }

    @Test
    public void parseAdsSeconds() throws Exception {
        String json = "{\"status\":\"complete\",\"ads\":[{\"start\":12.5,\"end\":40}]}";
        AdmarkEpisodeMarks marks = AdmarkResponseParser.parse(json);
        assertEquals(AdmarkEpisodeMarks.Status.READY, marks.getStatus());
        assertEquals(12500, marks.getRanges().get(0).getStartMs());
        assertEquals(40000, marks.getRanges().get(0).getEndMs());
    }

    @Test
    public void parsePending() throws Exception {
        AdmarkEpisodeMarks marks = AdmarkResponseParser.parse("{\"status\":\"pending\",\"ranges\":[]}");
        assertEquals(AdmarkEpisodeMarks.Status.PENDING, marks.getStatus());
    }
}
