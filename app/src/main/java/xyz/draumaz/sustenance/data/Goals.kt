package xyz.draumaz.sustenance.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.goalsDataStore by preferencesDataStore(name = "sustenance_goals")

/**
 * Daily targets the user is working toward. Only daily-total metrics support goals; the weekly
 * summary measures the week's daily average against these.
 */
object GoalCatalog {
    // Defaults follow US health-agency guidance for a 2,000 kcal day, taking the stricter figure
    // where agencies differ: AHA for sodium, saturated fat and sugar; the low end of the National
    // Academies' range (45% of energy) for carbs; FDA's long-standing 30%-of-energy figure for fat.
    val defaults: Map<Metric, Float> = linkedMapOf(
        Metric.TOTAL_CALORIES to 2000f,
        Metric.FOOD to 2000f,
        Metric.CALORIC_BALANCE to 0f,
        Metric.PROTEIN to 50f,
        Metric.CARBS to 225f,
        Metric.FAT to 65f,
        Metric.SATURATED_FAT to 13f,
        Metric.SODIUM to 1500f,
        Metric.SUGAR to 25f,
        Metric.FIBER to 28f,
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

    suspend fun setGoals(values: Map<Metric, Float>) {
        context.goalsDataStore.edit { prefs -> values.forEach { (metric, value) -> prefs[key(metric)] = value } }
    }
}
