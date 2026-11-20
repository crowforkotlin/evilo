package crow.evilo.plugin.catalog

import crow.evilo.network.ktor.BoundedHttpTransport
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.plugin.api.HOST_MEDIA_CAPABILITY
import crow.evilo.plugin.subscription.SubscriptionFixture
import crow.evilo.storage.EviloPreferences
import crow.evilo.storage.EviloPreferencesRepository
import crow.evilo.storage.MemoryDataStore
import crow.evilo.storage.MemoryPluginCatalogStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Drives a subscription address through the repository: pointer, key, signature, index,
 * then the catalog the rest of the app reads. The fixture bytes are the ones the builder
 * signs, so this covers the producer and the consumer together without a network.
 */
class SubscriptionSourceTest {
    private val base = "https://subscription.example.com"

    private fun files(): Map<String, ByteArray> = mapOf(
        "/repo.json" to SubscriptionFixture.pointer("$base/index.json").encodeToByteArray(),
        "/index.json" to SubscriptionFixture.INDEX_BODY.encodeToByteArray(),
        "/index.json.sig" to (SubscriptionFixture.SIGNATURE_HEX + "\n").encodeToByteArray(),
        "/index.json.pub" to (SubscriptionFixture.PUBLIC_KEY_HEX + "\n").encodeToByteArray(),
    )

    private fun repository(fault: (String) -> HttpStatusCode? = { null }):
        Pair<PluginCatalogRepository, BoundedHttpTransport> {
        val content = files()
        val transport = BoundedHttpTransport(HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val bytes = content[path]
            val forced = fault(path)
            when {
                bytes == null || forced != null -> respond("missing", forced ?: HttpStatusCode.NotFound)
                else -> respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }) { followRedirects = false }, requestTimeoutMillis = 0)
        val preferences = EviloPreferencesRepository(MemoryDataStore(EviloPreferences()))
        return PluginCatalogRepository(MemoryPluginCatalogStore(), preferences, transport,
            HostDescriptor("1.0.0", 1, capabilities = setOf(HOST_MEDIA_CAPABILITY))) to transport
    }

    @Test
    fun aPointerResolvesToAnInstallablePlugin() = runBlocking {
        val (repository, transport) = repository()
        try {
            repository.initialize()
            repository.addSource("$base/repo.json", allowHttp = false)
            // The published plugin is classified NSFW, so the preference must be on before
            // it is installable; that is app policy, not a gap in the index.
            repository.setShowNsfw(true)
            val listing = repository.state.value.listings.single { it.snapshot.subscription.url == "$base/repo.json" }
            assertEquals("crow.evilo.source.copymanga", listing.plugin.id)
            // A listing with no releasable version would appear in Browse but not install.
            val release = assertNotNull(repository.recommendedRelease(listing))
            assertEquals(1L, release.versionCode)
            assertEquals("1.0.0", release.version)
        } finally {
            transport.close()
        }
    }

    @Test
    fun aMissingSignatureIsReportedAsAnHttpFailure() = runBlocking {
        val (repository, transport) = repository(fault = { path ->
            if (path == "/index.json.sig") HttpStatusCode.NotFound else null
        })
        try {
            repository.initialize()
            val failure = assertFailsWith<SourceFailure> { repository.addSource("$base/repo.json", false) }
            assertTrue(failure.message.orEmpty().contains("HTTP 404"), failure.message.orEmpty())
        } finally {
            transport.close()
        }
    }
}
