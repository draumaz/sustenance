package xyz.draumaz.sustenance.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.delay
import xyz.draumaz.sustenance.R
import xyz.draumaz.sustenance.data.Metric
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Stable
class PredictiveBackState {
    var progress by mutableFloatStateOf(0f)
    var isSwipeActive by mutableStateOf(value = false)
}

// M3 Expressive floating toolbar spec.
private val ToolbarHeight = 64.dp
private val ToolbarContentPadding = 8.dp
private val ToolbarItemSpacing = 4.dp
private val ToolbarElevation = 6.dp // Elevation level 3
private val ToolbarScreenOffset = 16.dp

// M3 Expressive motion scheme springs. MotionScheme.expressive() is still internal in
// material3 1.4.0, so these mirror its ExpressiveMotionTokens.
private fun <T> expressiveSpatialSpec(): FiniteAnimationSpec<T> =
    spring(dampingRatio = 0.8f, stiffness = 380f)

private fun <T> expressiveEffectsSpec(): FiniteAnimationSpec<T> =
    spring(dampingRatio = 1f, stiffness = 1600f)

@Composable
fun ExpressiveNavigationBar(
    navController: NavHostController,
    destinations: List<xyz.draumaz.sustenance.ui.Dest>,
    predictiveBackState: PredictiveBackState,
    dateOffset: Int = 0,
    hasApiKey: Boolean = false,
    isCameraMode: Boolean = false,
    isBatchMode: Boolean = false,
    capturedBitmaps: List<Bitmap> = emptyList(),
    batchInfoText: String = "",
    onBatchInfoTextChange: (String) -> Unit = {},
    onSelectGallery: () -> Unit = {},
    onToggleTorch: () -> Unit = {},
    onCapture: () -> Unit = {},
    onCaptureBatch: () -> Unit = {},
    onFinishBatch: () -> Unit = {},
    isHistorySelected: Boolean = false,
    onHistoryClick: () -> Unit = {},
    onNavigate: (xyz.draumaz.sustenance.ui.Dest) -> Unit,
    onLogClick: () -> Unit = {},
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val isOnDetail = currentRoute?.startsWith("detail/") == true
    val detailMetric = if (isOnDetail) {
        navBackStackEntry?.arguments?.getString("metricKey")?.let { Metric.fromKey(it) }
    } else null

    val batchCount = capturedBitmaps.size

    // In batch mode the bar rides above the keyboard (tracking it frame by frame) so the info
    // box stays visible while typing. Everywhere else the keyboard simply covers the bar.
    val bottomInsets = if (isCameraMode && isBatchMode) {
        WindowInsets.ime.union(WindowInsets.navigationBars)
    } else {
        WindowInsets.navigationBars
    }

    // While a predictive back swipe is past halfway, preview the selection moving to the
    // destination being returned to.
    fun previewSelected(isSelected: Boolean, isCurrent: Boolean, isPrevious: Boolean): Boolean =
        when {
            !predictiveBackState.isSwipeActive -> isSelected
            isPrevious -> predictiveBackState.progress > 0.5f
            isCurrent -> predictiveBackState.progress <= 0.5f
            else -> isSelected
        }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(bottomInsets)
            .padding(bottom = ToolbarScreenOffset),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isCameraMode && capturedBitmaps.isNotEmpty()) {
            val lastPhotos = remember(capturedBitmaps) { capturedBitmaps.takeLast(8) }
            val photoShape = MaterialTheme.shapes.medium
            Row(
                modifier = Modifier.padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy((-16).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                lastPhotos.forEachIndexed { index, bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(56.dp)
                            .graphicsLayer {
                                rotationZ = (index - (lastPhotos.size / 2f)) * 7f
                                shadowElevation = 12f
                                shape = photoShape
                                clip = true
                            }
                            .background(MaterialTheme.colorScheme.surface)
                            .border(2.dp, MaterialTheme.colorScheme.surfaceVariant, photoShape),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Grows up out of the toolbar so it reads as part of it.
        AnimatedVisibility(
            visible = isCameraMode && isBatchMode,
            enter = expandVertically(expressiveSpatialSpec(), expandFrom = Alignment.Bottom) +
                scaleIn(expressiveSpatialSpec(), initialScale = 0.8f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                fadeIn(expressiveEffectsSpec()),
            exit = shrinkVertically(expressiveSpatialSpec(), shrinkTowards = Alignment.Bottom) +
                scaleOut(expressiveSpatialSpec(), targetScale = 0.8f, transformOrigin = TransformOrigin(0.5f, 1f)) +
                fadeOut(expressiveEffectsSpec()),
            label = "batch_info_box"
        ) {
            Surface(
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .width(320.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = ToolbarElevation
            ) {
                Box(
                    modifier = Modifier
                        .heightIn(min = 56.dp, max = 160.dp)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (batchInfoText.isEmpty()) {
                        Text(
                            text = stringResource(R.string.optional_info),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = batchInfoText,
                        onValueChange = onBatchInfoTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        singleLine = false,
                        maxLines = 5
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.animateContentSize(expressiveSpatialSpec()),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = ToolbarElevation
        ) {
            Row(
                modifier = Modifier
                    .height(ToolbarHeight)
                    .padding(horizontal = ToolbarContentPadding),
                horizontalArrangement = Arrangement.spacedBy(ToolbarItemSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isCameraMode) {
                    if ((batchCount == 0) && !isBatchMode) {
                        ToolbarAction(
                            label = stringResource(R.string.history),
                            icon = Icons.Rounded.History,
                            emphasized = isHistorySelected,
                        ) { if (!isHistorySelected) onHistoryClick() }
                        ToolbarAction(
                            label = stringResource(R.string.analyze),
                            icon = if (isHistorySelected) Icons.Rounded.Add else Icons.Rounded.FileUpload,
                            emphasized = !isHistorySelected,
                            onLongHold = onToggleTorch,
                        ) { if (isHistorySelected) { onHistoryClick(); onLogClick() } else { onCapture() } }
                    }

                    if (isBatchMode) {
                        ToolbarAction(
                            label = stringResource(R.string.select_from_gallery),
                            icon = Icons.Rounded.Image,
                            emphasized = false,
                            onClick = onSelectGallery
                        )
                        ToolbarAction(
                            label = stringResource(R.string.add_label),
                            icon = Icons.Rounded.CameraAlt,
                            emphasized = true,
                            onLongHold = onToggleTorch,
                            onClick = onCaptureBatch
                        )
                        ToolbarAction(
                            label = pluralStringResource(R.plurals.analyze_photos, batchCount, batchCount),
                            icon = Icons.Rounded.FileUpload,
                            emphasized = false,
                            onLongHold = onToggleTorch,
                            onClick = onFinishBatch
                        )
                    } else {
                        ToolbarAction(
                            label = stringResource(R.string.add_label),
                            icon = Icons.Rounded.CameraAlt,
                            emphasized = false,
                            onLongHold = onToggleTorch,
                        ) { if (isHistorySelected) { onHistoryClick() }; onCaptureBatch() }
                    }
                } else {
                    val previousRoute = navController.previousBackStackEntry?.destination?.route

                    val renderItem = @Composable { dest: xyz.draumaz.sustenance.ui.Dest ->
                        val isSelected = currentDestination?.hierarchy?.any {
                            it.route == dest.route || it.route?.startsWith("${dest.route}?") == true
                        } == true

                        ExpressiveNavItem(
                            label = stringResource(dest.labelRes),
                            icon = dest.icon,
                            selected = previewSelected(
                                isSelected = isSelected,
                                isCurrent = currentRoute == dest.route,
                                isPrevious = previousRoute == dest.route,
                            ),
                            onClick = { onNavigate(dest) }
                        )
                    }

                    val others = destinations.filter { it.route != "today" }
                    val todayDest = destinations.first { it.route == "today" }

                    // Summary (first item usually)
                    others.take(1).forEach { renderItem(it) }

                    // Today (Home) item - transforms into the detail metric, a past date, or Log
                    val isTodaySelected = (currentDestination?.hierarchy?.any { it.route == "today" } == true)
                    val isLog = (dateOffset == 0) && hasApiKey && isTodaySelected && !isOnDetail
                    val targetMetric = if (isOnDetail) detailMetric else null

                    val labelText = when {
                        targetMetric != null -> stringResource(targetMetric.titleRes)
                        isLog -> stringResource(R.string.log)
                        dateOffset == 0 -> stringResource(todayDest.labelRes)
                        dateOffset == 1 -> stringResource(R.string.yesterday)
                        else -> LocalDate.now().minusDays(dateOffset.toLong())
                            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    }

                    fun isTodayRoute(route: String?) = route == "today" || route?.startsWith("detail/") == true

                    ExpressiveNavItem(
                        label = labelText,
                        icon = when {
                            targetMetric != null -> targetMetric.icon
                            isLog -> Icons.Rounded.Add
                            else -> todayDest.icon
                        },
                        selected = previewSelected(
                            isSelected = isTodaySelected || isOnDetail,
                            isCurrent = isTodayRoute(currentRoute),
                            isPrevious = isTodayRoute(previousRoute),
                        ),
                        onLongHold = if (isLog) {
                            { onSelectGallery(); onLogClick() }
                        } else null,
                        onClick = {
                            if (isLog) onLogClick() else onNavigate(todayDest)
                        },
                    )

                    // Settings and others
                    others.drop(1).forEach { renderItem(it) }
                }
            }
        }
    }
}

/**
 * A navigation destination in the floating bar, built on the M3 Expressive
 * [ShortNavigationBarItem] in its horizontal (icon-start) configuration. The label is only
 * shown for the selected destination to keep the floating bar compact.
 */
@Composable
fun ExpressiveNavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onLongHold: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val (interactionSource, handleClick) = rememberLongHoldClick(onClick, onLongHold)

    ShortNavigationBarItem(
        selected = selected,
        onClick = handleClick,
        icon = {
            AnimatedContent(
                targetState = icon,
                transitionSpec = { fadeIn(expressiveEffectsSpec()) togetherWith fadeOut(expressiveEffectsSpec()) },
                label = "nav_item_icon"
            ) { targetIcon ->
                Icon(
                    imageVector = targetIcon,
                    // The label is announced instead whenever it's visible.
                    contentDescription = if (selected) null else label
                )
            }
        },
        label = if (selected) {
            {
                AnimatedContent(
                    targetState = label,
                    transitionSpec = { fadeIn(expressiveEffectsSpec()) togetherWith fadeOut(expressiveEffectsSpec()) },
                    label = "nav_item_label"
                ) { targetLabel ->
                    Text(text = targetLabel, maxLines = 1)
                }
            }
        } else null,
        iconPosition = NavigationItemIconPosition.Start,
        interactionSource = interactionSource,
    )
}

/**
 * An action in the floating toolbar. The emphasized action is a tonal button with its label;
 * the rest are standard icon buttons.
 */
@Composable
private fun ToolbarAction(
    label: String,
    icon: ImageVector,
    emphasized: Boolean,
    onLongHold: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val (interactionSource, handleClick) = rememberLongHoldClick(onClick, onLongHold)

    if (emphasized) {
        FilledTonalButton(
            onClick = handleClick,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            interactionSource = interactionSource
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.IconSize)
            )
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(text = label, maxLines = 1)
        }
    } else {
        IconButton(onClick = handleClick, interactionSource = interactionSource) {
            Icon(imageVector = icon, contentDescription = label)
        }
    }
}

/**
 * Adds tap haptics and an optional long-press action to components that only expose onClick.
 * Returns the
 * interaction source to pass to the component and the click handler to use, which ignores the
 * release that ends a long press.
 */
@Composable
private fun rememberLongHoldClick(
    onClick: () -> Unit,
    onLongHold: (() -> Unit)?,
): Pair<MutableInteractionSource, () -> Unit> {
    val haptic = LocalHapticFeedback.current
    val longPressTimeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var didLongHold by remember { mutableStateOf(false) }
    val currentOnLongHold by rememberUpdatedState(onLongHold)

    LaunchedEffect(isPressed) {
        if (!isPressed) return@LaunchedEffect
        didLongHold = false
        val longHold = currentOnLongHold ?: return@LaunchedEffect
        delay(longPressTimeout)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        didLongHold = true
        longHold()
    }

    return interactionSource to {
        if (didLongHold) {
            didLongHold = false
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.KeyboardTap)
            onClick()
        }
    }
}
