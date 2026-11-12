package crow.evilo.network.ktor

import crow.evilo.plugin.api.SourceHttpClient
import crow.evilo.plugin.api.SourceMetadata
import crow.evilo.plugin.api.SourceRequest
import crow.evilo.plugin.api.SourceResponse
import crow.wasmline.loader.network.WasmlineHttpResponse
import crow.wasmline.loader.network.WasmlineHttpStatus
import crow.wasmline.loader.network.WasmlineNetworkClient
import crow.wasmline.loader.network.WasmlineNetworkSink

class PolicySourceHttpClient(
    private val transport: BoundedHttpTransport,
    private val policy: SourceNetworkPolicy,
    private val maxApiBytes: Int = 2 * 1024 * 1024,
    private val maxImageBytes: Int = 16 * 1024 * 1024,
) : SourceHttpClient {
    override suspend fun execute(source: SourceMetadata, request: SourceRequest): SourceResponse {
        require(source.id == policy.sourceId) { "HTTP policy belongs to another source." }
        require(request.method == "GET" && request.body == null) { "The legacy manga host only permits bodyless GET requests." }
        val url = policy.approveApiUrl(request.url)
        val response = transport.fetch(url, policy.apiHeaders(request.headers), maxApiBytes)
        return SourceResponse(url, response.statusCode, response.headers, response.bytes.decodeToString(throwOnInvalidSequence = true))
    }

    suspend fun imageBytes(url: String, headers: Map<String, String>): ByteArray {
        val response = transport.fetch(policy.approveImageUrl(url), policy.imageHeaders(headers), maxImageBytes)
        if (response.statusCode !in 200..299) throw HttpStatusException(response.statusCode)
        val type = response.headers.entries.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value
        val mediaType = type?.substringBefore(';')?.trim()
        if (!(mediaType == null || mediaType.startsWith("image/", ignoreCase = true) ||
            mediaType.equals("application/octet-stream", ignoreCase = true))) throw HttpImageContentException()
        return response.bytes
    }
}

class PolicyPackageNetworkClient(
    private val transport: BoundedHttpTransport,
    private val policy: PackageNetworkPolicy,
    private val maxManifestBytes: Int = 2 * 1024 * 1024,
    private val maxArtifactBytes: Long = 64L * 1024 * 1024,
) : WasmlineNetworkClient {
    override suspend fun fetch(url: String): WasmlineHttpResponse {
        val response = transport.fetch(policy.approveUrl(url), emptyMap(), maxManifestBytes)
        return WasmlineHttpResponse(response.statusCode, response.bytes)
    }

    override suspend fun fetchTo(url: String, sink: WasmlineNetworkSink): WasmlineHttpStatus {
        val approved = policy.approveUrl(url)
        val limit = if (approved == policy.manifestUrl) maxManifestBytes.toLong() else maxArtifactBytes
        val response = transport.stream(approved, emptyMap(), limit, sink::write)
        return WasmlineHttpStatus(response.statusCode)
    }
}
