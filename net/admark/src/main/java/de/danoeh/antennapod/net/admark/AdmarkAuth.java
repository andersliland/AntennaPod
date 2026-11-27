package de.danoeh.antennapod.net.admark;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.Nullable;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;
import de.danoeh.antennapod.storage.preferences.AdmarkPreferences;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationService;
import org.json.JSONException;

public final class AdmarkAuth {
    public static final String ISSUER = "https://auth.liland.xyz/application/o/admark-antennapod/";
    public static final String CLIENT_ID = "admark-antennapod";
    public static final Uri REDIRECT_URI = Uri.parse(BuildConfig.REDIRECT_URI);
    private static final String STATE_KEY = "auth_state";
    private static final Object TOKEN_REFRESH_LOCK = new Object();

    private AdmarkAuth() {
    }

    private static SharedPreferences preferences(Context context) throws IOException {
        try {
            MasterKey key = new MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            return EncryptedSharedPreferences.create(context, "admark_oauth", key,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception exception) {
            throw new IOException("Unable to open Admark credentials", exception);
        }
    }

    public static synchronized AuthState load(Context context) throws IOException {
        String json = preferences(context).getString(STATE_KEY, null);
        if (json == null) {
            return new AuthState();
        }
        try {
            return AuthState.jsonDeserialize(json);
        } catch (JSONException exception) {
            throw new IOException("Invalid Admark credentials", exception);
        }
    }

    public static synchronized void save(Context context, AuthState state) throws IOException {
        if (!preferences(context).edit().putString(STATE_KEY, state.jsonSerializeString()).commit()) {
            throw new IOException("Unable to save Admark credentials");
        }
    }

    private static synchronized void saveIfCurrent(Context context, String previousRefreshToken,
                                                    AuthState state) throws IOException {
        if (!Objects.equals(load(context).getRefreshToken(), previousRefreshToken)) {
            throw new IOException("Admark credentials changed during refresh");
        }
        save(context, state);
    }

    public static synchronized void signOut(Context context) throws IOException {
        if (!preferences(context).edit().remove(STATE_KEY).commit()) {
            throw new IOException("Unable to clear Admark credentials");
        }
    }

    public static boolean isSignedIn(Context context) {
        try {
            return isSignedIn(load(context));
        } catch (IOException exception) {
            return false;
        }
    }

    static boolean isSignedIn(AuthState state) {
        return state.isAuthorized() && state.getRefreshToken() != null;
    }

    @Nullable
    public static String freshAccessToken() throws IOException {
        synchronized (TOKEN_REFRESH_LOCK) {
            return refreshAccessToken();
        }
    }

    @Nullable
    private static String refreshAccessToken() throws IOException {
        Context context = AdmarkPreferences.getContext();
        if (context == null) {
            throw new IOException("Admark preferences are not initialized");
        }
        AuthState state = load(context);
        if (state.getRefreshToken() == null) {
            return null;
        }
        String previousRefreshToken = state.getRefreshToken();
        AuthorizationService service = new AuthorizationService(context);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<Exception> error = new AtomicReference<>();
        state.performActionWithFreshTokens(service, (accessToken, idToken, exception) -> {
            token.set(accessToken);
            error.set(exception);
            try {
                saveIfCurrent(context, previousRefreshToken, state);
            } catch (IOException saveError) {
                error.set(saveError);
            }
            latch.countDown();
        });
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IOException("Admark token refresh timed out");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("Admark token refresh interrupted", exception);
        } finally {
            service.dispose();
        }
        if (error.get() != null) {
            throw new IOException("Admark token refresh failed", error.get());
        }
        return token.get();
    }
}
