package crow.evilo.core.source.runtime

import crow.evilo.core.source.api.MangaSource
import crow.evilo.core.source.api.MangaSourceService
import crow.evilo.core.source.api.SOURCE_API_VERSION
import crow.evilo.core.source.api.SourceHttpClient
import crow.wasmline.WasmlineLoadFailure
import crow.wasmline.WasmlineLoadResult
import crow.wasmline.link
import crow.wasmline.loader.WasmlineLoadOptions
import crow.wasmline.loader.WasmlineLoader
import crow.wasmline.loader.WasmlineSource

class WasmlineSourceLoader(
    private val httpClient: SourceHttpClient,
) {
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
            return WasmlineMangaSource(metadata, service, httpClient, wasmline)
        } catch (failure: Throwable) {
            wasmline.close()
            throw failure
        }
    }
}

class SourcePluginLoadException(
    val failure: WasmlineLoadFailure,
) : IllegalStateException(failure.message)
