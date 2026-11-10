package crow.evilo.model

import kotlinx.serialization.Serializable

@Serializable
data class Page(
    val index: Int,
    val imageUrl: String,
    val headers: Map<String, String> = emptyMap(),
) {
    init {
        require(index >= 0) { "Page index must not be negative." }
        require(imageUrl.isNotBlank()) { "Page image URL must not be blank." }
    }
}
