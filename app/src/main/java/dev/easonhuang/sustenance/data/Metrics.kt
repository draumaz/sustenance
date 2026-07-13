package dev.easonhuang.sustenance.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Balance
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Grain
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.WaterDrop
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
    TOTAL_CALORIES("total_calories", "Total energy", "kcal", MetricKind.DAILY_TOTAL, Color(0xFFB37B7B), Icons.Rounded.Bolt),
    FOOD("food", "Food", "kcal", MetricKind.DAILY_TOTAL, Color(0xFFC49A6C), Icons.Rounded.Restaurant),
    CALORIC_BALANCE("caloric_balance", "Caloric balance", "kcal", MetricKind.DAILY_TOTAL, Color(0xFF7B9E7B), Icons.Rounded.Balance),
    PROTEIN("protein", "Protein", "g", MetricKind.DAILY_TOTAL, Color(0xFFB38B7B), Icons.Rounded.FitnessCenter),
    CARBS("carbs", "Carbs", "g", MetricKind.DAILY_TOTAL, Color(0xFFC4AB7B), Icons.Rounded.BakeryDining),
    FAT("fat", "Fat", "g", MetricKind.DAILY_TOTAL, Color(0xFF7B99A3), Icons.Rounded.WaterDrop),
    SATURATED_FAT("saturated_fat", "Saturated fat", "g", MetricKind.DAILY_TOTAL, Color(0xFF8A9BA8), Icons.Rounded.Icecream),
    SODIUM("sodium", "Sodium", "mg", MetricKind.DAILY_TOTAL, Color(0xFF8D8AAB), Icons.Rounded.Grain),
    SUGAR("sugar", "Sugar", "g", MetricKind.DAILY_TOTAL, Color(0xFFAB8A9B), Icons.Rounded.Cookie),
    FIBER("fiber", "Fiber", "g", MetricKind.DAILY_TOTAL, Color(0xFF8F857E), Icons.Rounded.Eco);

    companion object {
        fun fromKey(key: String): Metric? = entries.firstOrNull { it.key == key }
    }
}
