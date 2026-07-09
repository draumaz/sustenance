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
    val progress: Float get() = if (goal > 0f) (todayValue / goal).coerceIn(0f, 1f) else 0f

    /** Day-over-day change as a percentage. */
    val deltaPercent: Float? get() =
        if (yesterdayValue > 0f) (todayValue - yesterdayValue) / yesterdayValue * 100f else null
}
