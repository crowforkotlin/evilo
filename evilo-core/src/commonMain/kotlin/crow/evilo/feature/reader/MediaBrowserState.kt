package crow.evilo.feature.reader

import crow.evilo.model.MediaChapter
import crow.evilo.model.MediaContent
import crow.evilo.model.MediaItem
import crow.evilo.model.MediaKind
import crow.evilo.model.MediaStream
import crow.evilo.plugin.api.MediaSource
import crow.evilo.plugin.api.SourceRequest

interface MediaSourceSession {
    val source: MediaSource
    fun approveImageRequest(url: String, headers: Map<String, String>): SourceRequest
    suspend fun imageBytes(url: String, headers: Map<String, String>): ByteArray
    fun externalStreamUrl(stream: MediaStream): String
    suspend fun close()
}

enum class MediaBrowserPage { CATALOG, DETAILS, READER }

sealed interface MediaBrowserIntent {
    data class Search(val query: String) : MediaBrowserIntent
    data object LoadMore : MediaBrowserIntent
    data class OpenItem(val item: MediaItem) : MediaBrowserIntent
    data class OpenChapter(val index: Int) : MediaBrowserIntent
    data object Retry : MediaBrowserIntent
    data object Back : MediaBrowserIntent
}

data class MediaBrowserState(
    val kind: MediaKind,
    val busy: Boolean = false,
    val page: MediaBrowserPage = MediaBrowserPage.CATALOG,
    val query: String = "",
    val searchPage: Int = 0,
    val items: List<MediaItem> = emptyList(),
    val hasNextPage: Boolean = false,
    val selectedItem: MediaItem? = null,
    val chapters: List<MediaChapter> = emptyList(),
    val chapterIndex: Int? = null,
    val content: MediaContent? = null,
    val error: String? = null,
)
