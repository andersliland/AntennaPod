package de.danoeh.antennapod.net.admark;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public class AdmarkSkipStatsTest {
    @Test
    public void newStatsStartEmpty() {
        AdmarkSkipStats stats = new AdmarkSkipStats(0, 0);
        assertEquals(0, stats.getCount());
        assertEquals(0, stats.getSavedMs());
    }

    @Test
    public void withSkipAccumulates() {
        AdmarkSkipStats stats = new AdmarkSkipStats(0, 0).withSkip(15000).withSkip(645000 - 600000);
        assertEquals(2, stats.getCount());
        assertEquals(60000, stats.getSavedMs());
    }

    @Test
    public void nonPositiveSkipIsIgnored() {
        AdmarkSkipStats stats = new AdmarkSkipStats(1, 1000);
        assertSame(stats, stats.withSkip(0));
        assertSame(stats, stats.withSkip(-5));
    }

    @Test
    public void encodeParseRoundTrip() {
        AdmarkSkipStats stats = new AdmarkSkipStats(7, 252000);
        assertEquals(stats.getCount(), AdmarkSkipStats.parse(stats.encode()).getCount());
        assertEquals(stats.getSavedMs(), AdmarkSkipStats.parse(stats.encode()).getSavedMs());
    }

    @Test
    public void parseInvalidReturnsNull() {
        assertNull(AdmarkSkipStats.parse(null));
        assertNull(AdmarkSkipStats.parse(""));
        assertNull(AdmarkSkipStats.parse("nonsense"));
        assertNull(AdmarkSkipStats.parse("1|2|3"));
        assertNull(AdmarkSkipStats.parse("x|y"));
    }

    @Test
    public void formatSavedTimeBuckets() {
        assertEquals("45s", AdmarkSkipStats.formatSavedTime(45000));
        assertEquals("12m 30s", AdmarkSkipStats.formatSavedTime(750000));
        assertEquals("2h 04m", AdmarkSkipStats.formatSavedTime(7440000));
    }
}
