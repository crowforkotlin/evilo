package crow.evilo.plugin.runtime

import crow.evilo.plugin.catalog.catalogPlugin
import crow.evilo.plugin.catalog.descriptor
import crow.wasmline.loader.network.WasmlineHttpResponse
import crow.wasmline.loader.network.WasmlineHttpStatus
import crow.wasmline.loader.network.WasmlineNetworkClient
import crow.wasmline.loader.network.WasmlineNetworkSink
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ManifestIdentityClientTest {
    @Test
    fun aChangedManifestIsRejectedBeforeDecodingOrStreamingItsBytes() = runTest {
        val plugin = catalogPlugin()
        val release = plugin.latestRelease
        val delegate = object : WasmlineNetworkClient {
            override suspend fun fetch(url: String) = WasmlineHttpResponse(200, byteArrayOf(1, 2, 3))
            override suspend fun fetchTo(url: String, sink: WasmlineNetworkSink): WasmlineHttpStatus =
                error("Manifest bytes must be checked before streaming.")
        }
        var callbacks = 0
        var writes = 0
        val client = ManifestIdentityClient(release.manifestUrl, delegate, plugin.descriptor(release),
            release.sourceRevision, release.sourceArchiveSha256, "d".repeat(64)) { callbacks++ }
        val direct = assertFailsWith<IllegalArgumentException> { client.fetch(release.manifestUrl) }
        val streamed = assertFailsWith<IllegalArgumentException> {
            client.fetchTo(release.manifestUrl, WasmlineNetworkSink { _, _, _ -> writes++ })
        }
        assertEquals("The signed manifest changed; select a new release and review it before installation.", direct.message)
        assertEquals(direct.message, streamed.message)
        assertEquals(0, callbacks)
        assertEquals(0, writes)
    }
}
