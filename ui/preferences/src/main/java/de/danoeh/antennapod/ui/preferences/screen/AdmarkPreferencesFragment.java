package de.danoeh.antennapod.ui.preferences.screen;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import de.danoeh.antennapod.net.admark.AdmarkIntegration;
import de.danoeh.antennapod.storage.preferences.AdmarkPreferences;
import de.danoeh.antennapod.ui.preferences.R;

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

        EditTextPreference token = findPreference(AdmarkPreferences.PREF_TOKEN);
        if (token != null) {
            token.setText(AdmarkPreferences.getToken());
            token.setSummaryProvider(preference -> {
                String value = AdmarkPreferences.getToken();
                return value.isEmpty()
                        ? getString(R.string.pref_admark_token_sum)
                        : getString(R.string.pref_admark_token_set_sum);
            });
            token.setOnPreferenceChangeListener((preference, newValue) -> {
                AdmarkPreferences.setToken(String.valueOf(newValue));
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
