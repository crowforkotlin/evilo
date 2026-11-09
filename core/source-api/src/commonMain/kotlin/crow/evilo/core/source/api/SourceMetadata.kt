package crow.evilo.core.source.api

import kotlinx.serialization.Serializable

const val SOURCE_API_VERSION = 1

@Serializable
data class SourceMetadata(
    val id: String,
    val name: String,
    val language: String,
    val version: String,
    val apiVersion: Int,
) {
    init {
        require(id.isNotBlank()) { "Source ID must not be blank." }
        require(name.isNotBlank()) { "Source name must not be blank." }
    }
}
