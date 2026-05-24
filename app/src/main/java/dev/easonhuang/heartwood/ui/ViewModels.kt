package dev.easonhuang.heartwood.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.easonhuang.heartwood.data.HealthConnectManager
import dev.easonhuang.heartwood.data.Metric
import dev.easonhuang.heartwood.data.MetricDetail
import dev.easonhuang.heartwood.data.MetricSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DashboardViewModel(private val manager: HealthConnectManager) : ViewModel() {
    private val _summaries = MutableStateFlow<List<MetricSummary>?>(null)
    val summaries = _summaries.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            _summaries.value = manager.readDashboard()
            _refreshing.value = false
        }
    }

    companion object {
        fun factory(manager: HealthConnectManager) = viewModelFactory {
            initializer { DashboardViewModel(manager) }
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
