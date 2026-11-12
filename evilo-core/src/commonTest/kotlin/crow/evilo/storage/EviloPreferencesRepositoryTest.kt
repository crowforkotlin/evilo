@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class, kotlinx.serialization.ExperimentalSerializationApi::class)

package crow.evilo.storage

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import crow.evilo.model.MediaKind
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.protobuf.ProtoNumber
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EviloPreferencesRepositoryTest {
    @Test
    fun settingsAndPluginValuesRoundTripThroughProtobuf() = runTest {
        val dataStore = MemoryDataStore(EviloPreferences())
        val repository = EviloPreferencesRepository(dataStore)
        repository.selectKind(MediaKind.NOVEL)
        repository.setShowNsfw(true)
        val session = repository.pluginSession("one")
        session.transaction { session.write("plugin.one", "setting", "{\"enabled\":true}") }
        val encoded = EviloPreferencesCodec.encode(dataStore.data.first())
        val reopened = EviloPreferencesRepository(MemoryDataStore(EviloPreferencesCodec.decode(encoded)))
        assertEquals(EviloSettings(MediaKind.NOVEL, true), reopened.currentSettings())
        val restored = reopened.pluginSession("one")
        restored.transaction { assertEquals("{\"enabled\":true}", restored.read("plugin.one", "setting")) }
    }

    @Test
    fun callbacksRequireAnActiveOperationAndCannotChooseAnotherNamespace() = runTest {
        val repository = EviloPreferencesRepository(MemoryDataStore(EviloPreferences()))
        val one = repository.pluginSession("one")
        assertFailsWith<IllegalStateException> { one.read("plugin.one", "setting") }
        one.transaction {
            one.write("plugin.one", "setting", "one")
            assertFailsWith<IllegalArgumentException> { one.read("plugin.two", "setting") }
            assertFailsWith<IllegalArgumentException> { one.write("host.plugins", "setting", "host") }
        }
        val two = repository.pluginSession("two")
        two.transaction { assertNull(two.read("plugin.two", "setting")) }
        assertFailsWith<IllegalStateException> { one.keys("plugin.one") }
    }

    @Test
    fun failedOperationsAndFailedWritesDiscardStagedValues() = runTest {
        val dataStore = ControlledDataStore()
        val repository = EviloPreferencesRepository(dataStore)
        val session = repository.pluginSession("one")
        session.transaction { session.write("plugin.one", "setting", "old") }
        assertFailsWith<IllegalStateException> {
            session.transaction { session.write("plugin.one", "setting", "new"); error("Plugin operation failed.") }
        }
        dataStore.failWrites = true
        assertFailsWith<IllegalStateException> { session.transaction { session.write("plugin.one", "setting", "new") } }
        dataStore.failWrites = false
        session.transaction { assertEquals("old", session.read("plugin.one", "setting")) }
        assertEquals(1L, dataStore.data.first().pluginValues.getValue("one").revision)
    }

    @Test
    fun cancelledOperationsDoNotCommitAfterNonCancellableGuestCleanup() = runTest {
        val dataStore = MemoryDataStore(EviloPreferences())
        val session = EviloPreferencesRepository(dataStore).pluginSession("one")
        val completed = CompletableDeferred<Unit>()
        val operation = launch {
            session.transaction {
                session.write("plugin.one", "setting", "cancelled")
                withContext(NonCancellable) { completed.await() }
            }
        }
        runCurrent()
        operation.cancel()
        completed.complete(Unit)
        operation.join()
        assertNull(dataStore.data.first().pluginValues["one"])
    }

    @Test
    fun cancellationDuringCommitKeepsTheMutexUntilDataStoreAcknowledgesTheWrite() = runTest {
        val gate = CompletableDeferred<Unit>()
        val dataStore = ControlledDataStore().apply { writeGate = gate }
        val repository = EviloPreferencesRepository(dataStore)
        val one = repository.pluginSession("one")
        val two = repository.pluginSession("one")
        val first = launch { one.transaction { one.write("plugin.one", "setting", "committed") } }
        runCurrent()
        first.cancel()
        var secondStarted = false
        val second = launch {
            two.transaction { secondStarted = true; assertEquals("committed", two.read("plugin.one", "setting")) }
        }
        runCurrent()
        assertFalse(secondStarted)
        gate.complete(Unit)
        first.join()
        second.join()
        assertTrue(secondStarted)
        assertEquals(1L, dataStore.data.first().pluginValues.getValue("one").revision)
    }

    @Test
    fun sessionsForTheSameSignedIdSerializeTheirOperations() = runTest {
        val repository = EviloPreferencesRepository(MemoryDataStore(EviloPreferences()))
        val one = repository.pluginSession("one")
        val two = repository.pluginSession("one")
        val completed = CompletableDeferred<Unit>()
        var secondStarted = false
        val first = launch {
            one.transaction { one.write("plugin.one", "setting", "committed"); completed.await() }
        }
        runCurrent()
        val second = launch {
            two.transaction { secondStarted = true; assertEquals("committed", two.read("plugin.one", "setting")) }
        }
        runCurrent()
        assertFalse(secondStarted)
        completed.complete(Unit)
        first.join()
        second.join()
        assertTrue(secondStarted)
    }

    @Test
    fun aSecondAppInstanceCannotReplaceANewerRevision() = runTest {
        val dataStore = MemoryDataStore(EviloPreferences())
        val first = EviloPreferencesRepository(dataStore).pluginSession("one")
        val second = EviloPreferencesRepository(dataStore).pluginSession("one")
        assertFailsWith<IllegalStateException> {
            first.transaction {
                first.write("plugin.one", "setting", "stale")
                second.transaction { second.write("plugin.one", "setting", "newer") }
            }
        }
        second.transaction { assertEquals("newer", second.read("plugin.one", "setting")) }
    }

    @Test
    fun erasingAndRestoringIdenticalValuesDoesNotReuseARevision() = runTest {
        val dataStore = MemoryDataStore(EviloPreferences(pluginValues = mapOf("one" to PluginValues(1, mapOf("setting" to "old")))))
        val first = EviloPreferencesRepository(dataStore).pluginSession("one")
        val otherInstance = EviloPreferencesRepository(dataStore)
        val second = otherInstance.pluginSession("one")
        assertFailsWith<IllegalStateException> {
            first.transaction {
                first.write("plugin.one", "setting", "stale")
                otherInstance.erasePluginData("one")
                second.transaction { second.write("plugin.one", "setting", "old") }
            }
        }
        assertEquals(3L, dataStore.data.first().pluginValues.getValue("one").revision)
        second.transaction { assertEquals("old", second.read("plugin.one", "setting")) }
    }

    @Test
    fun runtimeDisposalDoesNotReadUnavailablePreferences() = runTest {
        val dataStore = ControlledDataStore().apply { failReads = true }
        val session = EviloPreferencesRepository(dataStore).pluginSession("one")
        var disposed = false
        assertFailsWith<IllegalStateException> { session.transaction {} }
        session.dispose { disposed = true }
        assertTrue(disposed)
    }

    @Test
    fun invalidPreferencesDoNotSilentlyResetToDefaults() {
        assertFailsWith<CorruptionException> { EviloPreferencesCodec.decode(byteArrayOf(0xff.toByte())) }
        assertFailsWith<IllegalArgumentException> { EviloPreferencesCodec.encode(EviloPreferences(schemaVersion = 2)) }
    }

    @Test
    fun legacyPreferenceFieldsKeepTheirValuesAndNewSettingsHaveDeterministicDefaults() {
        val legacy = LegacyPreferences(selectedKind = MediaKind.NOVEL, showNsfw = true,
            pluginValues = mapOf("one" to PluginValues(4, mapOf("setting" to "retained"))))
        val restored = EviloPreferencesCodec.decode(ProtoBuf { encodeDefaults = true }.encodeToByteArray(LegacyPreferences.serializer(), legacy))
        assertEquals(MediaKind.NOVEL, restored.selectedKind)
        assertTrue(restored.showNsfw)
        assertEquals(legacy.pluginValues, restored.pluginValues)
        assertTrue(restored.mergeDuplicatePlugins)
        assertTrue(restored.preferredInstallations.isEmpty())
    }

    @Test
    fun duplicateDisplayAndPreferredInstallationsRoundTripWithoutChangingPluginValues() = runTest {
        val initial = EviloPreferences(pluginValues = mapOf("one" to PluginValues(2, mapOf("setting" to "retained"))))
        val data = MemoryDataStore(initial)
        val repository = EviloPreferencesRepository(data)
        repository.setMergeDuplicatePlugins(false)
        repository.setPreferredInstallation("one", "source.one:one")
        val restored = EviloPreferencesCodec.decode(EviloPreferencesCodec.encode(data.data.first()))
        assertFalse(restored.mergeDuplicatePlugins)
        assertEquals(mapOf("one" to "source.one:one"), restored.preferredInstallations)
        assertEquals(initial.pluginValues, restored.pluginValues)
        val longId = "a".repeat(128)
        repository.setPreferredInstallation(longId, "source:$longId")
        assertEquals("source:$longId", repository.currentSettings().preferredInstallations[longId])
    }

    @Test
    fun pinningKeepsAnExplicitSourceAndPruningRemovesOnlyIdsWithoutInstallations() = runTest {
        val values = mapOf("one" to PluginValues(1, mapOf("setting" to "retained")))
        val data = MemoryDataStore(EviloPreferences(pluginValues = values,
            preferredInstallations = mapOf("one" to "removed:one", "orphan" to "removed:orphan")))
        val repository = EviloPreferencesRepository(data)
        repository.pinDefaultInstallations(mapOf("one" to "new:one", "two" to "new:two"), setOf("one", "two"))
        assertEquals(mapOf("one" to "removed:one", "two" to "new:two"), repository.currentSettings().preferredInstallations)
        repository.retainPreferredInstallations(setOf("one"))
        assertEquals(mapOf("one" to "removed:one"), repository.currentSettings().preferredInstallations)
        assertEquals(values, data.data.first().pluginValues)
    }

    @Test
    fun failedSettingsWritesDoNotPublishOptimisticValues() = runTest {
        val data = ControlledDataStore().apply { failWrites = true }
        val repository = EviloPreferencesRepository(data)
        assertFailsWith<IllegalStateException> { repository.setMergeDuplicatePlugins(false) }
        assertFailsWith<IllegalStateException> { repository.setPreferredInstallation("one", "source:one") }
        assertEquals(EviloSettings(), repository.currentSettings())
    }

    @Test
    fun preferenceKeysAreBoundedAndCannotPointAtAnotherPlugin() = runTest {
        val repository = EviloPreferencesRepository(MemoryDataStore(EviloPreferences()))
        assertFailsWith<IllegalArgumentException> { repository.setPreferredInstallation("one", "source:two") }
        assertFailsWith<IllegalArgumentException> { repository.setPreferredInstallation("bad:id", "source:bad:id") }
        assertFailsWith<IllegalArgumentException> { repository.setPreferredInstallation("one", ":one") }
        for (key in listOf("   :one", "source name:one", "source/other:one", "source\n:one", "source:extra:one"))
            assertFailsWith<IllegalArgumentException> { repository.setPreferredInstallation("one", key) }
        repository.setPreferredInstallation("one", "custom.123:one")
        repository.setPreferredInstallation("one", "bundled:one")
        repository.setPreferredInstallation("one", "official:one")
        repository.retainPreferredInstallations(emptySet())
        val defaults = (0 until 256).associate { "plugin.$it" to "source:plugin.$it" }
        repository.pinDefaultInstallations(defaults)
        assertFailsWith<IllegalArgumentException> { repository.setPreferredInstallation("extra", "source:extra") }
        assertEquals(256, repository.currentSettings().preferredInstallations.size)
    }

    @Serializable
    private data class LegacyPreferences(
        @ProtoNumber(1) val schemaVersion: Int = 1,
        @ProtoNumber(2) val selectedKind: MediaKind = MediaKind.MANGA,
        @ProtoNumber(3) val showNsfw: Boolean = false,
        @ProtoNumber(4) val pluginValues: Map<String, PluginValues> = emptyMap(),
    )

    private class ControlledDataStore : DataStore<EviloPreferences> {
        private val delegate = MemoryDataStore(EviloPreferences())
        var failReads = false
        var failWrites = false
        var writeGate: CompletableDeferred<Unit>? = null
        override val data: Flow<EviloPreferences> get() = flow {
            check(!failReads) { "Preferences are unavailable." }
            emitAll(delegate.data)
        }
        override suspend fun updateData(transform: suspend (EviloPreferences) -> EviloPreferences): EviloPreferences {
            check(!failWrites) { "Preferences cannot be written." }
            writeGate?.await()
            return delegate.updateData(transform)
        }
    }
}
