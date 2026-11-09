package crow.evilo.core.source.api

import crow.evilo.core.model.Chapter
import crow.evilo.core.model.Manga
import crow.evilo.core.model.Page
import kotlinx.serialization.Serializable

@Serializable
data class MangaSearchResult(
    val manga: List<Manga>,
    val hasNextPage: Boolean,
)

interface MangaSource {
    val metadata: SourceMetadata

    suspend fun search(query: String, page: Int): MangaSearchResult

    suspend fun details(manga: Manga): Manga

    suspend fun chapters(manga: Manga): List<Chapter>

    suspend fun pages(chapter: Chapter): List<Page>

    suspend fun close()
}
