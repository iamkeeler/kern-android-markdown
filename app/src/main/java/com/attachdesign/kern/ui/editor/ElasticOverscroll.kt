package com.attachdesign.kern.ui.editor

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * State holding the elastic overscroll displacement and managing the [NestedScrollConnection]
 * for rubber-band resistance and spring bounce-back at document boundaries.
 */
@Stable
class ElasticOverscrollState(
    private val scope: CoroutineScope,
    private val maxOverscrollPx: Float
) {
    val overscrollOffset: Animatable<Float, AnimationVector1D> = Animatable(0f)

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            val current = overscrollOffset.value
            if (current == 0f) return Offset.Zero

            // If user drags in the opposite direction of current overscroll, consume delta to return towards 0
            if ((current > 0f && available.y < 0f) || (current < 0f && available.y > 0f)) {
                val newOffset = current + available.y
                val consumedY = if (current > 0f) {
                    if (newOffset <= 0f) {
                        scope.launch { overscrollOffset.snapTo(0f) }
                        -current
                    } else {
                        scope.launch { overscrollOffset.snapTo(newOffset) }
                        available.y
                    }
                } else {
                    if (newOffset >= 0f) {
                        scope.launch { overscrollOffset.snapTo(0f) }
                        -current
                    } else {
                        scope.launch { overscrollOffset.snapTo(newOffset) }
                        available.y
                    }
                }
                return Offset(0f, consumedY)
            }
            return Offset.Zero
        }

        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource
        ): Offset {
            if (source == NestedScrollSource.UserInput && available.y != 0f) {
                val current = overscrollOffset.value
                val absCurrent = abs(current)
                val resistance = (0.45f * (1f - (absCurrent / maxOverscrollPx).coerceIn(0f, 1f))).coerceAtLeast(0.05f)
                val delta = available.y * resistance
                val target = (current + delta).coerceIn(-maxOverscrollPx, maxOverscrollPx)
                scope.launch { overscrollOffset.snapTo(target) }
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (overscrollOffset.value != 0f) {
                animateBackToZero(available.y)
                return available
            }
            return Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (overscrollOffset.value != 0f || available.y != 0f) {
                val current = overscrollOffset.value
                if (current == 0f && available.y != 0f) {
                    val flingDamped = (available.y * 0.08f).coerceIn(-maxOverscrollPx * 0.4f, maxOverscrollPx * 0.4f)
                    overscrollOffset.snapTo(flingDamped)
                }
                animateBackToZero(available.y)
                return available
            }
            return Velocity.Zero
        }
    }

    suspend fun animateBackToZero(initialVelocity: Float = 0f) {
        overscrollOffset.animateTo(
            targetValue = 0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            ),
            initialVelocity = initialVelocity.coerceIn(-1500f, 1500f)
        )
    }

    fun reset() {
        scope.launch {
            overscrollOffset.snapTo(0f)
        }
    }
}

/**
 * Creates and remembers an [ElasticOverscrollState] instance tied to the composable lifecycle.
 */
@Composable
fun rememberElasticOverscrollState(
    maxOverscroll: Dp = 140.dp
): ElasticOverscrollState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val maxPx = with(density) { maxOverscroll.toPx() }
    return remember(scope, maxPx) {
        ElasticOverscrollState(scope, maxPx)
    }
}

/**
 * Container providing elastic overscroll scroll translation and visual elastic indicators
 * at the top and bottom bounds of the editor document.
 */
@Composable
fun ElasticOverscrollContainer(
    state: ElasticOverscrollState,
    accentColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val overscroll = state.overscrollOffset.value
    val absOverscroll = abs(overscroll)
    val progress = (absOverscroll / 120f).coerceIn(0f, 1f)

    Box(
        modifier = modifier.nestedScroll(state.nestedScrollConnection)
    ) {
        // Content with hardware-accelerated elastic translation
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationY = overscroll
                }
        ) {
            content()
        }

        // Top Elastic Indicator
        if (overscroll > 0.5f) {
            val indicatorWidth = 40.dp + (100.dp * progress)
            val indicatorAlpha = (progress * 0.85f).coerceIn(0.1f, 0.85f)
            val indicatorDisplacement = (overscroll * 0.2f).coerceAtMost(12f).dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp + indicatorDisplacement),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .width(indicatorWidth)
                        .height(3.5.dp)
                        .alpha(indicatorAlpha)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
            }
        }

        // Bottom Elastic Indicator
        if (overscroll < -0.5f) {
            val indicatorWidth = 40.dp + (100.dp * progress)
            val indicatorAlpha = (progress * 0.85f).coerceIn(0.1f, 0.85f)
            val indicatorDisplacement = (absOverscroll * 0.2f).coerceAtMost(12f).dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp + indicatorDisplacement),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .width(indicatorWidth)
                        .height(3.5.dp)
                        .alpha(indicatorAlpha)
                        .clip(RoundedCornerShape(2.dp))
                        .background(accentColor)
                )
            }
        }
    }
}
