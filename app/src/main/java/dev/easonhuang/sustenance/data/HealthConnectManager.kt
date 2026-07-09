package dev.easonhuang.sustenance.data

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.reflect.KClass

/**
 * Thin, read-only wrapper around Health Connect. Knows how to turn each [Metric] into a dashboard
 * summary or a full detail series. All public reads swallow per-metric errors so one missing data
 * type never blanks the whole dashboard.
 */
class HealthConnectManager(private val context: Context) {

    private val zone: ZoneId get() = ZoneId.systemDefault()
    private val dowFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE")
    private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
    private val timeFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    val client: HealthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    /** Per-metric data-type read permissions (used for the dashboard's per-tile lock state). */
    val metricPermissions: Set<String> =
        Metric.entries.map { HealthPermission.getReadPermission(recordClass(it)) }.toSet()

    /** Extra access requested only after data permissions are held (HC requires a separate step). */
    val extraPermissions: Set<String> = setOf(PERMISSION_READ_IN_BACKGROUND, PERMISSION_READ_HISTORY)

    /** Everything we request: the data types plus background + history access. */
    val permissions: Set<String> = metricPermissions + extraPermissions

    fun availability(): Int = HealthConnectClient.getSdkStatus(context)

    val isAvailable: Boolean get() = availability() == HealthConnectClient.SDK_AVAILABLE

    suspend fun grantedPermissions(): Set<String> =
        client.permissionController.getGrantedPermissions()

    fun permissionFor(metric: Metric): String =
        HealthPermission.getReadPermission(recordClass(metric))

    suspend fun isGranted(metric: Metric): Boolean {
        val granted = runCatching { grantedPermissions() }.getOrDefault(emptySet())
        return if (metric == Metric.CALORIC_BALANCE) {
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted &&
                    HealthPermission.getReadPermission(NutritionRecord::class) in granted
        } else {
            permissionFor(metric) in granted
        }
    }

    // ---- Dashboard -----------------------------------------------------------------------------

    suspend fun readDashboard(
        goals: Map<Metric, Float> = emptyMap(),
        isKeto: Boolean = false
    ): List<MetricSummary> {
        val granted = runCatching { grantedPermissions() }.getOrDefault(emptySet())
        val rawSummaries = Metric.entries.map { metric ->
            val has = granted.contains(permissionFor(metric))
            val goal = goals[metric]
            if (!has) {
                MetricSummary(metric, "-", null, hasData = false, granted = false, goal = goal)
            } else {
                runCatching { summarize(metric, goal) }
                    .getOrElse { MetricSummary(metric, "-", "No data", hasData = false, granted = true, goal = goal) }
            }
        }

        if (!isKeto) return rawSummaries

        // Keto logic: Transform Carbs into Net Carbs (Carbs - Fiber)
        val fiberSummary = rawSummaries.find { it.metric == Metric.FIBER }
        val carbsSummary = rawSummaries.find { it.metric == Metric.CARBS }

        if (carbsSummary != null && fiberSummary != null && carbsSummary.granted && fiberSummary.granted) {
            val fiberVal = fiberSummary.spark.lastOrNull() ?: 0f
            val carbsVal = carbsSummary.spark.lastOrNull() ?: 0f
            val netCarbs = (carbsVal - fiberVal).coerceAtLeast(0f)
            val goal = goals[Metric.CARBS]

            val displayValue = if (goal != null && goal > 0) {
                val diff = netCarbs - goal
                val absDiff = if (diff < 0) -diff else diff
                val label = if (diff >= 0) "over" else "left"
                "${Metric.CARBS.formatValue(netCarbs)}${Metric.CARBS.unit} (${Metric.CARBS.formatValue(absDiff)} $label)"
            } else "${Metric.CARBS.formatValue(netCarbs)}${Metric.CARBS.unit}"

            val netCarbsSummary = carbsSummary.copy(
                titleOverride = "Net Carbs",
                value = displayValue,
                spark = carbsSummary.spark.zip(fiberSummary.spark.ifEmpty { List(carbsSummary.spark.size) { 0f } }) { c, f -> (c - f).coerceAtLeast(0f) }
            )

            return rawSummaries.map { if (it.metric == Metric.CARBS) netCarbsSummary else it }
        }

        return rawSummaries
    }

    private suspend fun summarize(metric: Metric, goal: Float?): MetricSummary {
        val points = series(metric, days = 7)
        if (points.isEmpty()) {
            return MetricSummary(metric, "-", "No data", hasData = false, granted = true, goal = goal)
        }
        val spark = points.map { it.value }
        return when (metric.kind) {
            MetricKind.DAILY_TOTAL -> {
                val today = points.last().value
                val yesterday = if (points.size >= 2) points[points.size - 2].value else 0f
                val displayValue = when {
                    metric == Metric.CALORIC_BALANCE -> {
                        when {
                            today < 0 -> "${metric.formatValue(-today)}${metric.unit} surplus"
                            today > 0 -> "${metric.formatValue(today)}${metric.unit} deficit"
                            else -> "0${metric.unit}"
                        }
                    }
                    metric == Metric.TOTAL_CALORIES -> "${metric.formatValue(today)}${metric.unit}"
                    goal != null -> {
                        val diff = today - goal
                        val absDiff = if (diff < 0) -diff else diff
                        val label = if (diff >= 0) "over" else "left"
                        "${metric.formatValue(today)}${metric.unit} (${metric.formatValue(absDiff)} $label)"
                    }
                    else -> "${metric.formatValue(today)}${metric.unit}"
                }

                val displayCaption = if (metric == Metric.CALORIC_BALANCE) {
                    val absYesterday = if (yesterday < 0) -yesterday else yesterday
                    val label = if (yesterday < 0) "surplus" else "deficit"
                    "Yesterday: ${metric.formatValue(absYesterday)}${metric.unit} $label"
                } else {
                    "Yesterday: ${metric.formatValue(yesterday)}${metric.unit}"
                }

                MetricSummary(
                    metric = metric,
                    value = displayValue,
                    caption = displayCaption,
                    hasData = true,
                    granted = true,
                    spark = spark,
                    goal = goal,
                )
            }
            MetricKind.LATEST -> {
                val last = points.last()
                MetricSummary(
                    metric = metric,
                    value = "${metric.formatValue(last.value)}${metric.unit}",
                    caption = "as of ${dayFmt.format(last.time.atZone(zone))}",
                    hasData = true,
                    granted = true,
                    spark = spark,
                    goal = goal,
                )
            }
        }
    }

    // ---- Detail --------------------------------------------------------------------------------

    suspend fun readDetail(metric: Metric, goal: Float? = null): MetricDetail = runCatching {
        val days = if (metric.kind == MetricKind.DAILY_TOTAL) 14 else 90
        val points = series(metric, days)
        if (points.isEmpty()) {
            return MetricDetail(metric, "-", "No data recorded", emptyList(), goal = goal)
        }
        val values = points.map { it.value }
        val headline: String
        val caption: String
        val stats: List<Pair<String, String>>
        if (metric.kind == MetricKind.DAILY_TOTAL) {
            headline = metric.formatValue(points.last().value)
            caption = "Today, ${metric.unit}"
            stats = listOf(
                "Daily avg" to "${metric.formatValue(values.average().toFloat())} ${metric.unit}",
                "Best day" to "${metric.formatValue(values.max())} ${metric.unit}",
                "14-day total" to "${metric.formatValue(values.sum())} ${metric.unit}",
            )
        } else {
            headline = "${metric.formatValue(points.last().value)} ${metric.unit}"
            caption = "Latest, ${dayFmt.format(points.last().time.atZone(zone))}"
            stats = listOf(
                "Average" to "${metric.formatValue(values.average().toFloat())} ${metric.unit}",
                "Min" to "${metric.formatValue(values.min())} ${metric.unit}",
                "Max" to "${metric.formatValue(values.max())} ${metric.unit}",
            )
        }
        val recent = points.takeLast(20).reversed().map {
            RecordRow("${metric.formatValue(it.value)} ${metric.unit}", dayMonthTime(it.time))
        }

        val todaySections = if (metric == Metric.FOOD) {
            val start = LocalDate.now().atStartOfDay(zone).toInstant()
            val end = Instant.now()
            val records = read(NutritionRecord::class, start, end).filterIsInstance<NutritionRecord>()
            val grouped = records.groupBy { r ->
                val hour = r.startTime.atZone(zone).hour
                when (hour) {
                    in 5..11 -> "Morning"
                    in 12..17 -> "Day"
                    else -> "Night"
                }
            }
            listOf("Morning", "Day", "Night").mapNotNull { section ->
                grouped[section]?.let { recs ->
                    section to recs.sortedBy { it.startTime }.map { r ->
                        val name = r.name ?: "Unknown Food"
                        val kcal = r.energy?.inKilocalories ?: 0.0
                        val time = timeFmt.format(r.startTime.atZone(zone))
                        RecordRow(name, "${"%.0f".format(kcal)} kcal • $time")
                    }
                }
            }
        } else emptyList()

        MetricDetail(metric, headline, caption, points, stats, recent, todaySections, goal = goal)
    }.getOrElse {
        val msg = if (isGranted(metric)) "Error reading data" else "Permission not granted"
        MetricDetail(metric, "-", msg, emptyList(), emptyList(), emptyList(), emptyList(), goal = goal)
    }

    // ---- Public series for summary & export ----------------------------------------------------

    /** Daily totals over [days] for a [MetricKind.DAILY_TOTAL] metric; empty otherwise. */
    suspend fun readDailySeries(metric: Metric, days: Int): List<SeriesPoint> =
        if (metric.kind == MetricKind.DAILY_TOTAL) series(metric, days) else emptyList()

    /** Flat list of points for export, with finer granularity for sampled metrics. */
    suspend fun exportRows(metric: Metric, days: Int): List<SeriesPoint> = series(metric, days)

    // ---- Series builders -----------------------------------------------------------------------

    private suspend fun series(metric: Metric, days: Int): List<SeriesPoint> =
        when (metric.kind) {
            MetricKind.DAILY_TOTAL -> when (metric) {
                Metric.CALORIC_BALANCE -> caloricBalanceSeries(days)
                else -> dailyTotalSeries(metric, days)
            }
            MetricKind.LATEST -> latestSeries(metric, days)
        }

    private suspend fun caloricBalanceSeries(days: Int): List<SeriesPoint> {
        val energy = dailyTotalSeries(Metric.TOTAL_CALORIES, days)
        val food = dailyTotalSeries(Metric.FOOD, days)
        if (energy.isEmpty() && food.isEmpty()) return emptyList()

        return (0 until days).map { i ->
            val date = LocalDate.now().minusDays((days - 1 - i).toLong())
            val label = dowFmt.format(date)
            val eVal = energy.getOrNull(i)?.value ?: 0f
            val fVal = food.getOrNull(i)?.value ?: 0f
            SeriesPoint(date.atStartOfDay(zone).toInstant(), eVal - fVal, label)
        }.let { pts -> if (pts.all { it.value == 0f }) emptyList() else pts }
    }

    private suspend fun dailyTotalSeries(metric: Metric, days: Int): List<SeriesPoint> {
        val agg = aggregateMetricFor(metric) ?: return emptyList()
        val start = LocalDate.now().minusDays((days - 1).toLong()).atStartOfDay()
        val end = LocalDateTime.now()
        val buckets = client.aggregateGroupByPeriod(
            AggregateGroupByPeriodRequest(
                metrics = setOf(agg),
                timeRangeFilter = TimeRangeFilter.between(start, end),
                timeRangeSlicer = Period.ofDays(1),
            )
        )
        val byDate = buckets.associate { it.startTime.toLocalDate() to extract(it.result, metric) }
        return (0 until days).map { i ->
            val date = LocalDate.now().minusDays((days - 1 - i).toLong())
            val instant = date.atStartOfDay(zone).toInstant()
            SeriesPoint(instant, byDate[date] ?: 0f, dowFmt.format(date))
        }.let { pts ->
            if (pts.all { it.value == 0f } && buckets.isEmpty()) emptyList() else pts
        }
    }

    private suspend fun latestSeries(metric: Metric, days: Int): List<SeriesPoint> {
        val recs = read(recordClass(metric), daysAgoStart(days), Instant.now())
        return recs.mapNotNull { r ->
            val t = recordTime(r) ?: return@mapNotNull null
            val v = latestValue(metric, r) ?: return@mapNotNull null
            SeriesPoint(t, v, dayFmt.format(t.atZone(zone)))
        }.sortedBy { it.time }
    }

    // ---- Low-level reads -----------------------------------------------------------------------

    private suspend fun read(type: KClass<out Record>, start: Instant, end: Instant): List<Record> =
        client.readRecords(
            ReadRecordsRequest(recordType = type, timeRangeFilter = TimeRangeFilter.between(start, end))
        ).records

    private fun daysAgoStart(n: Int): Instant =
        LocalDate.now().minusDays(n.toLong()).atStartOfDay(zone).toInstant()

    private fun recordTime(r: Record): Instant? = when (r) {
        is NutritionRecord -> r.startTime
        is TotalCaloriesBurnedRecord -> r.startTime
        else -> null
    }

    private fun latestValue(metric: Metric, r: Record): Float? = when (metric) {
        Metric.TOTAL_CALORIES -> (r as? TotalCaloriesBurnedRecord)?.energy?.inKilocalories?.toFloat()
        Metric.FOOD -> (r as? NutritionRecord)?.energy?.inKilocalories?.toFloat()
        Metric.FIBER -> (r as? NutritionRecord)?.dietaryFiber?.inGrams?.toFloat()
        Metric.CARBS -> (r as? NutritionRecord)?.totalCarbohydrate?.inGrams?.toFloat()
        Metric.PROTEIN -> (r as? NutritionRecord)?.protein?.inGrams?.toFloat()
        Metric.FAT -> (r as? NutritionRecord)?.totalFat?.inGrams?.toFloat()
        Metric.SATURATED_FAT -> (r as? NutritionRecord)?.saturatedFat?.inGrams?.toFloat()
        Metric.SODIUM -> (r as? NutritionRecord)?.sodium?.inMilligrams?.toFloat()
        Metric.SUGAR -> (r as? NutritionRecord)?.sugar?.inGrams?.toFloat()
        else -> null
    }

    // ---- Catalog glue --------------------------------------------------------------------------

    private fun recordClass(metric: Metric): KClass<out Record> = when (metric) {
        Metric.TOTAL_CALORIES -> TotalCaloriesBurnedRecord::class
        Metric.CALORIC_BALANCE -> TotalCaloriesBurnedRecord::class // Derived, but need a class for permissions set
        Metric.FOOD,
        Metric.FIBER,
        Metric.CARBS,
        Metric.PROTEIN,
        Metric.FAT,
        Metric.SATURATED_FAT,
        Metric.SODIUM,
        Metric.SUGAR -> NutritionRecord::class
    }

    private fun aggregateMetricFor(metric: Metric) = when (metric) {
        Metric.TOTAL_CALORIES -> TotalCaloriesBurnedRecord.ENERGY_TOTAL
        Metric.FOOD -> NutritionRecord.ENERGY_TOTAL
        Metric.FIBER -> NutritionRecord.DIETARY_FIBER_TOTAL
        Metric.CARBS -> NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL
        Metric.PROTEIN -> NutritionRecord.PROTEIN_TOTAL
        Metric.FAT -> NutritionRecord.TOTAL_FAT_TOTAL
        Metric.SATURATED_FAT -> NutritionRecord.SATURATED_FAT_TOTAL
        Metric.SODIUM -> NutritionRecord.SODIUM_TOTAL
        Metric.SUGAR -> NutritionRecord.SUGAR_TOTAL
        else -> null
    }

    private fun extract(result: androidx.health.connect.client.aggregate.AggregationResult, metric: Metric): Float =
        when (metric) {
            Metric.TOTAL_CALORIES -> (result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0).toFloat()
            Metric.FOOD -> (result[NutritionRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0).toFloat()
            Metric.FIBER -> (result[NutritionRecord.DIETARY_FIBER_TOTAL]?.inGrams ?: 0.0).toFloat()
            Metric.CARBS -> (result[NutritionRecord.TOTAL_CARBOHYDRATE_TOTAL]?.inGrams ?: 0.0).toFloat()
            Metric.PROTEIN -> (result[NutritionRecord.PROTEIN_TOTAL]?.inGrams ?: 0.0).toFloat()
            Metric.FAT -> (result[NutritionRecord.TOTAL_FAT_TOTAL]?.inGrams ?: 0.0).toFloat()
            Metric.SATURATED_FAT -> (result[NutritionRecord.SATURATED_FAT_TOTAL]?.inGrams ?: 0.0).toFloat()
            Metric.SODIUM -> (result[NutritionRecord.SODIUM_TOTAL]?.inMilligrams ?: 0.0).toFloat()
            Metric.SUGAR -> (result[NutritionRecord.SUGAR_TOTAL]?.inGrams ?: 0.0).toFloat()
            else -> 0f
        }

    private fun dayMonthTime(t: Instant): String {
        val z = t.atZone(zone)
        return "${dayFmt.format(z)}, ${DateTimeFormatter.ofPattern("h:mm a").format(z)}"
    }

    companion object {
        // Reading from a background context (widgets, WorkManager) is blocked by Health Connect
        // without this. Older-than-30-days reads need the history permission.
        const val PERMISSION_READ_IN_BACKGROUND = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"
        const val PERMISSION_READ_HISTORY = "android.permission.health.READ_HEALTH_DATA_HISTORY"
    }
}
