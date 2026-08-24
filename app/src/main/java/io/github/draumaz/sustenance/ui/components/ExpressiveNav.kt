package io.github.draumaz.sustenance.ui.components

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.VerticalDivider
import io.github.draumaz.sustenance.data.Metric
import io.github.draumaz.sustenance.ui.NavKey
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.res.stringResource
import io.github.draumaz.sustenance.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Stable
class PredictiveBackState {
    var progress by mutableFloatStateOf(0f)
    var isSwipeActive by mutableStateOf(value = false)
}



@Composable
fun ExpressiveNavigationBar(
    currentKey: NavKey?,
    previousKey: NavKey?,
    destinations: List<NavKey>,
    predictiveBackState: PredictiveBackState,
    dateOffset: Int = 0,
    hasApiKey: Boolean = false,
    isCameraMode: Boolean = false,
    isBatchMode: Boolean = false,
    capturedBitmaps: List<Bitmap> = emptyList(),
    batchInfoText: String = "",
    isVertical: Boolean = false,
    onBatchInfoTextChange: (String) -> Unit = {},
    onSelectGallery: () -> Unit = {},
    onToggleTorch: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onCapture: () -> Unit = {},
    onCaptureBatch: () -> Unit = {},
    onFinishBatch: () -> Unit = {},
    isHistorySelected: Boolean = false,
    onHistoryClick: () -> Unit = {},
    onNavigate: (NavKey) -> Unit,
    onLogClick: () -> Unit = {},
) {
    val isOnDetail = currentKey is NavKey.Detail
    val detailMetric = (currentKey as? NavKey.Detail)?.let { Metric.fromKey(it.metricKey) }

    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val batchCount = capturedBitmaps.size

    val containerModifier = if (isVertical) {
        Modifier
            .fillMaxHeight()
            .padding(start = 16.dp, top = 36.dp, bottom = 36.dp)
            .graphicsLayer {
                translationX = if (isImeVisible) -700f else 0f
            }
    } else {
        Modifier
            .fillMaxWidth()
            .imePadding()
            .graphicsLayer {
                translationY = if (isImeVisible) 700f else 0f
            }
            .padding(bottom = 36.dp)
    }

    val LayoutContainer = @Composable { content: @Composable () -> Unit ->
        if (isVertical) {
            Row(modifier = containerModifier, verticalAlignment = Alignment.CenterVertically) {
                content()
            }
        } else {
            Column(modifier = containerModifier, horizontalAlignment = Alignment.CenterHorizontally) {
                content()
            }
        }
    }

    LayoutContainer {
        if (isCameraMode && capturedBitmaps.isNotEmpty()) {
            val lastPhotos = remember(capturedBitmaps) { capturedBitmaps.takeLast(8) }
            val photosLayout = @Composable {
                if (isVertical) {
                    Column(
                        modifier = Modifier.padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy((-16).dp),
                        horizontalAlignment = Alignment.CenterHorizontally
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
                                        shape = RoundedCornerShape(12.dp)
                                        clip = true
                                    }
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                } else {
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
                                        shape = RoundedCornerShape(12.dp)
                                        clip = true
                                    }
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        2.dp,
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(12.dp)
                                    ),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
            photosLayout()
        }
        Surface(
            modifier = Modifier
                .then(if (isVertical) Modifier.fillMaxHeight() else Modifier.wrapContentWidth())
                .clip(if (isVertical) RoundedCornerShape(32.dp) else CircleShape)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
        ) {
            val innerPadding = if (isVertical) PaddingValues(horizontal = 8.dp, vertical = 12.dp) else PaddingValues(8.dp)
            val ContentContainer = @Composable { content: @Composable () -> Unit ->
                if (isVertical) {
                    Column(
                        modifier = Modifier.padding(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        content()
                    }
                } else {
                    Column(
                        modifier = Modifier.padding(innerPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        content()
                    }
                }
            }

            ContentContainer {
                if (isCameraMode && isBatchMode) {
                    val batchBoxModifier = if (isVertical) {
                        Modifier.width(64.dp).heightIn(min = 160.dp, max = 320.dp)
                    } else {
                        Modifier.width(320.dp).heightIn(min = 64.dp, max = 160.dp)
                    }
                    Row(
                        modifier = batchBoxModifier
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = if (isVertical) 8.dp else 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (batchInfoText.isEmpty()) {
                                Text(
                                    text = if (isVertical) "" else stringResource(R.string.optional_info),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                            BasicTextField(
                                value = batchInfoText,
                                onValueChange = onBatchInfoTextChange,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = false,
                                maxLines = 5
                            )
                        }
                    }
                }
                
                val itemsContainer = @Composable { content: @Composable () -> Unit ->
                    if (isVertical) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            content()
                        }
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            content()
                        }
                    }
                }

                itemsContainer {
                    if (isCameraMode) {
                        if ((batchCount == 0) && !isBatchMode) {
                            ExpressiveNavItem(
                                label = stringResource(R.string.history),
                                icon = Icons.Rounded.History,
                                isSelected = isHistorySelected,
                                isVertical = isVertical
                            ) { if (!isHistorySelected) onHistoryClick() }
                            ExpressiveNavItem(
                                label = stringResource(R.string.analyze),
                                icon = if (isHistorySelected) Icons.Rounded.Add else Icons.Rounded.FileUpload,
                                isSelected = !isHistorySelected,
                                isVertical = isVertical,
                                onClick = { if (isHistorySelected) { onHistoryClick(); onLogClick() } else { onCaptureBatch(); onCaptureBatch() } },
                                onLongHold = onToggleTorch,
                            )
                        }

                        if (isBatchMode) {
                            ExpressiveNavItem(
                                label = stringResource(R.string.select_from_gallery),
                                icon = Icons.Rounded.Image,
                                isSelected = false,
                                isVertical = isVertical,
                                onClick = onSelectGallery
                            )
                            ExpressiveNavItem(
                                label = stringResource(R.string.add_label),
                                icon = Icons.Rounded.CameraAlt,
                                isSelected = true,
                                isVertical = isVertical,
                                onClick = onCaptureBatch,
                                onLongHold = onToggleTorch
                            )
                            ExpressiveNavItem(
                                label = stringResource(R.string.analyze_photos),
                                icon = Icons.Rounded.FileUpload,
                                isSelected = false,
                                isVertical = isVertical,
                                onClick = onFinishBatch,
                                onLongHold = onToggleTorch,
                            )
                        } else if (!((batchCount == 0) && !isBatchMode)) {
                            ExpressiveNavItem(
                                label = stringResource(R.string.add_label),
                                icon = Icons.Rounded.CameraAlt,
                                isSelected = false,
                                isVertical = isVertical,
                                onClick = { if (isHistorySelected) { onHistoryClick() }; onCaptureBatch() },
                                onLongHold = onToggleTorch,
                            )
                        }
                        
                    } else {
                        val insightsKey = NavKey.Insights
                        val todayKey = NavKey.Today

                        // Insights
                        ExpressiveNavItem(
                            label = stringResource(R.string.summary_title),
                            icon = Icons.Rounded.Insights,
                            isSelected = currentKey == insightsKey,
                            isVertical = isVertical,
                            onClick = { onNavigate(insightsKey) }
                        )

                        // Today (Home) item - now transforms into detail metric
                        val isTodaySelected = currentKey == todayKey
                        val isEffectivelySelected = isTodaySelected || isOnDetail

                        val isLogState = (dateOffset == 0) && hasApiKey && isTodaySelected && !isOnDetail

                        AnimatedContent(
                            targetState = Triple<Metric?, Int, Boolean>(if (isOnDetail) detailMetric else null, dateOffset, isLogState),
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.92f))
                                    .togetherWith(fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 0.92f))
                                    .using(SizeTransform(clip = false))
                            },
                            label = "today_transform"
                        ) { targetTriple ->
                            val targetMetric = targetTriple.first
                            val offset = targetTriple.second
                            val isLog = targetTriple.third
                            var selectionAlphaOverride: Float? = null
                            if (predictiveBackState.isSwipeActive) {
                                if (previousKey == todayKey || previousKey is NavKey.Detail) {
                                    selectionAlphaOverride = predictiveBackState.progress
                                } else if (currentKey == todayKey || currentKey is NavKey.Detail) {
                                    selectionAlphaOverride = 1f - predictiveBackState.progress
                                }
                            }

                            val labelText = when {
                                targetMetric != null -> stringResource(targetMetric.titleRes)
                                isLog -> stringResource(R.string.log)
                                offset == 0 -> stringResource(R.string.today_label)
                                offset == 1 -> stringResource(R.string.yesterday)
                                else -> LocalDate.now().minusDays(offset.toLong())
                                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                            }

                            ExpressiveNavItem(
                                label = labelText,
                                icon = when {
                                    targetMetric != null -> targetMetric.icon
                                    isLog -> Icons.Rounded.Add
                                    else -> Icons.Rounded.Today
                                },
                                isSelected = isEffectivelySelected,
                                isVertical = isVertical,
                                selectionAlphaOverride = selectionAlphaOverride,
                                onClick = {
                                    if (isLog) onLogClick() else onNavigate(todayKey)
                                },
                                onLongHold = { if (isLog) {  onSelectGallery(); onLogClick() } },
                            )
                        }

                        // Settings
                        ExpressiveNavItem(
                            label = stringResource(R.string.settings_title),
                            icon = Icons.Rounded.Settings,
                            isSelected = currentKey is NavKey.Settings,
                            isVertical = isVertical,
                            onClick = { onNavigate(NavKey.Settings()) }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun ExpressiveNavItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    isVertical: Boolean = false,
    selectionAlphaOverride: Float? = null,
    onLongHold: () -> Unit = {},
    onClick: () -> Unit
) {
    val view = LocalView.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = remember { Animatable(1f) }
    var isLongPressed by remember { mutableStateOf(false) }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            isLongPressed = false
            val job = launch {
                delay(500.milliseconds)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                isLongPressed = true
                onLongHold()
            }
            scale.animateTo(
                0.88f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
            job.join()
        } else {
            scale.animateTo(
                1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessHigh
                )
            )
        }
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = tween(300),
        label = "selection_alpha"
    )

    val selectionAlpha = selectionAlphaOverride ?: animatedAlpha
    val containerColorBase = MaterialTheme.colorScheme.primaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    val itemModifier = if (isVertical) {
        Modifier
            .width(56.dp)
            .heightIn(min = 56.dp)
            .padding(vertical = 12.dp)
    } else {
        Modifier
            .height(56.dp)
            .padding(horizontal = 16.dp)
    }

    Box(
        modifier = Modifier
            .then(if (isVertical) Modifier.width(56.dp) else Modifier.height(56.dp))
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .drawBehind {
                drawRect(color = containerColorBase, alpha = selectionAlpha)
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (!isLongPressed) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onClick()
                    }
                }
            )
            .then(itemModifier),
        contentAlignment = Alignment.Center
    ) {
        val contentColor = if (selectionAlpha > 0.5f) onPrimaryContainer else onSurfaceVariant

        val LayoutContainer = @Composable { content: @Composable () -> Unit ->
            if (isVertical) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    content()
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    content()
                }
            }
        }

        LayoutContainer {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )

            if (isSelected || (selectionAlphaOverride ?: 0f) > 0.8f) {
                if (isVertical) {
                    // In vertical mode, maybe we don't show text to save width, or show it below
                    // But for this "Expressive" style, let's try showing it below if selected
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = label,
                        color = contentColor,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        ),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
