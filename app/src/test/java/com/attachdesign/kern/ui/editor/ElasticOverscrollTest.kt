package com.attachdesign.kern.ui.editor

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalCoroutinesApi::class)
class ElasticOverscrollTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val maxOverscrollPx = 400f

    private lateinit var overscrollState: ElasticOverscrollState

    private class ImmediateFrameClock : MonotonicFrameClock {
        private var time = 1_000_000_000L
        override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R {
            time += 16_000_000L // 16ms per frame
            return onFrame(time)
        }
    }

    @Before
    fun setup() {
        overscrollState = ElasticOverscrollState(testScope, maxOverscrollPx)
    }

    @Test
    fun `initial overscroll offset is zero and does not consume pre-scroll`() {
        assertEquals(0f, overscrollState.overscrollOffset.value, 0.001f)

        val consumed = overscrollState.nestedScrollConnection.onPreScroll(
            available = Offset(0f, 50f),
            source = NestedScrollSource.UserInput
        )
        assertEquals(Offset.Zero, consumed)
    }

    @Test
    fun `pulling down at top of document accumulates positive elastic overscroll`() = testScope.runTest {
        val availableDelta = 100f
        val consumed = overscrollState.nestedScrollConnection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, availableDelta),
            source = NestedScrollSource.UserInput
        )
        testScheduler.advanceUntilIdle()

        assertEquals(Offset(0f, availableDelta), consumed)
        val overscroll = overscrollState.overscrollOffset.value
        assertTrue("Overscroll should be positive when pulled down: $overscroll", overscroll > 0f)
        assertTrue("Overscroll should be damped (< available): $overscroll < $availableDelta", overscroll < availableDelta)
    }

    @Test
    fun `pulling up at bottom of document accumulates negative elastic overscroll`() = testScope.runTest {
        val availableDelta = -100f
        val consumed = overscrollState.nestedScrollConnection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, availableDelta),
            source = NestedScrollSource.UserInput
        )
        testScheduler.advanceUntilIdle()

        assertEquals(Offset(0f, availableDelta), consumed)
        val overscroll = overscrollState.overscrollOffset.value
        assertTrue("Overscroll should be negative when pulled up: $overscroll", overscroll < 0f)
        assertTrue("Overscroll magnitude should be damped: ${abs(overscroll)} < 100", abs(overscroll) < 100f)
    }

    @Test
    fun `reversing drag direction consumes opposing delta and restores towards zero`() = testScope.runTest {
        // First pull down to accumulate positive overscroll
        overscrollState.nestedScrollConnection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, 100f),
            source = NestedScrollSource.UserInput
        )
        testScheduler.advanceUntilIdle()
        val initialOverscroll = overscrollState.overscrollOffset.value
        assertTrue(initialOverscroll > 0f)

        // Now drag upwards (opposite direction)
        val consumed = overscrollState.nestedScrollConnection.onPreScroll(
            available = Offset(0f, -20f),
            source = NestedScrollSource.UserInput
        )
        testScheduler.advanceUntilIdle()

        assertEquals(-20f, consumed.y, 0.001f)
        val reducedOverscroll = overscrollState.overscrollOffset.value
        assertTrue("Overscroll should reduce on opposing drag: $reducedOverscroll < $initialOverscroll", reducedOverscroll < initialOverscroll)
    }

    @Test
    fun `reversing drag past boundary clamps exactly at zero without overshooting`() = testScope.runTest {
        overscrollState.nestedScrollConnection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, 50f),
            source = NestedScrollSource.UserInput
        )
        testScheduler.advanceUntilIdle()
        val initialOverscroll = overscrollState.overscrollOffset.value
        assertTrue(initialOverscroll > 0f)

        // Drag upwards by more than current overscroll
        val consumed = overscrollState.nestedScrollConnection.onPreScroll(
            available = Offset(0f, -200f),
            source = NestedScrollSource.UserInput
        )
        testScheduler.advanceUntilIdle()

        assertEquals(-initialOverscroll, consumed.y, 0.001f)
        assertEquals(0f, overscrollState.overscrollOffset.value, 0.001f)
    }

    @Test
    fun `releasing drag on fling springs overscroll back to zero`() = testScope.runTest {
        withContext(ImmediateFrameClock()) {
            val stateWithClock = ElasticOverscrollState(this, maxOverscrollPx)
            stateWithClock.nestedScrollConnection.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(0f, 150f),
                source = NestedScrollSource.UserInput
            )
            testScheduler.advanceUntilIdle()
            assertTrue(stateWithClock.overscrollOffset.value > 0f)

            stateWithClock.nestedScrollConnection.onPreFling(Velocity(0f, 0f))
            testScheduler.advanceUntilIdle()

            assertEquals(0f, stateWithClock.overscrollOffset.value, 0.001f)
        }
    }

    @Test
    fun `maximum overscroll is strictly capped within max limit`() = testScope.runTest {
        // Simulate continuous aggressive drags
        for (i in 1..20) {
            overscrollState.nestedScrollConnection.onPostScroll(
                consumed = Offset.Zero,
                available = Offset(0f, 200f),
                source = NestedScrollSource.UserInput
            )
            testScheduler.advanceUntilIdle()
        }

        val finalOverscroll = overscrollState.overscrollOffset.value
        assertTrue("Final overscroll should not exceed max limit: $finalOverscroll <= $maxOverscrollPx", finalOverscroll <= maxOverscrollPx)
    }

    @Test
    fun `reset sets overscroll offset immediately to zero`() = testScope.runTest {
        overscrollState.nestedScrollConnection.onPostScroll(
            consumed = Offset.Zero,
            available = Offset(0f, 100f),
            source = NestedScrollSource.UserInput
        )
        testScheduler.advanceUntilIdle()
        assertTrue(overscrollState.overscrollOffset.value > 0f)

        overscrollState.reset()
        testScheduler.advanceUntilIdle()
        assertEquals(0f, overscrollState.overscrollOffset.value, 0.001f)
    }
}
