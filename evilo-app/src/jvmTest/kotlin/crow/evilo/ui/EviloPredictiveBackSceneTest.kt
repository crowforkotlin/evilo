package crow.evilo.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Mount the production page transition: seeking is a no-op without a composed Transition. */
@OptIn(ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class EviloPredictiveBackSceneTest {
    @Test
    fun sharedPreviewSeeksAndCommitsTheMountedDestination() = runTest {
        val state = EviloBackTransitionState("details") { it }
        val captured = state.target("details")
        val events = Channel<EviloBackEvent>(Channel.UNLIMITED)
        var returns = 0
        var completed = false
        lateinit var scope: CoroutineScope
        val scene = ImageComposeScene(400, 800, coroutineContext = backgroundScope.coroutineContext) {
            scope = rememberCoroutineScope()
            EviloPageTransition("details", { it }, { if (it == "details") 1 else 0 }, backState = state) {
                Box(Modifier.size(100.dp))
            }
        }
        try {
            frame(scene)
            scope.launch {
                state.handle(events.receiveAsFlow(), "details", "catalog", { true }, { returns++ },
                    awaitNavigation = {
                        assertTrue(state.previewing)
                        assertTrue(state.seekSharedElements)
                        assertEquals("catalog", state.transition.currentState.value)
                        assertSame(captured, state.target("catalog"))
                    }, sharedElements = true)
                completed = true
            }
            events.trySend(EviloBackEvent(0.3f))
            framesUntil(scene) { state.transition.fraction == 0.3f }
            assertEquals("details", state.transition.currentState.value)
            assertEquals("catalog", state.transition.targetState.value)
            events.trySend(EviloBackEvent(0.7f))
            framesUntil(scene) { state.transition.fraction == 0.7f }
            events.close()
            framesUntil(scene) { completed }
            assertEquals(1, returns)
            assertFalse(state.previewing)
            assertFalse(state.seekSharedElements)
        } finally {
            scene.close()
            events.cancel()
        }
    }

    @Test
    fun cancelledSharedPreviewRestoresTheMountedCurrentPage() = runTest {
        val state = EviloBackTransitionState("details") { it }
        val events = Channel<EviloBackEvent>(Channel.UNLIMITED)
        var returns = 0
        var cancelled = false
        lateinit var scope: CoroutineScope
        val scene = ImageComposeScene(400, 800, coroutineContext = backgroundScope.coroutineContext) {
            scope = rememberCoroutineScope()
            EviloPageTransition("details", { it }, { if (it == "details") 1 else 0 }, backState = state) {
                Box(Modifier.size(100.dp))
            }
        }
        try {
            frame(scene)
            scope.launch {
                try {
                    state.handle(events.receiveAsFlow(), "details", "catalog", { true }, { returns++ },
                        sharedElements = true)
                } catch (_: CancellationException) {
                    cancelled = true
                }
            }
            events.trySend(EviloBackEvent(0.7f))
            framesUntil(scene) { state.transition.fraction == 0.7f }
            events.close(CancellationException("Gesture cancelled"))
            framesUntil(scene) { cancelled }
            assertEquals(0, returns)
            assertEquals("details", state.transition.currentState.value)
            assertEquals("details", state.transition.targetState.value)
            assertFalse(state.previewing)
            assertFalse(state.seekSharedElements)
        } finally {
            scene.close()
            events.cancel()
        }
    }

    private fun TestScope.frame(scene: ImageComposeScene) {
        runCurrent()
        scene.render(testScheduler.currentTime * 1_000_000L).close()
        runCurrent()
        advanceTimeBy(16)
    }

    private fun TestScope.framesUntil(scene: ImageComposeScene, condition: () -> Boolean) {
        repeat(100) {
            frame(scene)
            if (condition()) return
        }
        assertTrue(condition(), "Mounted transition did not settle within 1600ms")
    }
}
