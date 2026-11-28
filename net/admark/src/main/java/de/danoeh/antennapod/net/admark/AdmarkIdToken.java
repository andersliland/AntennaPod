package de.danoeh.antennapod.net.admark;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Reads the claim set of an OIDC id token (JWT). The token is validated by the
 * authorization server; locally we only decode it to show which account is
 * signed in. Trusted for display purposes only.
 *
 * Only known display claims are extracted (iterating all keys uses
 * JSONObject methods that are not available in JVM unit tests).
 */
public final class AdmarkIdToken {
    private static final String[] KNOWN_CLAIMS = {
            "email", "name", "preferred_username", "given_name", "family_name", "sub",
    };

    private static final String ALPHABET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    private static final int[] REVERSE_INDEX = buildReverseIndex();

    private AdmarkIdToken() {
    }

    private static int[] buildReverseIndex() {
        int[] index = new int[128];
        java.util.Arrays.fill(index, -1);
        for (int i = 0; i < ALPHABET.length(); i++) {
            index[ALPHABET.charAt(i)] = i;
        }
        return index;
    }

    @Nullable
    public static String email(@Nullable String idToken) {
        return claims(idToken).get("email");
    }

    @Nullable
    public static String displayName(@Nullable String idToken) {
        Map<String, String> claims = claims(idToken);
        String name = claims.get("name");
        if (name != null && !name.isEmpty()) {
            return name;
        }
        String preferred = claims.get("preferred_username");
        if (preferred != null && !preferred.isEmpty()) {
            return preferred;
        }
        return claims.get("email");
    }

    @NonNull
    public static Map<String, String> claims(@Nullable String idToken) {
        Map<String, String> claims = new HashMap<>();
        if (idToken == null || idToken.isEmpty()) {
            return claims;
        }
        String[] parts = idToken.split("\\.");
        if (parts.length < 2) {
            return claims;
        }
        try {
            byte[] payload = decodeBase64Url(parts[1]);
            JSONObject json = new JSONObject(new String(payload, StandardCharsets.UTF_8));
            for (String key : KNOWN_CLAIMS) {
                Object value = json.opt(key);
                if (value instanceof String && !((String) value).isEmpty()) {
                    claims.put(key, (String) value);
                }
            }
        } catch (Exception ignored) {
            // Malformed token: no claims, the UI falls back to generic copy
        }
        return claims;
    }

    /** Padding-free base64url decode that works on the JVM and all API levels. */
    private static byte[] decodeBase64Url(String input) {
        String normalized = input.replace('-', '+').replace('_', '/');
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c == '=') {
                break;
            }
            int value = c < 128 ? REVERSE_INDEX[c] : -1;
            if (value < 0) {
                throw new IllegalArgumentException("Invalid base64url character: " + c);
            }
            buffer = (buffer << 6) | value;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out.write((buffer >> bits) & 0xFF);
            }
        }
        return out.toByteArray();
    }
}
