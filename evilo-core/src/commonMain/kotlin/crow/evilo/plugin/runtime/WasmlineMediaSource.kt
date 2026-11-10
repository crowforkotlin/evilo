package crow.evilo.plugin.runtime

import crow.evilo.model.MediaChapter
import crow.evilo.model.MediaId
import crow.evilo.plugin.api.EviloPluginService
import crow.evilo.plugin.api.MediaSource
import crow.evilo.plugin.api.PluginDescriptor
import crow.evilo.plugin.api.PluginJson
import crow.evilo.plugin.api.PluginOperation
import crow.evilo.plugin.api.PluginOperationKind
import crow.evilo.plugin.api.PluginResult
import crow.evilo.plugin.api.PluginStep
import crow.evilo.plugin.api.SOURCE_API_VERSION
import crow.evilo.plugin.api.SourceHttpClient
import crow.evilo.plugin.api.SourceMetadata
import crow.wasmline.Wasmline
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class WasmlineMediaSource(
    override val descriptor: PluginDescriptor,
    private val service: EviloPluginService,
    private val http: SourceHttpClient,
    private val wasmline: Wasmline,
) : MediaSource {
    private val calls = Mutex()
    private var closed = false
    private val metadata = SourceMetadata(descriptor.id, descriptor.name, descriptor.languages.first(), descriptor.version, SOURCE_API_VERSION)

    override suspend fun execute(operation: PluginOperation): PluginResult {
        require(operation.kind in descriptor.kinds && operation.page > 0 && operation.query.length <= 4096) { "Invalid media operation." }
        operation.item?.let { requireId(it.id, operation) }
        operation.chapter?.let { requireId(it.itemId, operation) }
        require(operation.operation !in setOf(PluginOperationKind.DETAILS, PluginOperationKind.CHAPTERS) || operation.item != null) { "An item is required." }
        require(operation.operation != PluginOperationKind.CONTENT || operation.chapter != null) { "A chapter is required." }
        val operationJson = PluginJson.encodeToString(operation)
        require(operationJson.encodeToByteArray().size <= 256 * 1024) { "Media operation is too large." }
        val chapters = mutableListOf<MediaChapter>()
        var step = call { service.start(operationJson) }
        var requests = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            step.batch?.let { batch ->
                require(operation.operation == PluginOperationKind.CHAPTERS && batch.items.isEmpty() && batch.details == null && batch.content == null) {
                    "Only chapter operations may return intermediate chapter batches."
                }
                chapters += checkedChapters(batch.chapters, operation)
                require(chapters.size <= 20_000) { "Too many chapters." }
            }
            step.result?.let { result ->
                val completed = if (operation.operation == PluginOperationKind.CHAPTERS) result.copy(chapters = chapters + result.chapters) else result
                validateResult(completed, operation)
                return completed
            }
            require(++requests <= 512 && step.context.encodeToByteArray().size <= 128 * 1024) { "Plugin continuation limit exceeded." }
            val response = http.execute(metadata, requireNotNull(step.request))
            currentCoroutineContext().ensureActive()
            step = call { service.resume(operationJson, step.context, PluginJson.encodeToString(response)) }
        }
    }

    override suspend fun close() = calls.withLock {
        if (!closed) {
            closed = true
            wasmline.close()
        }
    }

    private suspend fun call(block: () -> String): PluginStep = calls.withLock {
        check(!closed) { "Plugin ${descriptor.id} is closed." }
        val encoded = block()
        require(encoded.encodeToByteArray().size <= 4 * 1024 * 1024) { "Plugin result exceeds 4 MiB." }
        PluginJson.decodeFromString<PluginStep>(encoded)
    }

    private fun validateResult(result: PluginResult, operation: PluginOperation) {
        require(result.items.size <= 512 && result.items.distinctBy { it.id }.size == result.items.size) { "Invalid catalog result count or duplicate IDs." }
        result.items.forEach { requireId(it.id, operation); require(it.title.isNotBlank() && it.title.length <= 512) { "Invalid media title." } }
        result.details?.let {
            require(it.id == operation.item?.id && it.title.isNotBlank() && it.title.length <= 512) { "Details must preserve the requested media ID." }
        }
        checkedChapters(result.chapters, operation)
        require(result.chapters.size <= 20_000 && result.chapters.distinctBy { it.id }.size == result.chapters.size) { "Invalid chapter count or duplicate IDs." }
        result.content?.let { content ->
            require(content.images.size <= 2000 && content.images.map { it.index } == content.images.indices.toList()) { "Image pages must be contiguous and ordered from zero." }
            require(content.streams.size <= 32 && (content.text?.length ?: 0) <= 2 * 1024 * 1024) { "Media content exceeds its limits." }
        }
        when (operation.operation) {
            PluginOperationKind.SEARCH -> require(result.details == null && result.chapters.isEmpty() && result.content == null)
            PluginOperationKind.DETAILS -> require(result.details != null && result.items.isEmpty() && result.chapters.isEmpty() && result.content == null)
            PluginOperationKind.CHAPTERS -> require(result.items.isEmpty() && result.details == null && result.content == null)
            PluginOperationKind.CONTENT -> require(result.content != null && result.items.isEmpty() && result.details == null && result.chapters.isEmpty())
        }
    }

    private fun checkedChapters(chapters: List<MediaChapter>, operation: PluginOperation): List<MediaChapter> = chapters.also {
        it.forEach { chapter ->
            require(chapter.itemId == operation.item?.id && chapter.id.isNotBlank() && chapter.id.length <= 512 &&
                chapter.title.isNotBlank() && chapter.title.length <= 512) { "Chapters must preserve their requested media ID." }
        }
    }

    private fun requireId(id: MediaId, operation: PluginOperation) {
        require(id.pluginId == descriptor.id && id.kind == operation.kind && id.value.length <= 512) { "Media ID belongs to another plugin or media kind." }
    }
}
