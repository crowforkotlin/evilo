package crow.evilo.ui

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Geometry and callback tests; mounted page rendering still requires platform validation. */
class EviloPredictiveBackTest {
    @Test
    fun gestureShrinksOpaquePageAndMirrorsTheHorizontalShift() {
        val left = EviloPredictiveMotion.geometry(1f, 0f, false, 400f, 800f, 1f)
        val right = EviloPredictiveMotion.geometry(1f, 0f, true, 400f, 800f, 1f)
        assertEquals(0.94f, left.scale, 0.0001f)
        assertEquals(24f, left.x, 0.0001f)
        assertEquals(-24f, right.x, 0.0001f)
        assertEquals(24f, left.radius)
        assertEquals(1f, left.alpha)
        assertEquals(0f, left.y)
    }

    @Test
    fun commitContinuesInTheGestureDirectionWithoutInflatingThePage() {
        val drag = EviloPredictiveMotion.geometry(0.6f, 100f, true, 400f, 800f, 1f)
        val motion = EviloPredictiveMotion.geometry(0.6f, 100f, true, 400f, 800f, 1f, commit = 1f)
        assertEquals(drag.scale, motion.scale)
        assertEquals(-56f, motion.x, 0.0001f)
        assertTrue(motion.x < drag.x)
        assertEquals(0f, motion.y)
        assertEquals(drag.radius, motion.radius)
        assertEquals(0f, motion.alpha)
    }

    @Test
    fun commitStartsExactlyAtTheDraggedGeometryAndMirrorsBothEdges() {
        val drag = EviloPredictiveMotion.geometry(0.4f, 80f, false, 400f, 800f, 1f)
        val start = EviloPredictiveMotion.geometry(0.4f, 80f, false, 400f, 800f, 1f, commit = 0f)
        assertEquals(drag, start)
        for (commit in listOf(0f, 0.25f, 0.5f, 1f)) {
            val left = EviloPredictiveMotion.geometry(0.4f, 80f, false, 400f, 800f, 1f, commit)
            val right = EviloPredictiveMotion.geometry(0.4f, 80f, true, 400f, 800f, 1f, commit)
            assertEquals(left.x, -right.x)
            assertEquals(left.scale, right.scale)
            assertEquals(left.alpha, right.alpha)
        }
    }

    @Test
    fun zeroProgressRestoresThePageEvenAfterVerticalFingerMovement() {
        val motion = EviloPredictiveMotion.geometry(0f, 400f, false, 400f, 800f, 1f)
        assertEquals(EviloBackGeometry(1f, 0f, 0f, 0f, 1f), motion)
    }

    @Test
    fun completedPreviewKeepsItsGeometryUntilNavigationHasBeenObserved() = runTest {
        val state = EviloBackTransitionState("current") { it }
        var returns = 0
        var waited = false
        withContext(TestFrameClock()) {
            state.handle(flow { emit(EviloBackEvent(0.5f, 80f)) }, "current", "previous",
                isCurrent = { true }, onBack = { returns++ }, awaitNavigation = {
                    assertEquals(1, returns)
                    assertTrue(state.previewing)
                    assertEquals("current", state.outgoing)
                    assertEquals(1f, state.commitFraction)
                    waited = true
                })
        }
        assertTrue(waited)
        assertFalse(state.previewing)
        assertEquals(0f, state.commitFraction)
    }

    @Test
    fun sharedElementModeIsRetainedThroughCommitAndClearedAfterNavigation() = runTest {
        val state = EviloBackTransitionState("details") { it }
        val captured = state.target("details")
        var returns = 0
        withContext(TestFrameClock()) {
            state.handle(flow {
                emit(EviloBackEvent(0.3f))
                assertTrue(state.seekSharedElements)
                assertSame(captured, state.target("details"))
                emit(EviloBackEvent(0.7f))
            }, "details", "catalog", isCurrent = { true }, onBack = { returns++ },
                awaitNavigation = {
                    assertTrue(state.seekSharedElements)
                    // SeekableTransitionState does not mutate until rememberTransition mounts it.
                    // The JVM scene tests verify the actual composed transition destination.
                    assertEquals(1f, state.commitFraction)
                    assertSame(captured, state.target("catalog"))
                }, sharedElements = true)
        }
        assertEquals(1, returns)
        assertFalse(state.seekSharedElements)
        assertFalse(state.previewing)
    }

    @Test
    fun cancelledPreviewDoesNotCommitAndFullyRestoresGestureGeometry() = runTest {
        val state = EviloBackTransitionState("current") { it }
        var returns = 0
        withContext(TestFrameClock()) {
            assertFailsWith<CancellationException> {
                state.handle(flow {
                    emit(EviloBackEvent(0.7f, 120f))
                    throw CancellationException("Gesture cancelled")
                }, "current", "previous", isCurrent = { true }, onBack = { returns++ })
            }
        }
        assertEquals(0, returns)
        assertFalse(state.previewing)
        assertEquals(EviloBackEvent(0f), state.event)
        assertEquals(0f, state.commitFraction)
    }

    @Test
    fun completedGestureReturnsExactlyOnceAfterTheFlowCompletes() = runTest {
        val state = EviloBackTransitionState("current") { it }
        var returns = 0
        val progress = flow<EviloBackEvent> {
            assertEquals(0, returns)
            assertEquals("current", state.transition.currentState.value)
        }
        state.handle(progress, "current", "previous", isCurrent = { true }, onBack = { returns++ })
        assertEquals(1, returns)
        assertFalse(state.previewing)
    }

    @Test
    fun cancelledGestureDoesNotCommitNavigation() = runTest {
        val state = EviloBackTransitionState("current") { it }
        var returns = 0
        assertFailsWith<CancellationException> {
            state.handle(flow { throw CancellationException("Gesture cancelled") }, "current", "previous",
                isCurrent = { true }, onBack = { returns++ })
        }
        assertEquals(0, returns)
        assertEquals("current", state.transition.currentState.value)
        assertFalse(state.previewing)
    }

    @Test
    fun replacedDestinationDoesNotPopTheNewPage() = runTest {
        val state = EviloBackTransitionState("current") { it }
        var current = true
        var returns = 0
        state.handle(flow { current = false }, "current", "previous", isCurrent = { current }, onBack = { returns++ })
        assertEquals(0, returns)
        assertFalse(state.previewing)
    }

    @Test
    fun instantBackWithoutPreviewStillRunsTheSameAction() = runTest {
        val state = EviloBackTransitionState("current") { it }
        var returns = 0
        state.handle(emptyFlow(), "current", null, isCurrent = { true }, onBack = { returns++ })
        assertEquals(1, returns)
    }

    private class TestFrameClock : MonotonicFrameClock {
        private var frameNanos = 0L
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            delay(16)
            frameNanos += 16_000_000L
            return onFrame(frameNanos)
        }
    }
}
