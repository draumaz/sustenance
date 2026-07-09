package dev.easonhuang.sustenance.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/** How a metric is summarised and charted. */
enum class MetricKind {
    /** Sums to a daily total (steps, distance, calories…). Charted as 7-day bars. */
    DAILY_TOTAL,

    /** A point sample where the most recent value matters (weight, SpO2…). Charted as a trend line. */
    LATEST,
}

/**
 * Catalog of every Health Connect metric the app surfaces. Order here is the dashboard order.
 */
enum class Metric(
    val key: String,
    val title: String,
    val unit: String,
    val kind: MetricKind,
    val accent: Color,
    val icon: ImageVector,
) {
    TOTAL_CALORIES("total_calories", "Total energy", "kcal", MetricKind.DAILY_TOTAL, Color(0xFFEF5350), Icons.Rounded.Bolt),
    FOOD("food", "Food", "kcal", MetricKind.DAILY_TOTAL, Color(0xFFFF9800), Icons.Rounded.Restaurant),
    CALORIC_BALANCE("caloric_balance", "Caloric balance", "kcal", MetricKind.DAILY_TOTAL, Color(0xFF4CAF50), Icons.Rounded.Bolt),
    PROTEIN("protein", "Protein", "g", MetricKind.DAILY_TOTAL, Color(0xFFFF8A65), Icons.Rounded.Restaurant),
    CARBS("carbs", "Carbs", "g", MetricKind.DAILY_TOTAL, Color(0xFFFFB74D), Icons.Rounded.Restaurant),
    FAT("fat", "Fat", "g", MetricKind.DAILY_TOTAL, Color(0xFF4DD0E1), Icons.Rounded.Restaurant),
    SATURATED_FAT("saturated_fat", "Saturated fat", "g", MetricKind.DAILY_TOTAL, Color(0xFF4FC3F7), Icons.Rounded.Restaurant),
    SODIUM("sodium", "Sodium", "mg", MetricKind.DAILY_TOTAL, Color(0xFF9575CD), Icons.Rounded.Restaurant),
    SUGAR("sugar", "Sugar", "g", MetricKind.DAILY_TOTAL, Color(0xFFF06292), Icons.Rounded.Restaurant),
    FIBER("fiber", "Fiber", "g", MetricKind.DAILY_TOTAL, Color(0xFF8D6E63), Icons.Rounded.Restaurant);

    companion object {
        fun fromKey(key: String): Metric? = entries.firstOrNull { it.key == key }
    }
}
