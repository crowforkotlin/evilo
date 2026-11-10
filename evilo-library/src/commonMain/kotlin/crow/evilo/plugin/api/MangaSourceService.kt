package crow.evilo.plugin.api

import crow.evilo.model.Chapter
import crow.evilo.model.Manga
import crow.evilo.model.Page
import crow.wasmline.WasmlineService

interface MangaSourceService : WasmlineService {
    fun metadata(): SourceMetadata

    fun searchRequest(query: String, page: Int): SourceRequest

    fun parseSearch(query: String, page: Int, response: SourceResponse): MangaSearchResult

    fun detailsRequest(manga: Manga): SourceRequest

    fun parseDetails(manga: Manga, response: SourceResponse): Manga

    fun chaptersRequest(manga: Manga): SourceChapterRequest

    fun parseChapters(
        manga: Manga,
        request: SourceChapterRequest,
        response: SourceResponse,
    ): SourceChapterResult

    fun pagesRequest(chapter: Chapter): SourceRequest

    fun parsePages(chapter: Chapter, response: SourceResponse): List<Page>
}
