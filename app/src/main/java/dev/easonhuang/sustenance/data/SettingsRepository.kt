package dev.easonhuang.sustenance.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "sustenance_settings")

class SettingsRepository(private val context: Context) {
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val ketoModeKey = booleanPreferencesKey("keto_mode")
    private val lastLogTimerEnabledKey = booleanPreferencesKey("last_log_timer_enabled")
    private val fastBreakingCaloriesKey = androidx.datastore.preferences.core.intPreferencesKey("fast_breaking_calories")
    private val apiKeyEnabledKey = booleanPreferencesKey("api_key_enabled")
    private val apiKeyKey = stringPreferencesKey("api_key")

    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[dynamicColorKey] ?: true
    }

    val ketoMode: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[ketoModeKey] ?: false
    }

    val lastLogTimerEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[lastLogTimerEnabledKey] ?: false
    }

    val fastBreakingCalories: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[fastBreakingCaloriesKey] ?: 0
    }

    val apiKeyEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[apiKeyEnabledKey] ?: false
    }

    val apiKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[apiKeyKey] ?: ""
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[dynamicColorKey] = enabled
        }
    }

    suspend fun setKetoMode(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[ketoModeKey] = enabled
        }
    }

    suspend fun setLastLogTimerEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[lastLogTimerEnabledKey] = enabled
        }
    }

    suspend fun setFastBreakingCalories(calories: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[fastBreakingCaloriesKey] = calories
        }
    }

    suspend fun setApiKeyEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[apiKeyEnabledKey] = enabled
        }
    }

    suspend fun setApiKey(key: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[apiKeyKey] = key
        }
    }
}
