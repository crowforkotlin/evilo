package crow.evilo.feature.reader

import crow.evilo.architecture.LatestOperation
import crow.evilo.architecture.MviStore
import crow.evilo.model.MediaItem
import crow.evilo.model.MediaKind
import crow.evilo.plugin.api.PluginOperation
import crow.evilo.plugin.api.PluginOperationKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

class MediaBrowserController(private val session: MediaSourceSession, kind: MediaKind, parentScope: CoroutineScope) :
    MviStore<MediaBrowserState, MediaBrowserIntent> {
    private val mutableState = MutableStateFlow(MediaBrowserState(kind))
    override val state = mutableState.asStateFlow()
    private val operations = LatestOperation(parentScope)
    private var closed = false

    override fun dispatch(intent: MediaBrowserIntent) {
        if (closed) return
        when (intent) {
            is MediaBrowserIntent.Search -> search(intent.query)
            MediaBrowserIntent.LoadMore -> loadMore()
            is MediaBrowserIntent.OpenItem -> openItem(intent.item)
            is MediaBrowserIntent.OpenChapter -> openChapter(intent.index)
            MediaBrowserIntent.Retry -> retry()
            MediaBrowserIntent.Back -> back()
        }
    }

    private fun search(query: String) = replace(begin = {
        it.copy(page = MediaBrowserPage.CATALOG, query = query, searchPage = 0, items = emptyList(), hasNextPage = false,
            selectedItem = null, chapters = emptyList(), chapterIndex = null, content = null)
    }) { fetchSearch(query, 1) }

    private fun loadMore() {
        val current = mutableState.value
        if (current.busy || !current.hasNextPage || current.searchPage == Int.MAX_VALUE) return
        replace { fetchSearch(current.query, current.searchPage + 1) }
    }

    private fun openItem(item: MediaItem) = replace(begin = {
        it.copy(page = MediaBrowserPage.DETAILS, selectedItem = item, chapters = emptyList(), chapterIndex = null, content = null)
    }) {
        val kind = mutableState.value.kind
        val details = requireNotNull(session.source.execute(PluginOperation(PluginOperationKind.DETAILS, kind, item = item)).details)
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(selectedItem = details) }
        val chapters = session.source.execute(PluginOperation(PluginOperationKind.CHAPTERS, kind, item = details)).chapters
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(chapters = chapters) }
    }

    private fun openChapter(index: Int) {
        val current = mutableState.value
        val chapter = current.chapters.getOrNull(index) ?: return
        replace(begin = { it.copy(page = MediaBrowserPage.READER, chapterIndex = index, content = null) }) {
            val content = session.source.execute(PluginOperation(PluginOperationKind.CONTENT, current.kind, chapter = chapter)).content
            currentCoroutineContext().ensureActive()
            mutableState.update { it.copy(content = requireNotNull(content)) }
        }
    }

    private fun retry() {
        val current = mutableState.value
        when (current.page) {
            MediaBrowserPage.CATALOG -> if (current.searchPage == 0) search(current.query) else replace { fetchSearch(current.query, current.searchPage + 1) }
            MediaBrowserPage.DETAILS -> current.selectedItem?.let(::openItem)
            MediaBrowserPage.READER -> current.chapterIndex?.let(::openChapter)
        }
    }

    private fun back() = replace(busy = false, begin = {
        when (it.page) {
            MediaBrowserPage.CATALOG -> it
            MediaBrowserPage.DETAILS -> it.copy(page = MediaBrowserPage.CATALOG, selectedItem = null, chapters = emptyList())
            MediaBrowserPage.READER -> it.copy(page = MediaBrowserPage.DETAILS, chapterIndex = null, content = null)
        }
    }) {}

    override suspend fun close() = withContext(NonCancellable) {
        if (closed) return@withContext
        closed = true
        operations.close()
        session.close()
    }

    private suspend fun fetchSearch(query: String, page: Int) {
        val result = session.source.execute(PluginOperation(PluginOperationKind.SEARCH, mutableState.value.kind, query, page))
        currentCoroutineContext().ensureActive()
        mutableState.update {
            it.copy(query = query, searchPage = page, items = (if (page == 1) result.items else it.items + result.items).distinctBy { item -> item.id },
                hasNextPage = result.hasNextPage)
        }
    }

    private fun replace(busy: Boolean = true, begin: (MediaBrowserState) -> MediaBrowserState = { it }, block: suspend () -> Unit) {
        if (closed) return
        operations.launch(
            onStart = { mutableState.update { begin(it).copy(busy = busy, error = null) } },
            onFailure = { failure -> mutableState.update { it.copy(error = (failure.message ?: failure.toString()).take(4000)) } },
            onFinish = { mutableState.update { it.copy(busy = false) } },
            block = block,
        )
    }
}
