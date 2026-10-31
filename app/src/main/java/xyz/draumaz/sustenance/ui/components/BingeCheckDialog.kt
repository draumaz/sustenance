package xyz.draumaz.sustenance.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import xyz.draumaz.sustenance.R
import java.time.Duration

/**
 * How close together two logs of the same food need to be to prompt a check-in. DSM-5 caps a single
 * binge at "a discrete period of time (e.g., within any 2-hour period)", but a window that long also
 * catches ordinary repeats like a morning coffee and a second one before lunch. An hour keeps the
 * focus on going back for the same food again and again.
 */
val BINGE_CHECK_WINDOW: Duration = Duration.ofHours(1)

/**
 * A gentle pause shown when [foodName] has already been logged [priorLogs] times within
 * [BINGE_CHECK_WINDOW]. Shame tends to feed binge cycles, so this offers support and leaves the
 * choice to the user rather than warning them off.
 *
 * @param onLog log the food as planned.
 * @param onHoldOff close the review without logging.
 * @param onDismiss go back to the review dialog.
 */
@Composable
fun BingeCheckDialog(
    foodName: String,
    priorLogs: Int,
    onLog: () -> Unit,
    onHoldOff: () -> Unit,
    onDismiss: () -> Unit,
) {
    val repeat = pluralStringResource(R.plurals.binge_check_repeat, priorLogs, foodName, priorLogs)
    val nameStart = repeat.indexOf(foodName)
    val repeatText = if (nameStart < 0) AnnotatedString(repeat) else AnnotatedString(
        repeat,
        listOf(
            AnnotatedString.Range(
                SpanStyle(fontWeight = FontWeight.SemiBold),
                nameStart,
                nameStart + foodName.length
            )
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        icon = {
            Icon(
                Icons.Rounded.Spa,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(stringResource(R.string.binge_check_title), textAlign = TextAlign.Center)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    repeatText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.binge_check_no_judgement) + " " +
                        stringResource(R.string.binge_check_suggestion),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Text(
                    stringResource(R.string.binge_check_reassurance),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onHoldOff,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.binge_check_hold_off))
                }
                Button(
                    onClick = onLog,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.binge_check_log))
                }
            }
        }
    )
}
