package com.bar.sga.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.navigation.NavBackStackEntry

/** The app's one set of animations and selection colors. Use these rather than ad-hoc specs. */
object Motion {
    private const val PAGE_MS = 250
    private val sizeSpring = spring<IntSize>(stiffness = Spring.StiffnessMediumLow)
    /** Same feel as [reveal], for values animated by hand (offsets, padding). */
    val floatSpring = spring<Float>(stiffness = Spring.StiffnessMediumLow)
    val dpSpring = spring<Dp>(stiffness = Spring.StiffnessMediumLow)

    /** Anything that appears in place: panels, banners, button rows, form fields. */
    val reveal: EnterTransition = expandVertically(sizeSpring, expandFrom = Alignment.Top) + fadeIn()
    val hide: ExitTransition = shrinkVertically(sizeSpring, shrinkTowards = Alignment.Top) + fadeOut()

    // Pages: a short fade with a slight slide toward the reading direction (mirrored in right-to-left).
    fun AnimatedContentTransitionScope<NavBackStackEntry>.pageEnter(): EnterTransition =
        fadeIn(tween(PAGE_MS)) + slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(PAGE_MS)) { it / 8 }
    fun pageExit(): ExitTransition = fadeOut(tween(PAGE_MS))
    fun pagePopEnter(): EnterTransition = fadeIn(tween(PAGE_MS))
    fun AnimatedContentTransitionScope<NavBackStackEntry>.pagePopExit(): ExitTransition =
        fadeOut(tween(PAGE_MS)) + slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(PAGE_MS)) { it / 8 }
}

/** Card colors for anything tappable to select: the selected one is secondaryContainer, with an animated change. */
@Composable
fun selectableCardColors(selected: Boolean): CardColors {
    val container by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.secondaryContainer else CardDefaults.cardColors().containerColor,
        label = "cardSelection",
    )
    return CardDefaults.cardColors(containerColor = container, contentColor = MaterialTheme.colorScheme.onSurface)
}
