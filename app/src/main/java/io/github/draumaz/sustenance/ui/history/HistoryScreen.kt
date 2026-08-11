package io.github.draumaz.sustenance.ui.history

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.draumaz.sustenance.R
import io.github.draumaz.sustenance.data.HealthConnectManager
import io.github.draumaz.sustenance.data.HistoryItem
import io.github.draumaz.sustenance.data.SettingsRepository
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextOverflow
import io.github.draumaz.sustenance.ui.components.NutrientIconList
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    manager: HealthConnectManager,
    settingsRepo: SettingsRepository,
    bottomInset: androidx.compose.ui.unit.Dp = 0.dp,
    predictiveBackProgress: Float = 0f,
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

    val (pinned, unpinned) = remember(history) {
        history.partition { it.isPinned }
    }
    
    val timeFmt = remember { DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT) }
    val zone = remember { ZoneId.systemDefault() }

    LaunchedEffect(Unit) {
        rawHistory = manager.readHistory()
        isLoading = false
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = 1f - predictiveBackProgress
            },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                    if (pinned.isNotEmpty()) {
                        item(key = "pinned_card") {
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
                            ) {
                                Column(modifier = Modifier.animateContentSize()) {
                                    pinned.forEachIndexed { index, item ->
                                        HistoryRow(
                                            item = item,
                                            timeText = timeFmt.format(item.timestamp.atZone(zone)),
                                            showBackground = false,
                                            onClick = { onItemSelected(item) },
                                            onLongClick = {
                                                scope.launch {
                                                    settingsRepo.togglePinnedHistoryItem(item.nutrients.foodItem)
                                                }
                                            }
                                        )
                                        if (index < pinned.lastIndex) {
                                            HorizontalDivider(
                                                modifier = Modifier.padding(horizontal = 16.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(unpinned, key = { it.nutrients.foodItem }) { item ->
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
    showBackground: Boolean = true,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val view = LocalView.current
    val content = @Composable {
        Box(modifier = Modifier.padding(12.dp)) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.nutrients.foodItem,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${item.nutrients.calories.toInt()} ${stringResource(R.string.unit_kcal)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = item.accentColor ?: MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Black
                    )
                    if (item.isPinned) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            Icons.Rounded.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = item.accentColor ?: MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Spacer(Modifier.height(2.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${item.nutrients.servingSize} • $timeText",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    NutrientIconList(item.nutrients)
                }
            }
        }
    }

    if (showBackground) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = item.accentColor?.copy(alpha = 0.25f) ?: MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongClick()
                    }
                )
        ) {
            content()
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    },
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        onLongClick()
                    }
                )
        ) {
            content()
        }
    }
}
