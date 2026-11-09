package xyz.draumaz.sustenance.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

// An amount with its unit, e.g. "25 g", "1,500 mg", "6%", "300 kcal" (and the Russian г/мг/ккал).
private val amountPattern = Regex("""\d[\d.,]*\s?(?:mg|g|%|kcal|мг|г|ккал)(?!\p{L})""")

/** Bolds every amount-with-unit, so the numbers stand out in explanatory copy. */
fun String.withBoldAmounts(): AnnotatedString = buildAnnotatedString {
    var last = 0
    amountPattern.findAll(this@withBoldAmounts).forEach { m ->
        append(substring(last, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.value) }
        last = m.range.last + 1
    }
    append(substring(last))
}
