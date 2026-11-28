package de.danoeh.antennapod.net.admark;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AdmarkKeyLruTest {
    @Test
    public void pushMovesKeyToFront() {
        AdmarkKeyLru.Result result = AdmarkKeyLru.push("a,b,c", "b", 10);
        assertEquals("b,a,c", result.order);
        assertTrue(result.evicted.isEmpty());
    }

    @Test
    public void pushAddsNewKey() {
        AdmarkKeyLru.Result result = AdmarkKeyLru.push("a,b", "c", 10);
        assertEquals("c,a,b", result.order);
        assertTrue(result.evicted.isEmpty());
    }

    @Test
    public void pushEvictsOldestBeyondLimit() {
        AdmarkKeyLru.Result result = AdmarkKeyLru.push("a,b,c,d", "e", 3);
        assertEquals("e,a,b", result.order);
        assertEquals(2, result.evicted.size());
        assertEquals("c", result.evicted.get(0));
        assertEquals("d", result.evicted.get(1));
    }

    @Test
    public void emptyAndBlankSegmentsTolerated() {
        AdmarkKeyLru.Result result = AdmarkKeyLru.push("", "a", 3);
        assertEquals("a", result.order);
        assertTrue(result.evicted.isEmpty());
        assertEquals("b,a", AdmarkKeyLru.push(",,a,", "b", 3).order);
    }

    @Test
    public void splitSkipsBlanks() {
        List<String> keys = AdmarkKeyLru.split("a,,b");
        assertEquals(2, keys.size());
        assertEquals("a", keys.get(0));
        assertEquals("b", keys.get(1));
    }
}
