package crow.evilo.plugin.runtime

import crow.evilo.plugin.api.MangaSource
import crow.evilo.plugin.api.MangaSourceService
import crow.evilo.plugin.api.SOURCE_API_VERSION
import crow.evilo.plugin.api.SourceHttpClient
import crow.wasmline.WasmlineLoadFailure
import crow.wasmline.WasmlineLoadResult
import crow.wasmline.link
import crow.wasmline.loader.WasmlineLoadOptions
import crow.wasmline.loader.WasmlineLoader
import crow.wasmline.loader.WasmlineSource

class WasmlineSourceLoader(
    private val httpClient: SourceHttpClient,
    private val maxChapterRequests: Int = 512,
) {
    init {
        require(maxChapterRequests > 0) { "The chapter request limit must be positive." }
    }

    suspend fun load(source: WasmlineSource, options: WasmlineLoadOptions): MangaSource {
        require(source is WasmlineSource.RemoteManifestUrl || source is WasmlineSource.LocalManifestPath) {
            "Manga sources must be loaded from a signed package manifest."
        }

        val wasmline = when (val result = WasmlineLoader.load(source, options)) {
            is WasmlineLoadResult.Success -> result.wasmline
            is WasmlineLoadResult.Failure -> throw SourcePluginLoadException(result.failure)
        }

        try {
            val service = wasmline.link<MangaSourceService>()
            val metadata = service.metadata()
            require(metadata.apiVersion == SOURCE_API_VERSION) {
                "Source ${metadata.id} uses API ${metadata.apiVersion}; expected $SOURCE_API_VERSION."
            }
            return WasmlineMangaSource(metadata, service, httpClient, wasmline, maxChapterRequests)
        } catch (failure: Throwable) {
            wasmline.close()
            throw failure
        }
    }
}

class SourcePluginLoadException(
    val failure: WasmlineLoadFailure,
) : IllegalStateException(failure.message)
