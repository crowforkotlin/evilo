package crow.evilo.model

import kotlinx.serialization.Serializable

@Serializable
data class Chapter(
    val mangaId: MangaId,
    val id: String,
    val title: String,
    val url: String,
    val number: Double? = null,
)
