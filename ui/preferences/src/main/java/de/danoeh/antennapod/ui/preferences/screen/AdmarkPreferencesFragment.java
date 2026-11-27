package de.danoeh.antennapod.ui.preferences.screen;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import de.danoeh.antennapod.net.admark.AdmarkAuth;
import de.danoeh.antennapod.net.admark.AdmarkIntegration;
import de.danoeh.antennapod.storage.preferences.AdmarkPreferences;
import de.danoeh.antennapod.ui.preferences.R;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;

public class AdmarkPreferencesFragment extends AnimatedPreferenceFragment {
    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getPreferenceManager().setSharedPreferencesName(AdmarkPreferences.PREF_NAME);
        addPreferencesFromResource(R.xml.preferences_admark);
        setupScreen();
    }

    @Override
    public void onStart() {
        super.onStart();
        ((AppCompatActivity) getActivity()).getSupportActionBar().setTitle(R.string.pref_admark_title);
        refreshProxyStatus();
        refreshSignInStatus();
    }

    private void setupScreen() {
        SwitchPreferenceCompat enabled = findPreference(AdmarkPreferences.PREF_ENABLED);
        if (enabled != null) {
            enabled.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean on = Boolean.TRUE.equals(newValue);
                AdmarkPreferences.setEnabled(on);
                if (on) {
                    AdmarkIntegration.routeAllViaAdmark();
                }
                refreshProxyStatus();
                return true;
            });
        }

        EditTextPreference baseUrl = findPreference(AdmarkPreferences.PREF_BASE_URL);
        if (baseUrl != null) {
            baseUrl.setText(AdmarkPreferences.getBaseUrl());
            baseUrl.setSummaryProvider(preference -> AdmarkPreferences.getBaseUrl());
            baseUrl.setOnPreferenceChangeListener((preference, newValue) -> {
                AdmarkPreferences.setBaseUrl(String.valueOf(newValue));
                return true;
            });
        }

        Preference signIn = findPreference("prefAdmarkSignIn");
        if (signIn != null) {
            signIn.setOnPreferenceClickListener(preference -> {
                AuthorizationServiceConfiguration.fetchFromIssuer(Uri.parse(AdmarkAuth.ISSUER),
                        (configuration, error) -> {
                            if (!isAdded()) {
                                return;
                            }
                            if (configuration == null || error != null) {
                                Toast.makeText(requireContext(), R.string.pref_admark_sign_in_failed,
                                        Toast.LENGTH_LONG).show();
                                return;
                            }
                            AuthorizationRequest request = new AuthorizationRequest.Builder(configuration,
                                    AdmarkAuth.CLIENT_ID, ResponseTypeValues.CODE, AdmarkAuth.REDIRECT_URI)
                                    .setScopes("openid", "email", "profile", "offline_access")
                                    .build();
                            Intent callback = new Intent(requireContext(), AdmarkAuthCallbackActivity.class);
                            PendingIntent pending = PendingIntent.getActivity(requireContext(), 0, callback,
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                            AuthorizationService service = new AuthorizationService(requireContext());
                            service.performAuthorizationRequest(request, pending);
                            service.dispose();
                        });
                return true;
            });
        }
        Preference signOut = findPreference("prefAdmarkSignOut");
        if (signOut != null) {
            signOut.setOnPreferenceClickListener(preference -> {
                try {
                    AdmarkAuth.signOut(requireContext());
                    Toast.makeText(requireContext(), R.string.pref_admark_signed_out, Toast.LENGTH_SHORT).show();
                    refreshSignInStatus();
                } catch (java.io.IOException exception) {
                    Toast.makeText(requireContext(), R.string.pref_admark_sign_in_failed, Toast.LENGTH_LONG).show();
                }
                return true;
            });
        }

        Preference autoSkip = findPreference(AdmarkPreferences.PREF_AUTO_SKIP);
        if (autoSkip != null) {
            autoSkip.setOnPreferenceChangeListener((preference, newValue) -> {
                AdmarkPreferences.setAutoSkipEnabled(Boolean.TRUE.equals(newValue));
                return true;
            });
        }

        Preference autoEnqueue = findPreference(AdmarkPreferences.PREF_AUTO_ENQUEUE);
        if (autoEnqueue != null) {
            autoEnqueue.setOnPreferenceChangeListener((preference, newValue) -> {
                AdmarkPreferences.setAutoEnqueueEnabled(Boolean.TRUE.equals(newValue));
                return true;
            });
        }

        Preference routeAll = findPreference(AdmarkPreferences.PREF_ROUTE_ALL);
        if (routeAll != null) {
            routeAll.setOnPreferenceClickListener(preference -> {
                AdmarkIntegration.routeAllViaAdmark();
                refreshProxyStatus();
                return true;
            });
        }

        refreshProxyStatus();
    }

    private void refreshSignInStatus() {
        Preference signIn = findPreference("prefAdmarkSignIn");
        if (signIn != null && getContext() != null) {
            signIn.setSummary(AdmarkAuth.isSignedIn(getContext())
                    ? R.string.pref_admark_signed_in : R.string.pref_admark_sign_in_sum);
        }
    }

    private void refreshProxyStatus() {
        Preference status = findPreference(AdmarkPreferences.PREF_PROXY_STATUS);
        if (status == null) {
            return;
        }
        String detail = AdmarkPreferences.getProxyStatusDetail();
        switch (AdmarkPreferences.getProxyStatus()) {
            case AdmarkPreferences.STATUS_OK:
                status.setSummary(detail.isEmpty()
                        ? getString(R.string.pref_admark_proxy_status_ok) : detail);
                break;
            case AdmarkPreferences.STATUS_PENDING:
                status.setSummary(detail.isEmpty()
                        ? getString(R.string.pref_admark_proxy_status_pending) : detail);
                break;
            case AdmarkPreferences.STATUS_ERROR:
                status.setSummary(detail.isEmpty()
                        ? getString(R.string.pref_admark_proxy_status_error) : detail);
                break;
            default:
                status.setSummary(getString(R.string.pref_admark_proxy_status_idle));
                break;
        }
    }
}
