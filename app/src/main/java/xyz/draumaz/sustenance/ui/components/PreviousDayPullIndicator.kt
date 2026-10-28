package xyz.draumaz.sustenance.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

/**
 * M3 Expressive spring tokens (ExpressiveMotionTokens). `MotionScheme.expressive()` is still
 * internal in material3 1.4.0, so the values are mirrored here.
 */
object ExpressiveSprings {
    fun <T> fastSpatial(visibilityThreshold: T? = null) = spring(0.6f, 800f, visibilityThreshold)
    fun <T> defaultSpatial(visibilityThreshold: T? = null) = spring(0.8f, 380f, visibilityThreshold)
    fun <T> defaultEffects(visibilityThreshold: T? = null) = spring(1f, 1600f, visibilityThreshold)
}

/**
 * Pill shown at the bottom of the dashboard while the user pulls up to view the previous day.
 *
 * It rises and scales in with [progressProvider] (0..1, read in the draw phase only), fills a
 * determinate progress ring, and switches to the primary color role with a springy pop once the
 * pull is far enough to commit on release.
 */
@Composable
fun PreviousDayPullIndicator(
    label: String,
    progressProvider: () -> Float,
    armed: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val effects = ExpressiveSprings.defaultEffects<Color>()
    val containerColor by animateColorAsState(
        if (armed) colors.primary else colors.secondaryContainer, effects, label = "pull_container"
    )
    val contentColor by animateColorAsState(
        if (armed) colors.onPrimary else colors.onSecondaryContainer, effects, label = "pull_content"
    )
    val armedScale by animateFloatAsState(
        targetValue = if (armed) 1.08f else 1f,
        animationSpec = ExpressiveSprings.fastSpatial(),
        label = "pull_pop",
    )

    Surface(
        modifier = modifier.graphicsLayer {
            val p = progressProvider().coerceIn(0f, 1f)
            alpha = (p * 2f).coerceAtMost(1f)
            translationY = (1f - p) * 32.dp.toPx()
            val s = lerp(0.7f, 1f, p) * armedScale
            scaleX = s
            scaleY = s
        },
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progressProvider() },
                    modifier = Modifier.size(32.dp),
                    color = contentColor,
                    trackColor = contentColor.copy(alpha = 0.24f),
                    strokeWidth = 3.dp,
                    strokeCap = StrokeCap.Round,
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
