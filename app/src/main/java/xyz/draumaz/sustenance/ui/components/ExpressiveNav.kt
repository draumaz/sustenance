package xyz.draumaz.sustenance.ui.components

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Image
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import xyz.draumaz.sustenance.R
import xyz.draumaz.sustenance.data.Metric
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.time.Duration.Companion.milliseconds

@Stable
class PredictiveBackState {
    var progress by mutableFloatStateOf(0f)
    var isSwipeActive by mutableStateOf(value = false)
}

class ScallopedPillShape(private val isScalloped: Boolean = false) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val width = size.width
        val height = size.height
        val radius = height / 2f

        if (!isScalloped) {
            path.addRoundRect(
                RoundRect(
                    0f, 0f, width, height,
                    CornerRadius(radius)
                )
            )
            return Outline.Generic(path)
        }

        val bumpDepth = with(density) { 2.5.dp.toPx() }
        val bumpsCount = 12f
        val numPoints = 120

        fun getPoint(p: Float): Pair<Offset, Offset> {
            val straight = (width - (2 * radius)).coerceAtLeast(0f)
            val arc = PI.toFloat() * radius
            val total = 2 * straight + 2 * arc
            val d = p * total

            return when {
                d < straight -> {
                    Offset(radius + d, 0f) to Offset(0f, -1f)
                }
                d < straight + arc -> {
                    val angle = 1.5f * PI.toFloat() + (d - straight) / radius
                    val n = Offset(cos(angle), sin(angle))
                    Offset(width - radius, radius) + n * radius to n
                }
                d < 2 * straight + arc -> {
                    Offset(width - radius - (d - (straight + arc)), height) to Offset(0f, 1f)
                }
                else -> {
                    val angle = 0.5f * PI.toFloat() + (d - (2 * straight + arc)) / radius
                    val n = Offset(cos(angle), sin(angle))
                    Offset(radius, radius) + n * radius to n
                }
            }
        }

        for (i in 0..numPoints) {
            val p = i.toFloat() / numPoints
            val (pos, normal) = getPoint(p)
            val bump = sin(p * bumpsCount * 2 * PI.toFloat()) * bumpDepth
            val finalPos = pos + normal * bump
            if (i == 0) path.moveTo(finalPos.x, finalPos.y) else path.lineTo(finalPos.x, finalPos.y)
        }

        path.close()
        return Outline.Generic(path)
    }
}



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
    @Suppress("UNUSED_PARAMETER") onCapture: () -> Unit = {},
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

    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0
    val batchCount = capturedBitmaps.size
    var isScalloped by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .graphicsLayer {
                translationY = if (isImeVisible) 700f else 0f 
            }
            .padding(bottom = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isCameraMode && capturedBitmaps.isNotEmpty()) {
            val lastPhotos = remember(capturedBitmaps) { capturedBitmaps.takeLast(8) }
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
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
            shape = ScallopedPillShape(isScalloped),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isCameraMode && isBatchMode) {
                    Row(
                        modifier = Modifier
                            .width(320.dp)
                            .heightIn(min = 64.dp, max = 160.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).width(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (batchInfoText.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.optional_info),
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCameraMode) {
                        if ((batchCount == 0) && !isBatchMode) {
                            ExpressiveNavItem(
                                label = stringResource(R.string.history),
                                icon = Icons.Rounded.History,
                                isSelected = isHistorySelected,
                            ) { if (!isHistorySelected) onHistoryClick() }
                            ExpressiveNavItem(
                                label = if (isHistorySelected) stringResource(R.string.analyze) else stringResource(
                                    R.string.analyze
                                ),
                                icon = if (isHistorySelected) Icons.Rounded.Add else Icons.Rounded.FileUpload,
                                isSelected = !isHistorySelected,
                                onClick = { if (isHistorySelected) { onHistoryClick(); onLogClick() } else { onCaptureBatch(); onCaptureBatch() } },
                                onLongHold = onToggleTorch,
                            )
                        }

                        if (isBatchMode) {
                            ExpressiveNavItem(
                                label = stringResource(R.string.select_from_gallery),
                                icon = Icons.Rounded.Image,
                                isSelected = false,
                                onClick = onSelectGallery
                            )
                            ExpressiveNavItem(
                                label = stringResource(R.string.add_label),
                                icon = Icons.Rounded.CameraAlt,
                                isSelected = true,
                                onClick = onCaptureBatch,
                                onLongHold = onToggleTorch
                            )
                            ExpressiveNavItem(
                                label = stringResource(R.string.analyze_photos),
                                icon = Icons.Rounded.FileUpload,
                                isSelected = false,
                                onClick = onFinishBatch,
                                onLongHold = onToggleTorch,
                            )
                        } else {
                            ExpressiveNavItem(
                                label = stringResource(R.string.add_label),
                                icon = Icons.Rounded.CameraAlt,
                                isSelected = false,
                                onClick = { if (isHistorySelected) { onHistoryClick() }; onCaptureBatch() },
                                onLongHold = onToggleTorch,
                            )
                        }
                        
                    } else {
                        val renderItem = @Composable { dest: xyz.draumaz.sustenance.ui.Dest ->
                            val isSelected = currentDestination?.hierarchy?.any { 
                                it.route == dest.route || it.route?.startsWith("${dest.route}?") == true 
                            } == true

                            var selectionAlphaOverride: Float? = null
                            if (predictiveBackState.isSwipeActive) {
                                val previousRoute = navController.previousBackStackEntry?.destination?.route
                                if (previousRoute == dest.route) {
                                    selectionAlphaOverride = predictiveBackState.progress
                                } else if (currentRoute == dest.route) {
                                    selectionAlphaOverride = 1f - predictiveBackState.progress
                                }
                            }

                            ExpressiveNavItem(
                                label = stringResource(dest.labelRes),
                                icon = dest.icon,
                                isSelected = isSelected,
                                selectionAlphaOverride = selectionAlphaOverride,
                                onClick = { onNavigate(dest) }
                            )

                        }

                        val others = destinations.filter { it.route != "today" }
                        val todayDest = destinations.first { it.route == "today" }

                        // Summary (first item usually)
                        others.take(1).forEach { renderItem(it) }

                        // Today (Home) item - now transforms into detail metric
                        val isTodaySelected = (currentDestination?.hierarchy?.any { it.route == "today" } == true)
                        val isEffectivelySelected = isTodaySelected || isOnDetail

                        val isLogState = (dateOffset == 0) && hasApiKey && isTodaySelected && !isOnDetail

                        AnimatedContent(
                            targetState = Triple(if (isOnDetail) detailMetric else null, dateOffset, isLogState),
                            transitionSpec = {
                                (fadeIn(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)) + scaleIn(initialScale = 0.92f))
                                    .togetherWith(fadeOut(animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium)) + scaleOut(targetScale = 0.92f))
                                    .using(
                                        SizeTransform(clip = false) { _, _ ->
                                            spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        }
                                    )
                            },
                            label = "today_transform"
                        ) { (targetMetric, offset, isLog) ->
                            var selectionAlphaOverride: Float? = null
                            if (predictiveBackState.isSwipeActive) {
                                val previousRoute = navController.previousBackStackEntry?.destination?.route
                                if (previousRoute == "today" || previousRoute?.startsWith("detail/") == true) {
                                    selectionAlphaOverride = predictiveBackState.progress
                                } else if (currentRoute == "today" || currentRoute?.startsWith("detail/") == true) {
                                    selectionAlphaOverride = 1f - predictiveBackState.progress
                                }
                            }

                            val labelText = when {
                                targetMetric != null -> stringResource(targetMetric.titleRes)
                                isLog -> stringResource(R.string.log)
                                offset == 0 -> stringResource(todayDest.labelRes)
                                offset == 1 -> stringResource(R.string.yesterday)
                                else -> LocalDate.now().minusDays(offset.toLong())
                                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                            }

                            ExpressiveNavItem(
                                label = labelText,
                                icon = when {
                                    targetMetric != null -> targetMetric.icon
                                    isLog -> Icons.Rounded.Add
                                    else -> todayDest.icon
                                },
                                isSelected = isEffectivelySelected,
                                selectionAlphaOverride = selectionAlphaOverride,
                                onClick = {
                                    if (isLog) onLogClick() else onNavigate(todayDest)
                                },
                                onLongHold = { if (isLog) {  onSelectGallery(); onLogClick() } },
                            )
                        }

                        // Settings and others
                        others.drop(1).forEach { renderItem(it) }
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
    selectionAlphaOverride: Float? = null,
    onLongHold: () -> Unit = {},
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
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
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                isLongPressed = true
                onLongHold()
            }
            scale.animateTo(
                0.85f,
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
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
    }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "selection_alpha"
    )

    val iconScale by animateFloatAsState(
        targetValue = if (isSelected) 1.15f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_scale"
    )

    val selectionAlpha = selectionAlphaOverride ?: animatedAlpha
    val containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = selectionAlpha)
    val contentColor = if (selectionAlpha > 0.5f) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (!isLongPressed) {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        onClick()
                    }
                }
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )

            if (selectionAlpha > 0.8f) {
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
