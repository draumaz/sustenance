package dev.easonhuang.sustenance.ui.components

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
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToPhotos
import androidx.compose.material.icons.rounded.ArrowBackIosNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.CameraEnhance
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.MultipleStop
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.UploadFile
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import dev.easonhuang.sustenance.data.Metric
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
    var isSwipeActive by mutableStateOf(false)
}

class ScallopedPillShape(private val isScalloped: Boolean) : Shape {
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
                androidx.compose.ui.geometry.RoundRect(
                    0f, 0f, width, height,
                    androidx.compose.ui.geometry.CornerRadius(radius)
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
    destinations: List<dev.easonhuang.sustenance.ui.Dest>,
    predictiveBackState: PredictiveBackState,
    dateOffset: Int = 0,
    hasApiKey: Boolean = false,
    isCameraMode: Boolean = false,
    batchCount: Int = 0,
    batchInfoText: String = "",
    onBatchInfoTextChange: (String) -> Unit = {},
    onSelectGallery: () -> Unit = {},
    onCapture: () -> Unit = {},
    onCaptureBatch: () -> Unit = {},
    onFinishBatch: () -> Unit = {},
    onNavigate: (dev.easonhuang.sustenance.ui.Dest) -> Unit,
    onLogClick: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    
    val isOnDetail = currentRoute?.startsWith("detail/") == true
    val detailMetric = if (isOnDetail) {
        navBackStackEntry?.arguments?.getString("key")?.let { Metric.fromKey(it) }
    } else null

    var isScalloped by remember { mutableStateOf(false) }
    val pillShape = remember(isScalloped) { ScallopedPillShape(isScalloped) }

    val density = LocalDensity.current
    val isImeVisible = WindowInsets.ime.getBottom(density) > 0

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .then(
                if (isImeVisible) Modifier.graphicsLayer { translationY = 700f }
                else Modifier
            )
            .padding(bottom = 36.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .clip(pillShape)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
            color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f)
        ) {
            Column(
                modifier = Modifier.padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isCameraMode && batchCount > 0) {
                    Row(
                        modifier = Modifier
                            .width(250.dp)
                            .height(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier.weight(1f).width(50.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (batchInfoText.isEmpty()) {
                                Text(
                                    text = "(Optional additional info here) ",
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
                                singleLine = true
                            )
                        }
                        IconButton(
                            onClick = onSelectGallery,
                            modifier = Modifier.size(32.dp)
                        )
                        {
                            Icon(
                                imageVector = Icons.Rounded.Image,
                                contentDescription = "Select from gallery",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCameraMode) {
                        val todayDest = destinations.first { it.route == "today" }
                        ExpressiveNavItem(
                            label = "Today",
                            icon = Icons.Rounded.ArrowBackIosNew,
                            isSelected = false,
                            onClick = { onNavigate(todayDest) }
                        )
                        if (batchCount > 0) {
                            ExpressiveNavItem(
                                label = "Analyze $batchCount photos",
                                icon = Icons.Rounded.FileUpload,
                                isSelected = true,
                                onClick = onFinishBatch
                            )
                            ExpressiveNavItem(
                                label = "Capture ($batchCount)",
                                icon = Icons.Rounded.AddToPhotos,
                                isSelected = false,
                                onClick = onCaptureBatch
                            )
                        } else {
                            ExpressiveNavItem(
                                label = "Analyze",
                                icon = Icons.Rounded.FileUpload,
                                isSelected = true,//false,
                                onClick = onCapture,
                                onLongHold = onSelectGallery,
                            )
                            ExpressiveNavItem(
                                label = "Details",
                                icon = Icons.Rounded.AddToPhotos,
                                isSelected = false,//batchCount == 0,
                                onClick = onCaptureBatch
                            )
                        }
                    } else {
                        val renderItem = @Composable { dest: dev.easonhuang.sustenance.ui.Dest ->
                            val isSelected = currentDestination?.hierarchy?.any { it.route == dest.route } == true
                            
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
                                label = dest.label,
                                icon = dest.icon,
                                isSelected = isSelected,
                                selectionAlphaOverride = selectionAlphaOverride,
                                onLongHold = { isScalloped = !isScalloped },
                                onClick = { onNavigate(dest) }
                            )
                        }

                        val others = destinations.filter { it.route != "today" }
                        val todayDest = destinations.first { it.route == "today" }

                        // Summary (first item usually)
                        others.take(1).forEach { renderItem(it) }

                        // Today (Home) item - now transforms into detail metric
                        val isTodaySelected = currentDestination?.hierarchy?.any { it.route == "today" } == true
                        val isEffectivelySelected = isTodaySelected || isOnDetail

                        val currentOffset = if (isOnDetail) {
                            navBackStackEntry?.arguments?.getInt("offset") ?: 0
                        } else {
                            dateOffset
                        }

                        val isLogState = currentOffset == 0 && hasApiKey && isEffectivelySelected

                        AnimatedContent(
                            targetState = Triple(if (isOnDetail) detailMetric else null, currentOffset, isLogState),
                            transitionSpec = {
                                (fadeIn(animationSpec = tween(220)) + scaleIn(initialScale = 0.92f))
                                    .togetherWith(fadeOut(animationSpec = tween(160)) + scaleOut(targetScale = 0.92f))
                                    .using(SizeTransform(clip = false))
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

                            val label = when {
                                targetMetric != null -> targetMetric.title
                                isLog -> "Log"
                                offset == 0 -> todayDest.label
                                offset == 1 -> "Yesterday"
                                else -> LocalDate.now().minusDays(offset.toLong())
                                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                            }

                            ExpressiveNavItem(
                                label = label,
                                icon = when {
                                    targetMetric != null -> targetMetric.icon
                                    isLog -> Icons.Rounded.Add
                                    else -> todayDest.icon
                                },
                                isSelected = isEffectivelySelected,
                                selectionAlphaOverride = selectionAlphaOverride,
                                onLongHold = { isScalloped = !isScalloped },
                                onClick = {
                                    if (isLog) onLogClick() else onNavigate(todayDest)
                                }
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
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                }
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
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
                modifier = Modifier.size(24.dp)
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
