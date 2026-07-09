package dev.easonhuang.sustenance.data

/** Shared value formatting so the dashboard, summary and export all read the same. */
fun Metric.formatValue(value: Float): String = when (this) {
    Metric.SODIUM -> "%,d".format(value.toLong())
    Metric.TOTAL_CALORIES, Metric.FOOD, Metric.FIBER, Metric.CARBS, Metric.PROTEIN, Metric.FAT, Metric.SATURATED_FAT, Metric.SUGAR, Metric.CALORIC_BALANCE -> "%.1f".format(value)
}
