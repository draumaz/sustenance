package dev.easonhuang.sustenance.data

import androidx.compose.ui.graphics.Color
import dev.easonhuang.sustenance.util.FoodNutrients
import java.time.Instant

/** One dashboard tile: the headline value for a metric plus a sparkline of recent points. */
data class MetricSummary(
    val metric: Metric,
    val value: String,          // formatted headline, e.g. "8,432" or "-"
    val caption: String?,       // secondary line, e.g. "avg 72, 54-138 bpm"
    val hasData: Boolean,
    val granted: Boolean,
    val spark: List<Float> = emptyList(),
    val goal: Float? = null,
    val titleOverride: String? = null,
)

/** A single charted data point. */
data class SeriesPoint(
    val time: Instant,
    val value: Float,
    val label: String,          // x-axis label, e.g. "Mon" or "14:30"
)

/** Full detail payload for one metric. */
data class MetricDetail(
    val metric: Metric,
    val headline: String,
    val caption: String?,
    val points: List<SeriesPoint>,
    val stats: List<Pair<String, String>> = emptyList(),  // label -> value rows
    val recent: List<RecordRow> = emptyList(),
    val todaySections: List<Pair<String, List<RecordRow>>> = emptyList(), // Grouped today items
    val goal: Float? = null,
    val isGoalEditable: Boolean = true,
)

/** A row in the "recent records" list on a detail screen. */
data class RecordRow(
    val primary: String,
    val secondary: String,
    val tertiary: String? = null,
    val id: String? = null,
    val isEditable: Boolean = false,
    val startTime: Instant? = null,
    val accentColor: Color? = null,
    val nutrients: FoodNutrients? = null
)

/** A history item representing a previously logged food. */
data class HistoryItem(
    val nutrients: FoodNutrients,
    val timestamp: Instant,
    val isPinned: Boolean = false,
    val accentColor: Color? = null
)
