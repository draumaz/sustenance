package xyz.draumaz.sustenance.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import xyz.draumaz.sustenance.R
import xyz.draumaz.sustenance.data.GoalCatalog
import xyz.draumaz.sustenance.data.GoalsRepository
import xyz.draumaz.sustenance.data.HealthConnectManager
import xyz.draumaz.sustenance.data.Metric
import xyz.draumaz.sustenance.data.SeriesPoint
import xyz.draumaz.sustenance.data.SettingsRepository
import xyz.draumaz.sustenance.data.WeeklyStat
import xyz.draumaz.sustenance.data.formatValue
import xyz.draumaz.sustenance.util.GeminiManager
import xyz.draumaz.sustenance.util.GeminiQuotaException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import java.util.Locale
import androidx.health.connect.client.records.NutritionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class InsightsState(
    val loading: Boolean = true,
    val stats: List<WeeklyStat> = emptyList(),
)

class InsightsViewModel(
    private val manager: HealthConnectManager,
    private val goalsRepo: GoalsRepository,
    private val settings: SettingsRepository,
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

                val currentVal = if (metric == Metric.CALORIC_BALANCE) today.coerceAtLeast(0f) else today
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

    private val _summary = MutableStateFlow<DaySummaryState>(DaySummaryState.Hidden)
    val summary = _summary.asStateFlow()

    /** Bumped by [retrySummary]; failures aren't cached, so this re-runs the same report. */
    private val summaryRetries = MutableStateFlow(0)

    /** Key and model when AI features are on with a key set; null hides the summary. */
    private val gemini = combine(settings.apiKeyEnabled, settings.apiKey, settings.geminiModel) { enabled, key, model ->
        if (enabled && key.isNotBlank()) key.trim() to GeminiManager.modelName(model) else null
    }.distinctUntilChanged()

    init {
        refresh(showIndicator = false)
        viewModelScope.launch {
            combine(state, todayLogs, gemini, summaryRetries) { s, logs, g, _ -> Triple(s, logs, g) }
                .collectLatest { (s, logs, g) -> updateSummary(s, logs, g) }
        }
    }

    private suspend fun updateSummary(s: InsightsState, logs: List<NutritionRecord>, g: Pair<String, String>?) {
        if (g == null) { _summary.value = DaySummaryState.Hidden; return }
        // Show the spinner card before the stats arrive, so it's never inserted above cards that
        // are already on screen (LazyColumn keeps the old first item put, pushing the new card
        // off the top). If nothing turns out to be logged, it's removed below, which is harmless.
        if (s.loading) { _summary.value = DaySummaryState.Loading; return }
        val deficitGoal = (goalsRepo.goals.first()[Metric.CALORIC_BALANCE] ?: 0f) > 0f
        val report = buildDayReport(s.stats, logs, deficitGoal)
            ?: run { _summary.value = DaySummaryState.Hidden; return }
        val (key, model) = g
        val language = manager.context.resources.configuration.locales[0].getDisplayLanguage(Locale.ENGLISH)
        DaySummaryCache.lookup(report, model, language, System.currentTimeMillis())?.let {
            _summary.value = DaySummaryState.Ready(it)
            return
        }
        _summary.value = DaySummaryState.Loading
        // A refresh sets the series and today's logs one after the other; wait for both so one
        // refresh costs one request, not two (collectLatest cancels this on the next emission).
        delay(SUMMARY_DEBOUNCE)
        val now = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm", Locale.US))
        GeminiManager(key, model)
            .summarizeDay("Local time now: $now (the day may not be over).\n\n${report.report}", language)
            .onSuccess { summary ->
                DaySummaryCache.store(report, model, language, summary, System.currentTimeMillis())
                _summary.value = DaySummaryState.Ready(summary)
            }
            .onFailure { error ->
                _summary.value = DaySummaryState.Failed(
                    if (error is GeminiQuotaException) R.string.gemini_quota_reached else R.string.insights_summary_failed
                )
            }
    }

    fun retrySummary() { summaryRetries.value++ }

    fun refresh(showIndicator: Boolean = true) {
        viewModelScope.launch {
            if (showIndicator) _refreshing.value = true
            val granted = runCatching { manager.grantedPermissions() }.getOrDefault(emptySet())
            val metrics = GoalCatalog.metrics.filter { manager.permissionFor(it) in granted }
            val seriesMap = coroutineScope {
                metrics.map { metric ->
                    async { metric to runCatching { manager.readDailySeries(metric, 7) }.getOrDefault(emptyList()) }
                }.awaitAll().toMap()
            }
            // Logs before the series: the first state with stats then already has today's food,
            // so the AI summary card doesn't blink out and back in on the first load.
            if (granted.contains(manager.permissionFor(Metric.FOOD))) {
                todayLogs.value = runCatching { manager.readTodayNutrition() }.getOrDefault(emptyList())
            }
            series.value = seriesMap

            if (showIndicator) delay(500.milliseconds)
            _refreshing.value = false
        }
    }

    fun setGoal(metric: Metric, value: Float) {
        viewModelScope.launch { goalsRepo.setGoal(metric, value) }
    }

    companion object {
        private val SUMMARY_DEBOUNCE = 1_000.milliseconds

        fun factory(manager: HealthConnectManager, goalsRepo: GoalsRepository, settings: SettingsRepository) = viewModelFactory {
            initializer { InsightsViewModel(manager, goalsRepo, settings) }
        }
    }
}
