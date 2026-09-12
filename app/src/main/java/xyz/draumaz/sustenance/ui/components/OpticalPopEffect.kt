package xyz.draumaz.sustenance.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.dp

/**
 * Modifier that applies 3D depth, separation, texture sheen, and dissipation
 * to cards as the user pulls up to swipe to yesterday.
 */
fun Modifier.opticalDepthCard(
    sectionIndex: Int,
    cardIndex: Int = 0,
    pullProgress: Float, // 0.0 when idle, 1.0 at threshold, >1.0 over-drag
    accentColor: Color = Color.Unspecified
): Modifier = this.then(
    Modifier
        .graphicsLayer {
            val p = pullProgress.coerceIn(0f, 1.8f)

            if (p <= 0.001f) {
                translationX = 0f
                translationY = 0f
                rotationX = 0f
                rotationY = 0f
                scaleX = 1f
                scaleY = 1f
                alpha = 1f
                shadowElevation = 0f
                return@graphicsLayer
            }

            val density = density
            cameraDistance = 14f * density

            // 1. Vertical & Horizontal Depth Separation (Fanning out)
            val sectionOffsetY = when (sectionIndex) {
                0 -> -p * 45f // Top section pulls UP faster
                1 -> -p * 15f // Middle section stays steady
                else -> p * 30f // Bottom section pulls DOWN (creating physical depth gap!)
            }

            // Sub-card horizontal & vertical micro separation
            val odd = cardIndex % 2 == 1
            val cardOffsetX = if (odd) p * 10f else -p * 10f
            val cardOffsetY = cardIndex * p * 5f

            translationY = (sectionOffsetY + cardOffsetY) * density
            translationX = cardOffsetX * density

            // 2. 3D Perspective Rotation (Tilt)
            val baseRotationX = when (sectionIndex) {
                0 -> -12f * p
                1 -> -5f * p
                else -> 8f * p
            }
            val cardRotationY = if (odd) -4f * p else 4f * p

            rotationX = baseRotationX
            rotationY = cardRotationY

            // 3. Depth Scale Recede
            val baseScale = when (sectionIndex) {
                0 -> 1f - p * 0.04f
                1 -> 1f - p * 0.02f
                else -> 1f - p * 0.05f
            }

            scaleX = baseScale
            scaleY = baseScale

            // 4. Alpha Dissipation as progress approaches & exceeds 1.0
            val alphaDissipate = if (p > 0.25f) {
                (1f - (p - 0.25f) * 0.4f).coerceIn(0.45f, 1f)
            } else 1f

            alpha = alphaDissipate

            // 5. Dynamic Depth Shadow Elevation
            shadowElevation = (6.dp + (12.dp * p)).toPx()
        }
        .drawWithContent {
            drawContent()

            // Apply texture sheen & optical specular highlight overlay when pulling
            if (pullProgress > 0.02f) {
                val p = pullProgress.coerceIn(0f, 1.5f)
                val width = size.width
                val height = size.height

                // A. Specular Refraction Light Sweep Across Surface
                val sweepX = (p * 1.6f - 0.3f) * width
                val sweepWidth = width * 0.6f

                val sheenBrush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        accentColor.takeOrElse { Color.White }.copy(alpha = 0.2f * p),
                        Color.White.copy(alpha = 0.38f * p),
                        accentColor.takeOrElse { Color.White }.copy(alpha = 0.2f * p),
                        Color.Transparent
                    ),
                    start = Offset(sweepX - sweepWidth, 0f),
                    end = Offset(sweepX + sweepWidth, height)
                )
                drawRect(brush = sheenBrush, blendMode = BlendMode.SrcOver)

                // B. Optical Micro-Texture Grain Grid (Tactile Texture Feel)
                val gridAlpha = (p * 0.22f).coerceIn(0f, 0.22f)
                val dotSpacing = 16.dp.toPx()
                var x = 8.dp.toPx()
                while (x < width) {
                    var y = 8.dp.toPx()
                    while (y < height) {
                        drawCircle(
                            color = Color.White.copy(alpha = gridAlpha * 0.65f),
                            radius = 1.25.dp.toPx(),
                            center = Offset(x, y)
                        )
                        y += dotSpacing
                    }
                    x += dotSpacing
                }

                // C. Glowing Specular Rim
                val rimWidth = 1.5.dp.toPx()
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.45f * p),
                    size = Size(width - rimWidth, height - rimWidth),
                    topLeft = Offset(rimWidth / 2f, rimWidth / 2f),
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = rimWidth)
                )
            }
        }
)
