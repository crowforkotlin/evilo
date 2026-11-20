package crow.evilo.app

import crow.evilo.plugin.catalog.catalogPlugin
import crow.evilo.plugin.catalog.descriptor
import crow.evilo.plugin.runtime.ManifestIdentityClient
import crow.evilo.storage.MemoryPluginManifestCache
import crow.evilo.storage.PluginManifestCache
import crow.wasmline.loader.network.WasmlineHttpResponse
import crow.wasmline.loader.network.WasmlineHttpStatus
import crow.wasmline.loader.network.WasmlineNetworkClient
import crow.wasmline.loader.network.WasmlineNetworkSink
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.toByteString
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachedPackageClientTest {
    private val manifestUrl = "https://plugins.example.test/reader/manifest.wlm"
    private val bytes = byteArrayOf(1, 2, 3)
    private val digest = bytes.toByteString().sha256().hex()

    @Test
    fun pinnedCacheHitDoesNotFetchOrReportAManifestDownload() = runTest {
        val cache = MemoryPluginManifestCache()
        cache.writeVerified(digest, bytes)
        val phases = mutableListOf<PluginLoadPhase>()
        val network = RecordingNetwork(bytes)
        val client = CachedPackageClient(manifestUrl, network, cache, digest) { phases += it }
        assertContentEquals(bytes, client.fetch(manifestUrl).bytes)
        var streamed: ByteArray? = null
        client.fetchTo(manifestUrl, WasmlineNetworkSink { value, offset, count ->
            streamed = value.copyOfRange(offset, offset + count)
        })
        assertContentEquals(bytes, streamed)
        assertTrue(network.requests.isEmpty())
        assertEquals(listOf(PluginLoadPhase.VERIFYING_PACKAGE, PluginLoadPhase.VERIFYING_PACKAGE), phases)
    }

    @Test
    fun missingCacheDownloadsButDoesNotPersistBeforeAcceptance() = runTest {
        val cache = MemoryPluginManifestCache()
        val phases = mutableListOf<PluginLoadPhase>()
        val network = RecordingNetwork(bytes)
        val client = CachedPackageClient(manifestUrl, network, cache, digest) { phases += it }
        assertContentEquals(bytes, client.fetch(manifestUrl).bytes)
        assertNull(cache.read(digest))
        assertEquals(listOf(manifestUrl), network.requests)
        assertEquals(listOf(PluginLoadPhase.FETCHING_MANIFEST, PluginLoadPhase.VERIFYING_PACKAGE), phases)
        client.cacheVerifiedManifest(digest)
        assertContentEquals(bytes, cache.read(digest))
        val reopened = CachedPackageClient(manifestUrl, RecordingNetwork(bytes, status = 503), cache, digest) {}
        assertContentEquals(bytes, reopened.fetch(manifestUrl).bytes)
    }

    @Test
    fun aNewInstallationFetchesEvenWhenIdenticalBytesAreCached() = runTest {
        val cache = MemoryPluginManifestCache()
        cache.writeVerified(digest, bytes)
        val network = RecordingNetwork(bytes)
        val client = CachedPackageClient(manifestUrl, network, cache, null) {}
        client.fetch(manifestUrl)
        assertEquals(listOf(manifestUrl), network.requests)
    }

    @Test
    fun differentReleaseDigestCannotUseThePreviousRelease() = runTest {
        val cache = MemoryPluginManifestCache()
        cache.writeVerified(digest, bytes)
        val next = byteArrayOf(4, 5, 6)
        val nextDigest = next.toByteString().sha256().hex()
        val network = RecordingNetwork(next)
        val client = CachedPackageClient(manifestUrl, network, cache, nextDigest) {}
        assertContentEquals(next, client.fetch(manifestUrl).bytes)
        assertEquals(listOf(manifestUrl), network.requests)
        assertNull(cache.read(nextDigest))
    }

    @Test
    fun corruptCacheIsRemovedAndTheReplacementStillHasToMatchTheApproval() = runTest {
        var removed: String? = null
        var writes = 0
        val cache = object : PluginManifestCache {
            override suspend fun read(manifestSha256: String) = byteArrayOf(9)
            override suspend fun writeVerified(manifestSha256: String, bytes: ByteArray) { writes++ }
            override suspend fun remove(manifestSha256: String) { removed = manifestSha256 }
        }
        val network = RecordingNetwork(bytes)
        val client = CachedPackageClient(manifestUrl, network, cache, "d".repeat(64)) {}
        val plugin = catalogPlugin()
        val release = plugin.latestRelease
        val checked = ManifestIdentityClient(manifestUrl, client, plugin.descriptor(release),
            release.sourceRevision, release.sourceArchiveSha256, "d".repeat(64)) {}
        var streamed = 0
        assertFailsWith<IllegalArgumentException> {
            checked.fetchTo(manifestUrl, WasmlineNetworkSink { _, _, _ -> streamed++ })
        }
        assertEquals("d".repeat(64), removed)
        assertEquals(listOf(manifestUrl), network.requests)
        assertEquals(0, streamed)
        assertEquals(0, writes)
    }

    @Test
    fun cachedBytesStillPassThroughManifestIdentityChecks() = runTest {
        val cache = MemoryPluginManifestCache()
        cache.writeVerified(digest, bytes) // Hash-valid but not a valid signed envelope.
        val network = RecordingNetwork(bytes)
        val client = CachedPackageClient(manifestUrl, network, cache, digest) {}
        val plugin = catalogPlugin()
        val release = plugin.latestRelease
        val checked = ManifestIdentityClient(manifestUrl, client, plugin.descriptor(release),
            release.sourceRevision, release.sourceArchiveSha256, digest) {}
        assertFailsWith<Exception> { checked.fetch(manifestUrl) }
        assertTrue(network.requests.isEmpty())
    }

    @Test
    fun failedDownloadsAndUnverifiedDigestsAreNotStored() = runTest {
        val cache = MemoryPluginManifestCache()
        val client = CachedPackageClient(manifestUrl, RecordingNetwork(bytes, status = 503), cache, digest) {}
        var streamed = 0
        assertEquals(503, client.fetchTo(manifestUrl, WasmlineNetworkSink { _, _, _ -> streamed++ }).statusCode)
        client.cacheVerifiedManifest(digest)
        assertEquals(0, streamed)
        assertNull(cache.read(digest))
        val success = CachedPackageClient(manifestUrl, RecordingNetwork(bytes), cache, null) {}
        success.fetch(manifestUrl)
        assertFailsWith<IllegalArgumentException> { success.cacheVerifiedManifest("d".repeat(64)) }
        assertNull(cache.read(digest))
    }

    @Test
    fun artifactsRetainTheirOriginalUrlsAndStreaming() = runTest {
        val artifactUrl = "https://plugins.example.test/reader/artifacts/module.wasm"
        val network = RecordingNetwork(bytes)
        val phases = mutableListOf<PluginLoadPhase>()
        val client = CachedPackageClient(manifestUrl, network, MemoryPluginManifestCache(), digest) { phases += it }
        var streamed: ByteArray? = null
        client.fetchTo(artifactUrl, WasmlineNetworkSink { value, offset, count ->
            streamed = value.copyOfRange(offset, offset + count)
        })
        assertContentEquals(bytes, streamed)
        assertEquals(listOf(artifactUrl), network.requests)
        assertEquals(listOf(PluginLoadPhase.DOWNLOADING_ARTIFACT, PluginLoadPhase.INITIALIZING_RUNTIME), phases)
    }

    private class RecordingNetwork(private val bytes: ByteArray, private val status: Int = 200) : WasmlineNetworkClient {
        val requests = mutableListOf<String>()
        override suspend fun fetch(url: String): WasmlineHttpResponse {
            requests += url
            return WasmlineHttpResponse(status, bytes.copyOf())
        }
        override suspend fun fetchTo(url: String, sink: WasmlineNetworkSink): WasmlineHttpStatus {
            requests += url
            sink.write(bytes, 0, bytes.size)
            return WasmlineHttpStatus(status)
        }
    }
}
