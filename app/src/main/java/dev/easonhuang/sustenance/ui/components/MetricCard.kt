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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.easonhuang.sustenance.data.MetricSummary
import dev.easonhuang.sustenance.data.formatValue

@Composable
fun MetricCard(summary: MetricSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = summary.metric.accent
    val locked = !summary.granted
    val valueText = when {
        locked -> "Locked"
        summary.goal != null -> "${summary.value} / ${summary.metric.formatValue(summary.goal)}"
        else -> summary.value
    }
    Card(
        onClick = onClick,
        modifier = modifier.height(84.dp).fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.16f)))),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (locked) Icons.Rounded.Lock else summary.metric.icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = summary.metric.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = valueText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    if (!locked && summary.hasData) {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            text = summary.metric.unit,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp),
                        )
                    }
                }
            }
            if (!locked && summary.spark.size > 1) {
                Sparkline(summary.spark, accent, Modifier.width(80.dp).height(32.dp))
            } else if (locked) {
                Text(
                    text = "Grant access",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
