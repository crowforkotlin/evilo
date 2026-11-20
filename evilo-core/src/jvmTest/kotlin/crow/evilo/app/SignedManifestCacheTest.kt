@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package crow.evilo.app

import crow.evilo.plugin.api.PluginJson
import crow.evilo.plugin.catalog.catalogPlugin
import crow.evilo.plugin.catalog.descriptor
import crow.evilo.plugin.runtime.ManifestIdentityClient
import crow.evilo.storage.MemoryPluginManifestCache
import crow.wasmline.WasmlineExecutionModel
import crow.wasmline.WasmlineInvocationProtocol
import crow.wasmline.WasmlineLoadResult
import crow.wasmline.WasmlineLoadStage
import crow.wasmline.loader.WasmlineLoadOptions
import crow.wasmline.loader.WasmlineLoader
import crow.wasmline.loader.WasmlineNoOpCache
import crow.wasmline.loader.WasmlineSource
import crow.wasmline.loader.WasmlineTrustedKeys
import crow.wasmline.loader.model.SignedManifestEnvelope
import crow.wasmline.loader.model.WasmlineManifest
import crow.wasmline.loader.model.WasmlineManifestWireFormat
import crow.wasmline.loader.model.WasmlineRuntimeContract
import crow.wasmline.loader.network.WasmlineHttpResponse
import crow.wasmline.loader.network.WasmlineHttpStatus
import crow.wasmline.loader.network.WasmlineNetworkClient
import crow.wasmline.loader.network.WasmlineNetworkSink
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.protobuf.ProtoBuf
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SignedManifestCacheTest {
    @Test
    fun hashValidCachedManifestStillRequiresTheWasmlineSignatureVerifier() = runTest {
        rejectInvalidSignature(cached = true)
    }

    @Test
    fun downloadedManifestPassingIdentityChecksIsNotCachedWhenItsSignatureFails() = runTest {
        rejectInvalidSignature(cached = false)
    }

    private suspend fun rejectInvalidSignature(cached: Boolean) {
        val plugin = catalogPlugin()
        val release = plugin.latestRelease
        val descriptor = plugin.descriptor(release)
        val manifest = WasmlineManifest(plugin.id, release.version, release.versionCode,
            buildTimestamp = 0,
            metadata = mapOf("evilo.descriptor" to PluginJson.encodeToString(descriptor),
                "evilo.sourceRevision" to release.sourceRevision,
                "evilo.sourceArchiveSha256" to release.sourceArchiveSha256),
            runtimeContract = WasmlineRuntimeContract(WasmlineExecutionModel.CORE_WASM, WasmlineInvocationProtocol.WASMLINE_SERVICE),
            artifactTargets = emptyList())
        val envelope = SignedManifestEnvelope(signature = ByteArray(64), publicKeyId = "test-key",
            formatVersion = WasmlineManifestWireFormat.CURRENT_FORMAT_VERSION,
            payload = ProtoBuf.encodeToByteArray(WasmlineManifest.serializer(), manifest))
        val bytes = ProtoBuf.encodeToByteArray(SignedManifestEnvelope.serializer(), envelope)
        val digest = bytes.toByteString().sha256().hex()
        val cache = MemoryPluginManifestCache()
        // Seed hash-valid but forged local bytes to prove persistent storage cannot grant trust.
        if (cached) cache.writeVerified(digest, bytes)
        val requests = mutableListOf<String>()
        val network = object : WasmlineNetworkClient {
            override suspend fun fetch(url: String): WasmlineHttpResponse {
                requests += url
                assertEquals(release.manifestUrl, url)
                return WasmlineHttpResponse(200, bytes)
            }
            override suspend fun fetchTo(url: String, sink: WasmlineNetworkSink): WasmlineHttpStatus =
                error("Signature failure must happen before any artifact download.")
        }
        var identityChecks = 0
        var keyLookups = 0
        val client = CachedPackageClient(release.manifestUrl, network, cache, digest) {}
        val checked = ManifestIdentityClient(release.manifestUrl, client, descriptor,
            release.sourceRevision, release.sourceArchiveSha256, digest) { identityChecks++ }
        val keys = WasmlineTrustedKeys { algorithm, keyId ->
            assertEquals("Ed25519", algorithm)
            assertEquals("test-key", keyId)
            keyLookups++
            "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a".decodeHex().toByteArray()
        }
        val result = WasmlineLoader.load(WasmlineSource.RemoteManifestUrl(release.manifestUrl),
            WasmlineLoadOptions(networkClient = checked, trustedKeys = keys,
                manifestTtlMs = 0, cache = WasmlineNoOpCache))
        val failure = assertIs<WasmlineLoadResult.Failure>(result).failure
        assertEquals(WasmlineLoadStage.SIGNATURE_VERIFICATION, failure.stage)
        assertTrue(failure.message.contains("signature verification failed"))
        assertEquals(1, identityChecks)
        assertTrue(keyLookups > 0)
        assertEquals(if (cached) emptyList() else listOf(release.manifestUrl), requests)
        if (!cached) assertNull(cache.read(digest))
    }
}
