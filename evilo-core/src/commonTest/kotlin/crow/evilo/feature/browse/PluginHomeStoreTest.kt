@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package crow.evilo.feature.browse

import androidx.datastore.core.DataStore
import crow.evilo.app.AppPluginController
import crow.evilo.app.PluginSessionConnector
import crow.evilo.app.TestPluginSession
import crow.evilo.plugin.api.HttpAccess
import crow.evilo.plugin.api.PluginPermissions
import crow.evilo.plugin.catalog.BUNDLED_SOURCE_ID
import crow.evilo.plugin.catalog.CatalogPlugin
import crow.evilo.plugin.catalog.PluginCatalog
import crow.evilo.plugin.catalog.PluginCatalogRepositoryTest.RepositoryFixture
import crow.evilo.plugin.catalog.PluginRelease
import crow.evilo.plugin.catalog.catalogPlugin
import crow.evilo.plugin.catalog.descriptor
import crow.evilo.plugin.catalog.pluginCatalog
import crow.evilo.storage.EviloPreferences
import crow.evilo.storage.EviloPreferencesRepository
import crow.evilo.storage.MemoryDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PluginHomeStoreTest {
    @Test
    fun thirdPartyReleaseRequiresExplicitPublisherApprovalBeforeConnecting() = runTest {
        val original = catalogPlugin()
        val release = original.latestRelease.copy(
            signing = crow.evilo.plugin.catalog.PluginSigningIdentity("publisher-test", "02".repeat(32)),
            manifestSha256 = "d".repeat(64))
        val plugin = original.copy(availableVersions = listOf(release))
        val fixture = HomeFixture(this, pluginCatalog().copy(plugins = listOf(plugin)))
        try {
            fixture.initialize()
            val listing = fixture.repository.state.value.listings.single()
            fixture.openSourceDetail(listing.key)
            fixture.store.dispatch(PluginHomeIntent.ReviewRelease(listing.key, release.versionCode))
            val review = assertIs<HomeOverlay.PermissionReview>(fixture.store.state.value.overlay)
            fixture.store.dispatch(PluginHomeIntent.ApproveInstall(review))
            fixture.awaitIdle()
            assertTrue(fixture.sessions.isEmpty())
            assertTrue(fixture.repository.state.value.installed.isEmpty())
            assertEquals(review, fixture.store.state.value.overlay)
            assertTrue(fixture.store.state.value.actionError.orEmpty().contains("publisher approval"))
            fixture.store.dispatch(PluginHomeIntent.ApproveInstall(review, publisherApproved = true))
            fixture.awaitIdle()
            assertEquals(1, fixture.sessions.size)
            assertEquals("d".repeat(64), fixture.repository.state.value.installed.single().approvedManifestSha256)
        } finally { fixture.close() }
    }

    @Test
    fun transientDismissalKeepsThePluginBrowsersPersistentConnectionFailure() = runTest {
        val fixture = HomeFixture(this)
        try {
            fixture.initialize()
            fixture.installAll()
            val key = fixture.repository.state.value.installed.single().key
            fixture.store.dispatch(PluginHomeIntent.OpenPlugin(key))
            fixture.awaitIdle()
            fixture.connections.invalidate("This browser connection failed.")
            fixture.awaitIdle()
            val failure = assertNotNull(fixture.store.state.value.connection.error)
            assertEquals(HomePage.PluginBrowser(key), fixture.store.state.value.page)
            fixture.store.dispatch(PluginHomeIntent.DismissTransientError(expectedConnectionError = failure))
            fixture.awaitIdle()
            assertEquals(failure, fixture.store.state.value.connection.error)
        } finally { fixture.close() }
    }

    @Test
    fun transientConnectionDismissalDoesNotEraseAnUnrelatedActionFailure() = runTest {
        val fixture = HomeFixture(this)
        try {
            fixture.initialize()
            fixture.store.dispatch(PluginHomeIntent.OpenPlugin("missing:plugin"))
            val actionFailure = assertNotNull(fixture.store.state.value.actionError)
            fixture.connections.invalidate("This connection failed.")
            fixture.awaitIdle()
            val connectionFailure = assertNotNull(fixture.store.state.value.connection.error)
            fixture.store.dispatch(PluginHomeIntent.DismissTransientError(expectedConnectionError = "An older failure."))
            fixture.awaitIdle()
            assertEquals(connectionFailure, fixture.store.state.value.connection.error)
            fixture.store.dispatch(PluginHomeIntent.DismissTransientError(expectedConnectionError = connectionFailure))
            fixture.awaitIdle()
            assertNull(fixture.store.state.value.connection.error)
            assertEquals(actionFailure, fixture.store.state.value.actionError)
        } finally { fixture.close() }
    }

    @Test
    fun transientDismissalClearsOnlyTheMatchingActionFailureAndKeepsTheQuery() = runTest {
        val fixture = HomeFixture(this)
        try {
            fixture.initialize()
            fixture.store.dispatch(PluginHomeIntent.Search("kept query"))
            fixture.store.dispatch(PluginHomeIntent.OpenPlugin("missing:plugin"))
            val failure = assertNotNull(fixture.store.state.value.actionError)
            fixture.store.dispatch(PluginHomeIntent.DismissTransientError(expectedActionError = "An older failure."))
            assertEquals(failure, fixture.store.state.value.actionError)
            fixture.store.dispatch(PluginHomeIntent.DismissTransientError(expectedActionError = failure))
            assertNull(fixture.store.state.value.actionError)
            assertEquals("kept query", fixture.store.state.value.query)
            assertNull(fixture.store.state.value.page)
        } finally { fixture.close() }
    }

    @Test
    fun aboutDoesNotConnectAndTheMainActionOpensTheInstalledPluginDirectly() = runTest {
        val fixture = HomeFixture(this)
        try {
            fixture.initialize()
            fixture.installAll()
            val key = fixture.repository.state.value.installed.single().key
            fixture.store.dispatch(PluginHomeIntent.Search("Test"))
            fixture.store.dispatch(PluginHomeIntent.ManagePlugin(key))
            assertEquals(HomePage.PluginDetail(key), fixture.store.state.value.page)
            assertTrue(fixture.sessions.isEmpty())
            fixture.store.dispatch(PluginHomeIntent.Back)
            fixture.store.dispatch(PluginHomeIntent.OpenPlugin(key))
            fixture.awaitIdle()
            assertEquals(listOf(HomePage.PluginBrowser(key)), fixture.store.state.value.pages)
            assertSame(fixture.sessions.single(), fixture.store.state.value.connection.active?.session)
            assertEquals(key, fixture.repository.state.value.preferredInstallations["test.plugin"])
            fixture.store.dispatch(PluginHomeIntent.Back)
            fixture.awaitIdle()
            assertNull(fixture.store.state.value.page)
            assertEquals("Test", fixture.store.state.value.query)
            assertNull(fixture.store.state.value.connection.active)
            assertEquals(1, fixture.sessions.single().closeCalls)
        } finally { fixture.close() }
    }

    @Test
    fun permissionBackReturnsToTheInitiatingDetailWithoutChangingItsSourceCatalog() = runTest {
        val fixture = HomeFixture(this)
        try {
            fixture.initialize()
            val listing = fixture.repository.state.value.listings.single()
            fixture.openSourceDetail(listing.key)
            fixture.store.dispatch(PluginHomeIntent.SearchSource(BUNDLED_SOURCE_ID, "Test"))
            fixture.store.dispatch(PluginHomeIntent.ReviewRelease(listing.key, listing.plugin.latestRelease.versionCode))
            assertIs<HomeOverlay.PermissionReview>(fixture.store.state.value.overlay)
            fixture.store.dispatch(PluginHomeIntent.Back)
            assertNull(fixture.store.state.value.overlay)
            assertEquals(HomePage.PluginDetail(listing.key), fixture.store.state.value.page)
            fixture.store.dispatch(PluginHomeIntent.Back)
            assertEquals(HomePage.SourceCatalog(BUNDLED_SOURCE_ID), fixture.store.state.value.page)
            assertEquals("Test", fixture.store.state.value.sourceQueries[BUNDLED_SOURCE_ID])
            assertTrue(fixture.sessions.isEmpty())
            assertTrue(fixture.repository.state.value.installed.isEmpty())
        } finally { fixture.close() }
    }

    @Test
    fun installOnlyKeepsTheDetailAndClosesItsVerificationSession() = runTest {
        val fixture = HomeFixture(this)
        try {
            fixture.initialize()
            val listing = fixture.repository.state.value.listings.single()
            fixture.openSourceDetail(listing.key)
            val pages = fixture.store.state.value.pages
            fixture.store.dispatch(PluginHomeIntent.ReviewRelease(listing.key, listing.plugin.latestRelease.versionCode))
            val review = assertIs<HomeOverlay.PermissionReview>(fixture.store.state.value.overlay)
            fixture.store.dispatch(PluginHomeIntent.ApproveInstall(review))
            fixture.awaitIdle()
            assertEquals(pages, fixture.store.state.value.pages)
            assertNull(fixture.store.state.value.connection.active)
            assertEquals(1, fixture.sessions.single().closeCalls)
            assertEquals(listing.key, fixture.store.state.value.notice?.installationKey)
            assertEquals(1, fixture.store.state.value.browse.count)
            assertNull(fixture.store.state.value.error)
        } finally { fixture.close() }
    }

    @Test
    fun installAndBrowseRetainsOneSessionAndBackReturnsToTheReviewedDetail() = runTest {
        val fixture = HomeFixture(this)
        try {
            fixture.initialize()
            val listing = fixture.repository.state.value.listings.single()
            fixture.openSourceDetail(listing.key)
            fixture.store.dispatch(PluginHomeIntent.ReviewRelease(listing.key, listing.plugin.latestRelease.versionCode, openAfterInstall = true))
            fixture.store.dispatch(PluginHomeIntent.ApproveInstall(assertIs<HomeOverlay.PermissionReview>(fixture.store.state.value.overlay)))
            fixture.awaitIdle()
            assertEquals(HomePage.PluginBrowser(listing.key), fixture.store.state.value.page)
            assertSame(fixture.sessions.single(), fixture.store.state.value.connection.active?.session)
            assertEquals(0, fixture.sessions.single().closeCalls)
            assertNull(fixture.store.state.value.notice)
            fixture.store.dispatch(PluginHomeIntent.Back)
            fixture.awaitIdle()
            assertEquals(HomePage.PluginDetail(listing.key), fixture.store.state.value.page)
            assertEquals(1, fixture.sessions.single().closeCalls)
        } finally { fixture.close() }
    }

    @Test
    fun approvalRejectsTheExactReviewedReleaseAfterItsPermissionsChange() = runTest {
        val fixture = HomeFixture(this)
        try {
            fixture.initialize()
            val listing = fixture.repository.state.value.listings.single()
            val release = listing.plugin.latestRelease
            fixture.openSourceDetail(listing.key)
            fixture.store.dispatch(PluginHomeIntent.ReviewRelease(listing.key, release.versionCode))
            val review = assertIs<HomeOverlay.PermissionReview>(fixture.store.state.value.overlay)
            val changed = release.copy(permissions = PluginPermissions(media = HttpAccess(origins = setOf("https://images.example.com"))))
            fixture.catalogs.bundled = pluginCatalog(listOf(listing.plugin.copy(availableVersions = listOf(changed))))
            fixture.repository.refresh()
            fixture.awaitIdle()
            fixture.store.dispatch(PluginHomeIntent.ApproveInstall(review))
            fixture.awaitIdle()
            assertTrue(fixture.store.state.value.actionError.orEmpty().contains("metadata changed"))
            assertEquals(review, fixture.store.state.value.overlay)
            assertTrue(fixture.sessions.isEmpty())
            assertTrue(fixture.repository.state.value.installed.isEmpty())
        } finally { fixture.close() }
    }

    @Test
    fun openingFromASourceFilterOrItsDetailDoesNotChangeTheAllSourcesDefault() = runTest {
        val fixture = HomeFixture(this)
        try {
            fixture.initialize()
            fixture.repository.addSource(CUSTOM_URL, false)
            fixture.installAll()
            val installed = fixture.repository.state.value.installed
            val bundled = installed.first { it.sourceId == BUNDLED_SOURCE_ID }
            val custom = installed.first { it.sourceId != BUNDLED_SOURCE_ID }
            fixture.repository.selectPreferredInstallation(bundled.key)
            fixture.awaitIdle()
            fixture.store.dispatch(PluginHomeIntent.Search("Test"))
            fixture.store.dispatch(PluginHomeIntent.SelectBrowseSource(custom.sourceId))
            fixture.store.dispatch(PluginHomeIntent.OpenPlugin(custom.key, rememberSource = false))
            fixture.awaitIdle()
            assertEquals(bundled.key, fixture.repository.state.value.preferredInstallations[custom.plugin.id])
            fixture.store.dispatch(PluginHomeIntent.Back)
            fixture.awaitIdle()
            assertEquals(custom.sourceId, fixture.store.state.value.browseSourceId)
            assertEquals("Test", fixture.store.state.value.query)
            fixture.store.dispatch(PluginHomeIntent.ManagePlugin(custom.key, rememberSourceOnOpen = false))
            val detail = assertIs<HomePage.PluginDetail>(fixture.store.state.value.page)
            fixture.store.dispatch(PluginHomeIntent.OpenPlugin(detail.key, rememberSource = detail.rememberSourceOnOpen))
            fixture.awaitIdle()
            assertEquals(bundled.key, fixture.repository.state.value.preferredInstallations[custom.plugin.id])
            assertEquals(custom.key, fixture.store.state.value.connection.active?.installationKey)
        } finally { fixture.close() }
    }

    @Test
    fun managingAFailedBrowserReusesItsExistingDetailAndPreservesTheOpenOrigin() = runTest {
        val fixture = HomeFixture(this)
        fixture.connect = { _, _ -> error("Package load failed.") }
        try {
            fixture.initialize()
            fixture.installAll()
            val key = fixture.repository.state.value.installed.single().key
            fixture.store.dispatch(PluginHomeIntent.ManagePlugin(key, rememberSourceOnOpen = false))
            fixture.store.dispatch(PluginHomeIntent.OpenPlugin(key, rememberSource = false))
            fixture.awaitIdle()
            assertEquals("Package load failed.", fixture.store.state.value.connection.error)
            fixture.store.dispatch(PluginHomeIntent.ManagePlugin(key))
            assertEquals(listOf(HomePage.PluginDetail(key, rememberSourceOnOpen = false)), fixture.store.state.value.pages)
            fixture.store.dispatch(PluginHomeIntent.Back)
            assertTrue(fixture.store.state.value.pages.isEmpty())
        } finally { fixture.close() }
    }

    @Test
    fun backCancelsALoadAndCannotPublishALateSessionIntoTheReturnedPage() = runTest {
        val fixture = HomeFixture(this)
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        fixture.connect = { plugin, release ->
            started.complete(Unit)
            withContext(NonCancellable) { gate.await() }
            TestPluginSession(plugin.descriptor(release))
        }
        try {
            fixture.initialize()
            fixture.installAll()
            val key = fixture.repository.state.value.installed.single().key
            fixture.store.dispatch(PluginHomeIntent.Search("Test"))
            fixture.store.dispatch(PluginHomeIntent.OpenPlugin(key))
            started.await()
            fixture.store.dispatch(PluginHomeIntent.Back)
            gate.complete(Unit)
            fixture.awaitIdle()
            assertNull(fixture.store.state.value.page)
            assertNull(fixture.store.state.value.connection.active)
            assertNull(fixture.store.state.value.error)
            assertEquals("Test", fixture.store.state.value.query)
            assertEquals(1, fixture.sessions.single().closeCalls)
        } finally { gate.complete(Unit); fixture.close() }
    }

    @Test
    fun aNewNsfwClassificationRemovesTheBrowserRouteAndClosesTheActiveSession() = runTest {
        val fixture = HomeFixture(this)
        try {
            fixture.initialize()
            fixture.installAll()
            val installed = fixture.repository.state.value.installed.single()
            fixture.store.dispatch(PluginHomeIntent.OpenPlugin(installed.key))
            fixture.awaitIdle()
            fixture.catalogs.bundled = pluginCatalog(listOf(installed.plugin.copy(nsfw = true)))
            fixture.repository.refresh()
            fixture.awaitIdle()
            assertTrue(fixture.store.state.value.pages.isEmpty())
            assertNull(fixture.store.state.value.connection.active)
            assertEquals(1, fixture.sessions.single().closeCalls)
            assertEquals(listOf(installed), fixture.repository.state.value.installed)
            assertEquals(0, fixture.store.state.value.browse.count)
            assertEquals(1, fixture.store.state.value.browse.hiddenNsfw)
        } finally { fixture.close() }
    }

    @Test
    fun hidingNsfwAlsoDismissesItsDetailAndPermissionReview() = runTest {
        val preferences = EviloPreferencesRepository(MemoryDataStore(EviloPreferences(showNsfw = true)))
        val fixture = HomeFixture(this, pluginCatalog(listOf(catalogPlugin().copy(nsfw = true))), preferences)
        try {
            fixture.initialize()
            val listing = fixture.repository.state.value.listings.single()
            fixture.openSourceDetail(listing.key)
            fixture.store.dispatch(PluginHomeIntent.ReviewRelease(listing.key, listing.plugin.latestRelease.versionCode))
            assertNotNull(fixture.store.state.value.overlay)
            fixture.store.dispatch(PluginHomeIntent.ShowNsfw(false))
            fixture.awaitIdle()
            assertEquals(HomePage.SourceCatalog(BUNDLED_SOURCE_ID), fixture.store.state.value.page)
            assertNull(fixture.store.state.value.overlay)
            assertFalse(fixture.store.state.value.catalog.showNsfw)
            assertTrue(fixture.sessions.isEmpty())
        } finally { fixture.close() }
    }

    @Test
    fun localSearchFiltersAndNavigationRemainAvailableDuringACatalogRefresh() = runTest {
        val fixture = HomeFixture(this)
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        fixture.catalogs.beforeRequest = { started.complete(Unit); gate.await() }
        try {
            fixture.initialize()
            fixture.installAll()
            fixture.store.dispatch(PluginHomeIntent.Refresh)
            started.await()
            fixture.store.state.first { it.catalog.refreshing }
            fixture.store.dispatch(PluginHomeIntent.Search("Test"))
            fixture.store.dispatch(PluginHomeIntent.SelectBrowseSource(BUNDLED_SOURCE_ID))
            fixture.store.dispatch(PluginHomeIntent.ShowAbout)
            assertEquals(HomePage.About, fixture.store.state.value.page)
            assertEquals("Test", fixture.store.state.value.query)
            assertEquals(BUNDLED_SOURCE_ID, fixture.store.state.value.browseSourceId)
            assertFalse(fixture.store.state.value.changing)
            assertTrue(fixture.store.state.value.canMutate)
            assertTrue(fixture.sessions.isEmpty())
            gate.complete(Unit)
            fixture.awaitIdle()
            assertEquals(HomePage.About, fixture.store.state.value.page)
            assertNull(fixture.store.state.value.error)
        } finally { gate.complete(Unit); fixture.close() }
    }

    @Test
    fun leavingAddSourceCancelsTheDownloadAndDoesNotRedirectAReopenedForm() = runTest {
        val fixture = HomeFixture(this, pluginCatalog(emptyList()))
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        fixture.catalogs.beforeRequest = { started.complete(Unit); withContext(NonCancellable) { gate.await() } }
        try {
            fixture.initialize()
            fixture.store.dispatch(PluginHomeIntent.ShowSources)
            fixture.store.dispatch(PluginHomeIntent.ShowAddSource)
            fixture.store.dispatch(PluginHomeIntent.AddSource(CUSTOM_URL, false))
            started.await()
            fixture.store.dispatch(PluginHomeIntent.Back)
            assertEquals(HomePage.Sources, fixture.store.state.value.page)
            assertFalse(fixture.store.state.value.changing)
            fixture.store.dispatch(PluginHomeIntent.ShowAddSource)
            gate.complete(Unit)
            fixture.awaitIdle()
            assertEquals(HomePage.AddSource, fixture.store.state.value.page)
            assertNull(fixture.store.state.value.error)
            assertEquals(listOf(BUNDLED_SOURCE_ID), fixture.repository.state.value.subscriptions.map { it.id })
            assertTrue(fixture.repository.state.value.installed.isEmpty())
        } finally { gate.complete(Unit); fixture.close() }
    }

    @Test
    fun aFailedPreferenceWriteKeepsTheOldSwitchValueAndReportsTheFailure() = runTest {
        val data = MemoryDataStore(EviloPreferences())
        var failWrites = false
        val preferences = EviloPreferencesRepository(object : DataStore<EviloPreferences> by data {
            override suspend fun updateData(transform: suspend (EviloPreferences) -> EviloPreferences): EviloPreferences {
                check(!failWrites) { "Preferences cannot be written." }
                return data.updateData(transform)
            }
        })
        val fixture = HomeFixture(this, preferences = preferences)
        try {
            fixture.initialize()
            fixture.store.dispatch(PluginHomeIntent.ShowSettings(SettingsSection.BROWSE))
            failWrites = true
            fixture.store.dispatch(PluginHomeIntent.MergeDuplicatePlugins(false))
            fixture.awaitIdle()
            assertTrue(fixture.store.state.value.catalog.mergeDuplicatePlugins)
            assertTrue(preferences.currentSettings().mergeDuplicatePlugins)
            assertEquals("Preferences cannot be written.", fixture.store.state.value.actionError)
            assertEquals(HomePage.Settings(SettingsSection.BROWSE), fixture.store.state.value.page)
        } finally { fixture.close() }
    }

    private class HomeFixture(scope: CoroutineScope, catalog: PluginCatalog = pluginCatalog(),
        preferences: EviloPreferencesRepository = EviloPreferencesRepository(MemoryDataStore(EviloPreferences()))) {
        val catalogs = RepositoryFixture(catalog, preferences = preferences)
        val repository = catalogs.repository
        val sessions = mutableListOf<TestPluginSession>()
        var connect: suspend (CatalogPlugin, PluginRelease) -> TestPluginSession = { plugin, release -> TestPluginSession(plugin.descriptor(release)) }
        val connections = AppPluginController(repository, PluginSessionConnector { plugin, release, _, _ ->
            connect(plugin, release).also { sessions += it }
        }, scope)
        val store = PluginHomeStore(repository, connections, scope)

        suspend fun initialize() { catalogs.initialize(); sync() }
        suspend fun installAll() {
            repository.state.value.listings.forEach { repository.recordInstallation(it, it.plugin.latestRelease, it.plugin.descriptor(it.plugin.latestRelease), "d".repeat(64)) }
            sync()
        }
        fun openSourceDetail(key: String) {
            store.dispatch(PluginHomeIntent.ShowSources)
            store.dispatch(PluginHomeIntent.ShowSourceCatalog(BUNDLED_SOURCE_ID))
            store.dispatch(PluginHomeIntent.ManagePlugin(key))
        }
        suspend fun awaitIdle() {
            store.awaitIdle()
            sync()
            store.awaitIdle()
            sync()
        }
        private suspend fun sync() { store.state.first { it.catalog == repository.state.value && it.connection == connections.state.value } }
        suspend fun close() { try { store.close(); connections.close() } finally { catalogs.transport.close() } }
    }

    private companion object { const val CUSTOM_URL = "https://catalog.example.com/plugins.json" }
}
