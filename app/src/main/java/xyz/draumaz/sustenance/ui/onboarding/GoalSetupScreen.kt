package xyz.draumaz.sustenance.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.TrackChanges
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xyz.draumaz.sustenance.R
import xyz.draumaz.sustenance.data.Metric
import xyz.draumaz.sustenance.data.parseDecimal
import xyz.draumaz.sustenance.ui.theme.GoogleSansFlexRoundedFontFamily

private val energyMetrics = listOf(Metric.FOOD)
private val macroMetrics = listOf(Metric.PROTEIN, Metric.CARBS, Metric.FAT)
private val microMetrics = listOf(Metric.SATURATED_FAT, Metric.SUGAR, Metric.FIBER, Metric.SODIUM)

/** What each nutrient does for (or against) your health, shown under its row. */
private val blurbs = mapOf(
    Metric.FOOD to R.string.goal_blurb_food,
    Metric.PROTEIN to R.string.goal_blurb_protein,
    Metric.CARBS to R.string.goal_blurb_carbs,
    Metric.FAT to R.string.goal_blurb_fat,
    Metric.SATURATED_FAT to R.string.goal_blurb_saturated_fat,
    Metric.SUGAR to R.string.goal_blurb_sugar,
    Metric.FIBER to R.string.goal_blurb_fiber,
    Metric.SODIUM to R.string.goal_blurb_sodium,
)

/**
 * The last step of first-time setup, shown after Health Connect is linked and before the
 * dashboard loads, so the user sets daily targets up front. "Not now" keeps [initial].
 * Settings reopens it with [editing] set, which swaps "Not now" for "Cancel". The carb goal always
 * counts total carbs; [netCarbMode] adds a note saying so, since the dashboard then shows net carbs.
 */
@Composable
fun GoalSetupScreen(
    initial: Map<Metric, Float>,
    onSave: (Map<Metric, Float>) -> Unit,
    onSkip: () -> Unit,
    editing: Boolean = false,
    netCarbMode: Boolean = false,
) {
    val metrics = energyMetrics + macroMetrics + microMetrics
    val text = remember {
        mutableStateMapOf<Metric, String>().apply {
            metrics.forEach { m -> initial[m]?.let { put(m, it.toInputText()) } }
        }
    }
    val parsed = metrics.associateWith { text[it]?.let(::parseDecimal)?.toFloat() }
    val allValid = parsed.values.all { it != null }

    // Insets are padded inside the scroll so the list runs under the transparent system bars.
    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 24.dp)
            .padding(top = 32.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.TrackChanges, null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(36.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.goal_setup_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.goal_setup_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        GoalSection(R.string.goal_setup_energy, energyMetrics, text, netCarbMode)
        GoalSection(R.string.goal_setup_macros, macroMetrics, text, netCarbMode)
        GoalSection(R.string.goal_setup_micros, microMetrics, text, netCarbMode, isLast = true)

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { onSave(parsed.mapValues { it.value!! }) },
            enabled = allValid,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = CircleShape,
        ) {
            Text(stringResource(R.string.goal_setup_save), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (editing) R.string.cancel else R.string.goal_setup_skip))
        }
    }
}

/** A labelled run of rows grouped M3 Expressive-style: one rounded block, tight inner corners. */
@Composable
private fun GoalSection(
    titleRes: Int,
    metrics: List<Metric>,
    text: MutableMap<Metric, String>,
    netCarbMode: Boolean,
    isLast: Boolean = false,
) {
    val focusManager = LocalFocusManager.current
    Spacer(Modifier.height(24.dp))
    Text(
        stringResource(titleRes),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        metrics.forEachIndexed { i, metric ->
            val outer = 24.dp
            val inner = 6.dp
            val shape = RoundedCornerShape(
                topStart = if (i == 0) outer else inner,
                topEnd = if (i == 0) outer else inner,
                bottomStart = if (i == metrics.lastIndex) outer else inner,
                bottomEnd = if (i == metrics.lastIndex) outer else inner,
            )
            val lastField = isLast && i == metrics.lastIndex
            Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(metric.accent.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(metric.icon, null, tint = metric.accent, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(metric.titleRes),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                stringResource(
                                    when {
                                        metric == Metric.CARBS -> R.string.goal_setup_total_carbs
                                        metric.moreIsBetter -> R.string.goal_setup_daily_goal
                                        else -> R.string.goal_setup_daily_limit
                                    }
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        val value = text[metric].orEmpty()
                        OutlinedTextField(
                            value = value,
                            onValueChange = { text[metric] = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                            singleLine = true,
                            isError = parseDecimal(value) == null,
                            shape = MaterialTheme.shapes.large,
                            textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.End),
                            suffix = { Text(stringResource(metric.unitRes)) },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal,
                                imeAction = if (lastField) ImeAction.Done else ImeAction.Next,
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { focusManager.moveFocus(FocusDirection.Down) },
                                onDone = { focusManager.clearFocus() },
                            ),
                            modifier = Modifier.width(132.dp),
                        )
                    }
                    blurbs[metric]?.let { blurb ->
                        var body = stringResource(blurb)
                        if (netCarbMode && metric == Metric.CARBS) body += " " + stringResource(R.string.goal_setup_net_carb_note)
                        Text(
                            body.withBoldAmounts(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = GoogleSansFlexRoundedFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontFeatureSettings = null,
                                lineHeight = 22.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 18.dp),
                        )
                    }
                }
            }
        }
    }
}

// An amount with its unit, e.g. "25 g", "1,500 mg", "6%" (and the Russian г/мг).
private val amountPattern = Regex("""\d[\d.,]*\s?(?:mg|g|%|мг|г)(?!\p{L})""")

private fun String.withBoldAmounts() = buildAnnotatedString {
    var last = 0
    amountPattern.findAll(this@withBoldAmounts).forEach { m ->
        append(substring(last, m.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.value) }
        last = m.range.last + 1
    }
    append(substring(last))
}

private fun Float.toInputText() = if (this % 1f == 0f) toInt().toString() else toString()
