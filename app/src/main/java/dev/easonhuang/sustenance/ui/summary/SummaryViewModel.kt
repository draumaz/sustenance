package dev.easonhuang.sustenance.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.easonhuang.sustenance.data.GoalCatalog
import dev.easonhuang.sustenance.data.GoalsRepository
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.SeriesPoint
import dev.easonhuang.sustenance.data.WeeklyStat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SummaryState(
    val loading: Boolean = true,
    val stats: List<WeeklyStat> = emptyList(),
)

class SummaryViewModel(
    private val manager: HealthConnectManager,
    private val goalsRepo: GoalsRepository,
) : ViewModel() {

    // Raw daily series per metric (null until first load completes).
    private val series = MutableStateFlow<Map<Metric, List<SeriesPoint>>?>(null)

    val state = combine(series, goalsRepo.goals) { seriesByMetric, goals ->
        if (seriesByMetric == null) {
            SummaryState(loading = true)
        } else {
            val stats = GoalCatalog.metrics.mapNotNull { metric ->
                val pts = seriesByMetric[metric]
                if (pts.isNullOrEmpty()) return@mapNotNull null
                
                val today = pts.last().value
                val yesterday = if (pts.size >= 2) pts[pts.size - 2].value else 0f
                
                WeeklyStat(
                    metric = metric,
                    perDay = pts.takeLast(7),
                    todayValue = today,
                    yesterdayValue = yesterday,
                    goal = goals[metric] ?: GoalCatalog.defaults.getValue(metric),
                )
            }
            SummaryState(loading = false, stats = stats)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryState())

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
            series.value = GoalCatalog.metrics
                .filter { manager.permissionFor(it) in granted }
                .associateWith { runCatching { manager.readDailySeries(it, 14) }.getOrDefault(emptyList()) }
        }
    }

    fun setGoal(metric: Metric, value: Float) {
        viewModelScope.launch { goalsRepo.setGoal(metric, value) }
    }

    companion object {
        fun factory(manager: HealthConnectManager, goalsRepo: GoalsRepository) = viewModelFactory {
            initializer { SummaryViewModel(manager, goalsRepo) }
        }
    }
}
