package crow.evilo.plugin.api

import crow.evilo.model.Chapter
import kotlinx.serialization.Serializable

@Serializable
data class SourceChapterRequest(
    val request: SourceRequest,
    // Opaque plugin state, returned to the parser but never sent over HTTP.
    val context: String? = null,
)

@Serializable
data class SourceChapterResult(
    val chapters: List<Chapter> = emptyList(),
    val nextRequest: SourceChapterRequest? = null,
)
