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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DashboardViewModel(
    private val manager: HealthConnectManager,
    private val goalsRepo: GoalsRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {
    private val _summariesMap = MutableStateFlow<Map<Int, List<MetricSummary>>>(emptyMap())
    val summariesMap = _summariesMap.asStateFlow()
    
    private val _dateOffset = MutableStateFlow(0)
    val dateOffset = _dateOffset.asStateFlow()

    private val _lastLogTime = MutableStateFlow<java.time.Instant?>(null)
    val lastLogTime = _lastLogTime.asStateFlow()

    val lastLogTimerEnabled = settingsRepo.lastLogTimerEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val fastBreakingCalories = settingsRepo.fastBreakingCalories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val fastingGoalHours = settingsRepo.fastingGoalHours
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 16)

    val summaries = combine(_summariesMap, _dateOffset) { map, offset ->
        map[offset]
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _refreshing = MutableStateFlow(false)
    val refreshing = _refreshing.asStateFlow()

    init {
        viewModelScope.launch {
            combine(goalsRepo.goals, settingsRepo.ketoMode) { _, _ -> }.collect {
                _summariesMap.value = emptyMap()
                refresh(showIndicator = false)
            }
        }
        viewModelScope.launch {
            _dateOffset.collect { offset ->
                if (!_summariesMap.value.containsKey(offset)) {
                    refresh(showIndicator = false)
                }
            }
        }
        viewModelScope.launch {
            manager.changes.collect {
                _summariesMap.value = emptyMap()
                refresh(showIndicator = false)
            }
        }
    }

    fun moveBack() {
        _dateOffset.value++
    }

    fun moveForward() {
        if (_dateOffset.value > 0) {
            _dateOffset.value--
        }
    }

    fun resetOffset() {
        _dateOffset.value = 0
    }

    fun preload(offset: Int) {
        if (_summariesMap.value.containsKey(offset) || offset < 0) return
        viewModelScope.launch {
            fetchForOffset(offset)
        }
    }

    private suspend fun fetchForOffset(offset: Int) {
        val goals = goalsRepo.goals.first()
        val isKeto = settingsRepo.ketoMode.first()
        
        var finalGoals = goals
        val deficitAmount = goals[Metric.CALORIC_BALANCE] ?: 0f
        if (deficitAmount != 0f) {
            val energyOnDay = manager.readDailySeries(Metric.TOTAL_CALORIES, 1, offset).lastOrNull()?.value ?: 0f
            if (energyOnDay > 0) {
                finalGoals = goals + (Metric.FOOD to (energyOnDay - deficitAmount).coerceAtLeast(0f))
            }
        }
        val data = manager.readDashboard(finalGoals, isKeto, offset)
        if (offset == 0) {
            val threshold = settingsRepo.fastBreakingCalories.first().toDouble()
            _lastLogTime.value = manager.readLastFoodLogTime(threshold)
        }
        _summariesMap.value = _summariesMap.value + (offset to data)
    }

    fun refresh(showIndicator: Boolean = true) {
        viewModelScope.launch {
            if (showIndicator) _refreshing.value = true
            fetchForOffset(_dateOffset.value)
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
    private val settingsRepo: SettingsRepository,
    private val metric: Metric,
    private val dateOffset: Int = 0,
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
        viewModelScope.launch {
            manager.changes.collect {
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
                val energyOnDay = manager.readDailySeries(Metric.TOTAL_CALORIES, 1, dateOffset).lastOrNull()?.value ?: 0f
                if (energyOnDay > 0) {
                    finalGoal = (energyOnDay - caloricBalanceGoal).coerceAtLeast(0f)
                }
            }

            _detail.value = manager.readDetail(metric, finalGoal, caloricBalanceGoal != 0f, dateOffset)
            if (showIndicator) delay(500)
            _refreshing.value = false
        }
    }

    fun setGoal(value: Float) {
        viewModelScope.launch {
            goalsRepo.setGoal(metric, value)
        }
    }

    fun deleteRecord(id: String) {
        viewModelScope.launch {
            manager.deleteRecord(id)
        }
    }

    companion object {
        fun factory(manager: HealthConnectManager, goalsRepo: GoalsRepository, settingsRepo: SettingsRepository, metric: Metric, dateOffset: Int = 0) = viewModelFactory {
            initializer { DetailViewModel(manager, goalsRepo, settingsRepo, metric, dateOffset) }
        }
    }
}

class SettingsViewModel(
    private val repository: SettingsRepository
) : ViewModel() {
    val dynamicColor = repository.dynamicColor
    val ketoMode = repository.ketoMode
    val lastLogTimerEnabled = repository.lastLogTimerEnabled
    val fastBreakingCalories = repository.fastBreakingCalories
    val fastingGoalHours = repository.fastingGoalHours
    val apiKeyEnabled = repository.apiKeyEnabled
    val apiKey = repository.apiKey

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setKetoMode(enabled: Boolean) {
        viewModelScope.launch { repository.setKetoMode(enabled) }
    }

    fun setLastLogTimerEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setLastLogTimerEnabled(enabled) }
    }

    fun setFastBreakingCalories(calories: Int) {
        viewModelScope.launch { repository.setFastBreakingCalories(calories) }
    }

    fun setFastingGoalHours(hours: Int) {
        viewModelScope.launch { repository.setFastingGoalHours(hours) }
    }

    fun setApiKeyEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setApiKeyEnabled(enabled) }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch { repository.setApiKey(key) }
    }

    companion object {
        fun factory(repository: SettingsRepository) = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
