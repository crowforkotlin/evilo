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
    fun completedGestureReturnsExactlyOnceAfterTheFlowCompletes() = runTest {
        val state = EviloBackTransitionState("current")
        var returns = 0
        val progress = flow<Float> {
            emit(0.45f)
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
