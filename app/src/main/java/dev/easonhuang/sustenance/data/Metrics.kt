package dev.easonhuang.sustenance.data

import androidx.annotation.StringRes
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
import dev.easonhuang.sustenance.R

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
    @StringRes val titleRes: Int,
    @StringRes val unitRes: Int,
    val kind: MetricKind,
    val accent: Color,
    val icon: ImageVector,
) {
    TOTAL_CALORIES("total_calories", R.string.metric_total_calories, R.string.unit_kcal, MetricKind.DAILY_TOTAL, Color(0xFFB37B7B), Icons.Rounded.Bolt),
    FOOD("food", R.string.metric_food, R.string.unit_kcal, MetricKind.DAILY_TOTAL, Color(0xFFC49A6C), Icons.Rounded.Restaurant),
    CALORIC_BALANCE("caloric_balance", R.string.metric_caloric_balance, R.string.unit_kcal, MetricKind.DAILY_TOTAL, Color(0xFF7B9E7B), Icons.Rounded.Balance),
    PROTEIN("protein", R.string.metric_protein, R.string.unit_g, MetricKind.DAILY_TOTAL, Color(0xFFB38B7B), Icons.Rounded.FitnessCenter),
    CARBS("carbs", R.string.metric_carbs, R.string.unit_g, MetricKind.DAILY_TOTAL, Color(0xFFC4AB7B), Icons.Rounded.BakeryDining),
    FAT("fat", R.string.metric_fat, R.string.unit_g, MetricKind.DAILY_TOTAL, Color(0xFF7B99A3), Icons.Rounded.WaterDrop),
    SATURATED_FAT("saturated_fat", R.string.metric_saturated_fat, R.string.unit_g, MetricKind.DAILY_TOTAL, Color(0xFF8A9BA8), Icons.Rounded.Icecream),
    SODIUM("sodium", R.string.metric_sodium, R.string.unit_mg, MetricKind.DAILY_TOTAL, Color(0xFF8D8AAB), Icons.Rounded.Grain),
    SUGAR("sugar", R.string.metric_sugar, R.string.unit_g, MetricKind.DAILY_TOTAL, Color(0xFFAB8A9B), Icons.Rounded.Cookie),
    FIBER("fiber", R.string.metric_fiber, R.string.unit_g, MetricKind.DAILY_TOTAL, Color(0xFF8F857E), Icons.Rounded.Eco);

    companion object {
        fun fromKey(key: String): Metric? = entries.firstOrNull { it.key == key }
    }
}
