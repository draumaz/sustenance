package dev.easonhuang.sustenance.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
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
import dev.easonhuang.sustenance.ui.components.ScallopedLoadingAnimation
import dev.easonhuang.sustenance.ui.components.ScallopedPillShape
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.SizeTransform

@Composable
fun SquishyIconButton(
    onClick: () -> Unit,
    contentDescription: String?,
    icon: @Composable () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scaleX = remember { Animatable(1f) }
    val scaleY = remember { Animatable(1f) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            launch { scaleX.animateTo(1.25f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow)) }
            launch { scaleY.animateTo(0.7f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessLow)) }
        } else {
            launch { scaleX.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
            launch { scaleY.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
        }
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                this.scaleX = scaleX.value
                this.scaleY = scaleY.value
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        icon()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    settingsRepo: dev.easonhuang.sustenance.data.SettingsRepository,
    granted: Set<String>,
    bottomInset: androidx.compose.ui.unit.Dp,
    todayClickCount: Int = 0,
    onOpenMetric: (Metric, Int) -> Unit,
    onManagePermissions: () -> Unit,
    onDateChanged: () -> Unit = {},
) {
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(manager, goalsRepo, settingsRepo))
    val summariesMap by vm.summariesMap.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val dateOffset by vm.dateOffset.collectAsStateWithLifecycle()
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(dateOffset) {
        topAppBarState.heightOffset = 0f
        topAppBarState.contentOffset = 0f
        onDateChanged()
    }

    LaunchedEffect(todayClickCount) {
        if (todayClickCount > 0) {
            vm.resetOffset()
        }
    }

    val greeting = when (dateOffset) {
        0 -> when (LocalTime.now().hour) {
            in 0..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            else -> "Good evening"
        }
        1 -> "Yesterday"
        else -> LocalDate.now().minusDays(dateOffset.toLong()).format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    }

    // Refresh when permissions change (e.g. Food just granted) or when returning to the app.
    androidx.compose.runtime.LaunchedEffect(granted, dateOffset) {
        vm.refresh(showIndicator = false)
    }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        vm.refresh(showIndicator = false)
        onPauseOrDispose { }
    }

    val pullDistance = remember { Animatable(0f) }
    val pullThreshold = 50f
    val scope = rememberCoroutineScope()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                // If we are at the bottom and pulling UP (finger moves UP, available.y < 0)
                if (source == NestedScrollSource.UserInput && available.y < 0) {
                    val newPull = (pullDistance.value - available.y * 0.5f).coerceAtMost(pullThreshold * 1.5f)
                    scope.launch { pullDistance.snapTo(newPull) }
                    
                    // Preload yesterday's data as we pull up
                    if (newPull > pullThreshold * 0.4f) {
                        vm.preload(dateOffset + 1)
                    }

                    return androidx.compose.ui.geometry.Offset(0f, available.y)
                }
                return super.onPostScroll(consumed, available, source)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pullDistance.value >= pullThreshold) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.moveBack()
                }
                pullDistance.animateTo(0f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
                return super.onPostFling(consumed, available)
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(greeting, style = MaterialTheme.typography.labelSmall.copy(fontSize = 15.sp), color = MaterialTheme.colorScheme.primary)
                        Text("Sustenance", style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    SquishyIconButton(onClick = vm::refresh, contentDescription = "Refresh") {
                        if (refreshing) {
                            CircularProgressIndicator(Modifier.size(24.dp).padding(4.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
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
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                Box(Modifier.padding(top = inner.calculateTopPadding()).fillMaxSize()) {
                    AnimatedContent(
                        targetState = dateOffset,
                        transitionSpec = {
                            val springSpec = spring<androidx.compose.ui.unit.IntOffset>(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow
                            )
                            val gap = 150 // Material 3 Expressive styled gap in pixels
                            if (targetState > initialState) {
                                // Moving further back in time: current day slides DOWN, yesterday slides DOWN from top
                                (slideInVertically(animationSpec = springSpec) { height -> -height - gap } + scaleIn(initialScale = 0.98f) + fadeIn(tween(300, 100)))
                                    .togetherWith(slideOutVertically(animationSpec = springSpec) { height -> height + gap } + scaleOut(targetScale = 0.98f) + fadeOut(tween(300)))
                                    .using(SizeTransform(clip = false))
                            } else {
                                // Moving forward in time: current day slides UP, today slides UP from bottom
                                (slideInVertically(animationSpec = springSpec) { height -> height + gap } + scaleIn(initialScale = 0.98f) + fadeIn(tween(300, 100)))
                                    .togetherWith(slideOutVertically(animationSpec = springSpec) { height -> -height - gap } + scaleOut(targetScale = 0.98f) + fadeOut(tween(300)))
                                    .using(SizeTransform(clip = false))
                            }
                        },
                        label = "dashboard_day_transition"
                    ) { targetOffset ->
                        val listState = rememberLazyListState()
                        val data = summariesMap[targetOffset]
                        
                        AnimatedContent(
                            targetState = data != null,
                            transitionSpec = {
                                fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                            },
                            label = "data_loading_transition"
                        ) { isLoaded ->
                            if (!isLoaded) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    ScallopedLoadingAnimation(Modifier.offset(y = (-75).dp))
                                }
                            } else {
                                val currentData = summariesMap[targetOffset] ?: emptyList()
                                val energyMetrics = listOf(Metric.TOTAL_CALORIES, Metric.CALORIC_BALANCE)
                                val foodMetric = listOf(Metric.FOOD)
                                val microMetrics = listOf(Metric.SUGAR, Metric.SATURATED_FAT, Metric.SODIUM)

                                val energyGroup = currentData.filter { it.metric in energyMetrics }
                                val foodGroup = currentData.filter { it.metric in foodMetric }
                                val microsGroup = microMetrics.mapNotNull { m -> currentData.find { it.metric == m } }
                                val macrosGroup = currentData.filter { it.metric !in (energyMetrics + foodMetric + microMetrics) }

                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                                        .graphicsLayer {
                                            // Pull UP effect: Move the list UP as we pull
                                            translationY = -pullDistance.value * 0.3f
                                        },
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
                                                onOpenMetric = { onOpenMetric(it, targetOffset) },
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
                                                onOpenMetric = { onOpenMetric(it, targetOffset) },
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
                                                onOpenMetric = { onOpenMetric(it, targetOffset) },
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
                                                onOpenMetric = { onOpenMetric(it, targetOffset) },
                                                onManagePermissions = onManagePermissions
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                val progress = (pullDistance.value / pullThreshold).coerceIn(0f, 1f)
                val isReady = pullDistance.value >= pullThreshold

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height((pullDistance.value * 0.8f).dp + bottomInset)
                        .clip(RoundedCornerShape(topStart = 48.dp, topEnd = 48.dp))
                        .background(
                            if (isReady) MaterialTheme.colorScheme.primaryContainer 
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = progress * 0.8f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Box(Modifier.padding(bottom = bottomInset).fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack, 
                            null, 
                            modifier = Modifier
                                .size(40.dp)
                                .graphicsLayer { 
                                    rotationZ = if (isReady) -90f else -90f + (progress * 180f)
                                    scaleX = 0.8f + progress * 0.4f
                                    scaleY = 0.8f + progress * 0.4f
                                    alpha = progress
                                },
                            tint = if (isReady) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                        )
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
