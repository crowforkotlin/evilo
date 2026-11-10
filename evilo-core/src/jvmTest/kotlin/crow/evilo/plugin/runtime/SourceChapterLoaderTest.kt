package crow.evilo.plugin.runtime

import crow.evilo.model.Chapter
import crow.evilo.model.Manga
import crow.evilo.model.MangaId
import crow.evilo.plugin.api.SOURCE_API_VERSION
import crow.evilo.plugin.api.SourceChapterRequest
import crow.evilo.plugin.api.SourceChapterResult
import crow.evilo.plugin.api.SourceHttpClient
import crow.evilo.plugin.api.SourceMetadata
import crow.evilo.plugin.api.SourceRequest
import crow.evilo.plugin.api.SourceResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SourceChapterLoaderTest {
    private val source = SourceMetadata("test-source", "Test", "en", "1.0.0", SOURCE_API_VERSION)
    private val manga = Manga(MangaId(source.id, "example"), "Example", "https://source.invalid/example")

    @Test
    fun followsContinuationRequestsAndPreservesTheirContext() = runBlocking {
        val requests = mutableListOf<SourceRequest>()
        val parsedContexts = mutableListOf<String?>()
        val result = loadSourceChapters(
            source = source,
            manga = manga,
            httpClient = SourceHttpClient { metadata, request ->
                assertEquals(source, metadata)
                requests.add(request)
                response(request)
            },
            maxRequests = 3,
            firstRequest = { request("discovery") },
            parseResponse = { current, response ->
                assertEquals(current.request.url, response.url)
                parsedContexts.add(current.context)
                when (current.context) {
                    "context-discovery" -> SourceChapterResult(nextRequest = request("first"))
                    "context-first" -> SourceChapterResult(listOf(chapter("first")), request("second"))
                    "context-second" -> SourceChapterResult(listOf(chapter("second")))
                    else -> error("Unexpected request context.")
                }
            },
        )
        assertEquals(listOf("first", "second"), result.map { it.id })
        assertEquals(listOf("context-discovery", "context-first", "context-second"), parsedContexts)
        assertEquals(listOf("discovery", "first", "second"), requests.map { it.url.substringAfterLast('/') })
    }

    @Test
    fun requestLimitStopsAPluginThatNeverFinishes() {
        var requests = 0
        val failure = assertFailsWith<IllegalStateException> {
            runBlocking {
                loadSourceChapters(
                    source,
                    manga,
                    SourceHttpClient { _, request ->
                        requests++
                        response(request)
                    },
                    maxRequests = 2,
                    firstRequest = { request("endless") },
                    parseResponse = { current, _ -> SourceChapterResult(nextRequest = current) },
                )
            }
        }
        assertEquals(2, requests)
        assertTrue(failure.message.orEmpty().contains("limit of 2"))
    }

    @Test
    fun everyBatchMustBelongToTheRequestedManga() {
        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                loadSourceChapters(
                    source,
                    manga,
                    SourceHttpClient { _, request -> response(request) },
                    maxRequests = 1,
                    firstRequest = { request("foreign") },
                    parseResponse = { _, _ ->
                        SourceChapterResult(listOf(chapter("foreign").copy(mangaId = MangaId("other-source", "example"))))
                    },
                )
            }
        }
    }

    @Test
    fun failureInALaterBatchDoesNotReturnAPartialList() {
        var requests = 0
        val failure = assertFailsWith<IllegalStateException> {
            runBlocking {
                loadSourceChapters(
                    source,
                    manga,
                    SourceHttpClient { _, request ->
                        requests++
                        response(request)
                    },
                    maxRequests = 3,
                    firstRequest = { request("first") },
                    parseResponse = { current, _ ->
                        if (current.context == "context-first") {
                            SourceChapterResult(listOf(chapter("first")), request("failure"))
                        } else {
                            error("The next chapter page failed.")
                        }
                    },
                )
            }
        }
        assertEquals(2, requests)
        assertEquals("The next chapter page failed.", failure.message)
    }

    @Test
    fun cancellationFromTheHttpClientIsPropagated() {
        var parsed = false
        assertFailsWith<CancellationException> {
            runBlocking {
                loadSourceChapters(
                    source,
                    manga,
                    SourceHttpClient { _, _ -> throw CancellationException("Cancelled HTTP.") },
                    maxRequests = 1,
                    firstRequest = { request("first") },
                    parseResponse = { _, _ ->
                        parsed = true
                        SourceChapterResult()
                    },
                )
            }
        }
        assertFalse(parsed)
    }

    @Test
    fun cancellationAfterHttpPreventsParsingTheResponse() {
        var parsed = false
        assertFailsWith<CancellationException> {
            runBlocking {
                loadSourceChapters(
                    source,
                    manga,
                    SourceHttpClient { _, request ->
                        currentCoroutineContext().cancel()
                        response(request)
                    },
                    maxRequests = 1,
                    firstRequest = { request("first") },
                    parseResponse = { _, _ ->
                        parsed = true
                        SourceChapterResult()
                    },
                )
            }
        }
        assertFalse(parsed)
    }

    @Test
    fun chapterRequestLimitsMustBePositive() {
        val httpClient = SourceHttpClient { _, request -> response(request) }
        assertFailsWith<IllegalArgumentException> { WasmlineSourceLoader(httpClient, maxChapterRequests = 0) }
        assertFailsWith<IllegalArgumentException> {
            runBlocking {
                loadSourceChapters(
                    source,
                    manga,
                    httpClient,
                    maxRequests = 0,
                    firstRequest = { error("No request should be constructed.") },
                    parseResponse = { _, _ -> error("No response should be parsed.") },
                )
            }
        }
    }

    private fun request(name: String): SourceChapterRequest = SourceChapterRequest(
        request = SourceRequest("https://source.invalid/$name"),
        context = "context-$name",
    )

    private fun response(request: SourceRequest): SourceResponse = SourceResponse(
        url = request.url,
        statusCode = 200,
        headers = emptyMap(),
        body = "{}",
    )

    private fun chapter(id: String): Chapter = Chapter(manga.id, id, id, "https://source.invalid/chapter/$id")
}
