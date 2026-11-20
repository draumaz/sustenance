package xyz.draumaz.sustenance.wear.tile

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.weight
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Row
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future
import xyz.draumaz.sustenance.wear.MainActivity
import xyz.draumaz.sustenance.wear.R
import xyz.draumaz.sustenance.wear.data.DashboardStore
import xyz.draumaz.sustenance.wear.data.WearMetric
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime

private const val RESOURCES_VERSION = "1"

/** The phone widget's six, in the same order and with the same emoji. */
private val TILE_METRICS = listOf(
    "total_calories" to "⚡",
    "food" to "🍴",
    "protein" to "💪",
    "carbs" to "🍞",
    "fat" to "🥑",
    "fiber" to "🥦",
)

/** The phone widget's over-limit red. */
private const val OVER_LIMIT = 0xFFAB6161.toInt()

/**
 * The watch counterpart of the phone's home-screen widget: today's six main macros as a 2×3
 * grid of progress-filled cells. Re-rendered when the phone publishes (see
 * `DashboardListenerService`) and at local midnight, when the day's totals reset.
 */
class MacroTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val metrics = DashboardStore.load(this@MacroTileService)?.current().orEmpty()
        val layout = tileLayout(this@MacroTileService, requestParams.deviceConfiguration, metrics)
        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            // Phone pushes trigger updates on their own; this only guarantees the midnight reset.
            .setFreshnessIntervalMillis(millisUntilMidnight())
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

private fun millisUntilMidnight(): Long {
    val now = ZonedDateTime.now()
    val midnight = LocalDate.now().plusDays(1).atStartOfDay(now.zone)
    return Duration.between(now, midnight).toMillis().coerceAtLeast(60_000L) + 1_000L
}

private fun tileLayout(
    context: Context,
    device: DeviceParameters,
    metrics: List<WearMetric>,
): LayoutElement = materialScope(context, device) {
    primaryLayout(
        titleSlot = { text(context.getString(R.string.today).layoutString) },
        mainSlot = {
            val cells = TILE_METRICS.map { (key, emoji) -> cell(key, emoji, metrics.firstOrNull { it.key == key }) }
            grid(cells.chunked(2))
        },
        onClick = openAppClickable(context),
    )
}

private fun grid(rows: List<List<LayoutElement>>): LayoutElement {
    val column = Column.Builder().setWidth(expand()).setHeight(expand())
    rows.forEachIndexed { i, cells ->
        if (i > 0) column.addContent(Spacer.Builder().setHeight(dp(4f)).build())
        val row = Row.Builder().setWidth(expand()).setHeight(weight(1f))
        cells.forEachIndexed { j, cell ->
            if (j > 0) row.addContent(Spacer.Builder().setWidth(dp(4f)).build())
            row.addContent(cell)
        }
        column.addContent(row.build())
    }
    return column.build()
}

/** One macro: a rounded cell whose left part fills with the metric's accent up to the goal. */
private fun MaterialScope.cell(key: String, emoji: String, metric: WearMetric?): LayoutElement {
    val progress = metric?.progress?.coerceIn(0f, 1f) ?: 0f
    val fillColor = if (metric?.isOverLimit == true) OVER_LIMIT else metric?.accent ?: 0
    val cellShape = ModifiersBuilders.Corner.Builder().setRadius(dp(12f)).build()

    val box = Box.Builder()
        .setWidth(weight(1f))
        .setHeight(expand())
        .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
        .setModifiers(
            ModifiersBuilders.Modifiers.Builder()
                // Opens the app scrolled to this metric (see MainActivity.EXTRA_METRIC).
                .setClickable(openAppClickable(context, key))
                .setBackground(
                    ModifiersBuilders.Background.Builder()
                        .setColor(argb(colorScheme.surfaceContainer.staticArgb))
                        .setCorner(cellShape)
                        .build(),
                )
                .build(),
        )

    if (progress > 0f) {
        // A Row split by weights draws the fill without knowing the cell's width in dp.
        val fill = Row.Builder().setWidth(expand()).setHeight(expand())
            .addContent(
                Box.Builder().setWidth(weight(progress)).setHeight(expand())
                    .setModifiers(
                        ModifiersBuilders.Modifiers.Builder()
                            .setBackground(
                                ModifiersBuilders.Background.Builder()
                                    .setColor(argb(withAlpha(fillColor, 0.5f)))
                                    .setCorner(cellShape)
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
        if (progress < 1f) fill.addContent(Box.Builder().setWidth(weight(1f - progress)).setHeight(expand()).build())
        box.addContent(fill.build())
    }

    box.addContent(
        Row.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(8f)).setEnd(dp(4f)).build())
                    .build(),
            )
            .addContent(text(emoji.layoutString, typography = Typography.LABEL_SMALL))
            .addContent(Spacer.Builder().setWidth(dp(4f)).build())
            .addContent(
                text(
                    (metric?.number ?: "0").layoutString,
                    typography = Typography.LABEL_MEDIUM,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                ),
            )
            .build(),
    )
    return box.build()
}

private fun withAlpha(argb: Int, alpha: Float): Int =
    (argb and 0x00FFFFFF) or ((alpha * 255).toInt() shl 24)

/** Launches the app; with [metricKey], scrolled to that metric's row. */
private fun openAppClickable(context: Context, metricKey: String? = null): ModifiersBuilders.Clickable {
    val activity = ActionBuilders.AndroidActivity.Builder()
        .setPackageName(context.packageName)
        .setClassName(MainActivity::class.java.name)
    if (metricKey != null) {
        activity.addKeyToExtraMapping(MainActivity.EXTRA_METRIC, ActionBuilders.stringExtra(metricKey))
    }
    return ModifiersBuilders.Clickable.Builder()
        // Ids must be unique per clickable, or the renderer can't tell the cells apart.
        .setId(if (metricKey != null) "open_$metricKey" else "open_app")
        .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity.build()).build())
        .build()
}

