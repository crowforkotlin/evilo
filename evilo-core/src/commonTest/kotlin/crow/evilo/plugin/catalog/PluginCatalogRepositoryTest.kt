@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package crow.evilo.plugin.catalog

import androidx.datastore.core.DataStore
import crow.evilo.plugin.api.HOST_MEDIA_CAPABILITY
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.plugin.api.PluginJson
import crow.evilo.network.ktor.BoundedHttpTransport
import crow.evilo.storage.EviloPreferences
import crow.evilo.storage.EviloPreferencesRepository
import crow.evilo.storage.CatalogRecords
import crow.evilo.storage.CatalogWrite
import crow.evilo.storage.MemoryDataStore
import crow.evilo.storage.MemoryPluginCatalogStore
import crow.evilo.storage.PluginCatalogStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginCatalogRepositoryTest {
    @Test
    fun cancellationDuringCatalogCommitStillPublishesDurableState() = runTest {
        val records = ControlledCatalogStore()
        val fixture = RepositoryFixture(pluginCatalog(), records)
        try {
            fixture.initialize()
            val gate = CompletableDeferred<Unit>().also { records.writeGate = it }
            val operation = launch { fixture.repository.setSourceEnabled(OFFICIAL_SOURCE_ID, false) }
            runCurrent()
            operation.cancel()
            assertTrue(fixture.repository.state.value.subscriptions.single().enabled)
            gate.complete(Unit)
            operation.join()
            assertFalse(fixture.repository.state.value.subscriptions.single().enabled)
            assertEquals(fixture.repository.state.value.subscriptions, records.read().subscriptions)
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun failedCatalogWritesDoNotPublishAnInstallation() = runTest {
        val records = ControlledCatalogStore()
        val fixture = RepositoryFixture(pluginCatalog(), records)
        try {
            fixture.initialize()
            val listing = fixture.repository.state.value.listings.single()
            val release = listing.plugin.latestRelease
            records.failWrites = true
            assertFailsWith<IllegalStateException> {
                fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
            }
            assertTrue(fixture.repository.state.value.installed.isEmpty())
            assertTrue(records.read().installed.isEmpty())
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun recordingTheSameReleaseTwiceKeepsTheOriginalInstallation() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            val listing = fixture.repository.state.value.listings.single()
            val release = listing.plugin.latestRelease
            fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
            val original = fixture.repository.state.value.installed.single()
            fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
            assertEquals(original, fixture.repository.state.value.installed.single())
            assertEquals(listOf(original), fixture.records.read().installed)
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun compatibleInstallationIsNotBlockedAndCustomSourcesCannotSelfCertify() = runTest {
        val fixture = RepositoryFixture(pluginCatalog(listOf(catalogPlugin().copy(certified = true))))
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            val listing = fixture.repository.state.value.listings.first { it.snapshot.subscription.id != OFFICIAL_SOURCE_ID }
            val release = assertNotNull(fixture.repository.recommendedRelease(listing))
            assertFalse(listing.officiallyCertified)
            fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
            assertNull(fixture.repository.installationBlockReason(fixture.repository.state.value.installed.single()))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun updatedNsfwMetadataAppliesToExistingInstallationsUntilConsentIsEnabled() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            val listing = fixture.repository.state.value.listings.single()
            val release = listing.plugin.latestRelease
            fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
            fixture.official = pluginCatalog(listOf(listing.plugin.copy(nsfw = true)))
            fixture.repository.refresh()
            val installation = fixture.repository.state.value.installed.single()
            assertTrue(fixture.repository.state.value.catalogPluginByKey.getValue(installation.key).nsfw)
            assertTrue(fixture.repository.installationBlockReason(installation).orEmpty().contains("NSFW"))
            fixture.repository.setShowNsfw(true)
            assertNull(fixture.repository.installationBlockReason(installation))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun mandatoryFloorSurvivesRefreshAndRestartEvenWhenLatestNeedsANewerHost() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            val initial = fixture.repository.state.value.listings.single()
            val first = initial.plugin.latestRelease
            fixture.repository.recordInstallation(initial, first, initial.plugin.descriptor(first))
            val second = catalogRelease(2).copy(minimumHostVersionCode = 2)
            fixture.official = pluginCatalog(listOf(catalogPlugin(listOf(first, second)).copy(forceUpdate = true)))
            fixture.repository.refresh()
            assertTrue(fixture.repository.installationBlockReason(fixture.repository.state.value.installed.single()).orEmpty().contains("mandatory"))
            assertNull(fixture.repository.recommendedRelease(fixture.repository.state.value.listings.single()))

            fixture.official = fixture.official.copy(plugins = fixture.official.plugins.map { it.copy(forceUpdate = false) })
            fixture.repository.refresh()
            val reopened = PluginCatalogRepository(fixture.records, fixture.preferences, fixture.transport, fixture.host)
            reopened.initialize(PluginJson.encodeToString(pluginCatalog()))
            assertTrue(reopened.installationBlockReason(reopened.state.value.installed.single()).orEmpty().contains("mandatory"))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun knownOfficialMandatoryFloorAlsoBlocksOlderCustomCatalogCopies() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            val custom = fixture.repository.state.value.listings.first { it.snapshot.subscription.id != OFFICIAL_SOURCE_ID }
            val release = custom.plugin.latestRelease
            fixture.repository.recordInstallation(custom, release, custom.plugin.descriptor(release))
            fixture.official = pluginCatalog(listOf(catalogPlugin(listOf(catalogRelease(), catalogRelease(2))).copy(forceUpdate = true)))
            fixture.repository.refresh()
            assertTrue(fixture.repository.installationBlockReason(fixture.repository.state.value.installed.single()).orEmpty().contains("mandatory"))
            assertNull(fixture.repository.recommendedRelease(custom))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun aCustomMandatoryFloorDoesNotBlockTheOfficialSource() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        fixture.custom = pluginCatalog(listOf(catalogPlugin(listOf(catalogRelease(), catalogRelease(2))).copy(forceUpdate = true)))
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            val official = fixture.repository.state.value.listings.first { it.snapshot.subscription.id == OFFICIAL_SOURCE_ID }
            assertNull(fixture.repository.releaseBlockReason(official, official.plugin.latestRelease))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun failedRefreshRetainsCachedCatalogAndReportsTheHttpFailure() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            fixture.repository.refresh()
            fixture.status = HttpStatusCode.ServiceUnavailable
            fixture.repository.refresh()
            val snapshot = fixture.repository.state.value.snapshots.single()
            assertEquals(fixture.official, snapshot.catalog)
            assertTrue(snapshot.fromCache)
            assertTrue(snapshot.error.orEmpty().contains("503"))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun changedReleaseMetadataCannotBeInstalledUsingAStaleListing() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            val stale = fixture.repository.state.value.listings.single()
            val release = stale.plugin.latestRelease
            fixture.official = pluginCatalog(listOf(catalogPlugin(listOf(release.copy(sourceArchiveSha256 = "c".repeat(64))))))
            fixture.repository.refresh()
            assertFailsWith<IllegalArgumentException> {
                fixture.repository.recordInstallation(stale, release, stale.plugin.descriptor(release))
            }
            assertTrue(fixture.repository.state.value.installed.isEmpty())
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun removingASubscriptionRemovesItsInstallationsButRetainsPluginData() = runTest {
        val fixture = RepositoryFixture(pluginCatalog(emptyList()))
        fixture.custom = pluginCatalog()
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            val listing = fixture.repository.state.value.listings.single()
            val release = listing.plugin.latestRelease
            fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
            fixture.writePluginValue(listing.plugin.id, "retained")
            fixture.repository.removeSource(listing.snapshot.subscription.id)
            assertTrue(fixture.repository.state.value.installed.isEmpty())
            assertEquals("retained", fixture.readPluginValue(listing.plugin.id))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun uninstallErasesDataOnlyAfterTheLastInstallationOfTheSignedIdIsRemoved() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            fixture.repository.state.value.listings.forEach { listing ->
                val release = listing.plugin.latestRelease
                fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
            }
            fixture.writePluginValue("test.plugin", "shared")
            val installed = fixture.repository.state.value.installed
            fixture.repository.uninstall(installed.first().key, eraseData = true)
            assertEquals("shared", fixture.readPluginValue("test.plugin"))
            fixture.repository.uninstall(installed.last().key, eraseData = true)
            assertNull(fixture.readPluginValue("test.plugin"))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun failedPluginDataDeletionIsRetriedOnTheNextStartup() = runTest {
        val data = MemoryDataStore(EviloPreferences())
        var failWrites = false
        val preferences = EviloPreferencesRepository(object : DataStore<EviloPreferences> by data {
            override suspend fun updateData(transform: suspend (EviloPreferences) -> EviloPreferences): EviloPreferences {
                check(!failWrites) { "Preferences cannot be written." }
                return data.updateData(transform)
            }
        })
        val fixture = RepositoryFixture(pluginCatalog(), preferences = preferences)
        try {
            fixture.initialize()
            val listing = fixture.repository.state.value.listings.single()
            val release = listing.plugin.latestRelease
            fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
            fixture.writePluginValue(listing.plugin.id, "removed")
            failWrites = true
            assertFailsWith<IllegalStateException> { fixture.repository.uninstall(listing.key, eraseData = true) }
            assertTrue(fixture.repository.state.value.installed.isEmpty())
            assertEquals(setOf(listing.plugin.id), fixture.records.read().pendingDataDeletes)
            assertEquals("removed", fixture.readPluginValue(listing.plugin.id))
            failWrites = false
            val reopened = PluginCatalogRepository(fixture.records, preferences, fixture.transport, fixture.host)
            reopened.initialize(PluginJson.encodeToString(fixture.official))
            assertTrue(fixture.records.read().pendingDataDeletes.isEmpty())
            assertTrue(reopened.state.value.installed.isEmpty())
            assertNull(fixture.readPluginValue(listing.plugin.id))
        } finally {
            fixture.transport.close()
        }
    }

    private class RepositoryFixture(
        var official: PluginCatalog,
        val records: PluginCatalogStore = MemoryPluginCatalogStore(),
        val preferences: EviloPreferencesRepository = EviloPreferencesRepository(MemoryDataStore(EviloPreferences())),
    ) {
        var custom: PluginCatalog = official
        var status = HttpStatusCode.OK
        val host = HostDescriptor("1.0.0", 1, capabilities = setOf(HOST_MEDIA_CAPABILITY))
        val transport = BoundedHttpTransport(HttpClient(MockEngine { request ->
            val catalog = when (request.url.toString()) {
                DEFAULT_OFFICIAL_CATALOG_URL -> official
                CUSTOM_URL -> custom
                else -> error("Unexpected test request: ${request.url}")
            }
            respond(PluginJson.encodeToString(catalog), status)
        }) { followRedirects = false })
        val repository = PluginCatalogRepository(records, preferences, transport, host)
        suspend fun initialize() { repository.initialize(PluginJson.encodeToString(official)) }
        suspend fun writePluginValue(pluginId: String, value: String) {
            val session = preferences.pluginSession(pluginId)
            session.transaction { session.write("plugin.$pluginId", "setting", value) }
        }
        suspend fun readPluginValue(pluginId: String): String? {
            val session = preferences.pluginSession(pluginId)
            return session.transaction { session.read("plugin.$pluginId", "setting") }
        }
    }

    private class ControlledCatalogStore : PluginCatalogStore {
        private val delegate = MemoryPluginCatalogStore()
        var failWrites = false
        var writeGate: CompletableDeferred<Unit>? = null
        override suspend fun read(): CatalogRecords = delegate.read()
        override suspend fun write(change: CatalogWrite) {
            check(!failWrites) { "Catalog records cannot be written." }
            writeGate?.await()
            delegate.write(change)
        }
    }

    private companion object {
        const val CUSTOM_URL = "https://catalog.example.com/plugins.json"
    }
}
