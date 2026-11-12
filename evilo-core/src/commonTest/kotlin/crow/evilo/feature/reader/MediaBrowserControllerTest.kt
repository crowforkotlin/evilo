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
import crow.evilo.plugin.api.SourceRequest
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
    fun backPreviewDoesNotDispatchRequestsOrChangeControllerState() = runTest {
        val selected = item("a")
        var calls = 0
        val session = FakeSession { operation ->
            calls++
            when (operation.operation) {
                PluginOperationKind.SEARCH -> PluginResult(items = listOf(selected))
                PluginOperationKind.DETAILS -> PluginResult(details = selected)
                PluginOperationKind.CHAPTERS -> PluginResult()
                else -> error("Unexpected test operation.")
            }
        }
        val controller = MediaBrowserController(session, MediaKind.MANGA, this)
        try {
            controller.dispatch(MediaBrowserIntent.Search("retained"))
            runCurrent()
            controller.dispatch(MediaBrowserIntent.OpenItem(selected))
            runCurrent()
            val current = controller.state.value
            val requestCount = calls
            val preview = current.backDestination()
            assertEquals(MediaBrowserPage.CATALOG, preview?.page)
            assertEquals("retained", preview?.query)
            assertEquals(listOf(selected), preview?.items)
            assertEquals(current, controller.state.value)
            assertEquals(requestCount, calls)
            controller.dispatch(MediaBrowserIntent.Back)
            runCurrent()
            assertEquals(preview, controller.state.value)
            assertEquals(requestCount, calls)
        } finally { controller.close() }
    }

    @Test
    fun returningThroughDetailsKeepsTheCatalogQueryPagesAndResultsWithoutAnotherSearch() = runTest {
        val selected = item("a")
        val calls = mutableListOf<PluginOperation>()
        val session = FakeSession { operation ->
            calls += operation
            when (operation.operation) {
                PluginOperationKind.SEARCH -> if (operation.page == 1) PluginResult(items = listOf(selected), hasNextPage = true)
                    else PluginResult(items = listOf(item("b")))
                PluginOperationKind.DETAILS -> PluginResult(details = selected.copy(description = "Details"))
                PluginOperationKind.CHAPTERS -> PluginResult()
                else -> error("Unexpected test operation.")
            }
        }
        val controller = MediaBrowserController(session, MediaKind.MANGA, this)
        try {
            controller.dispatch(MediaBrowserIntent.Search("catalog query"))
            runCurrent()
            controller.dispatch(MediaBrowserIntent.LoadMore)
            runCurrent()
            val catalog = controller.state.value
            controller.dispatch(MediaBrowserIntent.OpenItem(selected))
            runCurrent()
            controller.dispatch(MediaBrowserIntent.Back)
            runCurrent()
            assertEquals(MediaBrowserPage.CATALOG, controller.state.value.page)
            assertEquals(catalog.query, controller.state.value.query)
            assertEquals(catalog.searchPage, controller.state.value.searchPage)
            assertEquals(catalog.items, controller.state.value.items)
            assertEquals(catalog.hasNextPage, controller.state.value.hasNextPage)
            assertEquals(2, calls.count { it.operation == PluginOperationKind.SEARCH })
            assertNull(controller.state.value.error)
        } finally { controller.close() }
    }

    @Test
    fun backCancelsDetailsAndCannotPublishItsLateResponseOrStartChapters() = runTest {
        val selected = item("a")
        val gate = CompletableDeferred<PluginResult>()
        var detailsStarted = false
        var chapterCalls = 0
        val session = FakeSession { operation ->
            when (operation.operation) {
                PluginOperationKind.SEARCH -> PluginResult(items = listOf(selected))
                PluginOperationKind.DETAILS -> { detailsStarted = true; withContext(NonCancellable) { gate.await() } }
                PluginOperationKind.CHAPTERS -> { chapterCalls++; PluginResult() }
                else -> error("Unexpected test operation.")
            }
        }
        val controller = MediaBrowserController(session, MediaKind.MANGA, this)
        try {
            controller.dispatch(MediaBrowserIntent.Search("kept query"))
            runCurrent()
            controller.dispatch(MediaBrowserIntent.OpenItem(selected))
            runCurrent()
            assertTrue(detailsStarted)
            controller.dispatch(MediaBrowserIntent.Back)
            gate.complete(PluginResult(details = selected.copy(description = "Obsolete details")))
            runCurrent()
            assertEquals(MediaBrowserPage.CATALOG, controller.state.value.page)
            assertEquals("kept query", controller.state.value.query)
            assertEquals(listOf(selected), controller.state.value.items)
            assertNull(controller.state.value.selectedItem)
            assertNull(controller.state.value.error)
            assertEquals(0, chapterCalls)
        } finally { gate.complete(PluginResult()); controller.close() }
    }

    @Test
    fun backCancelsChapterContentAndKeepsTheAlreadyLoadedDetails() = runTest {
        val selected = item("a")
        val chapter = MediaChapter(selected.id, "chapter-1", "Chapter one", "https://media.example.com/one")
        val gate = CompletableDeferred<PluginResult>()
        var contentStarted = false
        val session = FakeSession { operation ->
            when (operation.operation) {
                PluginOperationKind.DETAILS -> PluginResult(details = selected)
                PluginOperationKind.CHAPTERS -> PluginResult(chapters = listOf(chapter))
                PluginOperationKind.CONTENT -> { contentStarted = true; withContext(NonCancellable) { gate.await() } }
                else -> error("Unexpected test operation.")
            }
        }
        val controller = MediaBrowserController(session, MediaKind.MANGA, this)
        try {
            controller.dispatch(MediaBrowserIntent.OpenItem(selected))
            runCurrent()
            controller.dispatch(MediaBrowserIntent.OpenChapter(0))
            runCurrent()
            assertTrue(contentStarted)
            controller.dispatch(MediaBrowserIntent.Back)
            gate.complete(PluginResult(content = MediaContent(text = "Obsolete content")))
            runCurrent()
            assertEquals(MediaBrowserPage.DETAILS, controller.state.value.page)
            assertEquals(selected, controller.state.value.selectedItem)
            assertEquals(listOf(chapter), controller.state.value.chapters)
            assertNull(controller.state.value.chapterIndex)
            assertNull(controller.state.value.content)
            assertNull(controller.state.value.error)
        } finally { gate.complete(PluginResult()); controller.close() }
    }

    @Test
    fun retryingFailedPaginationRequestsTheFailedPageWithoutDiscardingPriorResults() = runTest {
        val pages = mutableListOf<Int>()
        var failed = false
        val session = FakeSession { operation ->
            pages += operation.page
            when {
                operation.page == 1 -> PluginResult(items = listOf(item("a")), hasNextPage = true)
                !failed -> { failed = true; error("Page two failed.") }
                else -> PluginResult(items = listOf(item("a"), item("b")))
            }
        }
        val controller = MediaBrowserController(session, MediaKind.MANGA, this)
        try {
            controller.dispatch(MediaBrowserIntent.Search("kept query"))
            runCurrent()
            controller.dispatch(MediaBrowserIntent.LoadMore)
            runCurrent()
            assertEquals(1, controller.state.value.searchPage)
            assertEquals(listOf("a"), controller.state.value.items.map { it.id.value })
            assertEquals("Page two failed.", controller.state.value.error)
            controller.dispatch(MediaBrowserIntent.Retry)
            runCurrent()
            assertEquals(listOf(1, 2, 2), pages)
            assertEquals(2, controller.state.value.searchPage)
            assertEquals("kept query", controller.state.value.query)
            assertEquals(listOf("a", "b"), controller.state.value.items.map { it.id.value })
            assertNull(controller.state.value.error)
        } finally { controller.close() }
    }

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
            controller.dispatch(MediaBrowserIntent.Search("old"))
            runCurrent()
            assertTrue(started)
            controller.dispatch(MediaBrowserIntent.Search("new"))
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
            controller.dispatch(MediaBrowserIntent.Search(""))
            runCurrent()
            controller.dispatch(MediaBrowserIntent.LoadMore)
            runCurrent()
            assertEquals(2, controller.state.value.searchPage)
            assertEquals(listOf("a", "b"), controller.state.value.items.map { it.id.value })
            controller.dispatch(MediaBrowserIntent.LoadMore)
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
            controller.dispatch(MediaBrowserIntent.OpenItem(selected))
            runCurrent()
            assertEquals(listOf(chapter), controller.state.value.chapters)
            controller.dispatch(MediaBrowserIntent.OpenChapter(0))
            runCurrent()
            assertEquals("Content request failed.", controller.state.value.error)
            controller.dispatch(MediaBrowserIntent.Retry)
            runCurrent()
            assertEquals("Chapter text", controller.state.value.content?.text)
            assertNull(controller.state.value.error)
            controller.dispatch(MediaBrowserIntent.Back)
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
        override fun approveImageRequest(url: String, headers: Map<String, String>) = SourceRequest(url = url, headers = headers)
        override fun externalStreamUrl(stream: MediaStream): String = stream.url
        override suspend fun close() { closeCalls++; source.close() }
    }

    private companion object {
        fun item(id: String) = MediaItem(MediaId("test.plugin", MediaKind.MANGA, id), id, "https://media.example.com/$id")
    }
}
