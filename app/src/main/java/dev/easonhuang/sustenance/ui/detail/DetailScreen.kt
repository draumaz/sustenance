package dev.easonhuang.sustenance.ui.detail

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.rounded.History
import dev.easonhuang.sustenance.R
import dev.easonhuang.sustenance.data.GoalsRepository
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.MetricDetail
import dev.easonhuang.sustenance.data.RecordRow
import dev.easonhuang.sustenance.data.SettingsRepository
import dev.easonhuang.sustenance.ui.DetailViewModel
import dev.easonhuang.sustenance.ui.components.BarChart
import dev.easonhuang.sustenance.ui.components.LineChart
import dev.easonhuang.sustenance.ui.components.PredictiveBackState
import dev.easonhuang.sustenance.data.formatValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    settingsRepo: SettingsRepository,
    metric: Metric,
    dateOffset: Int = 0,
    onBack: () -> Unit,
) {
    val vm: DetailViewModel = viewModel(
        key = "${metric.key}_$dateOffset",
        factory = DetailViewModel.factory(manager, goalsRepo, settingsRepo, metric, dateOffset),
    )
    val detail by vm.detail.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    var showGoalDialog by remember { mutableStateOf(false) }
    var recordToDelete by remember { mutableStateOf<RecordRow?>(null) }
    var selectedChartPoint by remember { mutableStateOf<Int?>(null) }

    LifecycleResumeEffect(metric.key) {
        vm.refresh(showIndicator = false)
        onPauseOrDispose { }
    }

    if (showGoalDialog) {
        val currentGoal = detail?.goal ?: 0f
        var text by remember { mutableStateOf(currentGoal.toString().removeSuffix(".0")) }
        val titleText = if (metric == Metric.CALORIC_BALANCE) stringResource(R.string.edit_caloric_balance_offset) else stringResource(R.string.edit_metric_goal, stringResource(metric.titleRes))
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text(titleText) },
            text = {
                val labelText = if (metric == Metric.CALORIC_BALANCE) stringResource(R.string.offset_label, stringResource(metric.unitRes)) else stringResource(R.string.target_label, stringResource(metric.unitRes))
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
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    recordToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { recordToDelete = null },
            title = { Text(stringResource(R.string.delete_log_title)) },
            text = { Text(stringResource(R.string.delete_log_confirm, record.primary)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        record.id?.let { vm.deleteRecord(it) }
                        recordToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { recordToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { selectedChartPoint = null }
            }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            stringResource(metric.titleRes),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
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
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = vm::refresh,
                modifier = Modifier.padding(top = inner.calculateTopPadding())
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    item { HeaderCard(d, onEditGoal = { showGoalDialog = true }) }
                    item { ChartCard(d, selectedChartPoint) { selectedChartPoint = it } }
                    if (d.todaySections.isNotEmpty()) {
                        item {
                            FoodItemsCard(
                                sections = d.todaySections,
                                onDelete = { id ->
                                    val row = d.todaySections.flatMap { it.second }.find { it.id == id }
                                    recordToDelete = row
                                }
                            )
                        }
                    }
                    if (d.stats.isNotEmpty()) item { StatsCard(d) }
                    if (d.recent.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.recent_records),
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
    }
}

@Composable
private fun FoodItemsCard(
    sections: List<Pair<String, List<RecordRow>>>,
    onDelete: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                stringResource(R.string.todays_intake),
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
                        val secondaryParts = item.secondary.split(" • ")
                        val kcal = secondaryParts.getOrNull(0) ?: ""
                        val time = secondaryParts.getOrNull(1) ?: ""
                        val rowModifier = if (item.accentColor != null) {
                            Modifier
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(item.accentColor.copy(alpha = 0.25f))
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                        } else {
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                        }
                        Row(
                            rowModifier,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(item.primary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                                Text(time, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                item.tertiary?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val kcalColor = if (item.accentColor != null) {
                                    // Make the text pop more by mixing with white/primary in a subtle way
                                    item.accentColor
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                                Text(kcal, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Black, color = kcalColor)
                                if (item.isEditable && item.id != null) {
                                    Spacer(Modifier.padding(horizontal = 4.dp))
                                    IconButton(
                                        onClick = { onDelete(item.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Rounded.Delete,
                                            contentDescription = stringResource(R.string.delete),
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (i < items.lastIndex && item.accentColor == null && items[i+1].accentColor == null) {
                            HorizontalDivider(Modifier.padding(horizontal = 16.dp).alpha(0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(d: MetricDetail, onEditGoal: () -> Unit) {
    val todayValue = d.points.lastOrNull()?.value ?: 0f
    val goal = d.goal ?: 0f
    val progress = if (goal > 0) (todayValue / goal).coerceIn(0f, 1f) else 0f
    val isOver = goal > 0 && todayValue > goal
    
    val accent = when {
        isOver -> Color(0xFFAB6161)
        else -> lerp(d.metric.accent, Color(0xFFEF5350), progress)
    }
    Card(
        modifier = Modifier.fillMaxWidth().height(100.dp).padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.12f), Color.Transparent)
                        )
                    )
            )
            Row(Modifier.padding(horizontal = 24.dp, vertical = 16.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.14f)))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(d.metric.icon, null, tint = accent, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.size(20.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    var textStyle by remember(d.headline) {
                        mutableStateOf(if (d.headline.contains("/")) {
                            TextStyle(
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.sp
                            )
                        } else {
                            TextStyle(
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = (-1).sp
                            )
                        })
                    }

                    Text(
                        text = d.headline,
                        style = textStyle,
                        maxLines = 1,
                        softWrap = false,
                        onTextLayout = { result ->
                            if (result.hasVisualOverflow) {
                                textStyle = textStyle.copy(fontSize = textStyle.fontSize * 0.9f)
                            }
                        }
                    )
                    d.caption?.let {
                        Text(
                            it.uppercase(), 
                            style = MaterialTheme.typography.labelMedium, 
                            fontWeight = FontWeight.ExtraBold,
                            color = accent,
                            letterSpacing = 1.sp,
                            maxLines = 1
                        )
                    }
                    d.goal?.let {
                        if (d.isGoalEditable) {
                            val label = if (d.metric == Metric.CALORIC_BALANCE) stringResource(R.string.offset_short) else stringResource(R.string.goal_short)
                            Text(
                                "$label: ${d.metric.formatValue(it)} ${stringResource(d.metric.unitRes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                maxLines = 1
                            )
                        }
                    }
                }
                if (d.isGoalEditable) {
                    val editDesc = if (d.metric == Metric.CALORIC_BALANCE) stringResource(R.string.edit_offset_desc) else stringResource(R.string.edit_goal_desc)
                    IconButton(onClick = onEditGoal) {
                        Icon(Icons.Rounded.Edit, contentDescription = editDesc, tint = accent.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartCard(d: MetricDetail, selectedIndex: Int?, onSelectedIndexChange: (Int?) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(Modifier.fillMaxWidth().height(200.dp).padding(24.dp)) {
            LineChart(
                points = d.points, 
                color = d.metric.accent, 
                modifier = Modifier.fillMaxSize(), 
                unit = stringResource(d.metric.unitRes),
                goal = d.goal,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = onSelectedIndexChange
            )
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
