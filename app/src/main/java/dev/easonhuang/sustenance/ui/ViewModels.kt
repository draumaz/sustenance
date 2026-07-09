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

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            val goals = goalsRepo.goals.first()
            val isKeto = settingsRepo.ketoMode.first()
            _summaries.value = manager.readDashboard(goals, isKeto)
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
    private val metric: Metric,
) : ViewModel() {
    private val _detail = MutableStateFlow<MetricDetail?>(null)
    val detail = _detail.asStateFlow()

    init {
        viewModelScope.launch { _detail.value = manager.readDetail(metric) }
    }

    companion object {
        fun factory(manager: HealthConnectManager, metric: Metric) = viewModelFactory {
            initializer { DetailViewModel(manager, metric) }
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
