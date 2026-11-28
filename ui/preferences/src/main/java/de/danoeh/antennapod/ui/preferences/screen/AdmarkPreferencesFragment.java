package de.danoeh.antennapod.ui.preferences.screen;

import android.app.PendingIntent;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
        refreshAuthStatus();
        refreshSkipStats();
        SwitchPreferenceCompat enabled = findPreference(AdmarkPreferences.PREF_ENABLED);
        if (enabled != null) {
            enabled.setChecked(AdmarkPreferences.isEnabled());
        }
    }

    private void setupScreen() {
        SwitchPreferenceCompat enabled = findPreference(AdmarkPreferences.PREF_ENABLED);
        if (enabled != null) {
            enabled.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean on = Boolean.TRUE.equals(newValue);
                if (on && !AdmarkAuth.isSignedIn(requireContext())) {
                    new MaterialAlertDialogBuilder(requireContext())
                            .setMessage(R.string.pref_admark_sign_in_required)
                            .setPositiveButton(R.string.pref_admark_sign_in_title,
                                    (dialog, which) -> startSignIn(true))
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                    return false;
                }
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
                startSignIn(false);
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
                    refreshAuthStatus();
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

        Preference authStatus = findPreference("prefAdmarkAuthStatus");
        if (authStatus != null) {
            authStatus.setOnPreferenceClickListener(preference -> {
                refreshAuthStatus();
                refreshSkipStats();
                return true;
            });
        }

        Preference reconcile = findPreference("prefAdmarkReconcile");
        if (reconcile != null) {
            reconcile.setOnPreferenceClickListener(preference -> {
                AdmarkIntegration.reconcileSubscriptions();
                Toast.makeText(requireContext(), R.string.pref_admark_reconcile_started, Toast.LENGTH_SHORT).show();
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

    private void startSignIn(boolean enableAfterSignIn) {
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
                    callback.putExtra(AdmarkAuthCallbackActivity.EXTRA_ENABLE_AFTER_SIGN_IN, enableAfterSignIn);
                    PendingIntent pending = PendingIntent.getActivity(requireContext(), 0, callback,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    AuthorizationService service = new AuthorizationService(requireContext());
                    service.performAuthorizationRequest(request, pending);
                    service.dispose();
                });
    }

    private void refreshSignInStatus() {
        Preference signIn = findPreference("prefAdmarkSignIn");
        if (signIn != null && getContext() != null) {
            String account = AdmarkAuth.signedInAccount(getContext());
            if (AdmarkAuth.isSignedIn(getContext()) && !TextUtils.isEmpty(account)) {
                signIn.setSummary(getString(R.string.pref_admark_signed_in_as, account));
            } else {
                signIn.setSummary(AdmarkAuth.isSignedIn(getContext())
                        ? R.string.pref_admark_signed_in : R.string.pref_admark_sign_in_sum);
            }
        }
    }

    private void refreshAuthStatus() {
        Preference status = findPreference("prefAdmarkAuthStatus");
        if (status == null || getContext() == null) {
            return;
        }
        String error = AdmarkPreferences.getAuthError();
        status.setSummary(TextUtils.isEmpty(error)
                ? getString(AdmarkAuth.isSignedIn(getContext())
                        ? R.string.pref_admark_auth_status_ok
                        : R.string.pref_admark_sign_in_sum)
                : error);
    }

    private void refreshSkipStats() {
        Preference stats = findPreference("prefAdmarkStats");
        if (stats == null) {
            return;
        }
        de.danoeh.antennapod.net.admark.AdmarkSkipStats skipStats =
                de.danoeh.antennapod.net.admark.AdmarkIntegration.getSkipStats();
        if (skipStats == null || skipStats.getCount() == 0) {
            stats.setSummary(getString(R.string.pref_admark_stats_none));
        } else {
            stats.setSummary(getString(R.string.pref_admark_stats_value,
                    skipStats.getCount(),
                    de.danoeh.antennapod.net.admark.AdmarkSkipStats.formatSavedTime(
                            skipStats.getSavedMs())));
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
