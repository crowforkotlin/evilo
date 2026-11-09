package crow.evilo.core.source.api

import crow.evilo.core.model.Chapter
import crow.evilo.core.model.Manga
import crow.evilo.core.model.Page
import crow.wasmline.WasmlineService

interface MangaSourceService : WasmlineService {
    fun metadata(): SourceMetadata

    fun searchRequest(query: String, page: Int): SourceRequest

    fun parseSearch(query: String, page: Int, response: SourceResponse): MangaSearchResult

    fun detailsRequest(manga: Manga): SourceRequest

    fun parseDetails(manga: Manga, response: SourceResponse): Manga

    fun chaptersRequest(manga: Manga): SourceRequest

    fun parseChapters(manga: Manga, response: SourceResponse): List<Chapter>

    fun pagesRequest(chapter: Chapter): SourceRequest

    fun parsePages(chapter: Chapter, response: SourceResponse): List<Page>
}
