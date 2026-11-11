package crow.evilo.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MemoryKeyValueStoreTest {
    @Test
    fun rejectedNamespaceUpdatesDoNotReplaceTheirValues() {
        val store = MemoryKeyValueStore()
        store.write("one", "setting", "old")
        assertFailsWith<IllegalArgumentException> {
            store.updateNamespace("one") { current -> require(current.isEmpty()); current + ("second" to "new") }
        }
        assertEquals(mapOf("setting" to "old"), store.snapshot("one"))
    }

    @Test
    fun editsRemoveOnlyTheirSelectedKeysAndNamespaces() {
        val store = MemoryKeyValueStore()
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
        val store = MemoryKeyValueStore()
        store.edit(listOf(StoreEdit("one", "setting.z", "z"), StoreEdit("one", "other", "o"), StoreEdit("one", "setting.a", "a")))
        assertEquals(listOf("setting.a", "setting.z"), store.keys("one", "setting."))
    }
}
