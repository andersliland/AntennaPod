package de.danoeh.antennapod.net.admark;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Base64;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class AdmarkIdTokenTest {
    private static String tokenWithPayload(String jsonPayload) {
        String header = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(jsonPayload.getBytes(StandardCharsets.UTF_8));
        return header + "." + payload + ".signature";
    }

    @Test
    public void readsEmailClaim() {
        String token = tokenWithPayload("{\"email\":\"user@example.com\",\"sub\":\"123\"}");
        assertEquals("user@example.com", AdmarkIdToken.email(token));
    }

    @Test
    public void displayNamePrefersNameThenUsernameThenEmail() {
        assertEquals("Anders", AdmarkIdToken.displayName(
                tokenWithPayload("{\"name\":\"Anders\",\"preferred_username\":\"a\",\"email\":\"x@y\"}")));
        assertEquals("a", AdmarkIdToken.displayName(
                tokenWithPayload("{\"preferred_username\":\"a\",\"email\":\"x@y\"}")));
        assertEquals("x@y", AdmarkIdToken.displayName(tokenWithPayload("{\"email\":\"x@y\"}")));
    }

    @Test
    public void malformedTokensYieldNull() {
        assertNull(AdmarkIdToken.email(null));
        assertNull(AdmarkIdToken.email(""));
        assertNull(AdmarkIdToken.email("not-a-jwt"));
        assertNull(AdmarkIdToken.email("a.b.c"));
    }

    @Test
    public void nonStringClaimsAreIgnored() {
        assertNull(AdmarkIdToken.email(tokenWithPayload("{\"email\":true}")));
    }
}
