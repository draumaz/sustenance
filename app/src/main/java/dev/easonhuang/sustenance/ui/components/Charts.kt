package dev.easonhuang.sustenance.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt
import dev.easonhuang.sustenance.data.SeriesPoint
import dev.easonhuang.sustenance.data.formatNumber

/** Tiny inline trend line for dashboard cards. */
@Composable
fun Sparkline(values: List<Float>, color: Color, modifier: Modifier = Modifier) {
    if (values.size < 2) {
        Spacer(modifier)
        return
    }
    Canvas(modifier) {
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val stepX = size.width / (values.size - 1)
        val line = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = size.height - ((v - min) / range) * size.height * 0.8f - size.height * 0.1f
            if (i == 0) {
                line.moveTo(x, y)
                fill.moveTo(x, size.height)
                fill.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.2f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** 7/14-day bar chart with weekday labels for daily-total metrics. */
@Composable
fun BarChart(points: List<SeriesPoint>, color: Color, modifier: Modifier = Modifier) {
    Column(modifier) {
        val max = points.maxOfOrNull { it.value }?.takeIf { it > 0f } ?: 1f
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val n = points.size
            if (n == 0) return@Canvas
            val slot = size.width / n
            val barW = slot * 0.6f
            val radius = CornerRadius(barW / 2f, barW / 2f)
            points.forEachIndexed { i, p ->
                val h = (p.value / max) * size.height
                val left = i * slot + (slot - barW) / 2f
                val top = size.height - h
                
                // Track
                drawRoundRect(
                    color = color.copy(alpha = 0.1f),
                    topLeft = Offset(left, 0f),
                    size = Size(barW, size.height),
                    cornerRadius = radius,
                )
                
                // Bar
                if (p.value > 0f) {
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(left, top),
                        size = Size(barW, h.coerceAtLeast(barW)),
                        cornerRadius = radius
                    )
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth()) {
            points.forEach { p ->
                Text(
                    text = p.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** Smooth-ish line chart with min/max guide labels for sampled metrics. */
@Composable
fun LineChart(
    points: List<SeriesPoint>,
    color: Color,
    modifier: Modifier = Modifier,
    unit: String? = null,
    selectedIndex: Int? = null,
    onSelectedIndexChange: (Int?) -> Unit = {}
) {
    if (points.size < 2) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("Not enough data to chart", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val values = points.map { it.value }
    val min = values.min()
    val max = values.max()

    val haptic = LocalHapticFeedback.current

    Column(
        modifier
            .pointerInput(points) {
            detectTapGestures { onSelectedIndexChange(null) }
        }
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()
            
            val density = LocalDensity.current
            val labelWidth = 42.dp
            val labelWidthPx = with(density) { labelWidth.toPx() }
            val chartWidth = width - labelWidthPx
            val stepX = chartWidth / (points.size - 1)
            val range = (max - min).takeIf { it > 0f } ?: 1f

            val peakColor = lerp(color, Color(0xFF709E73), 0.4f)
            val lowColor = lerp(color, Color(0xFFAB6161), 0.4f)

            // Y-Axis Labels
            Text(
                text = formatNumber(ceil(max)),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = peakColor,
                modifier = Modifier.offset { IntOffset(0, (height * 0.1f - 7.dp.toPx()).roundToInt()) }
            )
            Text(
                text = formatNumber(ceil(min)),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = lowColor,
                modifier = Modifier.offset { IntOffset(0, (height * 0.9f - 7.dp.toPx()).roundToInt()) }
            )

            Canvas(
                Modifier
                    .fillMaxSize()
                    .padding(start = labelWidth)
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            val i = (offset.x / stepX).roundToInt()
                            if (i in points.indices) {
                                val p = points[i]
                                val py = size.height - ((p.value - min) / range) * size.height * 0.8f - size.height * 0.1f
                                val dy = abs(offset.y - py)
                                
                                // Only trigger if tap is within 48dp of the point vertically
                                if (dy < 48.dp.toPx()) {
                                    if (selectedIndex != i) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onSelectedIndexChange(i)
                                    } else {
                                        onSelectedIndexChange(null)
                                    }
                                } else {
                                    onSelectedIndexChange(null)
                                }
                            } else {
                                onSelectedIndexChange(null)
                            }
                        }
                    }
                    .pointerInput(points) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val i = (offset.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectedIndexChange(i)
                            },
                            onDrag = { change, _ ->
                                val i = (change.position.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                if (selectedIndex != i) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectedIndexChange(i)
                                }
                                change.consume()
                            }
                        )
                    }
                    .pointerInput(points) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val i = (offset.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                if (selectedIndex != i) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelectedIndexChange(i)
                                }
                            },
                            onHorizontalDrag = { change, _ ->
                                val i = (change.position.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                if (selectedIndex != i) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectedIndexChange(i)
                                }
                            }
                        )
                    }
            ) {
                val line = Path()
                val fill = Path()
                points.forEachIndexed { i, p ->
                    val x = i * stepX
                    val y = size.height - ((p.value - min) / range) * size.height * 0.8f - size.height * 0.1f
                    if (i == 0) {
                        line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y)
                    } else {
                        line.lineTo(x, y); fill.lineTo(x, y)
                    }
                }
                fill.lineTo(size.width, size.height); fill.close()
                drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
                drawPath(line, color, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                
                // Draw points for each day
                points.forEachIndexed { i, p ->
                    val x = i * stepX
                    val y = size.height - ((p.value - min) / range) * size.height * 0.8f - size.height * 0.1f
                    
                    val isPeak = p.value == max
                    val isLow = p.value == min
                    val isToday = i == points.size - 1

                    if (isToday) {
                        // Special highlighting for today already handled below
                    } else if (isPeak || isLow) {
                        val pointColor = if (isPeak) peakColor else lowColor
                        // Enhanced points for peak/low
                        drawCircle(
                            color = pointColor.copy(alpha = 0.15f),
                            radius = 10.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = pointColor,
                            radius = 5.dp.toPx(),
                            center = Offset(x, y)
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 2.5.dp.toPx(),
                            center = Offset(x, y)
                        )
                    } else {
                        // Standard small points for other days
                        drawCircle(
                            color = color.copy(alpha = 0.5f),
                            radius = 3.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }

                // Today/Last point highlight
                val lastIdx = points.size - 1
                val lp = points[lastIdx]
                val lx = lastIdx * stepX
                val ly = size.height - ((lp.value - min) / range) * size.height * 0.8f - size.height * 0.1f
                
                drawCircle(
                    color = color.copy(alpha = 0.2f),
                    radius = 12.dp.toPx(),
                    center = Offset(lx, ly)
                )
                drawCircle(
                    color = color,
                    radius = 6.dp.toPx(),
                    center = Offset(lx, ly)
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = Offset(lx, ly)
                )

                // Selection highlight
                selectedIndex?.let { i ->
                    val x = i * stepX
                    val p = points[i]
                    val y = size.height - ((p.value - min) / range) * size.height * 0.8f - size.height * 0.1f
                    
                    drawLine(
                        color = color.copy(alpha = 0.4f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(x, y))
                    drawCircle(color, radius = 6.dp.toPx(), center = Offset(x, y), style = Stroke(width = 3.dp.toPx()))
                }
            }

            selectedIndex?.let { i ->
                val p = points[i]
                val x = i * stepX + labelWidthPx
                val y = height - ((p.value - min) / range) * height * 0.8f - height * 0.1f
                
                val popupWidthPx = with(density) { 110.dp.toPx() }
                val popupHeightPx = with(density) { 54.dp.toPx() }
                val marginPx = with(density) { 12.dp.toPx() }

                val isHigh = y < height / 2
                val yOffset = if (isHigh) {
                    (y + marginPx).roundToInt()
                } else {
                    (y - popupHeightPx - marginPx).roundToInt()
                }

                val animatedX by animateFloatAsState(
                    targetValue = x,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "popup_x"
                )
                val animatedY by animateFloatAsState(
                    targetValue = yOffset.toFloat(),
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
                    label = "popup_y"
                )

                Popup(
                    alignment = Alignment.TopStart,
                    offset = IntOffset(
                        x = (animatedX - popupWidthPx / 2).roundToInt().coerceIn(0, (width - popupWidthPx).roundToInt()),
                        y = animatedY.roundToInt()
                    ),
                    properties = PopupProperties(clippingEnabled = false)
                ) {
                    this@Column.AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + scaleIn(initialScale = 0.85f, animationSpec = spring(Spring.DampingRatioLowBouncy)),
                        exit = fadeOut() + scaleOut(targetScale = 0.85f)
                    ) {
                        Surface(
                            modifier = Modifier.padding(4.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shadowElevation = 6.dp,
                            tonalElevation = 4.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val dateText = remember(p.time) {
                                    DateTimeFormatter.ofPattern("MMM d")
                                        .withZone(ZoneId.systemDefault())
                                        .format(p.time)
                                }
                                Text(
                                    text = "${p.label}, $dateText",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (unit != null) "${formatNumber(ceil(p.value))} $unit" else formatNumber(ceil(p.value)),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = color
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .padding(start = 42.dp)
                .height(24.dp)
        ) {
            val width = constraints.maxWidth.toFloat()
            val stepX = if (points.size > 1) width / (points.size - 1) else 0f
            
            // Background box for gestures
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(points) {
                        detectTapGestures { offset ->
                            val i = (offset.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                            if (selectedIndex != i) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectedIndexChange(i)
                            } else {
                                onSelectedIndexChange(null)
                            }
                        }
                    }
                    .pointerInput(points) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                val i = (offset.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSelectedIndexChange(i)
                            },
                            onDrag = { change, _ ->
                                val i = (change.position.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                if (selectedIndex != i) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectedIndexChange(i)
                                }
                                change.consume()
                            }
                        )
                    }
                    .pointerInput(points) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                val i = (offset.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                if (selectedIndex != i) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onSelectedIndexChange(i)
                                }
                            },
                            onHorizontalDrag = { change, _ ->
                                val i = (change.position.x / stepX).roundToInt().coerceIn(0, points.size - 1)
                                if (selectedIndex != i) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onSelectedIndexChange(i)
                                }
                            }
                        )
                    }
            )

            val labelIndices = if (points.size <= 8) {
                points.indices.toList()
            } else {
                points.indices.filter { it % 2 == 0 || it == points.size - 1 }
            }

            labelIndices.forEach { index ->
                val p = points[index]
                val isLast = index == points.size - 1
                val x = index * stepX
                val labelWidth = 48.dp

                Text(
                    text = p.label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isLast) FontWeight.ExtraBold else FontWeight.Bold,
                    color = if (isLast) color else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (x - (labelWidth / 2).toPx()).roundToInt(),
                                y = 0
                            )
                        }
                        .width(labelWidth)
                )
            }
        }
    }
}
