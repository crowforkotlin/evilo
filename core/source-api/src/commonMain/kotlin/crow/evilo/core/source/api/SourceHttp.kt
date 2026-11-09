package crow.evilo.core.source.api

import kotlinx.serialization.Serializable

@Serializable
data class SourceRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

@Serializable
data class SourceResponse(
    val url: String,
    val statusCode: Int,
    val headers: Map<String, String>,
    val body: String,
)

fun interface SourceHttpClient {
    suspend fun execute(source: SourceMetadata, request: SourceRequest): SourceResponse
}
