package de.danoeh.antennapod.net.admark;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class AdmarkScanSummaryTest {
    @Test
    public void parseReadySummary() {
        AdmarkScanSummary summary = AdmarkScanSummary.parse("READY|2|4000");
        assertEquals(AdmarkScanSummary.Status.READY, summary.getStatus());
        assertEquals(2, summary.getSegmentCount());
        assertEquals(4000L, summary.getTotalAdMs());
        assertEquals(0xFF2E7D32, summary.getTrafficLightColor());
    }

    @Test
    public void missingIsNotAVisibleScan() {
        AdmarkEpisodeMarks marks = AdmarkEpisodeMarks.missing();
        assertEquals("MISSING|0|0", AdmarkScanSummary.encode(marks));
        assertNull(AdmarkScanSummary.parse(AdmarkScanSummary.encode(marks)));
        assertNull(AdmarkScanSummary.fromMarks(marks));
    }

    @Test
    public void sumsValidRanges() {
        AdmarkEpisodeMarks marks = new AdmarkEpisodeMarks(AdmarkEpisodeMarks.Status.READY, Arrays.asList(
                new AdmarkSkipRange(0, 1500, "ad"),
                new AdmarkSkipRange(9000, 9000, "empty"),
                new AdmarkSkipRange(2000, 3500, "ad")));
        AdmarkScanSummary summary = AdmarkScanSummary.fromMarks(marks);
        assertEquals(2, summary.getSegmentCount());
        assertEquals(3000L, summary.getTotalAdMs());
        assertEquals(0xFFF9A825, AdmarkScanSummary.parse("PENDING|0|0").getTrafficLightColor());
        assertEquals(0xFFC62828, AdmarkScanSummary.parse("FAILED|0|0").getTrafficLightColor());
    }
}
