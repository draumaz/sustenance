package dev.easonhuang.sustenance.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "sustenance_settings")

class SettingsRepository(private val context: Context) {
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val ketoModeKey = booleanPreferencesKey("keto_mode")

    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[dynamicColorKey] ?: true
    }

    val ketoMode: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[ketoModeKey] ?: false
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
}
