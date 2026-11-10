package crow.evilo.plugin.api

import crow.evilo.model.Chapter
import crow.evilo.model.Manga
import crow.evilo.model.Page
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
