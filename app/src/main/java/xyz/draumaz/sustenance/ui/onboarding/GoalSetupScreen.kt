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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import xyz.draumaz.sustenance.R
import xyz.draumaz.sustenance.data.Metric
import xyz.draumaz.sustenance.data.parseDecimal

private val energyMetrics = listOf(Metric.FOOD)
private val macroMetrics = listOf(Metric.PROTEIN, Metric.CARBS, Metric.FAT)
private val microMetrics = listOf(Metric.SATURATED_FAT, Metric.SUGAR, Metric.FIBER, Metric.SODIUM)

/**
 * The last step of first-time setup, shown after Health Connect is linked and before the
 * dashboard loads, so the user sets daily targets up front. "Not now" keeps [initial].
 */
@Composable
fun GoalSetupScreen(
    initial: Map<Metric, Float>,
    onSave: (Map<Metric, Float>) -> Unit,
    onSkip: () -> Unit,
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

        GoalSection(R.string.goal_setup_energy, energyMetrics, text)
        GoalSection(R.string.goal_setup_macros, macroMetrics, text)
        GoalSection(R.string.goal_setup_micros, microMetrics, text, isLast = true)

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
            Text(stringResource(R.string.goal_setup_skip))
        }
    }
}

/** A labelled run of rows grouped M3 Expressive-style: one rounded block, tight inner corners. */
@Composable
private fun GoalSection(
    titleRes: Int,
    metrics: List<Metric>,
    text: MutableMap<Metric, String>,
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
                            stringResource(if (metric.moreIsBetter) R.string.goal_setup_daily_goal else R.string.goal_setup_daily_limit),
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
            }
        }
    }
}

private fun Float.toInputText() = if (this % 1f == 0f) toInt().toString() else toString()
