package crow.evilo.feature.reader

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import crow.evilo.model.MediaKind
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.AbstractLongTimeSource
import kotlin.time.DurationUnit

@OptIn(ExperimentalComposeUiApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class,
    kotlin.time.ExperimentalTime::class)
class MediaLoadingPresentationSceneTest {
    @Test
    fun quickErrorAndSuccessRemainLoadingForAtLeastFourHundredMilliseconds() = runTest {
        val observed = mutableStateOf(MediaBrowserState(MediaKind.MANGA, busy = true))
        var presented = observed.value
        val clock = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
            override fun read(): Long = testScheduler.currentTime
        }
        val scene = ImageComposeScene(100, 100, coroutineContext = backgroundScope.coroutineContext) {
            presented = rememberMediaLoadingPresentation(observed.value, "session", clock)
        }
        try {
            frame(scene)
            advanceTimeBy(40)
            observed.value = observed.value.copy(busy = false, error = "Request failed")
            frame(scene)
            assertTrue(presented.busy)
            assertNull(presented.error)
            advanceTimeBy(359)
            frame(scene)
            assertTrue(presented.busy)
            advanceTimeBy(1)
            frame(scene)
            assertFalse(presented.busy)
            assertEquals("Request failed", presented.error)

            observed.value = observed.value.copy(busy = true, error = null)
            frame(scene)
            advanceTimeBy(20)
            observed.value = observed.value.copy(busy = false, searchPage = 1)
            frame(scene)
            advanceTimeBy(379)
            frame(scene)
            assertTrue(presented.busy)
            advanceTimeBy(1)
            frame(scene)
            assertFalse(presented.busy)
            assertEquals(1, presented.searchPage)
        } finally {
            scene.close()
        }
    }

    @Test
    fun replacedDestinationAndSessionDoNotRetainAnOldDelayedFailure() = runTest {
        val observed = mutableStateOf(MediaBrowserState(MediaKind.MANGA, busy = true))
        val owner = mutableStateOf("first")
        var presented = observed.value
        val clock = object : AbstractLongTimeSource(DurationUnit.MILLISECONDS) {
            override fun read(): Long = testScheduler.currentTime
        }
        val scene = ImageComposeScene(100, 100, coroutineContext = backgroundScope.coroutineContext) {
            presented = rememberMediaLoadingPresentation(observed.value, owner.value, clock)
        }
        try {
            frame(scene)
            advanceTimeBy(20)
            observed.value = observed.value.copy(busy = false, error = "Old error")
            frame(scene)
            observed.value = MediaBrowserState(MediaKind.MANGA, page = MediaBrowserPage.DETAILS)
            frame(scene)
            assertFalse(presented.busy)
            assertNull(presented.error)
            assertEquals(MediaBrowserPage.DETAILS, presented.page)
            owner.value = "second"
            observed.value = MediaBrowserState(MediaKind.NOVEL, searchPage = 1)
            frame(scene)
            advanceTimeBy(500)
            frame(scene)
            assertFalse(presented.busy)
            assertNull(presented.error)
            assertEquals(MediaKind.NOVEL, presented.kind)
        } finally {
            scene.close()
        }
    }

    private fun TestScope.frame(scene: ImageComposeScene) {
        runCurrent()
        scene.render(testScheduler.currentTime * 1_000_000L).close()
        runCurrent()
    }
}
