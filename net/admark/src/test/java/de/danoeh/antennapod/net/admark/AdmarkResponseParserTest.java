package de.danoeh.antennapod.net.admark;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AdmarkResponseParserTest {
    @Test
    public void parseSucceededSegmentsSeconds() throws Exception {
        String json = "{\"status\":\"succeeded\",\"segments\":[{\"start\":1.0,\"end\":5.0,\"label\":\"ad\"}]}";
        AdmarkEpisodeMarks marks = AdmarkResponseParser.parse(json);
        assertEquals(AdmarkEpisodeMarks.Status.READY, marks.getStatus());
        assertEquals(1, marks.getRanges().size());
        assertEquals(1000, marks.getRanges().get(0).getStartMs());
        assertEquals(5000, marks.getRanges().get(0).getEndMs());
        assertTrue(marks.getRanges().get(0).contains(2500));
    }

    @Test
    public void parseRunningWithJobId() throws Exception {
        String json = "{\"status\":\"running\",\"job_id\":\"job-42\",\"segments\":[]}";
        AdmarkEpisodeMarks marks = AdmarkResponseParser.parse(json);
        assertEquals(AdmarkEpisodeMarks.Status.PENDING, marks.getStatus());
        assertEquals("job-42", marks.getJobId());
    }

    @Test
    public void parseJobIdImpliesPending() throws Exception {
        AdmarkEpisodeMarks marks = AdmarkResponseParser.parse("{\"job_id\":\"abc\",\"segments\":[]}");
        assertEquals(AdmarkEpisodeMarks.Status.PENDING, marks.getStatus());
        assertEquals("abc", marks.getJobId());
    }
}
