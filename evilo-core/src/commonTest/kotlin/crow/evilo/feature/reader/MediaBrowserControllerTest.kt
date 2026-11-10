@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package crow.evilo.feature.reader

import crow.evilo.model.MediaChapter
import crow.evilo.model.MediaContent
import crow.evilo.model.MediaId
import crow.evilo.model.MediaItem
import crow.evilo.model.MediaKind
import crow.evilo.model.MediaStream
import crow.evilo.plugin.api.MediaSource
import crow.evilo.plugin.api.PluginDescriptor
import crow.evilo.plugin.api.PluginOperation
import crow.evilo.plugin.api.PluginOperationKind
import crow.evilo.plugin.api.PluginResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaBrowserControllerTest {
    @Test
    fun replacedSearchDoesNotPublishAResponseFromACancelledOperation() = runTest {
        val delayed = CompletableDeferred<PluginResult>()
        var started = false
        val session = FakeSession { operation ->
            if (operation.query == "old") {
                started = true
                withContext(NonCancellable) { delayed.await() }
            } else PluginResult(items = listOf(item("new")))
        }
        val controller = MediaBrowserController(session, MediaKind.MANGA, this)
        try {
            controller.search("old")
            runCurrent()
            assertTrue(started)
            controller.search("new")
            delayed.complete(PluginResult(items = listOf(item("old"))))
            runCurrent()
            assertEquals("new", controller.state.value.query)
            assertEquals(listOf("new"), controller.state.value.items.map { it.id.value })
            assertNull(controller.state.value.error)
        } finally {
            delayed.complete(PluginResult())
            controller.close()
        }
    }

    @Test
    fun paginationDeduplicatesQualifiedIdsAndStopsAtTheLastPage() = runTest {
        var calls = 0
        val session = FakeSession { operation ->
            calls++
            if (operation.page == 1) PluginResult(items = listOf(item("a")), hasNextPage = true)
            else PluginResult(items = listOf(item("a"), item("b")))
        }
        val controller = MediaBrowserController(session, MediaKind.MANGA, this)
        try {
            controller.search("")
            runCurrent()
            controller.loadMore()
            runCurrent()
            assertEquals(2, controller.state.value.searchPage)
            assertEquals(listOf("a", "b"), controller.state.value.items.map { it.id.value })
            controller.loadMore()
            runCurrent()
            assertEquals(2, calls)
        } finally {
            controller.close()
        }
    }

    @Test
    fun failedContentCanRetryTheSelectedChapterAndReturnToDetails() = runTest {
        val selected = item("a")
        val chapter = MediaChapter(selected.id, "chapter-1", "Chapter one", "https://media.example.com/one")
        var contentCalls = 0
        val session = FakeSession { operation ->
            when (operation.operation) {
                PluginOperationKind.DETAILS -> PluginResult(details = selected.copy(description = "Details"))
                PluginOperationKind.CHAPTERS -> PluginResult(chapters = listOf(chapter))
                PluginOperationKind.CONTENT -> {
                    assertEquals(chapter, operation.chapter)
                    if (++contentCalls == 1) error("Content request failed.")
                    PluginResult(content = MediaContent(text = "Chapter text"))
                }
                else -> error("Unexpected test operation.")
            }
        }
        val controller = MediaBrowserController(session, MediaKind.MANGA, this)
        try {
            controller.openItem(selected)
            runCurrent()
            assertEquals(listOf(chapter), controller.state.value.chapters)
            controller.openChapter(0)
            runCurrent()
            assertEquals("Content request failed.", controller.state.value.error)
            controller.retry()
            runCurrent()
            assertEquals("Chapter text", controller.state.value.content?.text)
            assertNull(controller.state.value.error)
            controller.back()
            runCurrent()
            assertEquals(MediaBrowserPage.DETAILS, controller.state.value.page)
            assertNull(controller.state.value.content)
            assertEquals(selected.id, controller.state.value.selectedItem?.id)
        } finally {
            controller.close()
            controller.close()
        }
        assertEquals(1, session.closeCalls)
    }

    private class FakeSession(handler: suspend (PluginOperation) -> PluginResult) : MediaSourceSession {
        var closeCalls = 0
        override val source = object : MediaSource {
            override val descriptor = PluginDescriptor("test.plugin", "Test", "1.0.0", 1, setOf(MediaKind.MANGA), listOf("en"))
            override suspend fun execute(operation: PluginOperation): PluginResult = handler(operation)
            override suspend fun close() = Unit
        }
        override suspend fun imageBytes(url: String, headers: Map<String, String>): ByteArray =
            error("These tests do not request images.")
        override fun externalStreamUrl(stream: MediaStream): String = stream.url
        override suspend fun close() { closeCalls++; source.close() }
    }

    private companion object {
        fun item(id: String) = MediaItem(MediaId("test.plugin", MediaKind.MANGA, id), id, "https://media.example.com/$id")
    }
}
