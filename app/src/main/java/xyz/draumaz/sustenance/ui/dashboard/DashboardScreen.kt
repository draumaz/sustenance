package xyz.draumaz.sustenance.ui.dashboard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.minutes
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
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import xyz.draumaz.sustenance.BuildConfig
import xyz.draumaz.sustenance.R
import xyz.draumaz.sustenance.data.HealthConnectManager
import xyz.draumaz.sustenance.data.Metric
import xyz.draumaz.sustenance.data.GoalsRepository
import xyz.draumaz.sustenance.data.MetricSummary
import xyz.draumaz.sustenance.ui.DashboardViewModel
import xyz.draumaz.sustenance.ui.components.MetricCard
import xyz.draumaz.sustenance.ui.components.ScallopedLoadingAnimation
import xyz.draumaz.sustenance.ui.components.opticalDepthCard
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import android.text.format.DateFormat
import xyz.draumaz.sustenance.data.FastingStretch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.blur
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.AnimatedVisibility
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    settingsRepo: xyz.draumaz.sustenance.data.SettingsRepository,
    granted: Set<String>,
    bottomInset: androidx.compose.ui.unit.Dp,
    todayClickCount: Int = 0,
    onOpenMetric: (Metric, Int) -> Unit,
    onManagePermissions: () -> Unit,
    onTimerClick: () -> Unit = {},
    onDateChanged: (Int) -> Unit = {},
    onResetView: () -> Unit = {},
    onLoadingChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val app = context.applicationContext as xyz.draumaz.sustenance.SustenanceApp
    val vm: DashboardViewModel = viewModel(factory = DashboardViewModel.factory(app, manager, goalsRepo, settingsRepo))
    val summariesMap by vm.summariesMap.collectAsStateWithLifecycle()
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val dateOffset by vm.dateOffset.collectAsStateWithLifecycle()
    val lastLogTime by vm.lastLogTime.collectAsStateWithLifecycle()
    val lastLogTimerEnabled by vm.lastLogTimerEnabled.collectAsStateWithLifecycle()
    val longestFastingMap by vm.longestFastingMap.collectAsStateWithLifecycle()
    val fastingGoalHours by vm.fastingGoalHours.collectAsStateWithLifecycle()
    var currentTime by remember { mutableStateOf(Instant.now()) }
    val topAppBarState = rememberTopAppBarState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
    val view = LocalView.current
    val pullToRefreshState = rememberPullToRefreshState()

    val currentSummaryData = summariesMap[dateOffset]
    val isLoading = currentSummaryData == null || currentSummaryData.all { it.value == "-" }

    LaunchedEffect(isLoading) {
        onLoadingChanged(isLoading)
    }

    var hapticTriggered by remember { mutableStateOf(value = false) }
    LaunchedEffect(pullToRefreshState.distanceFraction) {
        if (pullToRefreshState.distanceFraction >= 1f) {
            if (!hapticTriggered) {
                view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                hapticTriggered = true
            }
        } else {
            hapticTriggered = false
        }
    }

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
        0 -> if (BuildConfig.DEBUG) {
            "${BuildConfig.VERSION_NAME} (debug)"
        } else {
            when (LocalTime.now().hour) {
                in 0..11 -> stringResource(R.string.greeting_morning)
                in 12..16 -> stringResource(R.string.greeting_afternoon)
                else -> stringResource(R.string.greeting_evening)
            }
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
            delay(1.minutes)
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
    val pullProgress = (pullDistance.value / pullThreshold)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                if (pullDistance.value > 0f && available.y > 0f && source == NestedScrollSource.UserInput) {
                    val consumedY = minOf(pullDistance.value / 0.5f, available.y)
                    val newPull = (pullDistance.value - consumedY * 0.5f).coerceAtLeast(0f)
                    scope.launch { pullDistance.snapTo(newPull) }
                    return androidx.compose.ui.geometry.Offset(0f, consumedY)
                }
                return super.onPreScroll(available, source)
            }

            override fun onPostScroll(
                consumed: androidx.compose.ui.geometry.Offset,
                available: androidx.compose.ui.geometry.Offset,
                source: NestedScrollSource
            ): androidx.compose.ui.geometry.Offset {
                // If we are at the bottom and pulling UP (finger moves UP, available.y < 0)
                if ((source == NestedScrollSource.UserInput) && (available.y < 0)) {
                    val newPull = (pullDistance.value - (available.y * 0.5f)).coerceAtMost(pullThreshold * 1.5f)
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
                val wasPulling = pullDistance.value > 0f
                if (pullDistance.value >= pullThreshold) {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    vm.moveBack()
                } else if (wasPulling || topAppBarState.heightOffset != 0f) {
                    onResetView()
                    val initialOffset = topAppBarState.heightOffset
                    if (initialOffset != 0f) {
                        scope.launch {
                            val anim = Animatable(initialOffset)
                            anim.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) {
                                topAppBarState.heightOffset = value
                            }
                        }
                    }
                    topAppBarState.contentOffset = 0f
                    if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                }
                pullDistance.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                return super.onPostFling(consumed, available)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(nestedScrollConnection),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = greeting,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 15.sp),
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.titleLarge.copy(fontSize = 25.sp),
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
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
                                translationY = (pullProgress * 40.dp.toPx()) - 35.dp.toPx()
                                alpha = pullProgress
                                scaleX = 0.5f + (pullProgress * 0.5f)
                                scaleY = 0.5f + (pullProgress * 0.5f)
                            }
                    ) {
                        ScallopedLoadingAnimation(
                            size = DpSize(50.dp, 50.dp),
                            bumpsCount = 3f,
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
                                fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                            },
                            label = "dashboard_day_transition"
                        ) { targetOffset ->
                            val data = summariesMap[targetOffset]

                            val currentData = data ?: summariesMap[dateOffset] ?: summariesMap[0] ?: manager.initialSummaries()
                            val activePullProgress = if (targetOffset == dateOffset) pullProgress else 0f
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
                                        // Base pull UP offset
                                        translationY = -activePullProgress * pullThreshold * 0.15f
                                    },
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp,
                                    top = 3.dp,
                                    bottom = bottomInset + 16.dp,
                                ),
                                verticalArrangement = Arrangement.SpaceBetween,
                            ) {
                                if (energyGroup.isNotEmpty() || foodGroup.isNotEmpty() || lastLogTimerEnabled) {
                                    item {
                                        MetricSection(
                                            title = stringResource(R.string.section_energy),
                                            items = energyGroup,
                                            columns = 2,
                                            sectionIndex = 0,
                                            pullProgress = activePullProgress,
                                            onOpenMetric = { onOpenMetric(it, targetOffset) },
                                            onManagePermissions = onManagePermissions,
                                            bottomContent = {
                                                foodGroup.forEachIndexed { foodIdx, summary ->
                                                    Box(
                                                        Modifier.opticalDepthCard(
                                                            sectionIndex = 0,
                                                            cardIndex = energyGroup.size + foodIdx,
                                                            pullProgress = activePullProgress,
                                                            accentColor = summary.metric.accent,
                                                            cornerRadius = 16.dp
                                                        )
                                                    ) {
                                                        MetricCard(
                                                            summary = summary,
                                                            onClick = {
                                                                if (summary.granted) {
                                                                    onOpenMetric(summary.metric, targetOffset)
                                                                } else {
                                                                    onManagePermissions()
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                                if (lastLogTimerEnabled) {
                                                    Box(
                                                        Modifier.opticalDepthCard(
                                                            sectionIndex = 0,
                                                            cardIndex = energyGroup.size + foodGroup.size,
                                                            pullProgress = activePullProgress,
                                                            cornerRadius = 16.dp
                                                        )
                                                    ) {
                                                        TimerChip(
                                                            lastLogTime = if (targetOffset == 0) lastLogTime else null,
                                                            stretch = if (targetOffset > 0) longestFastingMap[targetOffset] else null,
                                                            isToday = targetOffset == 0,
                                                            goalHours = fastingGoalHours,
                                                            currentTime = currentTime,
                                                            onClick = onTimerClick
                                                        )
                                                    }
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
                                            sectionIndex = 1,
                                            pullProgress = activePullProgress,
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
                                            sectionIndex = 2,
                                            pullProgress = activePullProgress,
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
    modifier: Modifier = Modifier,
    sectionIndex: Int = 0,
    pullProgress: Float = 0f,
    extraContent: (@Composable () -> Unit)? = null,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .opticalDepthCard(
                sectionIndex = sectionIndex,
                cardIndex = 0,
                pullProgress = pullProgress,
                cornerRadius = 28.dp
            ),
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
                    items.forEachIndexed { cardIdx, summary ->
                        Box(
                            Modifier
                                .weight(1f)
                                .opticalDepthCard(
                                    sectionIndex = sectionIndex,
                                    cardIndex = cardIdx,
                                    pullProgress = pullProgress,
                                    accentColor = summary.metric.accent,
                                    cornerRadius = 16.dp
                                )
                        ) {
                            MetricCard(
                                summary = summary,
                                onClick = {
                                    if (summary.granted) onOpenMetric(summary.metric) else onManagePermissions()
                                }
                            )
                        }
                    }
                    extraContent?.let {
                        Box(
                            Modifier
                                .weight(1f)
                                .opticalDepthCard(
                                    sectionIndex = sectionIndex,
                                    cardIndex = items.size,
                                    pullProgress = pullProgress,
                                    cornerRadius = 16.dp
                                )
                        ) {
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
private fun TimerChip(
    lastLogTime: Instant?,
    stretch: FastingStretch? = null,
    isToday: Boolean = true,
    goalHours: Float,
    currentTime: Instant,
    onClick: () -> Unit = {}
) {
    val view = LocalView.current
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
    val timeFormatter = remember(is24Hour) {
        if (is24Hour) DateTimeFormatter.ofPattern("H:mm")
        else DateTimeFormatter.ofPattern("h:mm a")
    }

    val (duration, formatted) = if (isToday) {
        val d = lastLogTime?.let { Duration.between(it, currentTime) } ?: Duration.ZERO
        val hours = d.toHours()
        val minutes = d.toMinutes() % 60
        val fmt = stringResource(R.string.hour_minute_format, hours, minutes)
        d to fmt
    } else {
        if (stretch != null) {
            val d = stretch.duration
            val hours = d.toHours()
            val minutes = d.toMinutes() % 60
            val durationStr = stringResource(R.string.hour_minute_format, hours, minutes)
            val startStr = timeFormatter.format(stretch.startTime.atZone(zone))
            val endStr = timeFormatter.format(stretch.endTime.atZone(zone))
            val fmt = "$durationStr ($startStr - $endStr)"
            d to fmt
        } else {
            Duration.ZERO to "-"
        }
    }

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
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
