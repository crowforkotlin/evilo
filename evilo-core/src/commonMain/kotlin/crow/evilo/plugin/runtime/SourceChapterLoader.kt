package crow.evilo.plugin.runtime

import crow.evilo.model.Chapter
import crow.evilo.model.Manga
import crow.evilo.plugin.api.SourceChapterRequest
import crow.evilo.plugin.api.SourceChapterResult
import crow.evilo.plugin.api.SourceHttpClient
import crow.evilo.plugin.api.SourceMetadata
import crow.evilo.plugin.api.SourceResponse
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal suspend fun loadSourceChapters(
    source: SourceMetadata,
    manga: Manga,
    httpClient: SourceHttpClient,
    maxRequests: Int,
    firstRequest: suspend () -> SourceChapterRequest,
    parseResponse: suspend (SourceChapterRequest, SourceResponse) -> SourceChapterResult,
): List<Chapter> {
    require(maxRequests > 0) { "The chapter request limit must be positive." }
    currentCoroutineContext().ensureActive()
    var nextRequest: SourceChapterRequest? = firstRequest()
    var requestCount = 0
    val chapters = mutableListOf<Chapter>()

    while (true) {
        val request = nextRequest ?: break
        currentCoroutineContext().ensureActive()
        check(requestCount < maxRequests) {
            "Source ${source.id} exceeds the limit of $maxRequests chapter requests."
        }
        requestCount++
        val response = httpClient.execute(source, request.request)
        currentCoroutineContext().ensureActive()
        val result = parseResponse(request, response)
        currentCoroutineContext().ensureActive()
        require(result.chapters.all { chapter -> chapter.mangaId == manga.id }) {
            "Chapters must belong to the requested manga."
        }
        chapters.addAll(result.chapters)
        nextRequest = result.nextRequest
    }

    return chapters
}
