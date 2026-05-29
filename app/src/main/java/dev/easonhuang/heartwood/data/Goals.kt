package dev.easonhuang.heartwood.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.goalsDataStore by preferencesDataStore(name = "heartwood_goals")

/**
 * Daily targets the user is working toward. Only daily-total metrics support goals; the weekly
 * summary measures the week's daily average against these.
 */
object GoalCatalog {
    val defaults: Map<Metric, Float> = linkedMapOf(
        Metric.STEPS to 10_000f,
        Metric.ACTIVE_CALORIES to 500f,
        Metric.EXERCISE to 30f,
        Metric.DISTANCE to 5f,
        Metric.HYDRATION to 2f,
        Metric.FLOORS to 10f,
    )
    val metrics: List<Metric> get() = defaults.keys.toList()
}

class GoalsRepository(private val context: Context) {

    private fun key(metric: Metric) = floatPreferencesKey("goal_${metric.key}")

    val goals: Flow<Map<Metric, Float>> = context.goalsDataStore.data.map { prefs ->
        GoalCatalog.metrics.associateWith { metric ->
            prefs[key(metric)] ?: GoalCatalog.defaults.getValue(metric)
        }
    }

    suspend fun setGoal(metric: Metric, value: Float) {
        context.goalsDataStore.edit { prefs -> prefs[key(metric)] = value }
    }
}
