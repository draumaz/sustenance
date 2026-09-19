package xyz.draumaz.sustenance.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Modifier that applies Material 3 Expressive 3D depth, springy fanning separation,
 * holographic sheen, tactile micro-texture, and elastic response
 * as the user pulls up to swipe to yesterday.
 */
fun Modifier.opticalDepthCard(
    sectionIndex: Int,
    cardIndex: Int = 0,
    pullProgressProvider: () -> Float, // Lambda to avoid recomposition during drag
    accentColor: Color = Color.Unspecified,
    cornerRadius: Dp = 28.dp,
    columns: Int = 2,
    isFullWidth: Boolean = false
): Modifier = this.then(
    Modifier
        .graphicsLayer {
            val p = pullProgressProvider().coerceIn(0f, 2.0f)

            val shapeRadius = RoundedCornerShape(cornerRadius)
            shape = shapeRadius

            if (p <= 0.001f) {
                translationX = 0f
                translationY = 0f
                rotationX = 0f
                rotationY = 0f
                scaleX = 1f
                scaleY = 1f
                alpha = 1f
                shadowElevation = 0f
                clip = false
                return@graphicsLayer
            }

            clip = true
            val density = density
            cameraDistance = 12f * density

            // Expressive spring elastic damping & stagger based on cardIndex and sectionIndex
            val staggerOffset = (cardIndex * 0.04f).coerceAtMost(0.2f)
            val elasticP = (p - staggerOffset).coerceAtLeast(0f)
            val bounceMultiplier = 1f + 0.12f * sin(elasticP * Math.PI.toFloat() * 1.5f).coerceAtLeast(0f)

            // 1. Expressive Vertical & Horizontal Depth Separation (Accordion Fanning)
            val sectionOffsetY = when (sectionIndex) {
                0 -> -elasticP * 55f * bounceMultiplier // Top section pulls UP with spring bounce
                1 -> -elasticP * 18f * bounceMultiplier // Middle section floats
                else -> elasticP * 40f * bounceMultiplier // Bottom section drops down
            }

            // Staggered card horizontal & vertical separation
            val cardOffsetX: Float
            val cardOffsetY: Float
            val cardRotationY: Float
            val cardRotationX: Float

            if (isFullWidth || columns <= 1) {
                cardOffsetX = 0f
                cardOffsetY = cardIndex * elasticP * 7f
                cardRotationY = 0f
                cardRotationX = (cardIndex * 1.5f) * elasticP
            } else {
                val col = cardIndex % columns
                val row = cardIndex / columns
                val isLeft = col < columns / 2.0f
                cardOffsetX = if (isLeft) -elasticP * 16f else elasticP * 16f
                cardOffsetY = row * elasticP * 8f
                cardRotationY = if (isLeft) 4.5f * elasticP else -4.5f * elasticP
                cardRotationX = (row * 2f - 1f) * elasticP * 3f
            }

            translationY = (sectionOffsetY + cardOffsetY) * density
            translationX = cardOffsetX * density

            // 2. 3D Perspective Tilt with Expressive Rotation
            val baseRotationX = when (sectionIndex) {
                0 -> -15f * elasticP
                1 -> -6f * elasticP
                else -> 10f * elasticP
            }

            rotationX = (baseRotationX + cardRotationX).coerceIn(-25f, 25f)
            rotationY = cardRotationY.coerceIn(-20f, 20f)

            // 3. Elastic Scale Pop & Recede (Expressive Depth Scale)
            val baseScale = when (sectionIndex) {
                0 -> 1f + (0.04f * elasticP) - (elasticP * 0.06f)
                1 -> 1f + (0.02f * elasticP) - (elasticP * 0.03f)
                else -> 1f - (elasticP * 0.07f)
            }

            val finalScale = baseScale.coerceIn(0.85f, 1.08f)
            scaleX = finalScale
            scaleY = finalScale

            // 4. Smooth Alpha Dissipation for depth fading
            val alphaDissipate = if (p > 0.3f) {
                (1f - (p - 0.3f) * 0.35f).coerceIn(0.5f, 1f)
            } else 1f

            alpha = alphaDissipate

            // 5. Dynamic High-Elevation Shadow (Casting depth onto layers below)
            shadowElevation = (8.dp + (18.dp * p * bounceMultiplier)).toPx()
        }
        .drawWithContent {
            drawContent()

            // Apply holographic sheen, refractive specular lighting, and tactile micro-texture
            val p = pullProgressProvider()
            if (p > 0.01f) {
                val clampedP = p.coerceIn(0f, 2.0f)
                val width = size.width
                val height = size.height
                val radiusPx = cornerRadius.toPx()

                val clipPath = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = 0f,
                            top = 0f,
                            right = width,
                            bottom = height,
                            cornerRadius = CornerRadius(radiusPx)
                        )
                    )
                }

                clipPath(clipPath) {
                    // A. Holographic Refractive Light Sweep
                    val sweepX = (clampedP * 1.8f - 0.4f) * width
                    val sweepWidth = width * 0.7f

                    val resolvedAccent = accentColor.takeOrElse { Color(0xFF5EDDC4) }
                    val sheenBrush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            resolvedAccent.copy(alpha = 0.25f * clampedP),
                            Color.White.copy(alpha = 0.45f * clampedP),
                            resolvedAccent.copy(alpha = 0.25f * clampedP),
                            Color.Transparent
                        ),
                        start = Offset(sweepX - sweepWidth, 0f),
                        end = Offset(sweepX + sweepWidth, height)
                    )
                    drawRoundRect(
                        brush = sheenBrush,
                        cornerRadius = CornerRadius(radiusPx),
                        blendMode = BlendMode.SrcOver
                    )

                    // B. Tactile Micro-Texture Grain Grid (M3 Expressive tactile feel)
                    val gridAlpha = (clampedP * 0.28f).coerceIn(0f, 0.28f)
                    val dotSpacing = 14.dp.toPx()
                    var x = 6.dp.toPx()
                    while (x < width) {
                        var y = 6.dp.toPx()
                        while (y < height) {
                            drawCircle(
                                color = Color.White.copy(alpha = gridAlpha * 0.7f),
                                radius = 1.35.dp.toPx(),
                                center = Offset(x, y)
                            )
                            y += dotSpacing
                        }
                        x += dotSpacing
                    }

                    // C. Glowing Specular Rim
                    val rimWidth = 2.dp.toPx()
                    val rimRadius = (radiusPx - rimWidth / 2f).coerceAtLeast(0f)
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.55f * clampedP),
                        size = Size(width - rimWidth, height - rimWidth),
                        topLeft = Offset(rimWidth / 2f, rimWidth / 2f),
                        cornerRadius = CornerRadius(rimRadius),
                        style = Stroke(width = rimWidth)
                    )
                }
            }
        }
)

fun Modifier.opticalDepthCard(
    sectionIndex: Int,
    cardIndex: Int = 0,
    pullProgress: Float,
    accentColor: Color = Color.Unspecified,
    cornerRadius: Dp = 28.dp,
    columns: Int = 2,
    isFullWidth: Boolean = false
): Modifier = opticalDepthCard(
    sectionIndex = sectionIndex,
    cardIndex = cardIndex,
    pullProgressProvider = { pullProgress },
    accentColor = accentColor,
    cornerRadius = cornerRadius,
    columns = columns,
    isFullWidth = isFullWidth
)

