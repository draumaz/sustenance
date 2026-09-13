package xyz.draumaz.sustenance.widget

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
import xyz.draumaz.sustenance.SustenanceApp
import xyz.draumaz.sustenance.MainActivity
import xyz.draumaz.sustenance.data.Metric
import xyz.draumaz.sustenance.data.MetricSummary
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
    val padding = 8.dp
    val spacing = 6.dp
    val tileWidth = (size.width - padding * 2 - spacing) / 2

    // Always show all 6 available metrics, using a horizontal row layout per tile to fit any height.
    val displayTiles = tiles.take(6)
    val effectiveTileWidth = if (tileWidth > 0.dp) tileWidth else 80.dp
    val effectiveTileHeight = (size.height - padding * 2 - spacing * 2) / 3

    val maxDisplayLen = displayTiles.maxOfOrNull { tile ->
        val unit = context.getString(tile.metric.unitRes)
        tile.value.substringBefore(unit).trim().length
    } ?: 0

    val uniformFontSize = when {
        effectiveTileWidth < 75.dp || effectiveTileHeight < 30.dp || maxDisplayLen >= 5 -> 11.sp
        effectiveTileWidth < 90.dp || effectiveTileHeight < 40.dp || maxDisplayLen >= 4 -> 12.sp
        else -> 15.sp
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(20.dp)
            .padding(padding)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        if (displayTiles.isEmpty()) {
            val isCompact = size.height < 90.dp
            val isSmall = size.width < 150.dp || size.height < 150.dp
            Box(
                modifier = GlanceModifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (isCompact) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "🍽️",
                            style = TextStyle(fontSize = 14.sp),
                        )
                        Spacer(GlanceModifier.width(4.dp))
                        Text(
                            text = context.getString(xyz.draumaz.sustenance.R.string.tap_to_connect),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "🍽️",
                            style = TextStyle(fontSize = if (isSmall) 18.sp else 26.sp),
                        )
                        Spacer(GlanceModifier.height(4.dp))
                        Text(
                            text = context.getString(
                                if (isSmall) xyz.draumaz.sustenance.R.string.tap_to_connect
                                else xyz.draumaz.sustenance.R.string.widget_connect_prompt
                            ),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = if (isSmall) 11.sp else 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                        )
                    }
                }
            }
        } else {
            val rows = displayTiles.chunked(2)
            rows.forEachIndexed { i, rowTiles ->
                Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    rowTiles.forEachIndexed { j, tile ->
                        Tile(tile, GlanceModifier.defaultWeight().fillMaxHeight(), tileWidth, uniformFontSize, effectiveTileHeight)
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
private fun Tile(
    tile: MetricSummary,
    modifier: GlanceModifier,
    tileWidth: Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    tileHeight: Dp,
) {
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

    val isVeryShort = tileHeight < 35.dp
    val horizontalPadding = if (isVeryShort) 4.dp else 8.dp
    val verticalPadding = if (isVeryShort) 2.dp else 6.dp

    Box(
        modifier = modifier
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(12.dp)
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
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = emoji,
                style = TextStyle(fontSize = if (isVeryShort) 10.sp else 12.sp),
            )
            Spacer(GlanceModifier.width(4.dp))
            Text(
                text = displayValue,
                maxLines = 1,
                style = TextStyle(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = fontSize,
                ),
            )
        }
    }
}
