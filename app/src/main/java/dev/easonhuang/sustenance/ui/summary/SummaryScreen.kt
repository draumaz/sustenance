package dev.easonhuang.sustenance.ui.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TopAppBar
import dev.easonhuang.sustenance.data.GoalsRepository
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.WeeklyStat
import dev.easonhuang.sustenance.data.formatValue
import dev.easonhuang.sustenance.ui.components.GoalRing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    bottomInset: androidx.compose.ui.unit.Dp,
    onBack: () -> Unit = {},
) {
    val vm: SummaryViewModel = viewModel(factory = SummaryViewModel.factory(manager, goalsRepo))
    val state by vm.state.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var editing by remember { mutableStateOf<WeeklyStat?>(null) }
    val goals by goalsRepo.goals.collectAsStateWithLifecycle(emptyMap())
    val programmedDeficit = (goals[Metric.CALORIC_BALANCE] ?: 0f) > 0

    LifecycleResumeEffect(Unit) {
        vm.refresh(showIndicator = false)
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Your daily intake", style = MaterialTheme.typography.labelSmall.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.primary)
                        Text("Summary", style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        if (refreshing) {
                            CircularProgressIndicator(Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.padding(top = inner.calculateTopPadding())
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    top = 16.dp,
                    bottom = bottomInset + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!state.loading && state.stats.isEmpty()) {
                    item { EmptyState() }
                }
                items(state.stats, key = { it.metric.key }) { stat ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
                    ) {
                        InsightCard(
                            stat = stat,
                            onEdit = { editing = stat },
                            editEnabled = !(stat.metric == Metric.FOOD && programmedDeficit)
                        )
                    }
                }
            }
        }
    }

    editing?.let { stat ->
        GoalEditDialog(
            stat = stat,
            onDismiss = { editing = null },
            onSave = { value -> vm.setGoal(stat.metric, value); editing = null },
        )
    }
}

@Composable
private fun InsightCard(stat: WeeklyStat, onEdit: () -> Unit, editEnabled: Boolean = true) {
    val isTotalEnergy = stat.metric == Metric.TOTAL_CALORIES
    val isOver = stat.goal > 0 && stat.todayValue > stat.goal && !isTotalEnergy
    val progress = stat.progress
    
    val accent = when {
        isTotalEnergy -> lerp(Color(0xFF568259), Color(0xFF709E73), progress)
        isOver -> Color(0xFFAB6161)
        progress >= 1f -> Color(0xFF709E73)
        else -> stat.metric.accent
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalRing(progress = stat.progress, color = accent, diameter = 64.dp, stroke = 10.dp) {
                    Text(
                        "${(stat.progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stat.metric.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        "${stat.metric.formatValue(stat.todayValue)} / ${stat.metric.formatValue(stat.goal)} ${stat.metric.unit}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    DeltaChip(stat)
                }
                if (editEnabled) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.clip(CircleShape).background(accent.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = "Edit goal", tint = accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeltaChip(stat: WeeklyStat) {
    val delta = stat.deltaPercent
    val (icon, tint, label) = when {
        delta == null -> Triple(Icons.AutoMirrored.Rounded.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant, "New")
        delta >= 1f -> Triple(Icons.AutoMirrored.Rounded.TrendingUp, stat.metric.accent, "+${delta.roundToInt()}% vs yesterday")
        delta <= -1f -> Triple(Icons.AutoMirrored.Rounded.TrendingDown, MaterialTheme.colorScheme.error, "${delta.roundToInt()}% vs yesterday")
        else -> Triple(Icons.AutoMirrored.Rounded.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant, "Steady")
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = tint)
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nothing here yet",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Grant access to nutrition data in Settings to see your insights.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
