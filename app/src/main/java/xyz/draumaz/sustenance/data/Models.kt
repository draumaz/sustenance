package xyz.draumaz.sustenance.data

import androidx.compose.ui.graphics.Color
import xyz.draumaz.sustenance.util.FoodNutrients
import java.time.Duration
import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/** Represents a fasting stretch between two food logs. */
data class FastingStretch(
    val startTime: Instant,
    val endTime: Instant,
    val duration: Duration = Duration.between(startTime, endTime)
)

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

fun summariesToJson(summaries: List<MetricSummary>): String {
    val array = JSONArray()
    for (s in summaries) {
        val obj = JSONObject()
        obj.put("metric", s.metric.key)
        obj.put("value", s.value)
        if (s.caption != null) obj.put("caption", s.caption)
        obj.put("hasData", s.hasData)
        obj.put("granted", s.granted)
        val sparkArray = JSONArray()
        s.spark.forEach { sparkArray.put(it.toDouble()) }
        obj.put("spark", sparkArray)
        if (s.goal != null) obj.put("goal", s.goal.toDouble())
        if (s.titleOverride != null) obj.put("titleOverride", s.titleOverride)
        array.put(obj)
    }
    return array.toString()
}

fun summariesFromJson(jsonStr: String): List<MetricSummary>? {
    return runCatching {
        val array = JSONArray(jsonStr)
        val list = mutableListOf<MetricSummary>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val metricKey = obj.getString("metric")
            val metric = Metric.fromKey(metricKey) ?: continue
            val value = obj.getString("value")
            val caption = if (obj.has("caption") && !obj.isNull("caption")) obj.getString("caption") else null
            val hasData = obj.getBoolean("hasData")
            val granted = obj.getBoolean("granted")
            val sparkArray = obj.getJSONArray("spark")
            val spark = mutableListOf<Float>()
            for (j in 0 until sparkArray.length()) {
                spark.add(sparkArray.getDouble(j).toFloat())
            }
            val goal = if (obj.has("goal") && !obj.isNull("goal")) obj.getDouble("goal").toFloat() else null
            val titleOverride = if (obj.has("titleOverride") && !obj.isNull("titleOverride")) obj.getString("titleOverride") else null
            list.add(
                MetricSummary(
                    metric = metric,
                    value = value,
                    caption = caption,
                    hasData = hasData,
                    granted = granted,
                    spark = spark,
                    goal = goal,
                    titleOverride = titleOverride
                )
            )
        }
        list
    }.getOrNull()
}

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
