package dev.easonhuang.sustenance.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.GoalsRepository
import dev.easonhuang.sustenance.ui.DashboardViewModel
import dev.easonhuang.sustenance.ui.components.MetricCard
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    granted: Set<String>,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenMetric: (Metric) -> Unit,
    onManagePermissions: () -> Unit,
) {
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(manager, goalsRepo))
    val summaries by vm.summaries.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    val greeting = when (LocalTime.now().hour) {
        in 0..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }

    // Refresh when permissions change (e.g. Food just granted).
    androidx.compose.runtime.LaunchedEffect(granted) {
        vm.refresh()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(greeting, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Sustenance", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                    }
                },
                actions = {
                    IconButton(onClick = vm::refresh) {
                        if (refreshing) {
                            CircularProgressIndicator(Modifier.padding(4.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { inner ->
        val data = summaries
        if (data == null) {
            // initial load handled by spinner in app bar; keep grid space empty
            return@Scaffold
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(1),
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = inner.calculateTopPadding() + 8.dp,
                bottom = bottomInset + 88.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(data, key = { it.metric.key }) { summary ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                ) {
                    MetricCard(
                        summary = summary,
                        onClick = {
                            if (summary.granted) onOpenMetric(summary.metric) else onManagePermissions()
                        },
                    )
                }
            }
        }
    }
}
