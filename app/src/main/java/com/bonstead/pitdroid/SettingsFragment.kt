package com.bonstead.pitdroid

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat

// MODERNIZED: Extends PreferenceFragmentCompat for AndroidX
class SettingsFragment : PreferenceFragmentCompat(), SharedPreferences.OnSharedPreferenceChangeListener {

    // MODERNIZED: AndroidX requires onCreatePreferences instead of onCreate
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Loads the settings from res/xml/preferences.xml
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onResume() {
        super.onResume()
        preferenceScreen.sharedPreferences?.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onPause() {
        super.onPause()
        preferenceScreen.sharedPreferences?.unregisterOnSharedPreferenceChangeListener(this)
    }

    // MODERNIZED: Nullable parameters added to match modern Kotlin strictness
    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        // MainActivity handles the core logic for preference changes,
        // but this must be overridden to satisfy the interface.
    }
}