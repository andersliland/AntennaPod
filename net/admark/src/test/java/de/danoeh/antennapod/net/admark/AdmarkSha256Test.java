package de.danoeh.antennapod.net.admark;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

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
}
