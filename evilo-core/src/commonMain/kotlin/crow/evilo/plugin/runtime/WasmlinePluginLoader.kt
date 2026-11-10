@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package crow.evilo.plugin.runtime

import crow.evilo.plugin.api.EviloHostService
import crow.evilo.plugin.api.EviloPluginService
import crow.evilo.plugin.api.HOST_STORAGE_CAPABILITY
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.plugin.api.MediaSource
import crow.evilo.plugin.api.PluginDescriptor
import crow.evilo.plugin.api.PluginJson
import crow.evilo.plugin.api.SourceHttpClient
import crow.evilo.plugin.api.incompatibility
import crow.evilo.storage.KeyValueStore
import crow.wasmline.WasmlineLoadResult
import crow.wasmline.WasmlineExecutionModel
import crow.wasmline.WasmlineInvocationProtocol
import crow.wasmline.bind
import crow.wasmline.link
import crow.wasmline.loader.WasmlineLoadOptions
import crow.wasmline.loader.WasmlineLoader
import crow.wasmline.loader.WasmlineNoOpCache
import crow.wasmline.loader.WasmlineSource
import crow.wasmline.loader.model.SignedManifestEnvelope
import crow.wasmline.loader.model.WasmlineManifest
import crow.wasmline.loader.model.WasmlineManifestLimits
import crow.wasmline.loader.network.WasmlineHttpResponse
import crow.wasmline.loader.network.WasmlineHttpStatus
import crow.wasmline.loader.network.WasmlineNetworkClient
import crow.wasmline.loader.network.WasmlineNetworkSink
import kotlinx.serialization.protobuf.ProtoBuf

class WasmlinePluginLoader(
    private val host: HostDescriptor,
    private val store: KeyValueStore,
    private val http: SourceHttpClient,
) {
    suspend fun loadRemote(
        manifestUrl: String,
        options: WasmlineLoadOptions,
        expected: PluginDescriptor,
        sourceRevision: String,
        sourceArchiveSha256: String,
    ): MediaSource {
        require(options.trustedKeys != null && options.cache == WasmlineNoOpCache && options.manifestTtlMs == 0L) {
            "Plugin loads require explicit trusted keys and a freshly verified manifest."
        }
        val network = requireNotNull(options.networkClient) { "A policy-approved package network client is required." }
        val grantedHost = if (expected.permissions.storage) host else host.copy(capabilities = host.capabilities - HOST_STORAGE_CAPABILITY)
        require(expected.protocol.incompatibility(grantedHost) == null) { expected.protocol.incompatibility(grantedHost).orEmpty() }
        val checkedOptions = options.copy(networkClient = ManifestIdentityClient(manifestUrl, network, expected, sourceRevision, sourceArchiveSha256))
        val wasmline = when (val result = WasmlineLoader.load(WasmlineSource.RemoteManifestUrl(manifestUrl), checkedOptions)) {
            is WasmlineLoadResult.Success -> result.wasmline
            is WasmlineLoadResult.Failure -> throw SourcePluginLoadException(result.failure)
        }
        try {
            wasmline.bind(EviloHostService::class, PluginHostStorageService(expected.id, grantedHost, store, expected.permissions.storage))
            val service = wasmline.link<EviloPluginService>()
            val descriptorJson = service.describe()
            require(descriptorJson.encodeToByteArray().size <= WasmlineManifestLimits.DEFAULT_MAX_STRING_BYTES) { "Plugin descriptor is too large." }
            val descriptor = PluginJson.decodeFromString<PluginDescriptor>(descriptorJson)
            requireDescriptorMatch(descriptor, expected)
            require(descriptor.protocol.incompatibility(grantedHost) == null) { descriptor.protocol.incompatibility(grantedHost).orEmpty() }
            return WasmlineMediaSource(descriptor, service, http, wasmline)
        } catch (failure: Throwable) {
            try {
                wasmline.close()
            } catch (closeFailure: Throwable) {
                failure.addSuppressed(closeFailure)
            }
            throw failure
        }
    }
}

private class ManifestIdentityClient(
    private val manifestUrl: String,
    private val delegate: WasmlineNetworkClient,
    private val expected: PluginDescriptor,
    private val sourceRevision: String,
    private val sourceArchiveSha256: String,
) : WasmlineNetworkClient {
    override suspend fun fetch(url: String): WasmlineHttpResponse = delegate.fetch(url).also {
        if (isManifest(url) && it.statusCode in 200..299) checkManifest(it.bytes)
    }

    override suspend fun fetchTo(url: String, sink: WasmlineNetworkSink): WasmlineHttpStatus {
        if (!isManifest(url)) return delegate.fetchTo(url, sink)
        val response = fetch(url)
        sink.write(response.bytes, 0, response.bytes.size)
        return WasmlineHttpStatus(response.statusCode)
    }

    private fun checkManifest(bytes: ByteArray) {
        require(bytes.size <= 2 * 1024 * 1024) { "Plugin manifest exceeds its byte limit." }
        val envelope = ProtoBuf.decodeFromByteArray(SignedManifestEnvelope.serializer(), bytes)
        require(envelope.algorithm == "Ed25519") { "Plugin signatures must use Ed25519." }
        val manifest = ProtoBuf.decodeFromByteArray(WasmlineManifest.serializer(), envelope.payload)
        require(manifest.runtimeContract.executionModel == WasmlineExecutionModel.CORE_WASM &&
            manifest.runtimeContract.invocationProtocol == WasmlineInvocationProtocol.WASMLINE_SERVICE) {
            "The current evilo SDK requires a Core Wasm WasmlineService package."
        }
        require(manifest.pluginId == expected.id && manifest.version == expected.version && manifest.versionCode == expected.versionCode) {
            "Manifest identity/version does not match the selected catalog release."
        }
        val descriptorJson = requireNotNull(manifest.metadata["evilo.descriptor"]) { "The signed manifest must declare its evilo descriptor." }
        require(descriptorJson.encodeToByteArray().size <= WasmlineManifestLimits.DEFAULT_MAX_STRING_BYTES) { "Signed plugin descriptor exceeds its byte limit." }
        requireDescriptorMatch(PluginJson.decodeFromString<PluginDescriptor>(descriptorJson), expected)
        require(manifest.metadata["evilo.sourceRevision"] == sourceRevision &&
            manifest.metadata["evilo.sourceArchiveSha256"] == sourceArchiveSha256) { "Catalog source provenance differs from the signed package." }
        // This is an identity check, not signature verification; the unchanged bytes still pass through Wasmline's signed loader.
    }

    private fun isManifest(url: String): Boolean = url == manifestUrl || url.substringBefore('?').endsWith(".wlm")
}

private fun requireDescriptorMatch(actual: PluginDescriptor, expected: PluginDescriptor) {
    require(actual.id == expected.id && actual.version == expected.version && actual.versionCode == expected.versionCode &&
        actual.kinds == expected.kinds && actual.languages == expected.languages && actual.protocol == expected.protocol &&
        actual.permissions == expected.permissions && actual.name.isNotBlank() && actual.name.length <= 120) {
        "The signed plugin descriptor does not match its selected catalog release."
    }
}
