package crow.evilo.storage

import androidx.room3.ConstructedBy
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.Transaction
import androidx.room3.Upsert
import crow.evilo.plugin.api.PluginJson
import crow.evilo.plugin.catalog.CatalogSubscription
import crow.evilo.plugin.catalog.InstalledPlugin

@Entity(tableName = "catalog_sources")
data class CatalogSourceEntity(
    @PrimaryKey val id: String,
    val url: String,
    val allowHttp: Boolean,
    val enabled: Boolean,
)

@Entity(tableName = "catalog_cache")
data class CatalogCacheEntity(@PrimaryKey val sourceId: String, val catalogJson: String)

@Entity(tableName = "plugin_installations", primaryKeys = ["sourceId", "pluginId"], indices = [Index(value = ["pluginId"])])
data class PluginInstallationEntity(val sourceId: String, val pluginId: String, val versionCode: String, val installationJson: String)

@Entity(tableName = "plugin_update_floors")
data class PluginUpdateFloorEntity(@PrimaryKey val key: String, val versionCode: String)

@Entity(tableName = "plugin_data_deletions")
data class PluginDataDeletionEntity(@PrimaryKey val pluginId: String)

@Dao
abstract class RoomPluginCatalogStore : PluginCatalogStore {
    @Query("SELECT * FROM catalog_sources ORDER BY id")
    abstract suspend fun sources(): List<CatalogSourceEntity>

    @Query("SELECT * FROM catalog_cache")
    abstract suspend fun caches(): List<CatalogCacheEntity>

    @Query("SELECT * FROM plugin_installations ORDER BY sourceId, pluginId")
    abstract suspend fun installations(): List<PluginInstallationEntity>

    @Query("SELECT * FROM plugin_update_floors")
    abstract suspend fun floors(): List<PluginUpdateFloorEntity>

    @Query("SELECT * FROM plugin_data_deletions")
    abstract suspend fun dataDeletions(): List<PluginDataDeletionEntity>

    @Query("DELETE FROM catalog_sources")
    abstract suspend fun clearSources()

    @Query("DELETE FROM catalog_cache WHERE sourceId IN (:ids)")
    abstract suspend fun removeCaches(ids: List<String>)

    @Query("DELETE FROM plugin_installations")
    abstract suspend fun clearInstallations()

    @Query("DELETE FROM plugin_update_floors")
    abstract suspend fun clearFloors()

    @Query("DELETE FROM plugin_data_deletions WHERE pluginId IN (:ids)")
    abstract suspend fun finishDataDeletions(ids: List<String>)

    @Upsert
    abstract suspend fun saveSources(values: List<CatalogSourceEntity>)

    @Upsert
    abstract suspend fun saveCaches(values: List<CatalogCacheEntity>)

    @Upsert
    abstract suspend fun saveInstallations(values: List<PluginInstallationEntity>)

    @Upsert
    abstract suspend fun saveFloors(values: List<PluginUpdateFloorEntity>)

    @Upsert
    abstract suspend fun saveDataDeletions(values: List<PluginDataDeletionEntity>)

    @Transaction
    override suspend fun read(): CatalogRecords = CatalogRecords(
        subscriptions = sources().map { CatalogSubscription(it.id, it.url, it.allowHttp, it.enabled) },
        cachedCatalogs = caches().associate { it.sourceId to it.catalogJson },
        installed = installations().map { entity ->
            PluginJson.decodeFromString<InstalledPlugin>(entity.installationJson).also {
                require(it.sourceId == entity.sourceId && it.plugin.id == entity.pluginId && it.release.versionCode.toString() == entity.versionCode) {
                    "Stored plugin installation columns do not match their metadata."
                }
            }
        },
        updateFloors = floors().associate { it.key to it.versionCode.toLong() },
        pendingDataDeletes = dataDeletions().mapTo(mutableSetOf()) { it.pluginId },
    )

    @Transaction
    override suspend fun write(change: CatalogWrite) {
        change.subscriptions?.let { values ->
            clearSources()
            saveSources(values.map { CatalogSourceEntity(it.id, it.url, it.allowHttp, it.enabled) })
        }
        if (change.removeCachedCatalogs.isNotEmpty()) removeCaches(change.removeCachedCatalogs.toList())
        if (change.cachedCatalogs.isNotEmpty()) saveCaches(change.cachedCatalogs.map { CatalogCacheEntity(it.key, it.value) })
        change.installed?.let { values ->
            clearInstallations()
            // SQLite's Web driver transports numbers as doubles; preserve catalog Long values as decimal text.
            saveInstallations(values.map { PluginInstallationEntity(it.sourceId, it.plugin.id, it.release.versionCode.toString(), PluginJson.encodeToString(it)) })
        }
        change.updateFloors?.let { values ->
            clearFloors()
            saveFloors(values.map { PluginUpdateFloorEntity(it.key, it.value.toString()) })
        }
        if (change.pendingDataDeletes.isNotEmpty()) saveDataDeletions(change.pendingDataDeletes.map { PluginDataDeletionEntity(it) })
        if (change.completedDataDeletes.isNotEmpty()) finishDataDeletions(change.completedDataDeletes.toList())
    }
}

@Database(
    entities = [CatalogSourceEntity::class, CatalogCacheEntity::class, PluginInstallationEntity::class,
        PluginUpdateFloorEntity::class, PluginDataDeletionEntity::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(EviloDatabaseConstructor::class)
abstract class EviloDatabase : RoomDatabase() {
    abstract fun catalogs(): RoomPluginCatalogStore
}

@Suppress("KotlinNoActualForExpect")
expect object EviloDatabaseConstructor : RoomDatabaseConstructor<EviloDatabase> {
    override fun initialize(): EviloDatabase
}
