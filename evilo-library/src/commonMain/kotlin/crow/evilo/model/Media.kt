package crow.evilo.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MediaKind {
    @SerialName("manga") MANGA,
    @SerialName("novel") NOVEL,
    @SerialName("anime") ANIME,
    @SerialName("film") FILM,
}

@Serializable
data class MediaId(val pluginId: String, val kind: MediaKind, val value: String) {
    init {
        require(pluginId.isNotBlank() && value.isNotBlank()) { "Media IDs must be plugin-qualified and non-empty." }
    }
}

@Serializable
data class MediaItem(
    val id: MediaId,
    val title: String,
    val url: String,
    val coverUrl: String? = null,
    val description: String? = null,
    val authors: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val extensions: Map<String, String> = emptyMap(),
)

@Serializable
data class MediaChapter(
    val itemId: MediaId,
    val id: String,
    val title: String,
    val url: String,
    val number: Double? = null,
    val extensions: Map<String, String> = emptyMap(),
)

@Serializable
data class MediaStream(
    val title: String,
    val url: String,
    val mimeType: String? = null,
    val headers: Map<String, String> = emptyMap(),
)

@Serializable
data class MediaContent(
    val images: List<Page> = emptyList(),
    val text: String? = null,
    val streams: List<MediaStream> = emptyList(),
    val extensions: Map<String, String> = emptyMap(),
)
