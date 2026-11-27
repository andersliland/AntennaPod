package de.danoeh.antennapod.net.admark;

import android.net.Uri;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.GrantTypeValues;
import net.openid.appauth.TokenRequest;
import net.openid.appauth.TokenResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AdmarkAuthTest {
    @Test
    public void rejectedRefreshTokenRequiresSignIn() {
        AuthorizationServiceConfiguration configuration = new AuthorizationServiceConfiguration(
                Uri.parse("https://example.com/authorize"), Uri.parse("https://example.com/token"));
        TokenRequest request = new TokenRequest.Builder(configuration, "test-client")
                .setGrantType(GrantTypeValues.REFRESH_TOKEN).setRefreshToken("test-refresh").build();
        AuthState state = new AuthState(configuration);
        state.update(new TokenResponse.Builder(request).setTokenType("Bearer")
                .setAccessToken("test-access").setRefreshToken("test-refresh").build(), null);
        assertTrue(AdmarkAuth.isSignedIn(state));

        state.update((TokenResponse) null, AuthorizationException.TokenRequestErrors.INVALID_GRANT);

        assertNotNull(state.getRefreshToken());
        assertFalse(AdmarkAuth.isSignedIn(state));
    }
}
