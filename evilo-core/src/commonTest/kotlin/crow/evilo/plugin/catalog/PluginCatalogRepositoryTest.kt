@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package crow.evilo.plugin.catalog

import androidx.datastore.core.DataStore
import crow.evilo.plugin.api.HOST_MEDIA_CAPABILITY
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.plugin.api.HttpAccess
import crow.evilo.plugin.api.PluginJson
import crow.evilo.plugin.api.PluginPermissions
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
            val operation = launch { fixture.repository.setSourceEnabled(BUNDLED_SOURCE_ID, false) }
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
    fun compatibleInstallationFromACustomSourceIsNotBlocked() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            val listing = fixture.repository.state.value.listings.first { it.snapshot.subscription.id != BUNDLED_SOURCE_ID }
            val release = assertNotNull(fixture.repository.recommendedRelease(listing))
            fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
            assertNull(fixture.repository.installationBlockReason(fixture.repository.state.value.installed.single()))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun catalogSubscriptionsDoNotRequireASpecificFilenameOrJsonSuffix() = runTest {
        val fixture = RepositoryFixture(pluginCatalog(emptyList()))
        fixture.custom = pluginCatalog()
        val urls = listOf("https://catalog.example.com/plugin.json", "https://catalog.example.com/reader-list.json",
            "https://catalog.example.com/catalog")
        try {
            fixture.initialize()
            urls.forEach { fixture.repository.addSource(it, false) }
            assertEquals(urls.toSet(), fixture.repository.state.value.listings.map { it.snapshot.subscription.url }.toSet())
            assertTrue(fixture.repository.state.value.listings.all { fixture.repository.recommendedRelease(it) != null })
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun compactCustomSourcesKeepSeparateSubscriptionsAndStableMetadataAfterRestart() = runTest {
        val fixture = RepositoryFixture(pluginCatalog(emptyList()))
        fixture.customJson = compactCatalogJson().toString()
        val urls = listOf("https://catalog.example.com/my-plugins.json", "https://catalog.example.com/another-source")
        try {
            fixture.initialize()
            urls.forEach { fixture.repository.addSource(it, false) }
            val listings = fixture.repository.state.value.listings
            assertEquals(2, listings.map { it.key }.distinct().size)
            assertEquals(setOf("catalog"), listings.map { it.snapshot.catalog.id }.toSet())
            assertEquals(urls.toSet(), listings.map { it.snapshot.subscription.url }.toSet())
            val listing = listings.first()
            val release = assertNotNull(fixture.repository.recommendedRelease(listing))
            fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
            val installation = fixture.repository.state.value.installed.single()
            val cached = fixture.records.read().cachedCatalogs
            listings.forEach {
                assertEquals(it.snapshot.catalog, PluginJson.decodeFromString<PluginCatalog>(cached.getValue(it.snapshot.subscription.id)))
            }

            val reopened = PluginCatalogRepository(fixture.records, fixture.preferences, fixture.transport, fixture.host)
            reopened.initialize(PluginJson.encodeToString(fixture.bundled))
            reopened.refresh()
            assertEquals(listOf(installation), reopened.state.value.installed)
            assertNull(reopened.installationBlockReason(installation))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun changingCommonPermissionsDoesNotSilentlyChangeAnApprovedInstallation() = runTest {
        val first = catalogRelease().copy(permissions = PluginPermissions(api = HttpAccess(origins = setOf("https://first.example.com"))))
        val fixture = RepositoryFixture(pluginCatalog(emptyList()))
        fixture.customJson = compactCatalogJson(catalogPlugin(listOf(first))).toString()
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            val listing = fixture.repository.state.value.listings.single()
            val release = assertNotNull(fixture.repository.recommendedRelease(listing))
            fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
            val changed = first.copy(permissions = PluginPermissions(api = HttpAccess(origins = setOf("https://changed.example.com"))))
            fixture.customJson = compactCatalogJson(catalogPlugin(listOf(changed))).toString()
            fixture.repository.refresh()
            val installation = fixture.repository.state.value.installed.single()
            assertEquals(release.permissions, installation.release.permissions)
            assertTrue(fixture.repository.installationBlockReason(installation).orEmpty().contains("metadata changed"))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun legacyBundledRecordsMigrateWithoutLosingInstallationsCacheOrFloors() = runTest {
        val legacyCatalog = pluginCatalog().copy(id = "evilo.official", name = "evilo Official")
        val plugin = legacyCatalog.plugins.single()
        val installation = InstalledPlugin("official", LEGACY_BUNDLED_URL, plugin, plugin.latestRelease,
            "2026-11-09T00:00:00Z", false)
        val fixture = RepositoryFixture(pluginCatalog(emptyList()))
        try {
            fixture.records.write(CatalogWrite(
                subscriptions = listOf(CatalogSubscription("official", LEGACY_BUNDLED_URL, enabled = false)),
                cachedCatalogs = mapOf("official" to PluginJson.encodeToString(legacyCatalog)),
                installed = listOf(installation), updateFloors = mapOf("official:${plugin.id}" to 2L),
            ))
            fixture.writePluginValue(plugin.id, "retained")
            fixture.initialize()
            val state = fixture.repository.state.value
            val records = fixture.records.read()
            val migrated = installation.copy(sourceId = BUNDLED_SOURCE_ID, sourceUrl = DEFAULT_BUNDLED_CATALOG_URL)
            assertEquals(listOf(CatalogSubscription(BUNDLED_SOURCE_ID, DEFAULT_BUNDLED_CATALOG_URL, enabled = false)), state.subscriptions)
            assertEquals(listOf(migrated), state.installed)
            assertEquals(state.installed, records.installed)
            assertEquals(mapOf("$BUNDLED_SOURCE_ID:${plugin.id}" to 2L), records.updateFloors)
            assertEquals(setOf(BUNDLED_SOURCE_ID), records.cachedCatalogs.keys)
            val cached = PluginJson.decodeFromString<PluginCatalog>(records.cachedCatalogs.getValue(BUNDLED_SOURCE_ID))
            assertEquals(listOf(plugin), cached.plugins)
            assertEquals(fixture.bundled.id, cached.id)
            assertEquals(fixture.bundled.name, cached.name)
            assertEquals(fixture.bundled.description, cached.description)
            assertEquals("retained", fixture.readPluginValue(plugin.id))

            val reopened = PluginCatalogRepository(fixture.records, fixture.preferences, fixture.transport, fixture.host)
            reopened.initialize(PluginJson.encodeToString(fixture.bundled))
            assertEquals(listOf(migrated), reopened.state.value.installed)
            assertFalse(reopened.state.value.subscriptions.single().enabled)
            reopened.setSourceEnabled(BUNDLED_SOURCE_ID, true)
            assertTrue(reopened.installationBlockReason(migrated).orEmpty().contains("mandatory"))
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
            fixture.bundled = pluginCatalog(listOf(listing.plugin.copy(nsfw = true)))
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
            fixture.bundled = pluginCatalog(listOf(catalogPlugin(listOf(first, second)).copy(forceUpdate = true)))
            fixture.repository.refresh()
            assertTrue(fixture.repository.installationBlockReason(fixture.repository.state.value.installed.single()).orEmpty().contains("mandatory"))
            assertNull(fixture.repository.recommendedRelease(fixture.repository.state.value.listings.single()))

            fixture.bundled = fixture.bundled.copy(plugins = fixture.bundled.plugins.map { it.copy(forceUpdate = false) })
            fixture.repository.refresh()
            val reopened = PluginCatalogRepository(fixture.records, fixture.preferences, fixture.transport, fixture.host)
            reopened.initialize(PluginJson.encodeToString(pluginCatalog()))
            assertTrue(reopened.installationBlockReason(reopened.state.value.installed.single()).orEmpty().contains("mandatory"))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun aBundledMandatoryFloorDoesNotBlockOtherSubscriptions() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            val custom = fixture.repository.state.value.listings.first { it.snapshot.subscription.id != BUNDLED_SOURCE_ID }
            val release = custom.plugin.latestRelease
            fixture.repository.recordInstallation(custom, release, custom.plugin.descriptor(release))
            fixture.bundled = pluginCatalog(listOf(catalogPlugin(listOf(catalogRelease(), catalogRelease(2))).copy(forceUpdate = true)))
            fixture.repository.refresh()
            val bundled = fixture.repository.state.value.listings.first { it.snapshot.subscription.id == BUNDLED_SOURCE_ID }
            assertTrue(fixture.repository.releaseBlockReason(bundled, release).orEmpty().contains("mandatory"))
            assertNull(fixture.repository.installationBlockReason(fixture.repository.state.value.installed.single()))
            assertNotNull(fixture.repository.recommendedRelease(custom))
            fixture.repository.setSourceEnabled(BUNDLED_SOURCE_ID, false)
            assertNull(fixture.repository.installationBlockReason(fixture.repository.state.value.installed.single()))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun aCustomMandatoryFloorDoesNotBlockTheBundledSource() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        fixture.custom = pluginCatalog(listOf(catalogPlugin(listOf(catalogRelease(), catalogRelease(2))).copy(forceUpdate = true)))
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            val bundled = fixture.repository.state.value.listings.first { it.snapshot.subscription.id == BUNDLED_SOURCE_ID }
            assertNull(fixture.repository.releaseBlockReason(bundled, bundled.plugin.latestRelease))
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
            assertEquals(fixture.bundled, snapshot.catalog)
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
            fixture.bundled = pluginCatalog(listOf(catalogPlugin(listOf(release.copy(sourceArchiveSha256 = "c".repeat(64))))))
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
            reopened.initialize(PluginJson.encodeToString(fixture.bundled))
            assertTrue(fixture.records.read().pendingDataDeletes.isEmpty())
            assertTrue(reopened.state.value.installed.isEmpty())
            assertNull(fixture.readPluginValue(listing.plugin.id))
        } finally {
            fixture.transport.close()
        }
    }

    @Test
    fun singleSourceChecksLeaveOtherSourcesAndTheirCheckTimesUnchanged() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            val source = fixture.repository.state.value.subscriptions.last()
            val bundledCheck = fixture.repository.state.value.sourceChecks[BUNDLED_SOURCE_ID]
            val bundledSnapshot = fixture.repository.state.value.snapshots.first { it.subscription.id == BUNDLED_SOURCE_ID }
            fixture.requests.clear()
            fixture.repository.refresh(source.id)
            assertEquals(listOf(CUSTOM_URL), fixture.requests)
            assertEquals(bundledCheck, fixture.repository.state.value.sourceChecks[BUNDLED_SOURCE_ID])
            assertEquals(bundledSnapshot, fixture.repository.state.value.snapshots.first { it.subscription.id == BUNDLED_SOURCE_ID })
            assertNotNull(fixture.repository.state.value.sourceChecks[source.id]?.lastSuccessAt)
        } finally { fixture.transport.close() }
    }

    @Test
    fun aFailedSourceKeepsItsCacheWithoutMarkingOtherChecksAsFailed() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            val customId = fixture.repository.state.value.subscriptions.last().id
            val lastSuccess = fixture.repository.state.value.sourceChecks[customId]?.lastSuccessAt
            fixture.statusByUrl = mapOf(CUSTOM_URL to HttpStatusCode.ServiceUnavailable)
            fixture.repository.refresh()
            assertNull(fixture.repository.state.value.sourceChecks[BUNDLED_SOURCE_ID]?.error)
            assertNotNull(fixture.repository.state.value.sourceChecks[BUNDLED_SOURCE_ID]?.lastSuccessAt)
            val failed = assertNotNull(fixture.repository.state.value.sourceChecks[customId])
            assertTrue(failed.error.orEmpty().contains("503"))
            assertTrue(failed.usingCache)
            assertEquals(lastSuccess, failed.lastSuccessAt)
            assertEquals(fixture.custom, fixture.repository.state.value.snapshots.first { it.subscription.id == customId }.catalog)
        } finally { fixture.transport.close() }
    }

    @Test
    fun aSourceWithoutCachedMetadataStillHasAnIndependentFailureResult() = runTest {
        val fixture = RepositoryFixture(pluginCatalog(emptyList()))
        try {
            fixture.records.write(CatalogWrite(subscriptions = listOf(CatalogSubscription("custom.empty", CUSTOM_URL))))
            fixture.initialize()
            fixture.statusByUrl = mapOf(CUSTOM_URL to HttpStatusCode.ServiceUnavailable)
            fixture.repository.refresh("custom.empty")
            assertTrue(fixture.repository.state.value.snapshots.none { it.subscription.id == "custom.empty" })
            assertTrue(fixture.repository.state.value.sourceChecks["custom.empty"]?.error.orEmpty().contains("503"))
            assertFalse(fixture.repository.state.value.sourceChecks["custom.empty"]?.usingCache == true)
            assertNull(fixture.repository.state.value.sourceChecks["custom.empty"]?.lastSuccessAt)
        } finally { fixture.transport.close() }
    }

    @Test
    fun disablingADuplicateDefaultPinsItsKeyAndRetainsRecordsAndData() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            installAll(fixture)
            fixture.writePluginValue("test.plugin", "retained")
            val original = fixture.repository.state.value.installed
            val bundled = original.first { it.sourceId == BUNDLED_SOURCE_ID }
            fixture.repository.setSourceEnabled(BUNDLED_SOURCE_ID, false)
            assertEquals(original, fixture.records.read().installed)
            assertEquals(bundled.key, fixture.repository.state.value.preferredInstallations["test.plugin"])
            assertEquals(PluginBlockCode.SOURCE_DISABLED, fixture.repository.installationBlock(bundled)?.code)
            assertEquals("retained", fixture.readPluginValue("test.plugin"))
            fixture.requests.clear()
            fixture.repository.refresh()
            assertEquals(listOf(CUSTOM_URL), fixture.requests)
            fixture.repository.setSourceEnabled(BUNDLED_SOURCE_ID, true)
            assertEquals(original, fixture.repository.state.value.installed)
        } finally { fixture.transport.close() }
    }

    @Test
    fun removingTheChosenSourceRetainsItsPreferenceUntilTheLastCopyIsUninstalled() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            installAll(fixture)
            val chosen = fixture.repository.state.value.installed.last()
            fixture.repository.selectPreferredInstallation(chosen.key)
            fixture.writePluginValue(chosen.plugin.id, "retained")
            fixture.repository.removeSource(chosen.sourceId)
            assertEquals(chosen.key, fixture.repository.state.value.preferredInstallations[chosen.plugin.id])
            assertEquals("retained", fixture.readPluginValue(chosen.plugin.id))
            val remaining = fixture.repository.state.value.installed.single()
            fixture.repository.uninstall(remaining.key, eraseData = true)
            assertTrue(fixture.repository.state.value.preferredInstallations.isEmpty())
            assertNull(fixture.readPluginValue(chosen.plugin.id))
        } finally { fixture.transport.close() }
    }

    @Test
    fun updatingAnImplicitDefaultCannotMoveItToAnotherSourcesOlderRecord() = runTest {
        val plugin = catalogPlugin(listOf(catalogRelease(), catalogRelease(2)))
        val fixture = RepositoryFixture(pluginCatalog(listOf(plugin)))
        val custom = CatalogSubscription("custom.test", CUSTOM_URL)
        val first = InstalledPlugin(BUNDLED_SOURCE_ID, DEFAULT_BUNDLED_CATALOG_URL, plugin, plugin.availableVersions.first(),
            "2026-11-09T00:00:00Z", false)
        val second = first.copy(sourceId = custom.id, sourceUrl = CUSTOM_URL, installedAt = "2026-11-10T00:00:00Z")
        try {
            fixture.records.write(CatalogWrite(subscriptions = listOf(custom),
                cachedCatalogs = mapOf(custom.id to PluginJson.encodeToString(fixture.bundled)), installed = listOf(first, second)))
            fixture.initialize()
            assertTrue(fixture.repository.state.value.preferredInstallations.isEmpty())
            val listing = fixture.repository.state.value.listings.first { it.key == first.key }
            fixture.repository.recordInstallation(listing, plugin.latestRelease, plugin.descriptor(plugin.latestRelease))
            assertEquals(first.key, fixture.repository.state.value.preferredInstallations[plugin.id])
            assertEquals(first.key, fixture.preferences.currentSettings().preferredInstallations[plugin.id])
            assertEquals(second, fixture.repository.state.value.installed.first { it.key == second.key })
            assertEquals(plugin.latestRelease, fixture.repository.state.value.installed.first { it.key == first.key }.release)
        } finally { fixture.transport.close() }
    }

    @Test
    fun aDisabledInstalledNsfwCopyDoesNotLowerAnotherSourcesClassification() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            installAll(fixture)
            val custom = fixture.repository.state.value.subscriptions.last()
            fixture.custom = fixture.custom.copy(plugins = fixture.custom.plugins.map { it.copy(nsfw = true) })
            fixture.repository.refresh(custom.id)
            fixture.repository.setSourceEnabled(custom.id, false)
            val bundled = fixture.repository.state.value.installed.first { it.sourceId == BUNDLED_SOURCE_ID }
            assertEquals(PluginBlockCode.NSFW_HIDDEN, fixture.repository.installationBlock(bundled)?.code)
            fixture.repository.setShowNsfw(true)
            assertNull(fixture.repository.installationBlock(bundled))
        } finally { fixture.transport.close() }
    }

    private suspend fun installAll(fixture: RepositoryFixture) {
        fixture.repository.state.value.listings.forEach { listing ->
            val release = listing.plugin.latestRelease
            fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
        }
    }

    internal class RepositoryFixture(
        var bundled: PluginCatalog,
        val records: PluginCatalogStore = MemoryPluginCatalogStore(),
        val preferences: EviloPreferencesRepository = EviloPreferencesRepository(MemoryDataStore(EviloPreferences())),
    ) {
        var custom: PluginCatalog = bundled
        var customJson: String? = null
        var status = HttpStatusCode.OK
        var statusByUrl: Map<String, HttpStatusCode> = emptyMap()
        var beforeRequest: suspend (String) -> Unit = {}
        val requests = mutableListOf<String>()
        val host = HostDescriptor("1.0.0", 1, capabilities = setOf(HOST_MEDIA_CAPABILITY))
        val transport = BoundedHttpTransport(HttpClient(MockEngine { request ->
            val url = request.url.toString()
            requests += url
            beforeRequest(url)
            val json = when {
                request.url.toString() == DEFAULT_BUNDLED_CATALOG_URL -> PluginJson.encodeToString(bundled)
                request.url.host == "catalog.example.com" -> customJson ?: PluginJson.encodeToString(custom)
                else -> error("Unexpected test request: ${request.url}")
            }
            respond(json, statusByUrl[url] ?: status)
        }) { followRedirects = false })
        val repository = PluginCatalogRepository(records, preferences, transport, host)
        suspend fun initialize() { repository.initialize(PluginJson.encodeToString(bundled)) }
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
        const val LEGACY_BUNDLED_URL =
            "https://raw.githubusercontent.com/crowforkotlin/evilo/main/plugin-repository/official.json"
    }
}
