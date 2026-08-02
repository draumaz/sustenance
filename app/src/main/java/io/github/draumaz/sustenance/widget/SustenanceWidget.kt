package io.github.draumaz.sustenance.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
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
import io.github.draumaz.sustenance.SustenanceApp
import io.github.draumaz.sustenance.MainActivity
import io.github.draumaz.sustenance.data.Metric
import io.github.draumaz.sustenance.data.MetricSummary
import kotlinx.coroutines.flow.first

/** Preferred metrics to surface on the home screen, in priority order. */
private val WIDGET_METRICS = listOf(
    Metric.TOTAL_CALORIES, Metric.FOOD, Metric.PROTEIN, Metric.CARBS, Metric.FAT, Metric.FIBER,
)

class SustenanceWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as SustenanceApp
        val manager = app.healthConnect
        val goals = app.goals.goals.first()
        val isKeto = app.settings.ketoMode.first()

        val summaries = if (manager.isAvailable) {
            runCatching {
                manager.readDashboard(goals = goals, isKeto = isKeto)
            }.getOrDefault(emptyList())
        } else emptyList()

        val tiles = WIDGET_METRICS
            .mapNotNull { m -> summaries.firstOrNull { it.metric == m && it.granted && it.hasData } }
            .take(6)

        provideContent {
            GlanceTheme {
                WidgetContent(tiles)
            }
        }
    }
}

@Composable
private fun WidgetContent(tiles: List<MetricSummary>) {
    val context = LocalContext.current
    val size = LocalSize.current
    val padding = 12.dp
    val spacing = 8.dp
    val tileWidth = (size.width - padding * 2 - spacing) / 2

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(padding)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        if (tiles.isEmpty()) {
            Text(
                text = context.getString(io.github.draumaz.sustenance.R.string.widget_connect_prompt),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        } else {
            val rows = tiles.chunked(2)
            rows.forEachIndexed { i, rowTiles ->
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    rowTiles.forEachIndexed { j, tile ->
                        Tile(tile, GlanceModifier.defaultWeight().fillMaxHeight(), tileWidth)
                        if (j == 0 && rowTiles.size > 1) Spacer(GlanceModifier.width(spacing))
                    }
                    if (rowTiles.size == 1) Spacer(GlanceModifier.defaultWeight())
                }
                if (i < rows.size - 1) Spacer(GlanceModifier.height(spacing))
            }
        }
    }
}

@Composable
private fun Tile(tile: MetricSummary, modifier: GlanceModifier, tileWidth: Dp) {
    val context = LocalContext.current
    val unit = context.getString(tile.metric.unitRes)
    val displayValue = tile.value.substringBefore(unit).trim()

    val emoji = when (tile.metric) {
        Metric.TOTAL_CALORIES -> "⚡"
        Metric.FOOD -> "🍴"
        Metric.PROTEIN -> "💪"
        Metric.CARBS -> "🍞"
        Metric.FAT -> "🥑"
        Metric.FIBER -> "🥦"
        else -> ""
    }

    val today = tile.spark.lastOrNull() ?: 0f
    val summaryGoal = tile.goal ?: 0f
    val showProgress = summaryGoal > 0f
    val progress = if (showProgress) (today / summaryGoal).coerceIn(0f, 1f) else 0f
    val isOver = showProgress && today > summaryGoal

    val effectiveTileWidth = if (tileWidth > 0.dp) tileWidth else 80.dp
    val progressWidth = if (showProgress) effectiveTileWidth * (if (isOver) 1f else progress) else 0.dp

    val fillColor = if (isOver) {
        androidx.compose.ui.graphics.Color(0xFFAB6161)
    } else {
        tile.metric.accent
    }

    Box(
        modifier = modifier
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(18.dp)
            .clickable(
                actionStartActivity(
                    Intent(context, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_METRIC, tile.metric.key)
                        .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                ),
            )
    ) {
        if (showProgress && progressWidth > 0.dp) {
            Box(
                modifier = GlanceModifier
                    .width(progressWidth)
                    .fillMaxHeight()
                    .background(fillColor.copy(alpha = 0.5f))
            ) {}
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(8.dp),
        ) {
            Text(
                text = emoji,
                style = TextStyle(fontSize = 12.sp),
            )
            Spacer(GlanceModifier.defaultWeight())
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = displayValue,
                    maxLines = 1,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                    ),
                )
            }
        }
    }
}


