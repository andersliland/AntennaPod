package de.danoeh.antennapod.net.admark;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class AdmarkSha256 {
    private static final String TAG = "AdmarkSha256";
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private AdmarkSha256() {
    }

    @Nullable
    public static String hexOfFile(@Nullable String path) {
        if (TextUtils.isEmpty(path)) {
            return null;
        }
        File file = new File(path);
        if (!file.isFile()) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (FileInputStream in = new FileInputStream(file)) {
                int read;
                while ((read = in.read(buffer)) > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException | IOException e) {
            Log.d(TAG, "hash failed: " + e.getMessage());
            return null;
        }
    }

    public static boolean matches(@Nullable String expectedHex, @Nullable String actualHex) {
        if (TextUtils.isEmpty(expectedHex) || TextUtils.isEmpty(actualHex)) {
            return false;
        }
        return expectedHex.trim().equalsIgnoreCase(actualHex.trim());
    }

    private static String toHex(byte[] bytes) {
        char[] out = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            out[i * 2] = HEX[value >>> 4];
            out[i * 2 + 1] = HEX[value & 0x0f];
        }
        return new String(out);
    }
}
