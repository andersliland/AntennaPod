package de.danoeh.antennapod.net.admark;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;

public class AdmarkSkipGuardTest {
    private static final AdmarkSkipRange INTRO = new AdmarkSkipRange(0, 15000, "intro");
    private static final AdmarkSkipRange MID = new AdmarkSkipRange(600000, 645000, "midroll");

    @Test
    public void noRangesMeansNoSkip() {
        assertEquals(-1, AdmarkSkipGuard.decide(Collections.emptyList(), 5000, 0, 0, 100));
        assertEquals(-1, AdmarkSkipGuard.decide(null, 5000, 0, 0, 100));
    }

    @Test
    public void positionInsideRangeSkipsToRangeEnd() {
        assertEquals(15000, AdmarkSkipGuard.decide(Arrays.asList(INTRO, MID), 12000, 0, 0, 100));
        assertEquals(645000, AdmarkSkipGuard.decide(Arrays.asList(INTRO, MID), 640000, 0, 0, 100));
    }

    @Test
    public void positionOutsideRangesDoesNotSkip() {
        assertEquals(-1, AdmarkSkipGuard.decide(Arrays.asList(INTRO, MID), 30000, 0, 0, 100));
        // Range end is exclusive: sitting exactly at the end must not skip again
        assertEquals(-1, AdmarkSkipGuard.decide(Arrays.asList(INTRO, MID), 15000, 0, 0, 100));
    }

    @Test
    public void repeatSkipForSameRangeIsSuppressedWithinInterval() {
        long now = 100000;
        // First tick decides to skip to 15000
        assertEquals(15000, AdmarkSkipGuard.decide(Arrays.asList(INTRO), 12000, 0, 0, now));
        // Seek has not taken effect yet -> same range within 2s is suppressed
        assertEquals(-1, AdmarkSkipGuard.decide(Arrays.asList(INTRO), 12100, 15000, now, now + 500));
        // A different range is never suppressed by the guard
        assertEquals(645000, AdmarkSkipGuard.decide(Arrays.asList(INTRO, MID), 640000, 15000, now, now + 500));
    }

    @Test
    public void sameRangeMaySkipAgainAfterInterval() {
        long now = 100000;
        assertEquals(-1, AdmarkSkipGuard.decide(Arrays.asList(INTRO), 12000, 15000, now, now + 1999));
        assertEquals(15000, AdmarkSkipGuard.decide(Arrays.asList(INTRO), 12000, 15000, now, now + 2000));
    }

    @Test
    public void noPreviousSkipNeverSuppresses() {
        assertEquals(15000, AdmarkSkipGuard.decide(Arrays.asList(INTRO), 12000, 15000, 0, 100));
    }
}
