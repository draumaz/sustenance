package dev.easonhuang.sustenance.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.easonhuang.sustenance.data.Metric
import dev.easonhuang.sustenance.data.MetricSummary

@Composable
fun MetricCard(
    summary: MetricSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(48.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
    ) {
        MetricItemContent(summary, isCompact = true)
    }
}

@Composable
fun MetricItemContent(
    summary: MetricSummary,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    val accent = summary.metric.accent
    val locked = !summary.granted

    Row(
        modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(if (isCompact) 32.dp else 40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (locked) Icons.Rounded.Lock else summary.metric.icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(if (isCompact) 16.dp else 20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = summary.titleOverride ?: summary.metric.title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
            )

            var textStyle by remember(summary.value) {
                mutableStateOf(TextStyle(
                    fontSize = if (isCompact) 14.sp else 17.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp
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

            if (!locked && summary.goal != null && 
                summary.metric != Metric.CALORIC_BALANCE && 
                summary.metric != Metric.TOTAL_CALORIES) {
                val today = summary.spark.lastOrNull() ?: 0f
                val goal = summary.goal
                
                val progress = when {
                    goal > 0f -> (today / goal).coerceIn(0f, 1f)
                    goal < 0f -> (today / goal).coerceIn(0f, 1f)
                    else -> if (today > 0f) 1f else 0f
                }
                
                val isOver = if (goal >= 0f) today > goal else today < goal
                
                // Only show red for "over" on metrics where exceeding is generally undesirable (macros/food)
                val isNegativeOver = isOver
                val barColor = if (isNegativeOver) Color(0xFFEF5350) else accent

                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.8f)
                        .height(if (isCompact) 3.dp else 4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(progress)
                            .fillMaxSize()
                            .background(barColor)
                    )
                }
            }
        }
    }
}
