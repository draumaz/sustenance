package dev.easonhuang.sustenance.ui.dashboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.DpSize
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import dev.easonhuang.sustenance.R
import dev.easonhuang.sustenance.data.HealthConnectManager
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.GoalsRepository
import dev.easonhuang.sustenance.data.MetricSummary
import dev.easonhuang.sustenance.ui.DashboardViewModel
import dev.easonhuang.sustenance.ui.components.MetricCard
import dev.easonhuang.sustenance.ui.components.ScallopedLoadingAnimation
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import java.time.Instant
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
    val view = LocalView.current
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
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
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
    onTimerClick: () -> Unit = {},
    onDateChanged: (Int) -> Unit = {},
) {
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(manager, goalsRepo, settingsRepo))
    val summariesMap by vm.summariesMap.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val dateOffset by vm.dateOffset.collectAsStateWithLifecycle()
    val lastLogTime by vm.lastLogTime.collectAsStateWithLifecycle()
    val lastLogTimerEnabled by vm.lastLogTimerEnabled.collectAsStateWithLifecycle()
    val fastingGoalHours by vm.fastingGoalHours.collectAsStateWithLifecycle()
    var currentTime by remember { mutableStateOf(Instant.now()) }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
    val view = LocalView.current
    val pullToRefreshState = rememberPullToRefreshState()

    LaunchedEffect(dateOffset) {
        topAppBarState.heightOffset = 0f
        topAppBarState.contentOffset = 0f
        onDateChanged(dateOffset)
    }

    LaunchedEffect(todayClickCount) {
        if (todayClickCount > 0) {
            vm.resetOffset()
        }
    }

    val greeting = when (dateOffset) {
        0 -> when (LocalTime.now().hour) {
            in 0..11 -> stringResource(R.string.greeting_morning)
            in 12..16 -> stringResource(R.string.greeting_afternoon)
            else -> stringResource(R.string.greeting_evening)
        }
        1 -> stringResource(R.string.yesterday)
        else -> LocalDate.now().minusDays(dateOffset.toLong()).format(DateTimeFormatter.ofPattern("EEEE, MMM d"))
    }

    // Refresh when permissions change (e.g. Food just granted) or when returning to the app.
    androidx.compose.runtime.LaunchedEffect(granted, dateOffset) {
        vm.refresh(showIndicator = false)
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        while(true) {
            delay(60000)
            currentTime = Instant.now()
        }
    }

    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        currentTime = Instant.now()
        vm.refresh(showIndicator = false)
        onPauseOrDispose { }
    }

    val pullDistance = remember { Animatable(0f) }
    val pullThreshold = 60f
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
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
                        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp), fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    SquishyIconButton(onClick = vm::refresh, contentDescription = stringResource(R.string.refresh)) {
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
            state = pullToRefreshState,
            indicator = {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = inner.calculateTopPadding() + 12.dp)
                        .graphicsLayer {
                            val pullProgress = pullToRefreshState.distanceFraction.coerceIn(0f, 1f)
                            // Start higher and slide down into view
                            translationY = (pullProgress * 40.dp.toPx()) - 35.dp.toPx()
                            alpha = pullProgress
                            scaleX = 0.5f + (pullProgress * 0.5f)
                            scaleY = 0.5f + (pullProgress * 0.5f)
                        }
                ) {
                    ScallopedLoadingAnimation(
                        size = DpSize(50.dp, 50.dp),
                        bumpsCount = 3f
                    )
                }
            },
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
                                    ScallopedLoadingAnimation(size = androidx.compose.ui.unit.DpSize(
                                        150.dp,
                                        150.dp
                                    ), modifier = Modifier.offset(y = (-75).dp),)
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
                                    if (energyGroup.isNotEmpty() || foodGroup.isNotEmpty() || (lastLogTimerEnabled && targetOffset == 0)) {
                                        item {
                                            MetricSection(
                                                title = stringResource(R.string.section_energy),
                                                items = energyGroup,
                                                columns = 2,
                                                onOpenMetric = { onOpenMetric(it, targetOffset) },
                                                onManagePermissions = onManagePermissions,
                                                bottomContent = {
                                                    foodGroup.forEach { summary ->
                                                        MetricCard(
                                                            summary = summary,
                                                            onClick = {
                                                                if (summary.granted) onOpenMetric(summary.metric, targetOffset) else onManagePermissions()
                                                            }
                                                        )
                                                    }
                                                    if (lastLogTimerEnabled && targetOffset == 0) {
                                                        TimerChip(lastLogTime, fastingGoalHours, currentTime, onClick = onTimerClick)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                    if (macrosGroup.isNotEmpty()) {
                                        item {
                                            MetricSection(
                                                title = stringResource(R.string.section_macros),
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
                                                title = stringResource(R.string.section_micros),
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
    onManagePermissions: () -> Unit,
    extraContent: (@Composable () -> Unit)? = null,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null
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
            if (items.isNotEmpty() || extraContent != null) {
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
                    extraContent?.let {
                        Box(Modifier.weight(1f)) {
                            it()
                        }
                    }
                    // Fill remaining space in the last row if needed
                    val totalItems = items.size + (if (extraContent != null) 1 else 0)
                    val remainder = totalItems % columns
                    if (remainder != 0) {
                        repeat(columns - remainder) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            if (bottomContent != null) {
                if (items.isNotEmpty() || extraContent != null) {
                    Spacer(Modifier.height(8.dp))
                }
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bottomContent()
                }
            }
        }
    }
}

@Composable
private fun TimerChip(lastLogTime: Instant?, goalHours: Float, currentTime: Instant, onClick: () -> Unit = {}) {
    val view = LocalView.current
    val duration = lastLogTime?.let { Duration.between(it, currentTime) } ?: Duration.ZERO
    val hours = duration.toHours()
    val minutes = duration.toMinutes() % 60
    val formatted = stringResource(R.string.hour_minute_format, hours, minutes)

    val progress = if (goalHours > 0f) (duration.toMinutes().toFloat() / (goalHours * 60f)).coerceIn(0f, 1f) else 0f
    
    val accent = MaterialTheme.colorScheme.primary
    val progressColor = accent.copy(alpha = 0.7f)
    val textShadow = androidx.compose.ui.graphics.Shadow(
        color = Color.Black.copy(alpha = 0.4f),
        offset = androidx.compose.ui.geometry.Offset(0f, 1f),
        blurRadius = 4f
    )

    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        modifier = Modifier
            .height(48.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
            if (progress > 0.01f) {
                Box(
                    Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(progressColor)
                        .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(if (progress > 0.05f) Color.Black.copy(alpha = 0.25f) else accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = if (progress > 0.05f) Color.White else accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.time_since_last_ate),
                        style = MaterialTheme.typography.labelSmall.copy(
                            shadow = if (progress > 0.05f) textShadow else null
                        ),
                        fontWeight = FontWeight.Medium,
                        color = if (progress > 0.05f) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = formatted,
                        style = MaterialTheme.typography.labelMedium.copy(
                            shadow = if (progress > 0.05f) textShadow else null
                        ),
                        fontWeight = FontWeight.Bold,
                        color = if (progress > 0.05f) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
