package io.github.draumaz.sustenance.data

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
import io.github.draumaz.sustenance.R
import io.github.draumaz.sustenance.util.FoodNutrients

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

    sealed class Judgement {
        object Positive : Judgement()
        object Negative : Judgement()
    }

    companion object {
        fun fromKey(key: String): Metric? = entries.firstOrNull { it.key == key }

        fun judgeFoodItem(
            nutrients: FoodNutrients,
            currentTotals: Map<Metric, Float>,
            goals: Map<Metric, Float>
        ): Judgement {
            val metricsToCheck = listOf(
                Metric.TOTAL_CALORIES to nutrients.calories.toFloat(),
                Metric.CARBS to nutrients.carbs.toFloat(),
                Metric.FAT to nutrients.fat.toFloat(),
                Metric.SATURATED_FAT to nutrients.saturatedFat.toFloat(),
                Metric.SODIUM to nutrients.sodium.toFloat(),
                Metric.SUGAR to nutrients.sugar.toFloat()
            )

            var isNegative = false
            for ((metric, value) in metricsToCheck) {
                val current = currentTotals[metric] ?: 0f
                val goal = goals[metric] ?: continue
                if (goal <= 0f) continue

                // 1. Knocks you out of your limits
                if (current + value > goal) {
                    isNegative = true
                    break
                }

                // 2. Low nutritional value and gets you close to the limit (90%)
                val isLowNutritionalValue = when (metric) {
                    Metric.SUGAR -> value > 5.0f && (value * 4.0f / (nutrients.calories.toFloat().coerceAtLeast(1.0f)) > 0.2f)
                    Metric.SATURATED_FAT -> value > 2.0f && (value * 9.0f / (nutrients.calories.toFloat().coerceAtLeast(1.0f)) > 0.2f)
                    Metric.SODIUM -> value > 400.0f
                    else -> false
                }

                if (isLowNutritionalValue && (current + value) >= (goal * 0.9f)) {
                    isNegative = true
                    break
                }
            }

            return if (isNegative) Judgement.Negative else Judgement.Positive
        }

        /**
         * Returns a color association for a food item based on its nutrient density.
         * Combines absolute "High In" thresholds with relative "Dominant Nutrient" logic.
         */
        fun computeFoodAccentColor(
            kcal: Double,
            protein: Double,
            carbs: Double,
            fat: Double,
            sugar: Double,
            sodium: Double
        ): Color? {
            if (kcal < 5.0) return null

            // 1. High-priority Watch Nutrients (Absolute)
            if (sugar >= 5.0) return SUGAR.accent
            if (sodium >= 300.0) return SODIUM.accent

            // 2. High-priority Macronutrients (Absolute)
            if (protein >= 8.0) return PROTEIN.accent
            if (fat >= 12.0) return FAT.accent
            if (carbs >= 30.0) return CARBS.accent

            // 3. Dominant Macronutrient (Relative > 40% of energy)
            val pKcal = protein * 4.0
            val cKcal = carbs * 4.0
            val fKcal = fat * 9.0
            val total = pKcal + cKcal + fKcal
            if (total < 1.0) return null

            val pPct = pKcal / total
            val cPct = cKcal / total
            val fPct = fKcal / total

            return when {
                pPct >= 0.4 -> PROTEIN.accent
                fPct >= 0.4 -> FAT.accent
                cPct >= 0.4 -> if (carbs > 0 && sugar / carbs >= 0.3) SUGAR.accent else CARBS.accent
                else -> null
            }
        }
    }
}
