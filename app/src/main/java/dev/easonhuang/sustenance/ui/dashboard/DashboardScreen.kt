package dev.easonhuang.sustenance.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.GoalsRepository
import dev.easonhuang.sustenance.data.MetricSummary
import dev.easonhuang.sustenance.ui.DashboardViewModel
import dev.easonhuang.sustenance.ui.components.MetricCard
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    settingsRepo: dev.easonhuang.sustenance.data.SettingsRepository,
    granted: Set<String>,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenMetric: (Metric) -> Unit,
    onManagePermissions: () -> Unit,
) {
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(manager, goalsRepo, settingsRepo))
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(rememberTopAppBarState())

    val greeting = when (LocalTime.now().hour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    // Refresh when permissions change (e.g. Food just granted) or when returning to the app.
    androidx.compose.runtime.LaunchedEffect(granted) {
        vm.refresh(showIndicator = false)
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refresh(showIndicator = false)
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(greeting, style = MaterialTheme.typography.labelSmall.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.primary)
                        Text("Sustenance", style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp), fontWeight = FontWeight.Bold)
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
            modifier = Modifier.padding(inner)
        ) {
            val data = summaries
            if (data == null) {
                // initial load handled by spinner in app bar; keep grid space empty
                Box(Modifier.fillMaxSize())
            } else {
                val energyMetrics = listOf(Metric.TOTAL_CALORIES, Metric.CALORIC_BALANCE)
                val foodMetric = listOf(Metric.FOOD)
                val microMetrics = listOf(Metric.SUGAR, Metric.SATURATED_FAT, Metric.SODIUM)

                val energyGroup = data.filter { it.metric in energyMetrics }
                val foodGroup = data.filter { it.metric in foodMetric }
                val microsGroup = microMetrics.mapNotNull { m -> data.find { it.metric == m } }
                val macrosGroup = data.filter { it.metric !in (energyMetrics + foodMetric + microMetrics) }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(
                        start = 16.dp, end = 16.dp,
                        top = 16.dp,
                        bottom = bottomInset + 88.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (energyGroup.isNotEmpty()) {
                        item {
                            MetricSection(
                                title = "Energy",
                                items = energyGroup,
                                columns = 2,
                                onOpenMetric = onOpenMetric,
                                onManagePermissions = onManagePermissions
                            )
                        }
                    }
                    if (foodGroup.isNotEmpty()) {
                        item {
                            MetricSection(
                                title = "Food",
                                items = foodGroup,
                                columns = 1,
                                onOpenMetric = onOpenMetric,
                                onManagePermissions = onManagePermissions
                            )
                        }
                    }
                    if (macrosGroup.isNotEmpty()) {
                        item {
                            MetricSection(
                                title = "Macros",
                                items = macrosGroup,
                                columns = 2,
                                onOpenMetric = onOpenMetric,
                                onManagePermissions = onManagePermissions
                            )
                        }
                    }
                    if (microsGroup.isNotEmpty()) {
                        item {
                            MetricSection(
                                title = "Micros",
                                items = microsGroup,
                                columns = 1,
                                onOpenMetric = onOpenMetric,
                                onManagePermissions = onManagePermissions
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MetricSection(
    title: String,
    items: List<MetricSummary>,
    columns: Int,
    onOpenMetric: (Metric) -> Unit,
    onManagePermissions: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(bottom = 12.dp, start = 8.dp)
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = columns
            ) {
                items.forEach { summary ->
                    Box(Modifier.weight(1f)) {
                        MetricCard(
                            summary = summary,
                            onClick = {
                                if (summary.granted) onOpenMetric(summary.metric) else onManagePermissions()
                            }
                        )
                    }
                }
                // Fill remaining space in the last row if needed
                val remainder = items.size % columns
                if (remainder != 0) {
                    repeat(columns - remainder) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}
