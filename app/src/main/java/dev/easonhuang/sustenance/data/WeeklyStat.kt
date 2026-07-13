package dev.easonhuang.sustenance.data

/** Today's performance vs goal and yesterday's comparison for a metric. */
data class WeeklyStat(
    val metric: Metric,
    val perDay: List<SeriesPoint>,   // last 7 days, oldest → newest
    val todayValue: Float,
    val yesterdayValue: Float,
    val goal: Float,
) {
    /** 0f..1f progress of today toward the goal. */
    val progress: Float get() {
        if (goal <= 0f) return 0f
        val current = if (metric == Metric.CALORIC_BALANCE) kotlin.math.abs(todayValue) else todayValue
        return (current / goal).coerceIn(0f, 1f)
    }

    /** Day-over-day change as a percentage. */
    val deltaPercent: Float? get() {
        val current = if (metric == Metric.CALORIC_BALANCE) -todayValue else todayValue
        val prior = if (metric == Metric.CALORIC_BALANCE) -yesterdayValue else yesterdayValue
        val absPrior = kotlin.math.abs(prior)
        return if (absPrior > 0f) (current - prior) / absPrior * 100f else null
    }
}
