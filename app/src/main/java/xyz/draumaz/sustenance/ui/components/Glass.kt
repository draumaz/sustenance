package xyz.draumaz.sustenance.ui.components

import android.annotation.SuppressLint
import android.graphics.RecordingCanvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.draw
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Whether this device has Android 17 QPR2's backdrop render effects; anything older (back to
 * minSdk 30) gets plain tonal fills. SDK_INT_FULL itself only exists from 36, hence the guard.
 *
 * Ported from Lexeme (and Nickel before it), so the three apps' glass reads the same.
 */
val GlassSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA &&
    Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.CINNAMON_BUN_2

/**
 * Frosted glass: fills [shape] with [tint] and blurs whatever was already drawn behind it by
 * [blur], through QPR2's `RenderNode.setBackdropRenderEffect`.
 *
 * The tint stays a Material colour role at partial alpha rather than a neutral white wash,
 * so a glass surface still reads as the container it replaces (the navigation bar is still
 * the toolbar's container colour) and keeps the same on-colour contrast. Glass is a treatment
 * of a tonal surface, not a new surface.
 *
 * The content is recorded into a platform [RenderNode] of its own because Compose's
 * graphicsLayer has no backdrop slot: its renderEffect only filters the layer's own pixels.
 * The node is drawn back into Compose's recording canvas, so it composites in place and
 * moves with any graphicsLayer above it. Without hardware acceleration (or before QPR2)
 * this falls back to [fallback], an opaque-enough tint that text behind can't muddle it.
 *
 * [blur] is read at draw time, so animating it re-records only this node, never recomposes.
 */
fun Modifier.glass(
    shape: Shape,
    tint: Color,
    fallback: Color = tint,
    blur: () -> Dp = DefaultBlur,
): Modifier = this then GlassElement(shape, tint, fallback, blur)

/** Shared, so the element compares equal across recompositions and doesn't redraw for nothing. */
private val DefaultBlur: () -> Dp = { GlassDefaults.Blur }

object GlassDefaults {
    /** Strong enough that list text behind turns to colour, not readable ghosts. */
    val Blur = 24.dp

    /**
     * How much of a card's usual container colour its glass keeps: the navigation pill's wash,
     * so the cards and the bar read as one material.
     */
    const val CardTint = 0.5f
}

/**
 * [glass] on QPR2, nothing elsewhere: for a card whose own container turns transparent under
 * glass (see [glassOr]) and keeps its solid colour on older devices.
 */
fun Modifier.glassIfSupported(shape: Shape, tint: Color): Modifier =
    if (GlassSupported) glass(shape, tint) else this

/** The container colour for a card wearing [glassIfSupported]: clear under glass, [solid] without. */
fun glassOr(solid: Color): Color = if (GlassSupported) Color.Transparent else solid

private data class GlassElement(
    val shape: Shape,
    val tint: Color,
    val fallback: Color,
    val blur: () -> Dp,
) : ModifierNodeElement<GlassNode>() {
    override fun create() = GlassNode(shape, tint, fallback, blur)
    override fun update(node: GlassNode) {
        node.shape = shape
        node.tint = tint
        node.fallback = fallback
        node.blur = blur
        node.invalidateDraw()
    }
    override fun InspectorInfo.inspectableProperties() {
        name = "glass"
        properties["shape"] = shape
        properties["tint"] = tint
    }
}

private class GlassNode(
    var shape: Shape,
    var tint: Color,
    var fallback: Color,
    var blur: () -> Dp,
) : Modifier.Node(), DrawModifierNode {
    private var renderNode: RenderNode? = null
    private var lastBlurPx = -1f
    /** The last outline built, with what it was built for (plain fields avoid per-frame allocations). */
    private var cachedSize = Size.Unspecified
    private var cachedDirection: LayoutDirection? = null
    private var cachedShape: Shape? = null
    private var cachedOutline: Outline? = null
    /** The outline the render node already clips to, so it isn't rebuilt every frame. */
    private var appliedOutline: Outline? = null

    override fun onDetach() {
        renderNode?.discardDisplayList()
        renderNode = null
        lastBlurPx = -1f
        cachedSize = Size.Unspecified
        cachedDirection = null
        cachedShape = null
        cachedOutline = null
        appliedOutline = null
    }

    // setBackdropRenderEffect is 37.2; every path to it is behind GlassSupported.
    @SuppressLint("NewApi")
    override fun ContentDrawScope.draw() {
        val shapeOutline = if (cachedOutline != null && cachedSize == size && cachedDirection == layoutDirection && cachedShape == shape) {
            cachedOutline!!
        } else {
            shape.createOutline(size, layoutDirection, this).also {
                cachedSize = size
                cachedDirection = layoutDirection
                cachedShape = shape
                cachedOutline = it
            }
        }

        val target = drawContext.canvas.nativeCanvas
        if (!GlassSupported || target !is RecordingCanvas || size.isEmpty()) {
            drawOutline(shapeOutline, fallback)
            drawContent()
            return
        }

        val w = size.width.roundToInt()
        val h = size.height.roundToInt()
        val rn = renderNode ?: RenderNode("glass").also { renderNode = it }
        rn.setPosition(0, 0, w, h)
        if (shapeOutline !== appliedOutline) {
            applyOutline(rn, shapeOutline)
            appliedOutline = shapeOutline
        }

        // A zero radius would still pay for the backdrop pass; drop the effect instead.
        val blurPx = blur().toPx().coerceAtLeast(0f)
        if (blurPx != lastBlurPx) {
            rn.setBackdropRenderEffect(
                if (blurPx < 0.5f) null
                else RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
            )
            lastBlurPx = blurPx
        }

        val content = this
        val recording = rn.beginRecording(w, h)
        try {
            draw(this, layoutDirection, androidx.compose.ui.graphics.Canvas(recording), size) {
                drawOutline(shapeOutline, tint)
                content.drawContent()
            }
        } finally {
            rn.endRecording()
        }
        target.drawRenderNode(rn)
    }

    /**
     * Clips the node, and with it the backdrop pass, to the shape: otherwise a scalloped or
     * rounded surface would frost a rectangle of the list around itself.
     */
    @SuppressLint("NewApi") // Only reached on QPR2 (see draw); setPath is API 30.
    private fun applyOutline(rn: RenderNode, outline: Outline) {
        val platform = android.graphics.Outline()
        when (outline) {
            is Outline.Rectangle -> platform.setRect(
                outline.rect.left.roundToInt(), outline.rect.top.roundToInt(),
                outline.rect.right.roundToInt(), outline.rect.bottom.roundToInt(),
            )
            is Outline.Rounded -> {
                val r = outline.roundRect
                if (r.topLeftCornerRadius == r.topRightCornerRadius &&
                    r.topLeftCornerRadius == r.bottomLeftCornerRadius &&
                    r.topLeftCornerRadius == r.bottomRightCornerRadius
                ) {
                    platform.setRoundRect(
                        r.left.roundToInt(), r.top.roundToInt(),
                        r.right.roundToInt(), r.bottom.roundToInt(),
                        r.topLeftCornerRadius.x,
                    )
                } else {
                    platform.setPath(androidx.compose.ui.graphics.Path().apply { addRoundRect(r) }.asAndroidPath())
                }
            }
            is Outline.Generic -> platform.setPath(outline.path.asAndroidPath())
        }
        rn.setOutline(platform)
        rn.clipToOutline = true
    }
}
