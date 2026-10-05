package de.danoeh.antennapod.ui.preferences.screen;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
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
    }

    private void setupScreen() {
        SwitchPreferenceCompat enabled = findPreference(AdmarkPreferences.PREF_ENABLED);
        if (enabled != null) {
            enabled.setOnPreferenceChangeListener((preference, newValue) -> {
                AdmarkPreferences.setEnabled(Boolean.TRUE.equals(newValue));
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
    }
}
