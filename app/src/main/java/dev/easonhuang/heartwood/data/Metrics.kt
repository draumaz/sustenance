package dev.easonhuang.heartwood.data

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Height
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Route
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Stairs
import androidx.compose.material.icons.rounded.Timeline
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
    STEPS("steps", "Steps", "steps", MetricKind.DAILY_TOTAL, Color(0xFF4CAF50), Icons.Rounded.DirectionsWalk),
    DISTANCE("distance", "Distance", "km", MetricKind.DAILY_TOTAL, Color(0xFF26A69A), Icons.Rounded.Route),
    ACTIVE_CALORIES("active_calories", "Active energy", "kcal", MetricKind.DAILY_TOTAL, Color(0xFFFF7043), Icons.Rounded.LocalFireDepartment),
    TOTAL_CALORIES("total_calories", "Total energy", "kcal", MetricKind.DAILY_TOTAL, Color(0xFFEF5350), Icons.Rounded.Bolt),
    FOOD("food", "Food", "kcal", MetricKind.DAILY_TOTAL, Color(0xFFFF9800), Icons.Rounded.Restaurant),
    FLOORS("floors", "Floors climbed", "floors", MetricKind.DAILY_TOTAL, Color(0xFF8D6E63), Icons.Rounded.Stairs),
    EXERCISE("exercise", "Exercise", "min", MetricKind.DAILY_TOTAL, Color(0xFF7E57C2), Icons.Rounded.DirectionsRun),
    HYDRATION("hydration", "Hydration", "L", MetricKind.DAILY_TOTAL, Color(0xFF42A5F5), Icons.Rounded.WaterDrop),
    HEART_RATE("heart_rate", "Heart rate", "bpm", MetricKind.LATEST, Color(0xFFEC407A), Icons.Rounded.MonitorHeart),
    RESTING_HR("resting_heart_rate", "Resting heart rate", "bpm", MetricKind.LATEST, Color(0xFFE91E63), Icons.Rounded.Favorite),
    HRV("hrv", "Heart rate variability", "ms", MetricKind.LATEST, Color(0xFFAB47BC), Icons.Rounded.Timeline),
    SLEEP("sleep", "Sleep", "h", MetricKind.LATEST, Color(0xFF5C6BC0), Icons.Rounded.Bedtime),
    OXYGEN("oxygen", "Blood oxygen", "%", MetricKind.LATEST, Color(0xFF29B6F6), Icons.Rounded.Air),
    RESPIRATORY("respiratory", "Respiratory rate", "br/min", MetricKind.LATEST, Color(0xFF66BB6A), Icons.Rounded.Air),
    VO2MAX("vo2max", "VO₂ max", "mL/kg/min", MetricKind.LATEST, Color(0xFF26C6DA), Icons.Rounded.Speed),
    WEIGHT("weight", "Weight", "kg", MetricKind.LATEST, Color(0xFF78909C), Icons.Rounded.MonitorWeight),
    HEIGHT("height", "Height", "cm", MetricKind.LATEST, Color(0xFF90A4AE), Icons.Rounded.Height),
    BLOOD_PRESSURE("blood_pressure", "Blood pressure", "mmHg", MetricKind.LATEST, Color(0xFFD4504E), Icons.Rounded.Bloodtype),
    BLOOD_GLUCOSE("blood_glucose", "Blood glucose", "mg/dL", MetricKind.LATEST, Color(0xFFFFA726), Icons.Rounded.Bloodtype);

    companion object {
        fun fromKey(key: String): Metric? = entries.firstOrNull { it.key == key }
    }
}
