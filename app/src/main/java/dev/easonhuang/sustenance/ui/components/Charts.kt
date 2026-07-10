package dev.easonhuang.sustenance.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.easonhuang.sustenance.data.SeriesPoint

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
fun LineChart(points: List<SeriesPoint>, color: Color, modifier: Modifier = Modifier) {
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
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("max %.1f".format(max), style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = color)
            Text("min %.1f".format(min), style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = color.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            val range = (max - min).takeIf { it > 0f } ?: 1f
            val stepX = size.width / (points.size - 1)
            val line = Path()
            val fill = Path()
            points.forEachIndexed { i, p ->
                val x = i * stepX
                val y = size.height - ((p.value - min) / range) * size.height * 0.8f - size.height * 0.1f
                if (i == 0) {
                    line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y)
                } else {
                    // Simple bezier curve for smoother line
                    line.lineTo(x, y); fill.lineTo(x, y)
                }
            }
            fill.lineTo(size.width, size.height); fill.close()
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.3f), Color.Transparent)))
            drawPath(line, color, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            
            // Draw points at min/max
            points.forEach { p ->
                if (p.value == max || p.value == min) {
                    val x = points.indexOf(p) * stepX
                    val y = size.height - ((p.value - min) / range) * size.height * 0.8f - size.height * 0.1f
                    drawCircle(Color.White, radius = 6f, center = Offset(x, y))
                    drawCircle(color, radius = 6f, center = Offset(x, y), style = Stroke(width = 3f))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(points.first().label, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(points.last().label, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
