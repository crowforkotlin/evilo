package crow.evilo.plugin.runtime

import crow.evilo.plugin.api.HOST_STORAGE_CAPABILITY
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.storage.MemorySnapshotPersistence
import crow.evilo.storage.PersistentKeyValueStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class PluginHostStorageServiceTest {
    private val host = HostDescriptor("1.0.0", 1, capabilities = setOf(HOST_STORAGE_CAPABILITY),
        storageQuotaBytes = 6, storageValueLimitBytes = 3, storageEntryLimit = 2)

    @Test
    fun pluginsCannotReadHostValuesOrAnotherPluginsNamespace() {
        val store = PersistentKeyValueStore(MemorySnapshotPersistence())
        store.write("host.plugins", "setting", "host")
        val one = PluginHostStorageService("one", host, store, true)
        val two = PluginHostStorageService("two", host, store, true)
        one.writeValue("a", "12")
        assertNull(two.readValue("a"))
        assertNull(one.readValue("setting"))
        assertEquals("host", store.read("host.plugins", "setting"))
    }

    @Test
    fun missingConsentBlocksEveryStorageOperation() {
        val service = PluginHostStorageService("one", host, PersistentKeyValueStore(MemorySnapshotPersistence()), false)
        assertFailsWith<IllegalStateException> { service.readValue("a") }
        assertFailsWith<IllegalStateException> { service.writeValue("a", "1") }
        assertFailsWith<IllegalStateException> { service.removeValue("a") }
        assertFailsWith<IllegalStateException> { service.listKeys("") }
    }

    @Test
    fun rejectedQuotaChangesDoNotReplaceExistingValues() {
        val service = PluginHostStorageService("one", host, PersistentKeyValueStore(MemorySnapshotPersistence()), true)
        service.writeValue("a", "12")
        service.writeValue("b", "12")
        assertFailsWith<IllegalArgumentException> { service.writeValue("a", "123") }
        assertFailsWith<IllegalArgumentException> { service.writeValue("c", "1") }
        assertFailsWith<IllegalArgumentException> { service.writeValue("a", "1234") }
        assertEquals("12", service.readValue("a"))
        assertEquals("12", service.readValue("b"))
        assertNull(service.readValue("c"))
    }

    @Test
    fun controlCharactersAreRejectedInKeysAndPrefixes() {
        val service = PluginHostStorageService("one", host, PersistentKeyValueStore(MemorySnapshotPersistence()), true)
        assertFailsWith<IllegalArgumentException> { service.readValue("a\u007f") }
        assertFailsWith<IllegalArgumentException> { service.listKeys("a\u007f") }
    }
}
