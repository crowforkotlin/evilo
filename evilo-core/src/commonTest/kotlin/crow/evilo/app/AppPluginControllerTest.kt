@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package crow.evilo.app

import androidx.datastore.core.DataStore
import crow.evilo.plugin.catalog.PluginCatalogRepositoryTest.RepositoryFixture
import crow.evilo.plugin.catalog.catalogPlugin
import crow.evilo.plugin.catalog.descriptor
import crow.evilo.plugin.catalog.pluginCatalog
import crow.evilo.storage.EviloPreferences
import crow.evilo.storage.EviloPreferencesRepository
import crow.evilo.storage.MemoryDataStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class AppPluginControllerTest {
    @Test
    fun installOnlyRecordsThePluginReportsRealPhasesAndClosesTheVerificationSession() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        fixture.initialize()
        val listing = fixture.repository.state.value.listings.single()
        val release = listing.plugin.latestRelease
        val session = TestPluginSession(listing.plugin.descriptor(release))
        val controller = AppPluginController(fixture.repository, PluginSessionConnector { _, _, _, phase ->
            phase(PluginLoadPhase.LOADING_PACKAGE)
            phase(PluginLoadPhase.NEGOTIATING)
            session
        }, this)
        val phases = mutableListOf<PluginLoadPhase?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { controller.state.collect { phases += it.phase } }
        try {
            controller.install(listing, release)
            controller.awaitIdle()
            assertEquals(listing.key, fixture.repository.state.value.installed.single().key)
            assertEquals(1, session.closeCalls)
            assertNull(controller.state.value.active)
            assertEquals(listing.key, controller.state.value.completedInstallation?.key)
            assertNull(controller.state.value.error)
            assertTrue(phases.containsAll(listOf(PluginLoadPhase.PRECHECK, PluginLoadPhase.LOADING_PACKAGE,
                PluginLoadPhase.NEGOTIATING, PluginLoadPhase.RECORDING_INSTALLATION)))
            assertTrue(controller.state.value.loadTimings.isEmpty())
        } finally { controller.close(); fixture.transport.close() }
    }

    @Test
    fun debugTimingsRetainObservedPhasesAfterTheSessionOpens() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        fixture.initialize()
        val listing = fixture.repository.state.value.listings.single()
        val session = TestPluginSession(listing.plugin.descriptor(listing.plugin.latestRelease))
        val controller = AppPluginController(fixture.repository, PluginSessionConnector { _, _, _, phase ->
            phase(PluginLoadPhase.FETCHING_MANIFEST)
            phase(PluginLoadPhase.FETCHING_MANIFEST)
            phase(PluginLoadPhase.VERIFYING_PACKAGE)
            phase(PluginLoadPhase.NEGOTIATING)
            session
        }, this, debugDiagnostics = true)
        try {
            controller.install(listing, listing.plugin.latestRelease, openAfterInstall = true)
            controller.awaitIdle()
            val timings = controller.state.value.loadTimings
            assertEquals(listOf(PluginLoadPhase.PRECHECK, PluginLoadPhase.FETCHING_MANIFEST,
                PluginLoadPhase.VERIFYING_PACKAGE, PluginLoadPhase.NEGOTIATING, PluginLoadPhase.RECORDING_INSTALLATION),
                timings.map { it.phase })
            timings.forEach { assertTrue(assertNotNull(it.finishedAtMs) >= it.startedAtMs) }
            assertTrue(timings.zipWithNext().all { (before, after) -> before.finishedAtMs == after.startedAtMs })
            assertNull(controller.state.value.phase)
            assertSame(session, controller.state.value.active?.session)
        } finally { controller.close(); fixture.transport.close() }
    }

    @Test
    fun installAndBrowseUsesOneVerifiedSessionAndSavesTheExplicitSourceChoice() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        fixture.initialize()
        val listing = fixture.repository.state.value.listings.single()
        val session = TestPluginSession(listing.plugin.descriptor(listing.plugin.latestRelease))
        var connections = 0
        val controller = AppPluginController(fixture.repository, PluginSessionConnector { _, _, _, _ -> connections++; session }, this)
        try {
            controller.install(listing, listing.plugin.latestRelease, openAfterInstall = true)
            controller.awaitIdle()
            assertEquals(1, connections)
            assertSame(session, controller.state.value.active?.session)
            assertEquals(listing.key, fixture.repository.state.value.preferredInstallations[listing.plugin.id])
            assertEquals(0, session.closeCalls)
            controller.closeActive()
            controller.awaitIdle()
            controller.closeActive()
            controller.awaitIdle()
            assertEquals(1, session.closeCalls)
        } finally { controller.close(); fixture.transport.close() }
    }

    @Test
    fun cancellationClosesALateAcquiredSessionWithoutRecordingOrPublishingIt() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        fixture.initialize()
        val listing = fixture.repository.state.value.listings.single()
        val session = TestPluginSession(listing.plugin.descriptor(listing.plugin.latestRelease))
        val gate = CompletableDeferred<Unit>()
        var started = false
        val controller = AppPluginController(fixture.repository, PluginSessionConnector { _, _, _, phase ->
            started = true
            withContext(NonCancellable) { gate.await() }
            phase(PluginLoadPhase.NEGOTIATING)
            session
        }, this)
        try {
            controller.install(listing, listing.plugin.latestRelease, openAfterInstall = true)
            runCurrent()
            assertTrue(started)
            controller.closeActive()
            gate.complete(Unit)
            controller.awaitIdle()
            assertEquals(1, session.closeCalls)
            assertTrue(fixture.repository.state.value.installed.isEmpty())
            assertNull(controller.state.value.active)
            assertNull(controller.state.value.completedInstallation)
            assertNull(controller.state.value.error)
            assertNull(controller.state.value.phase)
        } finally { gate.complete(Unit); controller.close(); fixture.transport.close() }
    }

    @Test
    fun descriptorMismatchClosesTheSessionAndPreservesTheOriginalFailureWhenCloseAlsoFails() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        fixture.initialize()
        val listing = fixture.repository.state.value.listings.single()
        val release = listing.plugin.latestRelease
        val session = TestPluginSession(listing.plugin.descriptor(release.copy(versionCode = 2)), IllegalStateException("Close failed."))
        val controller = AppPluginController(fixture.repository, PluginSessionConnector { _, _, _, _ -> session }, this)
        try {
            controller.install(listing, release)
            controller.awaitIdle()
            assertTrue(controller.state.value.error.orEmpty().contains("descriptor"))
            assertEquals(1, session.closeCalls)
            assertTrue(fixture.repository.state.value.installed.isEmpty())
            assertNull(controller.state.value.active)
            assertNull(controller.state.value.completedInstallation)
        } finally { controller.close(); fixture.transport.close() }
    }

    @Test
    fun reopeningStillAcquiresASignedSessionAndDoesNotReplaceTheInstallationTimestamp() = runTest {
        val fixture = RepositoryFixture(pluginCatalog())
        fixture.initialize()
        val listing = fixture.repository.state.value.listings.single()
        val release = listing.plugin.latestRelease
        fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
        val installation = fixture.repository.state.value.installed.single()
        val sessions = mutableListOf<TestPluginSession>()
        val controller = AppPluginController(fixture.repository, PluginSessionConnector { plugin, selected, _, _ ->
            TestPluginSession(plugin.descriptor(selected)).also { sessions += it }
        }, this)
        try {
            controller.open(installation)
            controller.awaitIdle()
            controller.closeActive()
            controller.awaitIdle()
            controller.open(installation)
            controller.awaitIdle()
            assertEquals(2, sessions.size)
            assertEquals(1, sessions.first().closeCalls)
            assertSame(sessions.last(), controller.state.value.active?.session)
            assertEquals(installation, fixture.repository.state.value.installed.single())
        } finally { controller.close(); fixture.transport.close() }
        assertEquals(1, sessions.last().closeCalls)
    }

    @Test
    fun cancellingASourcePreferenceCommitCannotStartPackageLoadingAfterBack() = runTest {
        val data = MemoryDataStore(EviloPreferences())
        var writeGate: CompletableDeferred<Unit>? = null
        val preferences = EviloPreferencesRepository(object : DataStore<EviloPreferences> by data {
            override suspend fun updateData(transform: suspend (EviloPreferences) -> EviloPreferences): EviloPreferences {
                writeGate?.await()
                return data.updateData(transform)
            }
        })
        val fixture = RepositoryFixture(pluginCatalog(), preferences = preferences)
        fixture.initialize()
        val listing = fixture.repository.state.value.listings.single()
        val release = listing.plugin.latestRelease
        fixture.repository.recordInstallation(listing, release, listing.plugin.descriptor(release))
        val installation = fixture.repository.state.value.installed.single()
        var connections = 0
        val controller = AppPluginController(fixture.repository, PluginSessionConnector { plugin, selected, _, _ ->
            connections++; TestPluginSession(plugin.descriptor(selected))
        }, this)
        val gate = CompletableDeferred<Unit>().also { writeGate = it }
        try {
            controller.open(installation, rememberSource = true)
            runCurrent()
            controller.closeActive()
            gate.complete(Unit)
            controller.awaitIdle()
            assertEquals(0, connections)
            assertNull(controller.state.value.active)
            assertNull(controller.state.value.error)
            assertEquals(installation.key, preferences.currentSettings().preferredInstallations[installation.plugin.id])
        } finally { gate.complete(Unit); controller.close(); fixture.transport.close() }
    }

    @Test
    fun replacementDoesNotAcceptAnOldPhaseOrOverwriteTheNewLoadFailure() = runTest {
        val old = catalogPlugin()
        val next = catalogPlugin().copy(id = "next.plugin")
        val fixture = RepositoryFixture(pluginCatalog(listOf(old, next)))
        fixture.initialize()
        fixture.repository.state.value.listings.forEach { fixture.repository.recordInstallation(it, it.plugin.latestRelease, it.plugin.descriptor(it.plugin.latestRelease)) }
        val oldSession = TestPluginSession(old.descriptor(old.latestRelease))
        val gate = CompletableDeferred<Unit>()
        val controller = AppPluginController(fixture.repository, PluginSessionConnector { plugin, _, _, phase ->
            if (plugin.id == old.id) {
                withContext(NonCancellable) { gate.await() }
                phase(PluginLoadPhase.NEGOTIATING)
                oldSession
            } else error("New load failed.")
        }, this)
        try {
            controller.open(fixture.repository.state.value.installed.first { it.plugin.id == old.id })
            runCurrent()
            controller.open(fixture.repository.state.value.installed.first { it.plugin.id == next.id })
            assertEquals(PluginLoadPhase.PRECHECK, controller.state.value.phase)
            gate.complete(Unit)
            controller.awaitIdle()
            assertEquals(1, oldSession.closeCalls)
            assertEquals("New load failed.", controller.state.value.error)
            assertNull(controller.state.value.phase)
            assertNull(controller.state.value.active)
            assertNotNull(controller.state.value.installationKey)
        } finally { gate.complete(Unit); controller.close(); fixture.transport.close() }
    }
}
