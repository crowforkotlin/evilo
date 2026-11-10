package crow.evilo.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersistentKeyValueStoreTest {
    @Test
    fun namespacesSurviveReopeningAndDoNotShareKeys() {
        val persistence = MemorySnapshotPersistence()
        val store = PersistentKeyValueStore(persistence)
        store.write("plugin.one", "setting", "one")
        store.write("plugin.two", "setting", "two")
        store.write("host.plugins", "setting", "host")

        val reopened = PersistentKeyValueStore(persistence)
        assertEquals("one", reopened.read("plugin.one", "setting"))
        assertEquals("two", reopened.read("plugin.two", "setting"))
        assertEquals("host", reopened.read("host.plugins", "setting"))
        assertNull(reopened.read("plugin.unknown", "setting"))
    }

    @Test
    fun failedPersistenceDoesNotPublishAPartialBatch() {
        val persistence = FailingPersistence()
        val store = PersistentKeyValueStore(persistence)
        store.write("host.plugins", "installed", "old")
        val previous = persistence.snapshot
        persistence.failWrites = true

        assertFailsWith<IllegalStateException> {
            store.edit(listOf(StoreEdit("host.plugins", "installed", "new"), StoreEdit("plugin.one", "setting", "new")))
        }
        assertEquals("old", store.read("host.plugins", "installed"))
        assertNull(store.read("plugin.one", "setting"))
        assertEquals(previous, persistence.snapshot)
    }

    @Test
    fun rejectedNamespaceUpdatePreservesPreviousValues() {
        val store = PersistentKeyValueStore(MemorySnapshotPersistence())
        store.write("plugin.one", "setting", "old")
        assertFailsWith<IllegalArgumentException> {
            store.updateNamespace("plugin.one") { current ->
                require(current.size < 1) { "Quota exceeded." }
                current + ("second" to "new")
            }
        }
        assertEquals(listOf("setting"), store.keys("plugin.one"))
        assertEquals("old", store.read("plugin.one", "setting"))
    }

    @Test
    fun editsRemoveOnlyTheirSelectedKeysAndNamespaces() {
        val store = PersistentKeyValueStore(MemorySnapshotPersistence())
        store.edit(listOf(StoreEdit("one", "a", "1"), StoreEdit("one", "b", "2"), StoreEdit("two", "a", "3")))
        store.edit(listOf(StoreEdit("one", "a"), StoreEdit("two")))
        assertEquals(listOf("b"), store.keys("one"))
        assertTrue(store.keys("two").isEmpty())
        assertFalse(store.remove("one", "a"))
        assertTrue(store.remove("one", "b"))
        assertTrue(store.keys("one").isEmpty())
    }

    @Test
    fun keyListsAreSortedAndPrefixFiltered() {
        val store = PersistentKeyValueStore(MemorySnapshotPersistence())
        store.edit(listOf(StoreEdit("one", "setting.z", "z"), StoreEdit("one", "other", "o"), StoreEdit("one", "setting.a", "a")))
        assertEquals(listOf("setting.a", "setting.z"), store.keys("one", "setting."))
    }

    private class FailingPersistence : SnapshotPersistence {
        var snapshot: String? = null
        var failWrites = false
        override fun read(): String? = snapshot
        override fun write(snapshot: String) {
            check(!failWrites) { "Persistence is unavailable." }
            this.snapshot = snapshot
        }
    }
}
