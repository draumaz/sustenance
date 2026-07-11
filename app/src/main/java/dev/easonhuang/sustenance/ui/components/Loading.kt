package dev.easonhuang.sustenance.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MorphingScallopedShape(
    private val phase: Float,
    private val bumpDepthFactor: Float,
    private val bumpsCount: Float = 12f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        val width = size.width
        val height = size.height
        val radius = height / 2f
        
        val bumpDepth = with(density) { 4.dp.toPx() } * bumpDepthFactor
        val numPoints = 180

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
            val bump = sin(p * bumpsCount * 2 * PI.toFloat() + phase) * bumpDepth
            val finalPos = pos + normal * bump
            if (i == 0) path.moveTo(finalPos.x, finalPos.y) else path.lineTo(finalPos.x, finalPos.y)
        }
        
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
fun ScallopedLoadingAnimation(
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.DpSize = androidx.compose.ui.unit.DpSize(100.dp, 100.dp),
    bumpsCount: Float = 12f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2 * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    val bumpFactor by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bumpFactor"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val innerSize = androidx.compose.ui.unit.DpSize(size.width * 0.4f, size.height * 0.4f)

    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MorphingScallopedShape(phase, bumpFactor, bumpsCount))
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(innerSize)
                .clip(MorphingScallopedShape(-phase, bumpFactor * 0.5f, bumpsCount * 0.6f))
                .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
        )
    }
}
