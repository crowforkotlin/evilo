package crow.evilo.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** Callback semantics only; seeking/rendering needs a real Compose frame clock and platform gesture. */
class EviloPredictiveBackTest {
    @Test
    fun gestureShrinksOpaquePageAndMirrorsTheHorizontalShift() {
        val left = EviloPredictiveMotion.geometry(1f, 0f, false, 400f, 800f, 1f)
        val right = EviloPredictiveMotion.geometry(1f, 0f, true, 400f, 800f, 1f)
        assertEquals(0.9f, left.scale, 0.0001f)
        assertEquals(12f, left.x)
        assertEquals(-12f, right.x)
        assertEquals(32f, left.radius)
        assertEquals(1f, left.alpha)
        assertEquals(0f, left.y)
    }

    @Test
    fun commitRestoresScaleAndRadiusWhileFadingOutToTheRight() {
        val motion = EviloPredictiveMotion.geometry(0.6f, 100f, true, 400f, 800f, 1f, commit = 1f)
        assertEquals(1f, motion.scale)
        assertEquals(80f, motion.x)
        assertEquals(0f, motion.y)
        assertEquals(0f, motion.radius)
        assertEquals(0f, motion.alpha)
    }

    @Test
    fun completedGestureReturnsExactlyOnceAfterTheFlowCompletes() = runTest {
        val state = EviloBackTransitionState("current")
        var returns = 0
        val progress = flow<EviloBackEvent> {
            assertEquals(0, returns)
            assertEquals("current", state.transition.currentState)
        }
        state.handle(progress, "current", "previous", isCurrent = { true }, onBack = { returns++ })
        assertEquals(1, returns)
        assertFalse(state.previewing)
    }

    @Test
    fun cancelledGestureDoesNotCommitNavigation() = runTest {
        val state = EviloBackTransitionState("current")
        var returns = 0
        assertFailsWith<CancellationException> {
            state.handle(flow { throw CancellationException("Gesture cancelled") }, "current", "previous",
                isCurrent = { true }, onBack = { returns++ })
        }
        assertEquals(0, returns)
        assertEquals("current", state.transition.currentState)
        assertFalse(state.previewing)
    }

    @Test
    fun replacedDestinationDoesNotPopTheNewPage() = runTest {
        val state = EviloBackTransitionState("current")
        var current = true
        var returns = 0
        state.handle(flow { current = false }, "current", "previous", isCurrent = { current }, onBack = { returns++ })
        assertEquals(0, returns)
        assertFalse(state.previewing)
    }

    @Test
    fun instantBackWithoutPreviewStillRunsTheSameAction() = runTest {
        val state = EviloBackTransitionState("current")
        var returns = 0
        state.handle(emptyFlow(), "current", null, isCurrent = { true }, onBack = { returns++ })
        assertEquals(1, returns)
    }
}
