package dev.easonhuang.sustenance.ui.history

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.easonhuang.sustenance.R
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.HistoryItem
import dev.easonhuang.sustenance.data.SettingsRepository
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    manager: HealthConnectManager,
    settingsRepo: SettingsRepository,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    onItemSelected: (HistoryItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var rawHistory by remember { mutableStateOf<List<HistoryItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    
    val pinnedNames by settingsRepo.pinnedHistoryItems.collectAsStateWithLifecycle(initialValue = emptySet())

    val history = remember(rawHistory, pinnedNames) {
        rawHistory.map { item ->
            item.copy(isPinned = pinnedNames.contains(item.nutrients.foodItem))
        }.sortedWith(
            compareByDescending<HistoryItem> { it.isPinned }
                .thenByDescending { it.timestamp }
        )
    }
    
    val timeFmt = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    val zone = remember { ZoneId.systemDefault() }

    LaunchedEffect(Unit) {
        rawHistory = manager.readHistory()
        isLoading = false
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.history)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors()
            )
        }
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize().padding(inner)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (history.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.History,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.no_history),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        top = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp + bottomInset
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(history, key = { it.nutrients.foodItem }) { item ->
                        HistoryRow(
                            item = item,
                            timeText = timeFmt.format(item.timestamp.atZone(zone)),
                            modifier = Modifier.animateItem(),
                            onClick = { onItemSelected(item) },
                            onLongClick = {
                                scope.launch {
                                    settingsRepo.togglePinnedHistoryItem(item.nutrients.foodItem)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryRow(
    item: HistoryItem,
    timeText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                }
            )
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            if (item.isPinned) {
                Icon(
                    Icons.Rounded.PushPin,
                    contentDescription = null,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.TopEnd),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.nutrients.foodItem,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = item.nutrients.servingSize,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = "${item.nutrients.calories.toInt()} ${stringResource(R.string.unit_kcal)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = if (item.isPinned) 20.dp else 0.dp)
                    )
                }
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(12.dp))
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MacroChip(stringResource(R.string.metric_protein), "${item.nutrients.protein.toInt()}g", Color(0xFFE3F2FD))
                    MacroChip(stringResource(R.string.metric_carbs), "${item.nutrients.carbs.toInt()}g", Color(0xFFFFF3E0))
                    MacroChip(stringResource(R.string.metric_fat), "${item.nutrients.fat.toInt()}g", Color(0xFFFBE9E7))
                }
            }
        }
    }
}

@Composable
fun MacroChip(label: String, value: String, color: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Black.copy(alpha = 0.6f)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black.copy(alpha = 0.8f)
            )
        }
    }
}
