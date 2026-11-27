package de.danoeh.antennapod.net.admark;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AdmarkRevertPolicyTest {
    @Test
    public void transientFailuresDoNotRevert() {
        assertFalse(AdmarkRevertPolicy.shouldRevert(0));
        assertFalse(AdmarkRevertPolicy.shouldRevert(1));
        assertFalse(AdmarkRevertPolicy.shouldRevert(2));
    }

    @Test
    public void sustainedFailuresRevert() {
        assertTrue(AdmarkRevertPolicy.shouldRevert(3));
        assertTrue(AdmarkRevertPolicy.shouldRevert(4));
        assertTrue(AdmarkRevertPolicy.shouldRevert(50));
    }
}
