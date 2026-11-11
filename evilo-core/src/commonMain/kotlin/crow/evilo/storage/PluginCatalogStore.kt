package crow.evilo.storage

import crow.evilo.plugin.catalog.CatalogSubscription
import crow.evilo.plugin.catalog.InstalledPlugin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class CatalogRecords(
    val subscriptions: List<CatalogSubscription> = emptyList(),
    val cachedCatalogs: Map<String, String> = emptyMap(),
    val installed: List<InstalledPlugin> = emptyList(),
    val updateFloors: Map<String, Long> = emptyMap(),
    val pendingDataDeletes: Set<String> = emptySet(),
)

data class CatalogWrite(
    val subscriptions: List<CatalogSubscription>? = null,
    val cachedCatalogs: Map<String, String> = emptyMap(),
    val removeCachedCatalogs: Set<String> = emptySet(),
    val installed: List<InstalledPlugin>? = null,
    val updateFloors: Map<String, Long>? = null,
    val pendingDataDeletes: Set<String> = emptySet(),
    val completedDataDeletes: Set<String> = emptySet(),
)

interface PluginCatalogStore {
    suspend fun read(): CatalogRecords
    suspend fun write(change: CatalogWrite)
}

/** Preview/test implementation; application launchers supply the Room DAO. */
class MemoryPluginCatalogStore : PluginCatalogStore {
    private val operations = Mutex()
    private var records = CatalogRecords()

    override suspend fun read(): CatalogRecords = operations.withLock { records }

    override suspend fun write(change: CatalogWrite) = operations.withLock {
        records = records.copy(
            subscriptions = change.subscriptions?.toList() ?: records.subscriptions,
            cachedCatalogs = (records.cachedCatalogs - change.removeCachedCatalogs) + change.cachedCatalogs,
            installed = change.installed?.toList() ?: records.installed,
            updateFloors = change.updateFloors?.toMap() ?: records.updateFloors,
            pendingDataDeletes = (records.pendingDataDeletes + change.pendingDataDeletes) - change.completedDataDeletes,
        )
    }
}
