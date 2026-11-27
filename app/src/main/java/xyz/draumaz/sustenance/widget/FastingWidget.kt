package xyz.draumaz.sustenance.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.annotation.ColorRes
import androidx.compose.ui.graphics.Color
import android.text.format.DateFormat
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.color.ColorProvider
import androidx.glance.unit.ColorProvider as GlanceColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import xyz.draumaz.sustenance.MainActivity
import xyz.draumaz.sustenance.R
import xyz.draumaz.sustenance.SustenanceApp
import java.time.Duration
import java.time.Instant
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** What the fasting widget needs to draw; [lastLog] null means unknown or nothing logged. */
internal data class FastState(val lastLog: Instant?, val goalHours: Float, val now: Instant) {
    val elapsed: Duration get() = lastLog?.let { Duration.between(it, now).coerceAtLeast(Duration.ZERO) } ?: Duration.ZERO
    val progress: Float
        get() = if (lastLog == null || goalHours <= 0f) 0f
        else (elapsed.toMillis() / (goalHours * 3_600_000f)).coerceIn(0f, 1f)
    val goalReached: Boolean get() = lastLog != null && progress >= 1f
}

/**
 * Home-screen fasting timer: the whole widget fills left to right as the current fast approaches
 * the fasting goal. The elapsed time is a launcher-ticked TextClock (see [ElapsedClock]), so it
 * stays right to the minute between renders; the fill only moves when the widget re-renders (the shared 30-minute refresh, plus every food log
 * and dashboard refresh), which suits a bar that's meant to creep in over many hours anyway.
 */
class FastingWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as SustenanceApp
        val settings = app.settings
        val initialLastLog = readLastLog(app, settings.fastBreakingCalories.first())
        val initialGoal = settings.fastingGoalHours.first()

        provideContent {
            // Glance runs provideGlance once per session and keeps the session composing for a
            // while after each update, so values read up there stay stale for any update() that
            // lands inside it: opening the app refreshes the widget, so an edit made in the app
            // right after always hit a live session and never showed. Everything that moves the
            // widget is followed here instead, so the open session recomposes as soon as it
            // changes; a fresh session reads the same values in provideGlance as before.
            val goalHours by settings.fastingGoalHours.collectAsState(initialGoal)
            val lastLog by remember { lastLogUpdates(app) }.collectAsState(initialLastLog)
            // The fill is relative to now, so take the time anew whenever an input changes.
            val state = remember(lastLog, goalHours) { FastState(lastLog, goalHours, Instant.now()) }
            LaunchedEffect(state.lastLog) { scheduleDayRollover(context, state.lastLog, state.now) }
            FastingContent(state)
        }
    }
}

/**
 * The last meal at the fast-breaking threshold. Without the background-read permission the live
 * read fails (returns null), so fall back to the last time the dashboard saw, which uses the
 * same threshold.
 */
private suspend fun readLastLog(app: SustenanceApp, threshold: Int): Instant? {
    val live = if (app.healthConnect.isAvailable) app.healthConnect.readLastFoodLogTime(threshold.toDouble()) else null
    return live ?: app.settings.getCachedLastLogTime()
}

/**
 * Re-reads the last meal when the fast-breaking threshold changes (a smaller snack may no longer
 * count) or Health Connect reports new data (our own food logs at once, other apps' within its
 * 30-second poll). The first combination is skipped: provideGlance has just read exactly that.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private fun lastLogUpdates(app: SustenanceApp): Flow<Instant?> =
    combine(
        app.settings.fastBreakingCalories.distinctUntilChanged(),
        app.healthConnect.changes.onStart { emit(Unit) },
    ) { threshold, _ -> threshold }
        .drop(1)
        .mapLatest { readLastLog(app, it) }

/**
 * Aspect ratio and resting-edge offset of widget_fasting_bubbles (viewport 24x120, resting
 * meniscus at x=8). Scaling the strip's width with widget height keeps the bubbles circular
 * across compact, medium, and large placements.
 */
private const val BUBBLE_EDGE_ASPECT = 24f / 120f
private const val BUBBLE_EDGE_FILL_FRACTION = 8f / 24f

/** Internal so tests can render it at a given size without Health Connect. */
@Composable
internal fun FastingContent(state: FastState) {
    val context = LocalContext.current
    val size = LocalSize.current
    val textColor = context.dayNight(R.color.widget_fasting_text)
    val compact = size.height < 72.dp
    // The lock-screen hub (and 4x2+ home-screen placements) hand us roughly 400x250dp: there the
    // medium layout is a small block lost in the middle, so spread it out and scale the clock.
    val large = size.height >= 140.dp && size.width >= 220.dp

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(context.dayNight(R.color.widget_fasting_track))
            .cornerRadius(if (large) 24.dp else 20.dp)
            .clickable(
                actionStartActivity(
                    Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                ),
            ),
    ) {
        val fillWidth = size.width * state.progress
        if (state.lastLog != null && state.progress > 0f && state.progress < 1f) {
            // A fast in progress: solid fill, then the constant bubbling liquid edge (see
            // widget_fasting_bubbles). The resting meniscus sits a third of the way into the
            // strip, so the solid box stops that far short of the progress point.
            val edgeWidth = (size.height * BUBBLE_EDGE_ASPECT).coerceIn(10.dp, 48.dp)
            Row(GlanceModifier.fillMaxHeight()) {
                val boxWidth = fillWidth - edgeWidth * BUBBLE_EDGE_FILL_FRACTION
                if (boxWidth > 0.dp) {
                    Box(
                        GlanceModifier
                            .width(boxWidth)
                            .fillMaxHeight()
                            .background(context.dayNight(R.color.widget_fasting_fill)),
                    ) {}
                }
                AndroidRemoteViews(
                    RemoteViews(context.packageName, R.layout.widget_fasting_bubble_edge),
                    GlanceModifier.width(edgeWidth).fillMaxHeight(),
                )
            }
        } else if (fillWidth > 0.dp) {
            // Goal reached: a full, still fill.
            Box(
                GlanceModifier
                    .width(fillWidth)
                    .fillMaxHeight()
                    .background(context.dayNight(R.color.widget_fasting_fill)),
            ) {}
        }

        val footer = footerText(context, state)
        if (compact) {
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Elapsed(state, fontSize = if (size.height < 52.dp) 16.sp else 19.sp)
                // H:MM:SS at this size already takes most of a 2-cell-wide widget.
                if (size.width >= 180.dp) {
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        text = state.goalLabel(context),
                        maxLines = 1,
                        style = TextStyle(color = textColor, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    )
                }
            }
        } else if (large) {
            Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 20.dp)) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = context.getString(R.string.time_since_last_ate),
                        maxLines = 1,
                        style = TextStyle(color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    if (state.lastLog != null) {
                        Text(
                            text = percentText(context, state),
                            maxLines = 1,
                            style = TextStyle(color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        )
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                // Sized so the widest reading ("1d 23h 59m") still fits on one line.
                val clockSp = minOf(size.height.value * 0.22f, size.width.value * 0.13f).coerceIn(28f, 56f)
                Elapsed(state, fontSize = clockSp.sp)
                Spacer(GlanceModifier.defaultWeight())
                Text(
                    text = footer,
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = TextStyle(color = textColor, fontSize = 14.sp),
                )
            }
        } else {
            Column(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(R.string.time_since_last_ate),
                    maxLines = 1,
                    style = TextStyle(color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                )
                Spacer(GlanceModifier.height(2.dp))
                Elapsed(state, fontSize = if (size.height >= 110.dp && size.width >= 160.dp) 32.sp else 24.sp)
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    text = footer,
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth(),
                    style = TextStyle(color = textColor, fontSize = 11.sp),
                )
            }
        }
    }
}

@Composable
private fun Elapsed(state: FastState, fontSize: TextUnit) {
    val context = LocalContext.current
    if (state.lastLog == null) {
        Text(
            text = context.getString(R.string.widget_fasting_no_data),
            maxLines = 1,
            style = TextStyle(
                color = context.dayNight(R.color.widget_fasting_text),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        return
    }
    val clock = ElapsedClock.of(state.lastLog, state.now)
    val pattern = if (clock.days > 0) context.getString(R.string.widget_fasting_clock_days, clock.days)
    else context.getString(R.string.widget_fasting_clock)
    val views = RemoteViews(context.packageName, R.layout.widget_fasting_clock).apply {
        // Both formats, since TextClock picks one by the device's 12/24-hour setting.
        setCharSequence(R.id.fasting_clock, "setFormat12Hour", pattern)
        setCharSequence(R.id.fasting_clock, "setFormat24Hour", pattern)
        setString(R.id.fasting_clock, "setTimeZone", clock.timeZoneId)
        setTextViewTextSize(R.id.fasting_clock, TypedValue.COMPLEX_UNIT_SP, fontSize.value)
    }
    // Glance's host box for embedded RemoteViews ignores the layout's wrap_content and takes all
    // the remaining height, pushing the footer out of the widget, so size it to one line here.
    val fontScale = context.resources.configuration.fontScale
    AndroidRemoteViews(views, GlanceModifier.height((fontSize.value * fontScale * 1.3f).dp))
}

private fun FastState.goalLabel(context: Context): String =
    if (goalReached) context.getString(R.string.widget_fasting_goal_reached)
    else context.getString(
        R.string.widget_fasting_goal,
        if (goalHours % 1f == 0f) goalHours.toInt().toString() else goalHours.toString(),
    )

/** The progress, plus when the goal will be met while the fast is still short of it. */
private fun percentText(context: Context, state: FastState): String {
    val percent = (state.progress * 100).toInt()
    val lastLog = state.lastLog
    if (lastLog == null || state.goalReached) return context.getString(R.string.widget_fasting_percent, percent)
    val goalMet = lastLog.plusMillis((state.goalHours * 3_600_000f).toLong())
    // Follows the device's 12/24-hour setting, like the footer's "since" time.
    val at = DateFormat.getTimeFormat(context).format(Date.from(goalMet))
    return context.getString(R.string.widget_fasting_percent_eta, percent, at)
}

private fun footerText(context: Context, state: FastState): String {
    val lastLog = state.lastLog ?: return state.goalLabel(context)
    // Follows the device's 12/24-hour setting, like the dashboard's fasting timer.
    val since = DateFormat.getTimeFormat(context).format(Date.from(lastLog))
    return "${state.goalLabel(context)} · ${context.getString(R.string.widget_fasting_since, since)}"
}

/**
 * Glance 1.1 keeps resource-backed ColorProviders library-internal, so resolve the color
 * resource (dynamic system colors on API 31+) under both night modes and hand Glance the pair.
 * The palette is baked in at render time, so a wallpaper change shows on the next refresh.
 */
private fun Context.dayNight(@ColorRes id: Int): GlanceColor {
    fun resolve(night: Boolean): Color {
        val config = Configuration(resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        }
        return Color(createConfigurationContext(config).getColor(id))
    }
    return ColorProvider(day = resolve(false), night = resolve(true))
}

/**
 * A TextClock can only show the current time, so it's given a time zone offset by minus the last
 * meal's time of day: its "time" is then the time since that meal, ticking once a minute with no
 * renders. That wraps every 24 hours, so whole days are baked into the pattern at render time
 * ([days]) and [scheduleDayRollover] re-renders at the next day boundary. Works in whole minutes:
 * the minute flips with the wall clock, so it can read up to a minute short.
 */
internal class ElapsedClock(val days: Long, val timeZoneId: String) {
    companion object {
        fun of(lastLog: Instant?, now: Instant): ElapsedClock {
            val nowMinute = Math.floorDiv(now.epochSecond, 60L)
            // A meal stamped in the future (clock skew) counts as just eaten rather than wrapping.
            val startMinute = minOf(lastLog?.let { Math.floorDiv(it.epochSecond, 60L) } ?: nowMinute, nowMinute)
            val days = (nowMinute - startMinute) / MINUTES_PER_DAY
            // Shown time of day = now + offset = now - start (mod one day). Any offset is
            // equivalent mod a day, so pick the one within ±12h that GMT zone IDs accept.
            var offset = Math.floorMod(-startMinute, MINUTES_PER_DAY)
            if (offset > MINUTES_PER_DAY / 2) offset -= MINUTES_PER_DAY
            val zone = String.format(
                Locale.ROOT, "GMT%s%02d:%02d", if (offset < 0) "-" else "+", abs(offset) / 60, abs(offset) % 60,
            )
            return ElapsedClock(days, zone)
        }

        fun nextRollover(lastLog: Instant, now: Instant): Instant {
            val startMinute = Math.floorDiv(lastLog.epochSecond, 60L)
            val days = of(lastLog, now).days
            return Instant.ofEpochSecond((startMinute + (days + 1) * MINUTES_PER_DAY) * 60L)
        }

        private const val MINUTES_PER_DAY = 24L * 60L
    }
}

/**
 * Re-renders the fasting widgets when the fast crosses its next 24-hour mark, where the
 * [ElapsedClock] wraps and its baked-in day count goes stale. An AlarmManager broadcast rather
 * than WorkManager: rescheduling unique work from inside the render it triggers would cancel
 * itself. Inexact and non-waking on purpose: nobody sees the widget while the screen is off.
 */
private fun scheduleDayRollover(context: Context, lastLog: Instant?, now: Instant) {
    val alarms = context.getSystemService(AlarmManager::class.java) ?: return
    val component = ComponentName(context, FastingWidgetReceiver::class.java)
    val ids = AppWidgetManager.getInstance(context).getAppWidgetIds(component)
    val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
        .setComponent(component)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
    val pending = PendingIntent.getBroadcast(
        context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    if (lastLog == null || ids.isEmpty()) {
        alarms.cancel(pending)
        return
    }
    alarms.set(AlarmManager.RTC, ElapsedClock.nextRollover(lastLog, now).toEpochMilli(), pending)
}
