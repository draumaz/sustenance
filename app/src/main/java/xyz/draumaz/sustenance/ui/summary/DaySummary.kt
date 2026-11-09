package xyz.draumaz.sustenance.ui.summary

import androidx.annotation.StringRes
import androidx.health.connect.client.records.NutritionRecord
import xyz.draumaz.sustenance.data.Metric
import xyz.draumaz.sustenance.data.WeeklyStat
import xyz.draumaz.sustenance.util.DaySummaryText
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.time.Duration.Companion.minutes

/** The Gemini-written summary card at the top of Insights. */
sealed interface DaySummaryState {
    /** AI features are off, or nothing is logged yet, so there's no card at all. */
    data object Hidden : DaySummaryState
    data object Loading : DaySummaryState
    data class Ready(val summary: DaySummaryText) : DaySummaryState
    data class Failed(@param:StringRes val messageRes: Int) : DaySummaryState
}

/**
 * The facts Gemini summarises. [report] is the full prompt input; [materialKey] covers only what
 * should change the summary's content (food logs, goals, and which goals are over or short).
 */
internal data class DayReport(val report: String, val materialKey: String)

/**
 * Builds the plain-English facts for today's summary, or null when no food is logged (there's
 * nothing to explain yet). English regardless of the UI language: the prompt names the output
 * language separately, and fixed units keep the numbers unambiguous.
 */
internal fun buildDayReport(stats: List<WeeklyStat>, logs: List<NutritionRecord>, deficitGoal: Boolean): DayReport? {
    if (logs.isEmpty()) return null
    val goalLines = mutableListOf<String>()
    val statuses = mutableListOf<String>()
    for (stat in stats) {
        if (stat.goal <= 0f) continue
        val unit = unitFor(stat.metric)
        val value = stat.todayValue
        val goal = stat.goal
        val (line, status) = when (stat.metric) {
            Metric.CALORIC_BALANCE -> {
                // Positive is a deficit (burned more than eaten); the goal is the deficit to reach.
                val today = if (value >= 0f) "deficit of ${num(value)} $unit" else "surplus of ${num(abs(value))} $unit"
                val status = if (value >= goal) "reached" else "short by ${num(goal - value)} $unit"
                "Calorie deficit (calories burned minus eaten), a target to reach: today a $today, goal a deficit of ${num(goal)} $unit. Status: $status." to status.substringBefore(" by")
            }
            else -> {
                val target = stat.metric.moreIsBetter
                val status = when {
                    target && value >= goal -> "reached"
                    target -> "short by ${num(goal - value)} $unit"
                    value > goal -> "over by ${num(value - goal)} $unit"
                    else -> "within the limit"
                }
                val kind = if (target) "a target to reach" else "a limit to stay under"
                val goalNote = if (stat.metric == Metric.FOOD && deficitGoal) {
                    " (set from calories burned minus the deficit goal, so it rises as they move)"
                } else ""
                "${labelFor(stat.metric)}, $kind: today ${num(value)} $unit, goal ${num(goal)} $unit$goalNote. Status: $status." to status.substringBefore(" by")
            }
        }
        goalLines += "- $line"
        statuses += "${stat.metric.key}=$status"
    }

    val zone = ZoneId.systemDefault()
    val time = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    val foodLines = logs.sortedBy { it.startTime }.map { log ->
        val parts = listOfNotNull(
            log.energy?.inKilocalories?.let { "${num(it)} kcal" },
            log.protein?.inGrams?.let { "protein ${num(it)} g" },
            log.totalCarbohydrate?.inGrams?.let { "carbs ${num(it)} g" },
            log.totalFat?.inGrams?.let { "fat ${num(it)} g" },
            log.saturatedFat?.inGrams?.let { "saturated fat ${num(it)} g" },
            log.sugar?.inGrams?.let { "sugar ${num(it)} g" },
            log.dietaryFiber?.inGrams?.let { "fibre ${num(it)} g" },
            log.sodium?.inMilligrams?.let { "sodium ${num(it)} mg" },
        )
        "- ${time.format(log.startTime.atZone(zone))} ${log.name ?: "Unnamed food"}: ${parts.joinToString()}"
    }

    val report = buildString {
        appendLine("Goals:")
        goalLines.forEach(::appendLine)
        appendLine()
        appendLine("Food logged today:")
        foodLines.forEach(::appendLine)
    }.trim()
    val logKey = logs.sortedBy { it.startTime }.joinToString("|") {
        "${it.startTime.toEpochMilli()}:${it.name}:${it.energy?.inKilocalories}"
    }
    return DayReport(report, "$logKey#${statuses.joinToString()}")
}

private fun num(value: Number): String = String.format(Locale.US, "%.0f", value.toDouble())

private fun unitFor(metric: Metric): String = when (metric) {
    Metric.TOTAL_CALORIES, Metric.FOOD, Metric.CALORIC_BALANCE -> "kcal"
    Metric.SODIUM -> "mg"
    else -> "g"
}

private fun labelFor(metric: Metric): String = when (metric) {
    Metric.TOTAL_CALORIES -> "Calories burned (activity and resting)"
    Metric.FOOD -> "Calories eaten"
    Metric.CALORIC_BALANCE -> "Calorie deficit"
    Metric.PROTEIN -> "Protein"
    Metric.CARBS -> "Carbs"
    Metric.FAT -> "Fat"
    Metric.SATURATED_FAT -> "Saturated fat"
    Metric.SODIUM -> "Sodium"
    Metric.SUGAR -> "Sugar"
    Metric.FIBER -> "Fibre"
}

/**
 * Last successful summary, process-wide so leaving and reopening Insights doesn't spend another
 * request (free-tier keys get only a few hundred a day). Memory only, on purpose: it's a digest
 * of the user's eating, and keeping it off disk keeps it out of backups too.
 */
internal object DaySummaryCache {
    private data class Entry(
        val date: LocalDate,
        val model: String,
        val language: String,
        val report: DayReport,
        val summary: DaySummaryText,
        val atMillis: Long,
    )

    /**
     * Calories burned tick up all day, so the report changes on most polls. Within this window
     * only a material change (a log, a goal, a goal flipping over/under) asks for a new summary.
     */
    private val STALE_AFTER = 30.minutes

    @Volatile private var entry: Entry? = null

    /** The cached text if it still describes [report] well enough, else null. */
    fun lookup(report: DayReport, model: String, language: String, nowMillis: Long): DaySummaryText? {
        val e = entry ?: return null
        if (e.date != LocalDate.now() || e.model != model || e.language != language) return null
        if (e.report == report) return e.summary
        val fresh = nowMillis - e.atMillis < STALE_AFTER.inWholeMilliseconds
        return if (fresh && e.report.materialKey == report.materialKey) e.summary else null
    }

    fun store(report: DayReport, model: String, language: String, summary: DaySummaryText, nowMillis: Long) {
        entry = Entry(LocalDate.now(), model, language, report, summary, nowMillis)
    }
}
