package xyz.draumaz.sustenance.ui.components

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import xyz.draumaz.sustenance.R
import java.time.Duration
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * How close together two logs of the same food need to be to prompt a check-in. DSM-5 caps a single
 * binge at "a discrete period of time (e.g., within any 2-hour period)", but a window that long also
 * catches ordinary repeats like a morning coffee and a second one before lunch. An hour keeps the
 * focus on going back for the same food again and again.
 */
val BINGE_CHECK_WINDOW: Duration = Duration.ofHours(1)

/** How long "log it anyway" has to be held down. */
private const val LOG_HOLD_MILLIS = 5_000

/** How quickly the fill drains back when the hold is let go early. */
private const val LOG_HOLD_RELEASE_MILLIS = 300

/**
 * A gentle pause shown when [foodName] has already been logged [priorLogs] times within
 * [BINGE_CHECK_WINDOW]. Shame tends to feed binge cycles, so this offers support and leaves the
 * choice to the user rather than warning them off.
 *
 * Logging anyway takes a [LOG_HOLD_MILLIS] hold rather than a tap: the user stays free to log,
 * but the pause the dialog asks for actually happens instead of being tapped straight through.
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
            // Intrinsic height so both buttons match when only one label wraps.
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalButton(
                    onClick = onHoldOff,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        stringResource(R.string.binge_check_hold_off),
                        textAlign = TextAlign.Center
                    )
                }
                HoldToConfirmButton(
                    onConfirm = onLog,
                    modifier = Modifier.weight(1f).fillMaxHeight()
                ) {
                    Text(
                        stringResource(R.string.binge_check_log),
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    )
}

/**
 * A button that fires [onConfirm] only after being held for [LOG_HOLD_MILLIS], filling left to right
 * as it goes. Letting go (or sliding off) early drains the fill, and pressing again resumes from
 * wherever the drain has got to at the same rate, so the full hold always takes the same time.
 *
 * The label is drawn twice, once over the empty track and once over the fill clipped to its width,
 * so it keeps its contrast where the fill edge crosses it. Progress is only read in the draw phase,
 * so the 5-second animation redraws without recomposing.
 *
 * While held, [HoldRattle] ticks along with the fill, ending on a firmer click when it logs.
 */
@Composable
private fun HoldToConfirmButton(
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val rattle = remember(context) { HoldRattle(context) }
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val shape = RoundedCornerShape(12.dp)
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .defaultMinSize(minHeight = ButtonDefaults.MinHeight)
            .clip(shape)
            .background(colors.primaryContainer)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val hold = scope.launch {
                        val ticking = launch { rattle.play { progress.value } }
                        val remaining = ((1f - progress.value) * LOG_HOLD_MILLIS).toInt()
                        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                        ticking.cancel()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        currentOnConfirm()
                    }
                    tryAwaitRelease()
                    if (hold.isActive) {
                        hold.cancel()
                        scope.launch {
                            progress.animateTo(0f, tween(LOG_HOLD_RELEASE_MILLIS))
                        }
                    }
                })
            },
        contentAlignment = Alignment.Center
    ) {
        HoldButtonLabel(colors.onPrimaryContainer, content)
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    clipRect(right = size.width * progress.value) {
                        this@drawWithContent.drawContent()
                    }
                }
                .background(colors.primary),
            contentAlignment = Alignment.Center
        ) {
            HoldButtonLabel(colors.onPrimary, content)
        }
    }
}

@Composable
private fun HoldButtonLabel(color: Color, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.padding(ButtonDefaults.ContentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides color, content = content)
    }
}

/** Gap between rattle ticks at the start of a hold, and at the end. */
private const val RATTLE_SLOW_MILLIS = 160f
private const val RATTLE_FAST_MILLIS = 35f

/**
 * Tick strength at the start of a hold; it rises to full by the end. Barely there on purpose:
 * the further it has to climb, the more the end of the hold presses on you.
 */
private const val RATTLE_MIN_SCALE = 0.04f

/** Length of a fallback pulse on motors without primitives: short enough to read as a tap. */
private const val RATTLE_PULSE_MILLIS = 8L

/**
 * The hold's haptics: a ratchet-like rattle of separate ticks rather than a continuous buzz, each
 * one crisper and closer to the last as the fill grows. Ticks are played one by one from a loop
 * that reads the live fill, so they stay in step with it however the hold was resumed, and
 * cancelling the loop stops them dead.
 *
 * Best is the TICK primitive, scaled per tick (the crisp clicks the system UI uses). Without it,
 * a few-millisecond pulse at a set amplitude, and failing that the fixed tick effect, where only
 * the quickening rhythm carries the build.
 */
private class HoldRattle(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        context.getSystemService(Vibrator::class.java)
    }?.takeIf { it.hasVibrator() }

    private val hasPrimitives = vibrator?.areAllPrimitivesSupported(
        VibrationEffect.Composition.PRIMITIVE_TICK
    ) == true

    private val hasAmplitude = vibrator?.hasAmplitudeControl() == true

    /** Rattles until cancelled, with strength and pace taken from [progress] (0 to 1). */
    suspend fun play(progress: () -> Float) {
        val vibrator = vibrator ?: return
        while (true) {
            // Cubic, so the first seconds stay sparse and faint and the build lands hard at the end.
            val p = progress().coerceIn(0f, 1f)
            val level = p * p * p
            vibrate(vibrator, tick(RATTLE_MIN_SCALE + level * (1f - RATTLE_MIN_SCALE)))
            delay(
                (RATTLE_SLOW_MILLIS + level * (RATTLE_FAST_MILLIS - RATTLE_SLOW_MILLIS)).roundToLong()
            )
        }
    }

    private fun tick(scale: Float): VibrationEffect = when {
        hasPrimitives -> VibrationEffect.startComposition()
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, scale)
            .compose()
        hasAmplitude -> VibrationEffect.createOneShot(
            RATTLE_PULSE_MILLIS,
            (scale * 255).roundToInt().coerceIn(1, 255)
        )
        else -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
    }

    private fun vibrate(vibrator: Vibrator, effect: VibrationEffect) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Touch usage, so turning off touch feedback in system settings also silences this.
            vibrator.vibrate(
                effect,
                VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH)
            )
        } else {
            vibrator.vibrate(effect)
        }
    }
}
