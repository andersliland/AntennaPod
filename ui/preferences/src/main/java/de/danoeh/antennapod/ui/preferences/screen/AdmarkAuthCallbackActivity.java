package de.danoeh.antennapod.ui.preferences.screen;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;
import de.danoeh.antennapod.net.admark.AdmarkAuth;
import de.danoeh.antennapod.net.admark.AdmarkIntegration;
import de.danoeh.antennapod.storage.preferences.AdmarkPreferences;
import de.danoeh.antennapod.ui.preferences.R;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;

public class AdmarkAuthCallbackActivity extends Activity {
    public static final String EXTRA_ENABLE_AFTER_SIGN_IN = "enableAfterSignIn";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AdmarkPreferences.init(getApplicationContext());
        AuthorizationResponse response = AuthorizationResponse.fromIntent(getIntent());
        AuthorizationException exception = AuthorizationException.fromIntent(getIntent());
        if (response == null) {
            Toast.makeText(this, R.string.pref_admark_sign_in_failed, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        AuthState state = new AuthState(response, exception);
        AuthorizationService service = new AuthorizationService(this);
        service.performTokenRequest(response.createTokenExchangeRequest(), (tokenResponse, tokenError) -> {
            state.update(tokenResponse, tokenError);
            boolean success = tokenResponse != null && tokenError == null && state.getRefreshToken() != null;
            if (success) {
                try {
                    AdmarkAuth.save(getApplicationContext(), state);
                    AdmarkPreferences.setToken("");
                    if (getIntent().getBooleanExtra(EXTRA_ENABLE_AFTER_SIGN_IN, false)) {
                        AdmarkPreferences.setEnabled(true);
                    }
                    AdmarkIntegration.routeAllViaAdmark();
                } catch (java.io.IOException saveError) {
                    success = false;
                }
            }
            int message = success ? R.string.pref_admark_signed_in : R.string.pref_admark_sign_in_failed;
            service.dispose();
            runOnUiThread(() -> {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                finish();
            });
        });
    }
}
