package xyz.draumaz.sustenance.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.navigation.NavBackStackEntry
import xyz.draumaz.sustenance.data.Metric

/*
 * Opening a metric on a phone: the tapped tile flies open into its detail, the way a Wear OS
 * tile cell expands into the watch app it launches. The tile and the detail share bounds, so
 * the tile's rectangle grows to fill the screen while its contents crossfade into the detail's,
 * and back shrinks the detail into the tile again. Split layouts show the detail in a pane
 * beside the list instead, so they leave these locals unset and nothing is shared.
 */

/** The layout tiles and details share bounds in, or null where nothing flies (split panes). */
val LocalFlyInScope = compositionLocalOf<SharedTransitionScope?> { null }

/** The NavHost destination's own transition, which the shared bounds animate alongside. */
val LocalFlyInDestination = compositionLocalOf<AnimatedVisibilityScope?> { null }

private const val FLY_OPEN_MILLIS = 450
private const val FLY_CLOSE_MILLIS = 320
private const val FADE_OUT_MILLIS = 110
private const val FADE_IN_DELAY_MILLIS = 90
private const val FADE_IN_MILLIS = 200

/** Emphasized: quick off the mark, a long, soft landing, like the watch's launch. */
private val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private fun <T> flyOpen() = tween<T>(FLY_OPEN_MILLIS, easing = emphasized)
private fun <T> flyClose() = tween<T>(FLY_CLOSE_MILLIS, easing = FastOutSlowInEasing)

/**
 * Growing (opening) takes the emphasized curve; shrinking back (back, or the predictive back
 * gesture seeking through it) a plainer, shorter one, so it tracks the finger evenly.
 */
private val flyBounds = BoundsTransform { initial, target ->
    if (target.width * target.height >= initial.width * initial.height) flyOpen() else flyClose()
}

/**
 * Tags a metric tile, or the detail it opens into, as one shape that flies between them.
 * [shape] clips it in flight: the tile's own corners, which the growing detail keeps until it
 * lands. A no-op where [LocalFlyInScope] is unset.
 */
@Composable
fun Modifier.metricFlyIn(metric: Metric, shape: Shape): Modifier {
    val shared = LocalFlyInScope.current ?: return this
    val destination = LocalFlyInDestination.current ?: return this
    return with(shared) {
        this@metricFlyIn.sharedBounds(
            sharedContentState = rememberSharedContentState("metric_${metric.key}"),
            animatedVisibilityScope = destination,
            // Fade through rather than crossfade: what's leaving is gone in the first stretch,
            // and what's arriving comes in once the bounds are well under way, so a tile's text
            // is never seen blown up to the screen's width, nor the detail squeezed into a tile.
            enter = fadeIn(tween(FADE_IN_MILLIS, delayMillis = FADE_IN_DELAY_MILLIS, easing = LinearEasing)),
            exit = fadeOut(tween(FADE_OUT_MILLIS, easing = LinearEasing)),
            boundsTransform = flyBounds,
            clipInOverlayDuringTransition = OverlayClip(shape),
        )
    }
}

private fun NavBackStackEntry.isMetricDetail() = destination.route?.startsWith("detail/") == true

/** True when this transition opens a metric's detail from a list. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.isOpeningMetric(): Boolean =
    targetState.isMetricDetail()

/** True when this transition returns from a metric's detail to the list. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.isClosingMetric(): Boolean =
    initialState.isMetricDetail()

/*
 * The routes' own transitions only frame the flight: the detail stays present for its length
 * (the shared bounds do the moving), and the list recedes a touch behind it, as the watch face
 * does behind a launching app.
 */
fun flyDetailEnter(): EnterTransition = fadeIn(flyOpen())
fun flyDetailExit(): ExitTransition = fadeOut(flyClose())
fun flyListExit(): ExitTransition = fadeOut(flyOpen()) + scaleOut(flyOpen(), targetScale = 0.94f)
fun flyListEnter(): EnterTransition = fadeIn(flyClose()) + scaleIn(flyClose(), initialScale = 0.94f)
