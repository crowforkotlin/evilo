package crow.evilo.model

import kotlinx.serialization.Serializable

@Serializable
data class MangaId(
    val sourceId: String,
    val value: String,
) {
    init {
        require(sourceId.isNotBlank()) { "Source ID must not be blank." }
        require(value.isNotBlank()) { "Manga ID must not be blank." }
    }
}

@Serializable
data class Manga(
    val id: MangaId,
    val title: String,
    val url: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val authors: List<String> = emptyList(),
)
