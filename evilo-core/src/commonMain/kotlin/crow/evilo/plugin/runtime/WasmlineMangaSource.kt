package crow.evilo.plugin.runtime

import crow.evilo.model.Chapter
import crow.evilo.model.Manga
import crow.evilo.model.MangaId
import crow.evilo.model.Page
import crow.evilo.plugin.api.MangaSearchResult
import crow.evilo.plugin.api.MangaSource
import crow.evilo.plugin.api.MangaSourceService
import crow.evilo.plugin.api.SourceHttpClient
import crow.evilo.plugin.api.SourceMetadata
import crow.wasmline.Wasmline
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class WasmlineMangaSource(
    override val metadata: SourceMetadata,
    private val service: MangaSourceService,
    private val httpClient: SourceHttpClient,
    private val wasmline: Wasmline,
    private val maxChapterRequests: Int,
) : MangaSource {
    private val mutex = Mutex()
    private var closed = false

    override suspend fun search(query: String, page: Int): MangaSearchResult {
        require(page > 0) { "Search pages start at one." }
        val request = call { service.searchRequest(query, page) }
        val response = httpClient.execute(metadata, request)
        return call { service.parseSearch(query, page, response) }.also { result ->
            result.manga.forEach { manga -> requireSource(manga.id) }
        }
    }

    override suspend fun details(manga: Manga): Manga {
        requireSource(manga.id)
        val request = call { service.detailsRequest(manga) }
        val response = httpClient.execute(metadata, request)
        return call { service.parseDetails(manga, response) }.also { result ->
            require(result.id == manga.id) { "Manga details must preserve the requested manga ID." }
        }
    }

    override suspend fun chapters(manga: Manga): List<Chapter> {
        requireSource(manga.id)
        return loadSourceChapters(
            source = metadata,
            manga = manga,
            httpClient = httpClient,
            maxRequests = maxChapterRequests,
            firstRequest = { call { service.chaptersRequest(manga) } },
            parseResponse = { request, response ->
                call { service.parseChapters(manga, request, response) }
            },
        )
    }

    override suspend fun pages(chapter: Chapter): List<Page> {
        requireSource(chapter.mangaId)
        val request = call { service.pagesRequest(chapter) }
        val response = httpClient.execute(metadata, request)
        return call { service.parsePages(chapter, response) }
    }

    override suspend fun close() {
        mutex.withLock {
            if (!closed) {
                closed = true
                wasmline.close()
            }
        }
    }

    private suspend fun <Result> call(block: () -> Result): Result = mutex.withLock {
        check(!closed) { "Source ${metadata.id} is closed." }
        block()
    }

    private fun requireSource(mangaId: MangaId) {
        require(mangaId.sourceId == metadata.id) {
            "Manga ${mangaId.value} belongs to ${mangaId.sourceId}, not ${metadata.id}."
        }
    }
}
