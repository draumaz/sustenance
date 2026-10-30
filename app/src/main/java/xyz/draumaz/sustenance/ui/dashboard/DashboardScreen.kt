package xyz.draumaz.sustenance.ui.dashboard

import android.view.HapticFeedbackConstants
import android.os.Build
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animate
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import androidx.compose.foundation.background
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
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
import xyz.draumaz.sustenance.ui.components.ExpressiveSprings
import xyz.draumaz.sustenance.ui.components.PreviousDayPullIndicator
import java.time.LocalDate
import java.time.LocalTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.platform.LocalConfiguration
import android.text.format.DateFormat
import xyz.draumaz.sustenance.data.FastingStretch
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.AnimatedContent
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



    // Pull-up-to-view-previous-day: distance (px) the content has been pulled up. Commits on
    // release once past the threshold, like pull-to-refresh.
    val density = LocalDensity.current
    val pullThreshold = with(density) { PullThreshold.toPx() }
    // pullDistance offsets the content of pullDay only, so on commit the outgoing day keeps its
    // position and slides out from there instead of snapping back. indicatorPull drives the pill.
    var pullDistance by remember { mutableFloatStateOf(0f) }
    var pullDay by remember { mutableIntStateOf(0) }
    var indicatorPull by remember { mutableFloatStateOf(0f) }
    val pullArmed by remember { derivedStateOf { indicatorPull >= pullThreshold } }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

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
        listState.scrollToItem(0)
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
        else -> dayLabel(dateOffset)
    }
    val previousDayLabel = dayLabel(dateOffset + 1)

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

    fun settlePull() {
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(indicatorPull, 0f, animationSpec = PullSettleSpec) { value, _ ->
                indicatorPull = value
                pullDistance = value
            }
        }
    }

    fun commitPull() {
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        vm.moveBack()
        settleJob?.cancel()
        settleJob = scope.launch {
            animate(indicatorPull, 0f, animationSpec = PullSettleSpec) { value, _ -> indicatorPull = value }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (pullDay != dateOffset) {
                    // First drag on a newly shown day: start a fresh pull for it.
                    settleJob?.cancel()
                    pullDay = dateOffset
                    pullDistance = 0f
                    indicatorPull = 0f
                }
                val current = pullDistance
                val next = when {
                    // Dragging up with nothing left to scroll: pull the content up.
                    available.y < 0f && !listState.canScrollForward ->
                        (current - available.y * PullResistance).coerceAtMost(pullThreshold * 1.25f)
                    // Dragging back down: release the pull before the list scrolls.
                    available.y > 0f && current > 0f ->
                        (current - available.y * PullResistance).coerceAtLeast(0f)
                    else -> return Offset.Zero
                }
                settleJob?.cancel()
                pullDistance = next
                indicatorPull = next

                if (current < pullThreshold && next >= pullThreshold) {
                    view.performHapticFeedback(ThresholdActivateHaptic)
                } else if (current >= pullThreshold && next < pullThreshold) {
                    view.performHapticFeedback(ThresholdDeactivateHaptic)
                }
                if (next > pullThreshold * 0.25f) vm.preload(dateOffset + 1)

                val consumedY = if (available.y < 0f) available.y else (current - next) / PullResistance
                return Offset(0f, consumedY)
            }

            // The drag has ended (always dispatched, after its last scroll delta): commit if
            // armed, settle, and swallow the fling so it doesn't scroll the list.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullDay != dateOffset || pullDistance <= 0f) return Velocity.Zero
                if (pullDistance >= pullThreshold) commitPull() else settlePull()
                return available
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
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.zIndex(0f)
                )
            },
        ) { inner ->
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    currentTime = Instant.now()
                    vm.refresh()
                },
                state = pullToRefreshState,
                indicator = {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = inner.calculateTopPadding() + 12.dp)
                            .zIndex(2f)
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
                    AnimatedContent(
                        targetState = dateOffset,
                        modifier = Modifier
                            .zIndex(1f),
                        transitionSpec = {
                            // Older days enter from below (the direction of the pull); returning
                            // toward today reverses it. Shared-axis Y with expressive springs.
                            val dir = if (targetState > initialState) 1 else -1
                            val slide = ExpressiveSprings.defaultSpatial(IntOffset.VisibilityThreshold)
                            val fade = ExpressiveSprings.defaultEffects<Float>()
                            (slideInVertically(slide) { dir * it / 5 } + fadeIn(fade)) togetherWith
                                (slideOutVertically(slide) { -dir * it / 5 } + fadeOut(fade))
                        },
                        label = "dashboard_day_transition"
                    ) { targetOffset ->
                            // Once a pulled day has finished sliding out, drop its leftover offset.
                            DisposableEffect(Unit) {
                                onDispose { if (pullDay == targetOffset) pullDistance = 0f }
                            }
                            val data = summariesMap[targetOffset]

                            val currentData = data ?: summariesMap[dateOffset] ?: summariesMap[0] ?: manager.initialSummaries()

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
                                        translationY = if (targetOffset == pullDay) -pullDistance else 0f
                                    },
                                contentPadding = PaddingValues(
                                    start = 16.dp, end = 16.dp,
                                    top = inner.calculateTopPadding() + 3.dp,
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
                                            onOpenMetric = { onOpenMetric(it, targetOffset) },
                                            onManagePermissions = onManagePermissions,
                                            bottomContent = {
                                                foodGroup.forEach { summary ->
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
                                                if (lastLogTimerEnabled) {
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
                    PreviousDayPullIndicator(
                        label = previousDayLabel,
                        progressProvider = { indicatorPull / pullThreshold },
                        armed = pullArmed,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = bottomInset + 24.dp)
                            .zIndex(2f),
                    )
                    }
                }
            }
        }
}

private val PullThreshold = 72.dp
private const val PullResistance = 0.5f

// Critically damped so a cancelled pull glides back without overshooting past rest.
private val PullSettleSpec = spring<Float>(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow)

private val ThresholdActivateHaptic =
    if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE
    else HapticFeedbackConstants.CONTEXT_CLICK
private val ThresholdDeactivateHaptic =
    if (Build.VERSION.SDK_INT >= 34) HapticFeedbackConstants.GESTURE_THRESHOLD_DEACTIVATE
    else HapticFeedbackConstants.CLOCK_TICK

@Composable
private fun dayLabel(offset: Int): String {
    if (offset == 1) return stringResource(R.string.yesterday)
    val locale = LocalConfiguration.current.locales[0]
    // Locale-appropriate order, e.g. "Monday, Oct 27" in English, "понедельник, 27 окт." in Russian.
    val formatter = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEEMMMd"), locale)
    return LocalDate.now().minusDays(offset.toLong()).format(formatter)
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
    extraContent: (@Composable () -> Unit)? = null,
    bottomContent: (@Composable ColumnScope.() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
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

    val rawProgress = if (goalHours > 0f) (duration.toMinutes().toFloat() / (goalHours * 60f)).coerceIn(0f, 1f) else 0f
    
    val animatedProgress by animateFloatAsState(
        targetValue = if (rawProgress > 0.01f) rawProgress else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "timer_progress_morph"
    )

    val hasFill = animatedProgress > 0.05f
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
            if (animatedProgress > 0.001f) {
                Box(
                    Modifier
                        .fillMaxWidth(animatedProgress.coerceIn(0.01f, 1f))
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
                        .background(if (hasFill) Color.Black.copy(alpha = 0.25f) else accent.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = if (hasFill) Color.White else accent,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.time_since_last_ate),
                        style = MaterialTheme.typography.labelSmall.copy(
                            shadow = if (hasFill) textShadow else null
                        ),
                        fontWeight = FontWeight.Medium,
                        color = if (hasFill) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    AnimatedContent(
                        targetState = formatted,
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.96f))
                                .togetherWith(fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 1.04f))
                                .using(SizeTransform(clip = false))
                        },
                        label = "timer_value_morph"
                    ) { valueText ->
                        Text(
                            text = valueText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                shadow = if (hasFill) textShadow else null
                            ),
                            fontWeight = FontWeight.Bold,
                            color = if (hasFill) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
