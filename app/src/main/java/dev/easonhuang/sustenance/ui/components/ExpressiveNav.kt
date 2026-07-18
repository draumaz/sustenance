package dev.easonhuang.sustenance.ui.components

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material.icons.rounded.History
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.res.stringResource
import dev.easonhuang.sustenance.R
import dev.easonhuang.sustenance.data.Metric
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Stable
class PredictiveBackState {
    var progress by mutableFloatStateOf(0f)
    var isSwipeActive by mutableStateOf(false)
}



@Composable
fun ExpressiveNavigationBar(
    navController: NavHostController,
    destinations: List<dev.easonhuang.sustenance.ui.Dest>,
    predictiveBackState: PredictiveBackState,
    dateOffset: Int = 0,
    hasApiKey: Boolean = false,
    isCameraMode: Boolean = false,
    capturedBitmaps: List<Bitmap> = emptyList(),
    batchInfoText: String = "",
    onBatchInfoTextChange: (String) -> Unit = {},
    onSelectGallery: () -> Unit = {},
    onToggleTorch: () -> Unit = {},
    onCapture: () -> Unit = {},
    onCaptureBatch: () -> Unit = {},
    onFinishBatch: () -> Unit = {},
    onHistoryClick: () -> Unit = {},
    onNavigate: (dev.easonhuang.sustenance.ui.Dest) -> Unit,
    onLogClick: () -> Unit = {}
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .then(
                if (isImeVisible) Modifier.graphicsLayer { translationY = 700f }
                else Modifier
            )
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
                .clip(CircleShape)
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
                            .width(320.dp)
                            .height(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                                contentDescription = stringResource(R.string.select_from_gallery),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(
                            onClick = onFinishBatch,
                            modifier = Modifier.size(32.dp)
                        )
                        {
                            Icon(
                                imageVector = Icons.Rounded.FileUpload,
                                contentDescription = stringResource(R.string.analyze_photos, batchCount),
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
                            label = stringResource(todayDest.labelRes),
                            icon = Icons.Rounded.ArrowBackIosNew,
                            isSelected = false,
                            onClick = { onNavigate(todayDest) },
                        )
                        if (batchCount == 0) {
                            ExpressiveNavItem(
                                label = stringResource(R.string.history),
                                icon = Icons.Rounded.History,
                                isSelected = false,
                                onClick = onHistoryClick
                            )
                        }
                        if (batchCount > 0) {
                            ExpressiveNavItem(
                                label = stringResource(R.string.analyze_photos, batchCount),
                                icon = Icons.Rounded.FileUpload,
                                isSelected = false,//true,
                                onClick = onFinishBatch,
                                onLongHold = onToggleTorch,
                            )
                            ExpressiveNavItem(
                                label = stringResource(R.string.capture_count, batchCount),
                                icon = Icons.Rounded.AddToPhotos,
                                isSelected = true,//false,
                                onClick = onCaptureBatch
                            )
                        } else {
                            ExpressiveNavItem(
                                label = stringResource(R.string.analyze),
                                icon = Icons.Rounded.FileUpload,
                                isSelected = true,//false,
                                onClick = onCapture,
                                onLongHold = onToggleTorch,
                            )
                            ExpressiveNavItem(
                                label = stringResource(R.string.details),
                                icon = Icons.Rounded.AddToPhotos,
                                isSelected = false,//batchCount == 0,
                                onClick = onCaptureBatch,
                                onLongHold = onSelectGallery,
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
                        val isTodaySelected = currentDestination?.hierarchy?.any { it.route == "today" } == true
                        val isEffectivelySelected = isTodaySelected || isOnDetail

                        val currentOffset = dateOffset

                        val isLogState = currentOffset == 0 && hasApiKey && isTodaySelected && !isOnDetail

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

                            val label = (when {
                                targetMetric != null -> stringResource(targetMetric.titleRes)
                                isLog -> stringResource(R.string.log)
                                offset == 0 -> stringResource(todayDest.labelRes)
                                offset == 1 -> stringResource(R.string.yesterday)
                                else -> LocalDate.now().minusDays(offset.toLong())
                                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                            }).also {

                                ExpressiveNavItem(
                                    label = it,
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
                                    onLongHold = { if (isLog) { onLogClick(); onSelectGallery() } },

                                )
                            }
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
    val containerColorBase = MaterialTheme.colorScheme.primaryContainer
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .height(56.dp)
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
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onClick()
                    }
                }
            )
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        val contentColor = if (selectionAlpha > 0.5f) onPrimaryContainer else onSurfaceVariant

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

            if (isSelected || (selectionAlphaOverride ?: 0f) > 0.8f) {
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
