package dev.easonhuang.sustenance.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import dev.easonhuang.sustenance.SustenanceApp
import dev.easonhuang.sustenance.MainActivity
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.MetricSummary

/** Preferred metrics to surface on the home screen, in priority order. */
private val WIDGET_METRICS = listOf(
    Metric.TOTAL_CALORIES, Metric.FOOD, Metric.PROTEIN, Metric.CARBS, Metric.FAT, Metric.FIBER,
)

class SustenanceWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val manager = (context.applicationContext as SustenanceApp).healthConnect
        val summaries = if (manager.isAvailable) {
            runCatching { manager.readDashboard() }.getOrDefault(emptyList())
        } else emptyList()
        val tiles = WIDGET_METRICS
            .mapNotNull { m -> summaries.firstOrNull { it.metric == m && it.granted && it.hasData } }
            .take(4)

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
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(24.dp)
            .padding(14.dp)
            .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
    ) {
        Text(
            text = context.getString(dev.easonhuang.sustenance.R.string.app_name),
            style = TextStyle(
                color = GlanceTheme.colors.primary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            ),
        )
        Spacer(GlanceModifier.height(10.dp))
        if (tiles.isEmpty()) {
            Text(
                text = context.getString(dev.easonhuang.sustenance.R.string.widget_connect_prompt),
                style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp),
            )
        } else {
            tiles.chunked(2).forEach { rowTiles ->
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    rowTiles.forEachIndexed { i, tile ->
                        Tile(tile, GlanceModifier.defaultWeight())
                        if (i == 0 && rowTiles.size > 1) Spacer(GlanceModifier.width(10.dp))
                    }
                    if (rowTiles.size == 1) Spacer(GlanceModifier.defaultWeight())
                }
                Spacer(GlanceModifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun Tile(tile: MetricSummary, modifier: GlanceModifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .background(GlanceTheme.colors.secondaryContainer)
            .cornerRadius(18.dp)
            .padding(12.dp),
    ) {
        Text(
            text = context.getString(tile.metric.titleRes),
            maxLines = 1,
            style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 12.sp),
        )
        Spacer(GlanceModifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = tile.value,
                style = TextStyle(
                    color = GlanceTheme.colors.onSecondaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                ),
            )
            Spacer(GlanceModifier.width(3.dp))
            Text(
                text = context.getString(tile.metric.unitRes),
                style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 11.sp),
            )
        }
    }
}
