package io.github.draumaz.sustenance.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "sustenance_settings")

class SettingsRepository(private val context: Context) {
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val ketoModeKey = booleanPreferencesKey("keto_mode")
    private val lastLogTimerEnabledKey = booleanPreferencesKey("last_log_timer_enabled")
    private val judgementalModeKey = booleanPreferencesKey("judgemental_mode")
    private val gramIncrementKey = androidx.datastore.preferences.core.intPreferencesKey("gram_increment")
    private val fastBreakingCaloriesKey = androidx.datastore.preferences.core.intPreferencesKey("fast_breaking_calories")
    private val fastingGoalHoursKey = androidx.datastore.preferences.core.floatPreferencesKey("fasting_goal_hours_v2")
    private val fastingGoalHoursOldKey = androidx.datastore.preferences.core.intPreferencesKey("fasting_goal_hours")
    private val fastingNotificationsEnabledKey = booleanPreferencesKey("fasting_notifications_enabled")
    private val lastFastingNotificationTimeKey = androidx.datastore.preferences.core.longPreferencesKey("last_fasting_notification_time")
    private val apiKeyEnabledKey = booleanPreferencesKey("api_key_enabled")
    private val apiKeyKey = stringPreferencesKey("api_key")
    private val apiKeyVerificationStatusKey = stringPreferencesKey("api_key_verification_status")
    private val geminiModelKey = stringPreferencesKey("gemini_model")
    private val geminiModelVerificationStatusKey = stringPreferencesKey("gemini_model_verification_status")
    private val pinnedHistoryItemsKey = stringSetPreferencesKey("pinned_history_items")

    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[dynamicColorKey] ?: true
    }

    val ketoMode: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[ketoModeKey] ?: false
    }

    val lastLogTimerEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[lastLogTimerEnabledKey] ?: false
    }

    val judgementalMode: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[judgementalModeKey] ?: false
    }

    val gramIncrement: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        (prefs[gramIncrementKey] ?: 1).coerceAtLeast(1)
    }

    val fastBreakingCalories: Flow<Int> = context.settingsDataStore.data.map { prefs ->
        prefs[fastBreakingCaloriesKey] ?: 0
    }

    val fastingGoalHours: Flow<Float> = context.settingsDataStore.data.map { prefs ->
        prefs[fastingGoalHoursKey] ?: (prefs[fastingGoalHoursOldKey]?.toFloat() ?: 16f)
    }

    val fastingNotificationsEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[fastingNotificationsEnabledKey] ?: false
    }

    val lastFastingNotificationTime: Flow<Long> = context.settingsDataStore.data.map { prefs ->
        prefs[lastFastingNotificationTimeKey] ?: 0L
    }

    val apiKeyEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[apiKeyEnabledKey] ?: false
    }

    val apiKey: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[apiKeyKey] ?: ""
    }

    val apiKeyVerificationStatus: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[apiKeyVerificationStatusKey] ?: "idle"
    }

    val geminiModel: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[geminiModelKey] ?: "3.5-flash-lite"
    }

    val geminiModelVerificationStatus: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[geminiModelVerificationStatusKey] ?: "idle"
    }

    val pinnedHistoryItems: Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        prefs[pinnedHistoryItemsKey] ?: emptySet()
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

    suspend fun setJudgementalMode(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[judgementalModeKey] = enabled
        }
    }

    suspend fun setGramIncrement(increment: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[gramIncrementKey] = increment.coerceAtLeast(1)
        }
    }

    suspend fun setFastBreakingCalories(calories: Int) {
        context.settingsDataStore.edit { prefs ->
            prefs[fastBreakingCaloriesKey] = calories
        }
    }

    suspend fun setFastingGoalHours(hours: Float) {
        context.settingsDataStore.edit { prefs ->
            prefs[fastingGoalHoursKey] = hours
        }
    }

    suspend fun setFastingNotificationsEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[fastingNotificationsEnabledKey] = enabled
        }
    }

    suspend fun setLastFastingNotificationTime(time: Long) {
        context.settingsDataStore.edit { prefs ->
            prefs[lastFastingNotificationTimeKey] = time
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

    suspend fun setApiKeyVerificationStatus(status: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[apiKeyVerificationStatusKey] = status
        }
    }

    suspend fun setGeminiModel(model: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[geminiModelKey] = model
        }
    }

    suspend fun setGeminiModelVerificationStatus(status: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[geminiModelVerificationStatusKey] = status
        }
    }

    suspend fun togglePinnedHistoryItem(foodName: String) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[pinnedHistoryItemsKey] ?: emptySet()
            if (current.contains(foodName)) {
                prefs[pinnedHistoryItemsKey] = current - foodName
            } else {
                prefs[pinnedHistoryItemsKey] = current + foodName
            }
        }
    }
}
