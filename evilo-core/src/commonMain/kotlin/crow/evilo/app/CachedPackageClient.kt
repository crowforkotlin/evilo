package crow.evilo.app

import crow.evilo.storage.PluginManifestCache
import crow.wasmline.loader.network.WasmlineHttpResponse
import crow.wasmline.loader.network.WasmlineHttpStatus
import crow.wasmline.loader.network.WasmlineNetworkClient
import crow.wasmline.loader.network.WasmlineNetworkSink
import okio.ByteString.Companion.toByteString

/** Supplies pinned bytes to the normal signed loader, without changing the package's URL base. */
internal class CachedPackageClient(
    private val manifestUrl: String,
    private val delegate: WasmlineNetworkClient,
    private val cache: PluginManifestCache,
    private val approvedManifestSha256: String?,
    private val report: suspend (PluginLoadPhase) -> Unit,
) : WasmlineNetworkClient {
    private var downloadedManifest: ByteArray? = null

    override suspend fun fetch(url: String): WasmlineHttpResponse {
        if (url == manifestUrl) {
            downloadedManifest = null
            val cached = approvedManifestSha256?.let { digest ->
                cache.read(digest)?.let { bytes ->
                    if (bytes.size in 1..PluginManifestCache.MAX_MANIFEST_BYTES && bytes.toByteString().sha256().hex() == digest)
                        bytes
                    else {
                        cache.remove(digest)
                        null
                    }
                }
            }
            if (cached != null) {
                report(PluginLoadPhase.VERIFYING_PACKAGE)
                return WasmlineHttpResponse(200, cached.copyOf())
            }
            report(PluginLoadPhase.FETCHING_MANIFEST)
            val response = delegate.fetch(url)
            if (response.statusCode in 200..299) {
                require(response.bytes.size <= PluginManifestCache.MAX_MANIFEST_BYTES) { "Plugin manifest exceeds its byte limit." }
                downloadedManifest = response.bytes.copyOf()
                report(PluginLoadPhase.VERIFYING_PACKAGE)
            }
            return response
        }
        report(PluginLoadPhase.DOWNLOADING_ARTIFACT)
        return delegate.fetch(url).also {
            if (it.statusCode in 200..299) report(PluginLoadPhase.INITIALIZING_RUNTIME)
        }
    }

    override suspend fun fetchTo(url: String, sink: WasmlineNetworkSink): WasmlineHttpStatus {
        if (url == manifestUrl) {
            val response = fetch(url)
            if (response.statusCode in 200..299) sink.write(response.bytes, 0, response.bytes.size)
            return WasmlineHttpStatus(response.statusCode)
        }
        report(PluginLoadPhase.DOWNLOADING_ARTIFACT)
        return delegate.fetchTo(url, sink).also {
            if (it.statusCode in 200..299) report(PluginLoadPhase.INITIALIZING_RUNTIME)
        }
    }

    /** Called only after signature/runtime checks succeed and the controller accepts the installation. */
    suspend fun cacheVerifiedManifest(verifiedManifestSha256: String) {
        val bytes = downloadedManifest ?: return
        require(bytes.toByteString().sha256().hex() == verifiedManifestSha256) {
            "The downloaded manifest differs from the verified package."
        }
        cache.writeVerified(verifiedManifestSha256, bytes)
        downloadedManifest = null
    }
}
