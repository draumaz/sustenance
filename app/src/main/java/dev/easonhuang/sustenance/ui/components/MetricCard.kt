package dev.easonhuang.sustenance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.easonhuang.sustenance.data.MetricSummary

@Composable
fun MetricCard(
    summary: MetricSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    val accent = summary.metric.accent
    val today = summary.spark.lastOrNull() ?: 0f
    val goal = summary.goal
    val locked = !summary.granted

    val progress = if (goal != null && !locked) {
        if (goal > 0f) (today / goal).coerceIn(0f, 1f) else 0f
    } else 0f

    val showProgress = goal != null && !locked
    val isOver = showProgress && today > (goal ?: Float.MAX_VALUE)

    val fillColor = when {
        isOver -> Color(0xFFAB6161)
        showProgress -> lerp(accent, Color(0xFFEF5350), progress)
        else -> accent
    }

    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp))) {
            if (showProgress && (progress > 0.01f || isOver)) {
                Box(
                    Modifier
                        .fillMaxWidth(if (isOver) 1f else progress)
                        .fillMaxHeight()
                        .background(fillColor)
                        .clip(RoundedCornerShape(topEnd = 12.dp, bottomEnd = 12.dp))
                )
            }
            MetricItemContent(
                summary = summary, 
                isCompact = true, 
                hasFill = showProgress && (progress > 0.05f || isOver)
            )
        }
    }
}

@Composable
fun MetricItemContent(
    summary: MetricSummary,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    hasFill: Boolean = false
) {
    val accent = summary.metric.accent
    val locked = !summary.granted

    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.75f),
        offset = Offset(0f, 2f),
        blurRadius = 8f
    )

    Row(
        modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(if (isCompact) 28.dp else 36.dp)
                .clip(CircleShape)
                .background(if (hasFill) Color.Black.copy(alpha = 0.25f) else accent.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (locked) Icons.Rounded.Lock else summary.metric.icon,
                contentDescription = null,
                tint = if (hasFill) Color.White else accent,
                modifier = Modifier.size(if (isCompact) 16.dp else 20.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = summary.titleOverride ?: summary.metric.title,
                style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
            )

            var textStyle by remember(summary.value) {
                mutableStateOf(TextStyle(
                    fontSize = if (isCompact) 13.sp else 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                    color = Color.White,
                    shadow = textShadow
                ))
            }

            Text(
                text = if (locked) "Locked" else summary.value,
                style = textStyle,
                maxLines = 1,
                softWrap = false,
                onTextLayout = { textLayoutResult ->
                    if (textLayoutResult.hasVisualOverflow) {
                        textStyle = textStyle.copy(fontSize = textStyle.fontSize * 0.9f)
                    }
                }
            )
        }
    }
}
