package xyz.draumaz.sustenance.ui.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry

/*
 * Card-style screen transitions for the NavHost.
 *
 * Pop: the leaving screen shrinks into a rounded card and slides right. Push (opening a metric's
 * detail) is the same motion in reverse: the new screen grows in from the right as a card and
 * squares off, while the screen underneath shifts left.
 *
 * Pop transitions Navigation Compose seeks these with the predictive back
 * gesture, so the leaving screen shrinks into a rounded card and slides right while the
 * screen underneath comes in, then finishes or springs back on release.
 *
 * Linear tweens, because the gesture seeks through them by progress and a spring would be
 * almost finished a third of the way in. The leaving screen stays opaque until the last
 * stretch so the two screens don't blur together.
 */

// Short on purpose. NavHost finishes a committed gesture by playing out the rest of these
// specs at their own speed (it doesn't take a finishing spec), so a quick flick released early
// plays nearly the whole duration. The length doesn't change how the screen tracks the finger,
// since the gesture seeks by fraction.
private const val BACK_MILLIS = 220

private val backFloat = tween<Float>(BACK_MILLIS, easing = LinearEasing)
private val backOffset = tween<IntOffset>(BACK_MILLIS, easing = LinearEasing)
private val backFade = keyframes<Float> {
    durationMillis = BACK_MILLIS
    1f at BACK_MILLIS * 3 / 4 using LinearEasing
}

val popEnterBack: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(backOffset) { -it / 8 } + fadeIn(backFloat)
}

val popExitBack: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    scaleOut(backFloat, targetScale = 0.86f) +
        slideOutHorizontally(backOffset) { it / 4 } +
        fadeOut(backFade)
}

private const val OPEN_MILLIS = 400
private val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private fun <T> open() = tween<T>(OPEN_MILLIS, easing = emphasizedDecelerate)

/** The detail screen grows in from the right as a card; it turns solid quickly so nothing shows through. */
val pushEnterCard: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    scaleIn(open(), initialScale = 0.86f) +
        slideInHorizontally(open()) { it / 4 } +
        fadeIn(tween(OPEN_MILLIS / 8, easing = LinearEasing))
}

/** The screen being covered shifts left and fades, mirroring how it comes back on pop. */
val pushExitUnder: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(open()) { -it / 8 } + fadeOut(open())
}

/** True when this transition is opening a metric's detail screen. */
fun AnimatedContentTransitionScope<NavBackStackEntry>.isOpeningDetail(): Boolean =
    targetState.destination.route?.startsWith("detail/") == true

/**
 * Rounds the screen's corners while it's a card: as it leaves on pop, and as it grows in on
 * push, squaring off over the opening animation.
 */
@Composable
fun AnimatedVisibilityScope.ScreenCard(content: @Composable () -> Unit) {
    val corner by transition.animateDp(
        transitionSpec = {
            if (targetState == EnterExitState.Visible) open()
            else tween(BACK_MILLIS / 4, easing = LinearEasing)
        },
        label = "cardCorner",
    ) { if (it == EnterExitState.Visible) 0.dp else 40.dp }
    Box(Modifier.graphicsLayer {
        shape = RoundedCornerShape(corner)
        clip = corner > 0.dp
    }) {
        content()
    }
}
