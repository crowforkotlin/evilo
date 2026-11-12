package crow.evilo.storage

import androidx.datastore.core.DataStore
import crow.evilo.model.MediaKind
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class EviloPreferencesRepository(private val dataStore: DataStore<EviloPreferences>) {
    private val locks = StoreLock()
    private val pluginLocks = mutableMapOf<String, Mutex>()
    val settings = dataStore.data.map {
        EviloSettings(it.selectedKind, it.showNsfw, it.mergeDuplicatePlugins, it.preferredInstallations)
    }.distinctUntilChanged()

    suspend fun currentSettings(): EviloSettings = settings.first()

    suspend fun selectKind(kind: MediaKind) {
        dataStore.updateData { it.copy(selectedKind = kind) }
    }

    suspend fun setShowNsfw(show: Boolean) {
        dataStore.updateData { it.copy(showNsfw = show) }
    }

    suspend fun setMergeDuplicatePlugins(merge: Boolean) {
        dataStore.updateData { it.copy(mergeDuplicatePlugins = merge) }
    }

    suspend fun setPreferredInstallation(pluginId: String, key: String) {
        require(validPreferredInstallations(mapOf(pluginId to key))) { "Invalid preferred plugin installation." }
        dataStore.updateData { current ->
            val values = current.preferredInstallations + (pluginId to key)
            require(validPreferredInstallations(values)) { "Too many preferred plugin installations." }
            current.copy(preferredInstallations = values)
        }
    }

    suspend fun pinDefaultInstallations(values: Map<String, String>, installedPluginIds: Set<String>? = null) {
        require(validPreferredInstallations(values)) { "Invalid default plugin installations." }
        dataStore.updateData { current ->
            val next = values + current.preferredInstallations.filterKeys { installedPluginIds == null || it in installedPluginIds }
            require(validPreferredInstallations(next)) { "Too many preferred plugin installations." }
            current.copy(preferredInstallations = next)
        }
    }

    suspend fun retainPreferredInstallations(pluginIds: Set<String>) {
        dataStore.updateData { current ->
            current.copy(preferredInstallations = current.preferredInstallations.filterKeys { it in pluginIds })
        }
    }

    fun pluginSession(pluginId: String): PluginStorageSession =
        PluginStorageSession(pluginId, dataStore, mutex(pluginId))

    suspend fun erasePluginData(pluginId: String) = mutex(pluginId).withLock {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            dataStore.updateData { current ->
                val previous = current.pluginValues[pluginId] ?: return@updateData current
                check(previous.revision < Long.MAX_VALUE) { "Plugin storage revision overflow." }
                current.copy(pluginValues = current.pluginValues + (pluginId to PluginValues(previous.revision + 1)))
            }
        }
        Unit
    }

    private fun mutex(pluginId: String): Mutex = locks.withLock {
        require(pluginId.isNotBlank()) { "A signed plugin ID is required for storage." }
        pluginLocks.getOrPut(pluginId) { Mutex() }
    }
}

/** Synchronous guest callbacks stage values; the enclosing host operation commits them asynchronously. */
class PluginStorageSession internal constructor(
    private val pluginId: String,
    private val dataStore: DataStore<EviloPreferences>,
    private val operations: Mutex,
) : KeyValueStore {
    private val namespace = "plugin.$pluginId"
    private val lock = StoreLock()
    private var active = false
    private var staged = MemoryKeyValueStore()

    suspend fun <T> transaction(block: suspend () -> T): T = operations.withLock {
        val initial = dataStore.data.first().pluginValues[pluginId] ?: PluginValues()
        lock.withLock {
            check(!active) { "A plugin storage transaction is already active." }
            staged = MemoryKeyValueStore(mapOf(namespace to initial.entries))
            active = true
        }
        try {
            val result = block()
            currentCoroutineContext().ensureActive()
            val entries = lock.withLock { staged.snapshot(namespace) }
            if (entries != initial.entries) {
                // Once commit starts, retain the operation mutex until DataStore acknowledges the write.
                withContext(NonCancellable) {
                    dataStore.updateData { current ->
                        val previous = current.pluginValues[pluginId] ?: PluginValues()
                        check(previous == initial) { "Plugin data changed in another app instance; retry the operation." }
                        check(previous.revision < Long.MAX_VALUE) { "Plugin storage revision overflow." }
                        current.copy(pluginValues = current.pluginValues + (pluginId to PluginValues(previous.revision + 1, entries)))
                    }
                }
            }
            result
        } finally {
            lock.withLock {
                active = false
                staged = MemoryKeyValueStore()
            }
        }
    }

    // Runtime disposal has no guest KV calls and must still run when DataStore cannot be read.
    internal suspend fun dispose(block: suspend () -> Unit) = operations.withLock { block() }

    override fun read(namespace: String, key: String): String? = access(namespace) { staged.read(namespace, key) }
    override fun write(namespace: String, key: String, value: String) = access(namespace) { staged.write(namespace, key, value) }
    override fun remove(namespace: String, key: String): Boolean = access(namespace) { staged.remove(namespace, key) }
    override fun keys(namespace: String, prefix: String): List<String> = access(namespace) { staged.keys(namespace, prefix) }
    override fun clear(namespace: String) = access(namespace) { staged.clear(namespace) }
    override fun updateNamespace(namespace: String, update: (Map<String, String>) -> Map<String, String>) =
        access(namespace) { staged.updateNamespace(namespace, update) }

    override fun edit(changes: List<StoreEdit>) = lock.withLock {
        changes.forEach { approve(it.namespace) }
        staged.edit(changes)
    }

    private fun <T> access(namespace: String, block: () -> T): T = lock.withLock {
        approve(namespace)
        block()
    }

    private fun approve(namespace: String) {
        check(active) { "Plugin storage is only available during a host operation." }
        require(namespace == this.namespace) { "A plugin cannot access another storage namespace." }
    }
}
