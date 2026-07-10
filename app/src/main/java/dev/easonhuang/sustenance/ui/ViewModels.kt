package dev.easonhuang.sustenance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.easonhuang.sustenance.data.GoalsRepository
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.MetricDetail
import dev.easonhuang.sustenance.data.MetricSummary
import dev.easonhuang.sustenance.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val manager: HealthConnectManager,
    private val goalsRepo: GoalsRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    private val _summaries = MutableStateFlow<List<MetricSummary>?>(null)
    val summaries = _summaries.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                goalsRepo.goals,
                settingsRepo.ketoMode,
            ) { _, _ ->
                refresh(showIndicator = false)
            }.collect {}
        }
    }

    fun refresh(showIndicator: Boolean = true) {
        viewModelScope.launch {
            if (showIndicator) _refreshing.value = true
            val goals = goalsRepo.goals.first()
            val isKeto = settingsRepo.ketoMode.first()

            var finalGoals = goals
            val deficitAmount = goals[Metric.CALORIC_BALANCE] ?: 0f
            if (deficitAmount != 0f) {
                val energyToday = manager.readDailySeries(Metric.TOTAL_CALORIES, 1).lastOrNull()?.value ?: 0f
                if (energyToday > 0) {
                    finalGoals = goals + (Metric.FOOD to (energyToday - deficitAmount).coerceAtLeast(0f))
                }
            }

            _summaries.value = manager.readDashboard(finalGoals, isKeto)
            _refreshing.value = false
        }
    }

    companion object {
        fun factory(manager: HealthConnectManager, goalsRepo: GoalsRepository, settingsRepo: SettingsRepository) = viewModelFactory {
            initializer { DashboardViewModel(manager, goalsRepo, settingsRepo) }
        }
    }
}

class DetailViewModel(
    private val manager: HealthConnectManager,
    private val goalsRepo: GoalsRepository,
    private val metric: Metric,
) : ViewModel() {
    private val _detail = MutableStateFlow<MetricDetail?>(null)
    val detail = _detail.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        viewModelScope.launch {
            goalsRepo.goals.collect { _ ->
                refresh(showIndicator = false)
            }
        }
    }

    fun refresh(showIndicator: Boolean = true) {
        viewModelScope.launch {
            if (showIndicator) _refreshing.value = true
            val goals = goalsRepo.goals.first()
            val caloricBalanceGoal = goals[Metric.CALORIC_BALANCE] ?: 0f

            var finalGoal = goals[metric]
            if (metric == Metric.FOOD && caloricBalanceGoal != 0f) {
                val energyToday = manager.readDailySeries(Metric.TOTAL_CALORIES, 1).lastOrNull()?.value ?: 0f
                if (energyToday > 0) {
                    finalGoal = (energyToday - caloricBalanceGoal).coerceAtLeast(0f)
                }
            }

            _detail.value = manager.readDetail(metric, finalGoal, caloricBalanceGoal != 0f)
            if (showIndicator) delay(500)
            _refreshing.value = false
        }
    }

    fun setGoal(value: Float) {
        viewModelScope.launch {
            goalsRepo.setGoal(metric, value)
        }
    }

    companion object {
        fun factory(manager: HealthConnectManager, goalsRepo: GoalsRepository, metric: Metric) = viewModelFactory {
            initializer { DetailViewModel(manager, goalsRepo, metric) }
        }
    }
}

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {
    val dynamicColor = repository.dynamicColor
    val ketoMode = repository.ketoMode

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setKetoMode(enabled: Boolean) {
        viewModelScope.launch { repository.setKetoMode(enabled) }
    }

    companion object {
        fun factory(repository: SettingsRepository) = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
