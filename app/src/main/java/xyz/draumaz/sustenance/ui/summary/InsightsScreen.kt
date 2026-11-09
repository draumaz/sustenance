package xyz.draumaz.sustenance.ui.summary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.background
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import android.view.HapticFeedbackConstants
import xyz.draumaz.sustenance.R
import xyz.draumaz.sustenance.data.GoalsRepository
import xyz.draumaz.sustenance.data.HealthConnectManager
import xyz.draumaz.sustenance.data.SettingsRepository
import androidx.compose.animation.animateContentSize
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import xyz.draumaz.sustenance.ui.components.withBoldAmounts
import xyz.draumaz.sustenance.ui.theme.GoogleSansFlexRoundedFontFamily
import xyz.draumaz.sustenance.util.DaySummaryText
import xyz.draumaz.sustenance.data.Metric
import xyz.draumaz.sustenance.data.WeeklyStat
import xyz.draumaz.sustenance.data.formatValue
import xyz.draumaz.sustenance.ui.components.GoalRing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    manager: HealthConnectManager,
    goalsRepo: GoalsRepository,
    settingsRepo: SettingsRepository,
    bottomInset: androidx.compose.ui.unit.Dp,
    onOpenMetric: (Metric) -> Unit,
    onBack: () -> Unit = {},
) {
    val vm: InsightsViewModel = viewModel(factory = InsightsViewModel.factory(manager, goalsRepo, settingsRepo))
    val state by vm.state.collectAsStateWithLifecycle()
    val summary by vm.summary.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var statCardHeight by remember { mutableIntStateOf(0) }
    val summaryVisible = summary != DaySummaryState.Hidden
    // The ViewModel shows the card (spinning) before the stats load, but if it does arrive later
    // (e.g. the first food of the day is logged while Insights is open), LazyColumn keeps the old
    // first card in place and the new one lands above the top edge. If the user was at the top,
    // the old first card is now item 1 at offset 0: scroll back up to reveal the summary.
    LaunchedEffect(summaryVisible) {
        if (!summaryVisible) return@LaunchedEffect
        withFrameNanos { } // let the list lay the new card out first
        if (listState.firstVisibleItemIndex == 1 && listState.firstVisibleItemScrollOffset == 0) {
            listState.scrollToItem(0)
        }
    }
    val refreshing by vm.refreshing.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var editing by remember { mutableStateOf<WeeklyStat?>(null) }
    val goals by goalsRepo.goals.collectAsStateWithLifecycle(emptyMap())
    val programmedDeficit = (goals[Metric.CALORIC_BALANCE] ?: 0f) > 0
    val view = LocalView.current
    val pullToRefreshState = rememberPullToRefreshState()

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

    LifecycleResumeEffect(Unit) {
        vm.refresh(showIndicator = false)
        onPauseOrDispose { }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(R.string.summary_subtitle),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 15.sp),
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.summary_title),
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
                )
            )
        },
    ) { inner ->
        PullToRefreshBox(
            state = pullToRefreshState,
            isRefreshing = refreshing,
            onRefresh = vm::refresh,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = refreshing,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = inner.calculateTopPadding() + 12.dp)
                )
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = inner.calculateTopPadding() + 16.dp,
                    bottom = bottomInset + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (summary != DaySummaryState.Hidden) {
                    item(key = "ai_summary") {
                        DaySummaryCard(
                            state = summary,
                            collapsedHeight = with(density) { statCardHeight.toDp() },
                            onRetry = vm::retrySummary,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
                if (!state.loading && state.stats.isEmpty()) {
                    item { EmptyState() }
                }
                items(state.stats, key = { it.metric.key }) { stat ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + slideInVertically { it / 4 },
                        // The summary card's collapsed height follows the first stat card.
                        modifier = if (stat == state.stats.first()) {
                            Modifier.onSizeChanged { statCardHeight = it.height }
                        } else Modifier,
                    ) {
                        InsightCard(
                            stat = stat,
                            onClick = { onOpenMetric(stat.metric) },
                            onEdit = { editing = stat },
                            editEnabled = !(stat.metric == Metric.FOOD && programmedDeficit)
                        )
                    }
                }
            }
        }
    }

    editing?.let { stat ->
        GoalEditDialog(
            stat = stat,
            onDismiss = { editing = null },
            onSave = { value -> vm.setGoal(stat.metric, value); editing = null },
        )
    }
}

/**
 * Gemini's take on the day. Collapsed it matches the stat cards' height ([collapsedHeight], 0
 * until one has been measured) and shows just the headline; tapping expands the full summary.
 * Shows a spinner until Gemini answers, and a tap retries if it couldn't.
 */
@Composable
private fun DaySummaryCard(
    state: DaySummaryState,
    collapsedHeight: Dp,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    val ready = state is DaySummaryState.Ready
    val yesterday = (state as? DaySummaryState.Ready)?.yesterday ?: (state as? DaySummaryState.Loading)?.yesterday ?: false
    val open = expanded && ready
    Card(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            if (ready) expanded = !expanded else onRetry()
        },
        enabled = ready || state is DaySummaryState.Failed,
        modifier = modifier
            .fillMaxWidth()
            // Before the height so the expand/collapse animates instead of snapping.
            .animateContentSize()
            .then(if (!open && collapsedHeight > 0.dp) Modifier.height(collapsedHeight) else Modifier),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
            disabledContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(if (yesterday) R.string.insights_summary_title_yesterday else R.string.insights_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.weight(1f),
                )
                if (ready) {
                    val rotation by animateFloatAsState(if (open) 180f else 0f, label = "summary_chevron")
                    Icon(
                        Icons.Rounded.ExpandMore,
                        contentDescription = stringResource(
                            if (open) R.string.insights_summary_collapse else R.string.insights_summary_expand
                        ),
                        modifier = Modifier.size(24.dp).rotate(rotation),
                    )
                }
            }
            Spacer(Modifier.height(if (open) 12.dp else 8.dp))
            when (state) {
                is DaySummaryState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(stringResource(R.string.insights_summary_loading), style = summaryBodyStyle())
                }
                is DaySummaryState.Ready -> SummaryBody(state.summary, expanded = open, yesterday = state.yesterday)
                is DaySummaryState.Failed -> Text(stringResource(state.messageRes), style = summaryBodyStyle())
                DaySummaryState.Hidden -> Unit
            }
        }
    }
}

/** Rounded Google Sans Flex, as for the goal setup blurbs: soft, explanatory copy. */
@Composable
private fun summaryBodyStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    fontFamily = GoogleSansFlexRoundedFontFamily,
    fontWeight = FontWeight.Normal,
    // The theme's feature settings pin ROND to 0, which would undo the rounded family.
    fontFeatureSettings = null,
    lineHeight = 22.sp,
)

@Composable
private fun ColumnScope.SummaryBody(summary: DaySummaryText, expanded: Boolean, yesterday: Boolean) {
    val body = summaryBodyStyle()
    if (!expanded) {
        // Collapsed: just the gist. weight(fill = false) caps it at the space the card's fixed
        // height leaves, so a long headline ends in an ellipsis instead of being sliced mid-line
        // (Text ellipsises against a height limit, but maxLines alone ignores it).
        Text(
            summary.headline.ifEmpty { summary.points.firstOrNull().orEmpty() }.withBoldAmounts(),
            style = body.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Bold),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (summary.headline.isNotEmpty()) {
            Text(
                summary.headline.withBoldAmounts(),
                style = body.copy(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold),
            )
        }
        summary.points.forEach { SummaryBullet(it.withBoldAmounts(), body) }
        if (summary.tomorrow.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                // Looking back at yesterday, the tips are for today.
                stringResource(if (yesterday) R.string.insights_summary_today else R.string.insights_summary_tomorrow),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            summary.tomorrow.forEach { SummaryBullet(it.withBoldAmounts(), body) }
        }
    }
}

@Composable
private fun SummaryBullet(text: AnnotatedString, style: TextStyle) {
    Row {
        // Centred on the first line: (lineHeight 22sp - dot 6dp) / 2.
        Spacer(
            Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.size(12.dp))
        Text(text, style = style)
    }
}

@Composable
private fun InsightCard(
    stat: WeeklyStat,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    editEnabled: Boolean = true,
) {
    val view = LocalView.current
    val currentVal = if (stat.metric == Metric.CALORIC_BALANCE) stat.todayValue.coerceAtLeast(0f) else stat.todayValue
    val isOver = stat.goal > 0 && currentVal > stat.goal
    val progress = stat.progress
    
    val accent = when {
        stat.metric.moreIsBetter -> {
            if (isOver) stat.metric.accent
            else lerp(Color(0xFFEF5350), stat.metric.accent, progress)
        }
        else -> {
            if (isOver) Color(0xFFAB6161)
            else lerp(stat.metric.accent, Color(0xFFEF5350), progress)
        }
    }

    Card(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.18f)),
    ) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GoalRing(progress = stat.progress, color = accent, diameter = 64.dp, stroke = 10.dp) {
                    Text(
                        "${(stat.progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.size(20.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stat.metric.title(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    val displayToday = if (stat.metric == Metric.CALORIC_BALANCE) {
                        val absVal = kotlin.math.abs(stat.todayValue)
                        val sign = if (stat.todayValue > 0) "-" else if (stat.todayValue < 0) "+" else ""
                        "$sign${stat.metric.formatValue(absVal)}"
                    } else stat.metric.formatValue(stat.todayValue)

                    Text(
                        "$displayToday / ${stat.metric.formatValue(stat.goal)} ${stringResource(stat.metric.unitRes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    stat.insight?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            lineHeight = 16.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    DeltaChip(stat)
                }
                if (editEnabled) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.clip(CircleShape).background(accent.copy(alpha = 0.2f))
                    ) {
                        Icon(Icons.Rounded.Edit, contentDescription = stringResource(R.string.edit_goal_desc), tint = accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun DeltaChip(stat: WeeklyStat) {
    val delta = stat.deltaPercent
    val (icon, tint, label) = when {
        delta == null -> Triple(Icons.AutoMirrored.Rounded.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.new_label))
        delta >= 1f -> Triple(Icons.AutoMirrored.Rounded.TrendingUp, stat.metric.accent, stringResource(R.string.vs_yesterday, delta.roundToInt()))
        delta <= -1f -> Triple(Icons.AutoMirrored.Rounded.TrendingDown, MaterialTheme.colorScheme.error, stringResource(R.string.vs_yesterday, delta.roundToInt()))
        else -> Triple(Icons.AutoMirrored.Rounded.TrendingFlat, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.steady_label))
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.25f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.size(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = tint)
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.nothing_here),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Black
        )
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.grant_access_prompt),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
