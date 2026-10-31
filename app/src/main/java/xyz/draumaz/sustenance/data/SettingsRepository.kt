package xyz.draumaz.sustenance.data

import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import xyz.draumaz.sustenance.util.GeminiManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "sustenance_settings")

/** Result of the last "verify" tap in Settings. Stored as its lowercase name. */
enum class VerificationStatus {
    IDLE, SUCCESS, ERROR;

    companion object {
        fun fromStored(value: String?): VerificationStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: IDLE
    }
}

private val apiKeyKey = stringPreferencesKey("api_key")

// The Gemini API key lives in its own file so it can be excluded from backups (see
// res/xml/data_extraction_rules.xml). Older installs kept it in sustenance_settings; move it over.
private val Context.secretsDataStore by preferencesDataStore(
    name = "sustenance_secrets",
    produceMigrations = { context ->
        listOf(object : DataMigration<Preferences> {
            override suspend fun shouldMigrate(currentData: Preferences) =
                currentData[apiKeyKey] == null &&
                    context.settingsDataStore.data.first()[apiKeyKey] != null

            override suspend fun migrate(currentData: Preferences): Preferences {
                val legacyKey = context.settingsDataStore.data.first()[apiKeyKey]
                return currentData.toMutablePreferences().apply {
                    if (legacyKey != null) this[apiKeyKey] = legacyKey
                }
            }

            override suspend fun cleanUp() {
                context.settingsDataStore.edit { it.remove(apiKeyKey) }
            }
        })
    },
)

class SettingsRepository(private val context: Context) {
    private val dynamicColorKey = booleanPreferencesKey("dynamic_color")
    private val ketoModeKey = booleanPreferencesKey("keto_mode")
    private val lastLogTimerEnabledKey = booleanPreferencesKey("last_log_timer_enabled")
    private val judgementalModeKey = booleanPreferencesKey("judgemental_mode")
    private val bingeCheckEnabledKey = booleanPreferencesKey("binge_check_enabled")
    private val gramIncrementEnabledKey = booleanPreferencesKey("gram_increment_enabled")
    private val gramIncrementKey = androidx.datastore.preferences.core.intPreferencesKey("gram_increment")
    private val fastBreakingCaloriesKey = androidx.datastore.preferences.core.intPreferencesKey("fast_breaking_calories")
    private val fastingGoalHoursKey = androidx.datastore.preferences.core.floatPreferencesKey("fasting_goal_hours_v2")
    private val fastingGoalHoursOldKey = androidx.datastore.preferences.core.intPreferencesKey("fasting_goal_hours")
    private val fastingNotificationsEnabledKey = booleanPreferencesKey("fasting_notifications_enabled")
    private val lastFastingNotificationTimeKey = androidx.datastore.preferences.core.longPreferencesKey("last_fasting_notification_time")
    private val apiKeyEnabledKey = booleanPreferencesKey("api_key_enabled")
    private val apiKeyVerificationStatusKey = stringPreferencesKey("api_key_verification_status")
    private val geminiModelKey = stringPreferencesKey("gemini_model")
    private val geminiModelVerificationStatusKey = stringPreferencesKey("gemini_model_verification_status")
    private val pinnedHistoryItemsKey = stringSetPreferencesKey("pinned_history_items")
    private val goalSetupPendingKey = booleanPreferencesKey("goal_setup_pending")
    private val goalSetupDeferredKey = booleanPreferencesKey("goal_setup_deferred")

    val dynamicColor: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[dynamicColorKey] ?: false
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

    val bingeCheckEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[bingeCheckEnabledKey] ?: true
    }

    val gramIncrementEnabled: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[gramIncrementEnabledKey] ?: false
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

    val apiKey: Flow<String> = context.secretsDataStore.data.map { prefs ->
        prefs[apiKeyKey] ?: ""
    }

    val apiKeyVerificationStatus: Flow<VerificationStatus> = context.settingsDataStore.data.map { prefs ->
        VerificationStatus.fromStored(prefs[apiKeyVerificationStatusKey])
    }

    val geminiModel: Flow<String> = context.settingsDataStore.data.map { prefs ->
        prefs[geminiModelKey] ?: GeminiManager.DEFAULT_MODEL
    }

    val geminiModelVerificationStatus: Flow<VerificationStatus> = context.settingsDataStore.data.map { prefs ->
        VerificationStatus.fromStored(prefs[geminiModelVerificationStatusKey])
    }

    val pinnedHistoryItems: Flow<Set<String>> = context.settingsDataStore.data.map { prefs ->
        prefs[pinnedHistoryItemsKey] ?: emptySet()
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[dynamicColorKey] = enabled
        }
    }

    /** Set once Health Connect is first linked; cleared when the user leaves the goal setup sheet. */
    val goalSetupPending: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[goalSetupPendingKey] ?: false
    }

    suspend fun setGoalSetupPending(pending: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[goalSetupPendingKey] = pending
        }
    }

    /** True once the user taps "Not now" on goal setup, until they save goals from it. */
    val goalSetupDeferred: Flow<Boolean> = context.settingsDataStore.data.map { prefs ->
        prefs[goalSetupDeferredKey] ?: false
    }

    /** Closes the goal setup step; [deferred] keeps a prompt in Settings to finish it later. */
    suspend fun finishGoalSetup(deferred: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[goalSetupPendingKey] = false
            prefs[goalSetupDeferredKey] = deferred
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

    suspend fun setBingeCheckEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[bingeCheckEnabledKey] = enabled
        }
    }

    suspend fun setGramIncrementEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[gramIncrementEnabledKey] = enabled
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
        context.secretsDataStore.edit { prefs ->
            prefs[apiKeyKey] = key
        }
    }

    suspend fun setApiKeyVerificationStatus(status: VerificationStatus) {
        context.settingsDataStore.edit { prefs ->
            prefs[apiKeyVerificationStatusKey] = status.name.lowercase()
        }
    }

    suspend fun setGeminiModel(model: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[geminiModelKey] = model
        }
    }

    suspend fun setGeminiModelVerificationStatus(status: VerificationStatus) {
        context.settingsDataStore.edit { prefs ->
            prefs[geminiModelVerificationStatusKey] = status.name.lowercase()
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

    fun getCachedDashboardSummaries(): List<MetricSummary>? {
        val prefs = context.getSharedPreferences("sustenance_dashboard_cache", Context.MODE_PRIVATE)
        val cachedDate = prefs.getString("date", null) ?: return null
        if (cachedDate != java.time.LocalDate.now().toString()) return null
        val json = prefs.getString("json", null) ?: return null
        return summariesFromJson(json)
    }

    fun saveCachedDashboardSummaries(summaries: List<MetricSummary>) {
        val prefs = context.getSharedPreferences("sustenance_dashboard_cache", Context.MODE_PRIVATE)
        val json = summariesToJson(summaries)
        prefs.edit()
            .putString("date", java.time.LocalDate.now().toString())
            .putString("json", json)
            .apply()
    }

    fun getCachedLastLogTime(): java.time.Instant? {
        val prefs = context.getSharedPreferences("sustenance_dashboard_cache", Context.MODE_PRIVATE)
        val epochMilli = prefs.getLong("last_log_time", -1L)
        return if (epochMilli != -1L) java.time.Instant.ofEpochMilli(epochMilli) else null
    }

    fun saveCachedLastLogTime(instant: java.time.Instant?) {
        val prefs = context.getSharedPreferences("sustenance_dashboard_cache", Context.MODE_PRIVATE)
        prefs.edit().apply {
            if (instant != null) {
                putLong("last_log_time", instant.toEpochMilli())
            } else {
                remove("last_log_time")
            }
        }.apply()
    }
}
