package xyz.draumaz.sustenance.wear.ui

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import xyz.draumaz.sustenance.wear.R
import xyz.draumaz.sustenance.wear.data.DashboardStore
import xyz.draumaz.sustenance.wear.data.WearDashboard
import xyz.draumaz.sustenance.wear.data.WearMetric
import java.text.NumberFormat

/** The phone widget's over-limit red, so "over" reads the same on both devices. */
private val OverLimit = Color(0xFFAB6161)

private sealed interface LoadState {
    data object Loading : LoadState
    data class Loaded(val dashboard: WearDashboard?) : LoadState
}

@Composable
fun DashboardScreen() {
    val context = LocalContext.current
    val state by produceState<LoadState>(LoadState.Loading) {
        DashboardStore.observe(context).collect { value = LoadState.Loaded(it) }
    }

    // Nothing yet, or yesterday's snapshot: nudge the phone. Opening the app is the moment the
    // user wants current numbers, and the phone's own refresh may be up to 30 minutes away.
    val loaded = state as? LoadState.Loaded
    LaunchedEffect(loaded?.dashboard?.isToday) {
        if (loaded != null && loaded.dashboard?.isToday != true) DashboardStore.requestRefresh(context)
    }

    MaterialTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(
                    state = listState,
                    contentPadding = contentPadding,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    item {
                        ListHeader { Text(stringResource(R.string.today)) }
                    }
                    when (val s = state) {
                        LoadState.Loading -> item { CircularProgressIndicator() }
                        is LoadState.Loaded -> {
                            val dashboard = s.dashboard
                            if (dashboard == null) {
                                item {
                                    Text(
                                        stringResource(R.string.waiting_for_phone),
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                    )
                                }
                            } else {
                                items(dashboard.current(), key = { it.key }) { MetricRow(it) }
                                item { UpdatedFooter(dashboard) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricRow(metric: WearMetric) {
    val accent = Color(metric.accent)
    val goal = metric.goal
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(8.dp).background(accent, CircleShape))
            Spacer(Modifier.size(6.dp))
            Text(
                metric.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = if (metric.granted) metric.display else stringResource(R.string.no_access),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (goal != null && goal > 0f) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { metric.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                colors = ProgressIndicatorDefaults.colors(
                    indicatorColor = if (metric.isOverLimit) OverLimit else accent,
                ),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.goal, formatGoal(goal), metric.unit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdatedFooter(dashboard: WearDashboard) {
    val updatedMillis = dashboard.updatedAt.toEpochMilli()
    val now = System.currentTimeMillis()
    // DateUtils renders anything under a minute as "0 minutes ago".
    val label = if (now - updatedMillis < DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.updated_just_now)
    } else {
        val ago = remember(updatedMillis, now / DateUtils.MINUTE_IN_MILLIS) {
            DateUtils.getRelativeTimeSpanString(updatedMillis, now, DateUtils.MINUTE_IN_MILLIS).toString()
        }
        stringResource(R.string.updated, ago)
    }
    Text(
        label,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

private fun formatGoal(goal: Float): String = NumberFormat.getIntegerInstance().format(goal.toLong())
