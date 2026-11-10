package crow.evilo.plugin.api

import crow.evilo.model.Chapter
import crow.evilo.model.Manga
import crow.evilo.model.MangaId
import crow.evilo.model.MediaChapter
import crow.evilo.model.MediaContent
import crow.evilo.model.MediaId
import crow.evilo.model.MediaItem
import crow.evilo.model.MediaKind

class MangaPluginAdapter(
    private val source: MangaSourceService,
    private val descriptor: PluginDescriptor,
) : EviloPluginService {
    override fun describe(): String = PluginJson.encodeToString(descriptor)

    override fun start(operationJson: String): String {
        val operation = operation(operationJson)
        val step = when (operation.operation) {
            PluginOperationKind.SEARCH -> PluginStep(request = source.searchRequest(operation.query, operation.page))
            PluginOperationKind.DETAILS -> PluginStep(request = source.detailsRequest(requireNotNull(operation.item).toManga()))
            PluginOperationKind.CHAPTERS -> {
                val request = source.chaptersRequest(requireNotNull(operation.item).toManga())
                PluginStep(request = request.request, context = PluginJson.encodeToString(request))
            }
            PluginOperationKind.CONTENT -> PluginStep(request = source.pagesRequest(requireNotNull(operation.chapter).toChapter()))
        }
        return PluginJson.encodeToString(step)
    }

    override fun resume(operationJson: String, context: String, responseJson: String): String {
        val operation = operation(operationJson)
        val response = PluginJson.decodeFromString<SourceResponse>(responseJson)
        val step = when (operation.operation) {
            PluginOperationKind.SEARCH -> {
                val result = source.parseSearch(operation.query, operation.page, response)
                PluginStep(result = PluginResult(items = result.manga.map { it.toMedia() }, hasNextPage = result.hasNextPage))
            }
            PluginOperationKind.DETAILS -> PluginStep(result = PluginResult(
                details = source.parseDetails(requireNotNull(operation.item).toManga(), response).toMedia()))
            PluginOperationKind.CHAPTERS -> {
                val request = PluginJson.decodeFromString<SourceChapterRequest>(context)
                val result = source.parseChapters(requireNotNull(operation.item).toManga(), request, response)
                val chapters = PluginResult(chapters = result.chapters.map { it.toMediaChapter() })
                result.nextRequest?.let { next ->
                    PluginStep(request = next.request, context = PluginJson.encodeToString(next), batch = chapters)
                } ?: PluginStep(result = chapters)
            }
            PluginOperationKind.CONTENT -> PluginStep(result = PluginResult(content = MediaContent(
                images = source.parsePages(requireNotNull(operation.chapter).toChapter(), response))))
        }
        return PluginJson.encodeToString(step)
    }

    private fun operation(json: String): PluginOperation = PluginJson.decodeFromString<PluginOperation>(json).also {
        require(it.kind == MediaKind.MANGA) { "This adapter only supports manga operations." }
    }

    private fun MediaItem.toManga(): Manga = Manga(MangaId(id.pluginId, id.value), title, url, coverUrl, description, authors)
    private fun Manga.toMedia(): MediaItem = MediaItem(MediaId(id.sourceId, MediaKind.MANGA, id.value), title, url, coverUrl, description, authors)
    private fun MediaChapter.toChapter(): Chapter = Chapter(MangaId(itemId.pluginId, itemId.value), id, title, url, number)
    private fun Chapter.toMediaChapter(): MediaChapter = MediaChapter(MediaId(mangaId.sourceId, MediaKind.MANGA, mangaId.value), id, title, url, number)
}
