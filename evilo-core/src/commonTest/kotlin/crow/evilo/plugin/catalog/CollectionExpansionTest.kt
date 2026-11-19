package crow.evilo.plugin.catalog

import crow.evilo.network.ktor.BoundedHttpTransport
import crow.evilo.plugin.api.HOST_MEDIA_CAPABILITY
import crow.evilo.plugin.api.PluginJson
import crow.evilo.plugin.api.HostDescriptor
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
import kotlin.test.assertTrue

/**
 * A collection lists plugin repositories rather than plugins. Adding it must produce one
 * source per repository and fetch each, because the collection document holds no plugin
 * data of its own.
 */
class CollectionExpansionTest {
    private val base = "https://collection.example.com"
    private fun catalogFor(id: String): String {
        val plugin = catalogPlugin().copy(id = id)
        return PluginJson.encodeToString(pluginCatalog(listOf(plugin)))
    }

    private fun repository(failFor: Set<String> = emptySet()):
        Pair<PluginCatalogRepository, BoundedHttpTransport> {
        val collection = """
            {"name":"Two repositories","sources":[
              "$base/a/catalog.json","$base/b/catalog.json"]}
        """.trimIndent()
        val transport = BoundedHttpTransport(HttpClient(MockEngine { request ->
            val path = request.url.encodedPath
            val body = when (path) {
                "/collection.json" -> collection
                "/a/catalog.json" -> catalogFor("com.example.a")
                "/b/catalog.json" -> catalogFor("com.example.b")
                else -> null
            }
            when {
                body == null || path in failFor -> respond("missing", HttpStatusCode.NotFound)
                else -> respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }
        }) { followRedirects = false }, requestTimeoutMillis = 0)
        val preferences = EviloPreferencesRepository(MemoryDataStore(EviloPreferences()))
        return PluginCatalogRepository(MemoryPluginCatalogStore(), preferences, transport,
            HostDescriptor("1.0.0", 1, capabilities = setOf(HOST_MEDIA_CAPABILITY))) to transport
    }

    @Test
    fun addingOneCollectionProducesOneSourcePerRepository() = runBlocking {
        val (repository, transport) = repository()
        try {
            repository.initialize("""{"name":"bundled","plugins":[]}""")
            repository.addSource("$base/collection.json", allowHttp = false)
            val added = repository.state.value.subscriptions.filter { it.url.startsWith(base) }
            assertEquals(2, added.size)
            assertEquals(setOf("$base/a/catalog.json", "$base/b/catalog.json"), added.map { it.url }.toSet())
            // Each entry was fetched, so both plugins are present without a manual refresh.
            assertEquals(2, repository.state.value.listings.count { it.snapshot.subscription.url.startsWith(base) })
        } finally {
            transport.close()
        }
    }

    @Test
    fun aFailingEntryDoesNotDiscardTheOthers() = runBlocking {
        val (repository, transport) = repository(failFor = setOf("/b/catalog.json"))
        try {
            repository.initialize("""{"name":"bundled","plugins":[]}""")
            repository.addSource("$base/collection.json", allowHttp = false)
            val added = repository.state.value.subscriptions.filter { it.url.startsWith(base) }
            assertEquals(2, added.size, "both entries stay subscribed")
            val loaded = repository.state.value.listings.count { it.snapshot.subscription.url.startsWith(base) }
            assertEquals(1, loaded, "only the reachable entry produced a listing")
            val failed = repository.state.value.sourceChecks.filterKeys { key -> added.any { it.id == key } }
                .values.count { it.error != null }
            assertEquals(1, failed)
        } finally {
            transport.close()
        }
    }

    @Test
    fun addingTheSameCollectionTwiceAddsNothing() = runBlocking {
        val (repository, transport) = repository()
        try {
            repository.initialize("""{"name":"bundled","plugins":[]}""")
            repository.addSource("$base/collection.json", allowHttp = false)
            val failure = assertFailsWith<IllegalArgumentException> {
                repository.addSource("$base/collection.json", allowHttp = false)
            }
            assertTrue(failure.message.orEmpty().contains("already subscribed"), failure.message.orEmpty())
            assertEquals(2, repository.state.value.subscriptions.count { it.url.startsWith(base) })
        } finally {
            transport.close()
        }
    }
}
