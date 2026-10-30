package xyz.draumaz.sustenance.data

import java.util.Locale

/** Shared value formatting so the dashboard, summary and widget all read the same. */
fun Metric.formatValue(value: Float): String = when (this) {
    Metric.SODIUM -> String.format(Locale.getDefault(), "%,d", value.toLong())
    else -> String.format(Locale.getDefault(), "%,.0f", value)
}

/** Format a number with locale-aware thousands separators. */
fun formatNumber(value: Number): String = String.format(Locale.getDefault(), "%,.0f", value.toDouble())

/** Parses user-typed decimals, accepting a comma as the separator (as many locales' keyboards type). */
fun parseDecimal(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()
