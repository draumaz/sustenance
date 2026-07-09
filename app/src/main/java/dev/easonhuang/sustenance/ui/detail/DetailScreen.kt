package dev.easonhuang.sustenance.ui.detail

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import dev.easonhuang.sustenance.data.GoalsRepository
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.MetricDetail
import dev.easonhuang.sustenance.data.MetricKind
import dev.easonhuang.sustenance.data.RecordRow
import dev.easonhuang.sustenance.data.formatValue
import dev.easonhuang.sustenance.ui.DetailViewModel
import dev.easonhuang.sustenance.ui.components.BarChart
import dev.easonhuang.sustenance.ui.components.LineChart

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    metric: Metric,
    onBack: () -> Unit,
) {
    val vm: DetailViewModel = viewModel(
        key = metric.key,
        factory = DetailViewModel.factory(manager, goalsRepo, metric),
    )
    val detail by vm.detail.collectAsStateWithLifecycle()
    var showGoalDialog by remember { mutableStateOf(false) }

    if (showGoalDialog) {
        val currentGoal = detail?.goal ?: 0f
        var text by remember { mutableStateOf(currentGoal.toString().removeSuffix(".0")) }
        val titleText = if (metric == Metric.CALORIC_BALANCE) "Edit Caloric balance offset" else "Edit ${metric.title} Goal"
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text(titleText) },
            text = {
                val labelText = if (metric == Metric.CALORIC_BALANCE) "Offset (${metric.unit})" else "Target (${metric.unit})"
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(labelText) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    text.toFloatOrNull()?.let { vm.setGoal(it) }
                    showGoalDialog = false
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        metric.title, 
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold 
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
    ) { inner ->
        val d = detail
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValuesFrom(inner),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item { HeaderCard(d, onEditGoal = { showGoalDialog = true }) }
            if (d.todaySections.isNotEmpty()) {
                item { FoodItemsCard(d.todaySections) }
            }
            item { ChartCard(d) }
            if (d.stats.isNotEmpty()) item { StatsCard(d) }
            if (d.recent.isNotEmpty()) {
                item {
                    Text(
                        "Recent records",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }
                items(d.recent) { row -> RecordItem(row) }
                item { Spacer(Modifier.height(100.dp)) } // Leave room for nav pill
            }
        }
    }
}

@Composable
private fun FoodItemsCard(sections: List<Pair<String, List<RecordRow>>>) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                "Today's intake",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            sections.forEach { (section, items) ->
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        section.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
                    )
                    items.forEachIndexed { i, item ->
                        val (kcal, time) = item.secondary.split(" • ")
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.primary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(kcal, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }
                        if (i < items.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp).alpha(0.5f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(d: MetricDetail, onEditGoal: () -> Unit) {
    val accent = d.metric.accent
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.12f), Color.Transparent)
                        )
                    )
            )
            Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.14f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(d.metric.icon, null, tint = accent, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        d.headline, 
                        style = MaterialTheme.typography.headlineLarge, 
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-1).sp
                    )
                    d.caption?.let {
                        Text(
                            it.uppercase(), 
                            style = MaterialTheme.typography.labelMedium, 
                            fontWeight = FontWeight.ExtraBold,
                            color = accent,
                            letterSpacing = 1.sp
                        )
                    }
                    d.goal?.let {
                        val label = if (d.metric == Metric.CALORIC_BALANCE) "Offset" else "Goal"
                        Text(
                            "$label: ${d.metric.formatValue(it)} ${d.metric.unit}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                val editDesc = if (d.metric == Metric.CALORIC_BALANCE) "Edit Offset" else "Edit Goal"
                IconButton(onClick = onEditGoal) {
                    Icon(Icons.Rounded.Edit, contentDescription = editDesc, tint = accent.copy(alpha = 0.6f))
                }
            }
        }
    }
}

@Composable
private fun ChartCard(d: MetricDetail) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxWidth().height(260.dp).padding(24.dp)) {
            if (d.metric.kind == MetricKind.DAILY_TOTAL) {
                BarChart(d.points, d.metric.accent, Modifier.fillMaxSize())
            } else {
                LineChart(d.points, d.metric.accent, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun StatsCard(d: MetricDetail) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            d.stats.forEachIndexed { i, (label, value) ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black)
                }
                if (i < d.stats.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 24.dp))
            }
        }
    }
}

@Composable
private fun RecordItem(row: RecordRow) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(row.primary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(row.secondary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Top inset padding + horizontal/bottom breathing room for the detail list. */
@Composable
private fun PaddingValuesFrom(inner: androidx.compose.foundation.layout.PaddingValues) =
    androidx.compose.foundation.layout.PaddingValues(
        top = inner.calculateTopPadding() + 16.dp,
        bottom = inner.calculateBottomPadding() + 16.dp,
    )
