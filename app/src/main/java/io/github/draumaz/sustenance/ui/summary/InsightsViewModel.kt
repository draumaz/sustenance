package io.github.draumaz.sustenance.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.draumaz.sustenance.R
import io.github.draumaz.sustenance.data.GoalCatalog
import io.github.draumaz.sustenance.data.GoalsRepository
import io.github.draumaz.sustenance.data.HealthConnectManager
import io.github.draumaz.sustenance.data.Metric
import io.github.draumaz.sustenance.data.SeriesPoint
import io.github.draumaz.sustenance.data.WeeklyStat
import io.github.draumaz.sustenance.data.formatValue
import androidx.health.connect.client.records.NutritionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class InsightsState(
    val loading: Boolean = true,
    val stats: List<WeeklyStat> = emptyList(),
)

class InsightsViewModel(
    private val manager: HealthConnectManager,
    private val goalsRepo: GoalsRepository,
) : ViewModel() {

    // Raw daily series per metric (null until first load completes).
    private val series = MutableStateFlow<Map<Metric, List<SeriesPoint>>?>(null)
    private val todayLogs = MutableStateFlow<List<NutritionRecord>>(emptyList())

    private val _refreshing = MutableStateFlow(value = false)
    val refreshing = _refreshing.asStateFlow()

    val state = combine(series, todayLogs, goalsRepo.goals) { seriesByMetric, logs, goals ->
        if (seriesByMetric == null) {
            InsightsState(loading = true)
        } else {
            val stats = GoalCatalog.metrics.mapNotNull { metric ->
                val pts = seriesByMetric[metric]
                if (pts.isNullOrEmpty()) return@mapNotNull null
                
                val today = pts.last().value
                val yesterday = if (pts.size >= 2) pts[pts.size - 2].value else 0f
                
                var goal = goals[metric] ?: GoalCatalog.defaults.getValue(metric)
                if (metric == Metric.FOOD) {
                    val deficitAmount = goals[Metric.CALORIC_BALANCE] ?: 0f
                    if (deficitAmount > 0) {
                        val energyToday = seriesByMetric[Metric.TOTAL_CALORIES]?.last()?.value ?: 0f
                        if (energyToday > 0) {
                            goal = (energyToday - deficitAmount).coerceAtLeast(0f)
                        }
                    }
                }

                val currentVal = if (metric == Metric.CALORIC_BALANCE) kotlin.math.abs(today) else today
                val insight = when {
                    goal <= 0f -> null
                    currentVal > goal -> {
                        val biggestOffender = logs.maxByOrNull { log ->
                            when (metric) {
                                Metric.FOOD, Metric.TOTAL_CALORIES -> log.energy?.inKilocalories ?: 0.0
                                Metric.PROTEIN -> log.protein?.inGrams ?: 0.0
                                Metric.CARBS -> log.totalCarbohydrate?.inGrams ?: 0.0
                                Metric.FAT -> log.totalFat?.inGrams ?: 0.0
                                Metric.FIBER -> log.dietaryFiber?.inGrams ?: 0.0
                                Metric.SATURATED_FAT -> log.saturatedFat?.inGrams ?: 0.0
                                Metric.SODIUM -> log.sodium?.inMilligrams ?: 0.0
                                Metric.SUGAR -> log.sugar?.inGrams ?: 0.0
                                else -> 0.0
                            }
                        }
                        if (biggestOffender != null) {
                            val offenderVal = when (metric) {
                                Metric.FOOD, Metric.TOTAL_CALORIES -> biggestOffender.energy?.inKilocalories ?: 0.0
                                Metric.PROTEIN -> biggestOffender.protein?.inGrams ?: 0.0
                                Metric.CARBS -> biggestOffender.totalCarbohydrate?.inGrams ?: 0.0
                                Metric.FAT -> biggestOffender.totalFat?.inGrams ?: 0.0
                                Metric.FIBER -> biggestOffender.dietaryFiber?.inGrams ?: 0.0
                                Metric.SATURATED_FAT -> biggestOffender.saturatedFat?.inGrams ?: 0.0
                                Metric.SODIUM -> biggestOffender.sodium?.inMilligrams ?: 0.0
                                Metric.SUGAR -> biggestOffender.sugar?.inGrams ?: 0.0
                                else -> 0.0
                            }
                            val foodName = biggestOffender.name ?: manager.context.getString(R.string.unknown_food)
                            val unit = manager.context.getString(metric.unitRes)
                            manager.context.getString(
                                R.string.insight_over_goal,
                                foodName,
                                "${metric.formatValue(offenderVal.toFloat())} $unit",
                            )
                        } else null
                    }
                    currentVal > 0 -> manager.context.getString(R.string.insight_good_job)
                    else -> null
                }

                WeeklyStat(
                    metric = metric,
                    perDay = pts.takeLast(7),
                    todayValue = today,
                    yesterdayValue = yesterday,
                    goal = goal,
                    insight = insight
                )
            }
            InsightsState(loading = false, stats = stats)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), InsightsState())

    init { refresh(showIndicator = false) }

    fun refresh(showIndicator: Boolean = true) {
        viewModelScope.launch {
            if (showIndicator) _refreshing.value = true
            val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
            series.value = GoalCatalog.metrics
                .filter { manager.permissionFor(it) in granted }
                .associateWith { runCatching { manager.readDailySeries(it, 14) }.getOrDefault(emptyList()) }
            
            if (granted.contains(manager.permissionFor(Metric.FOOD))) {
                todayLogs.value = runCatching { manager.readTodayNutrition() }.getOrDefault(emptyList())
            }

            if (showIndicator) delay(500.milliseconds)
            _refreshing.value = false
        }
    }

    fun setGoal(metric: Metric, value: Float) {
        viewModelScope.launch { goalsRepo.setGoal(metric, value) }
    }

    companion object {
        fun factory(manager: HealthConnectManager, goalsRepo: GoalsRepository) = viewModelFactory {
            initializer { InsightsViewModel(manager, goalsRepo) }
        }
    }
}
