package crow.evilo.storage

import androidx.room3.Dao
import androidx.room3.Entity
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.ByteString.Companion.toByteString
import kotlin.time.Clock

/**
 * Host-only storage of exact signed manifest bytes, addressed by their lowercase SHA-256 digest.
 * The caller must verify the signature and accept the loaded session before [writeVerified]; this
 * store checks the digest and byte bounds, not the signature. It is not a guest storage namespace.
 */
interface PluginManifestCache {
    suspend fun read(manifestSha256: String): ByteArray?
    suspend fun writeVerified(manifestSha256: String, bytes: ByteArray)
    suspend fun remove(manifestSha256: String)

    companion object {
        const val MAX_MANIFEST_BYTES = 2 * 1024 * 1024
        const val MAX_CACHE_BYTES = 16 * 1024 * 1024
        const val MAX_ENTRIES = 256
    }
}

/** Preview/test implementation; application launchers supply [RoomPluginManifestCache]. */
class MemoryPluginManifestCache(
    private val maxManifestBytes: Int = PluginManifestCache.MAX_MANIFEST_BYTES,
    private val maxCacheBytes: Int = PluginManifestCache.MAX_CACHE_BYTES,
    private val maxEntries: Int = PluginManifestCache.MAX_ENTRIES,
) : PluginManifestCache {
    init {
        require(maxManifestBytes in 1..PluginManifestCache.MAX_MANIFEST_BYTES)
        require(maxCacheBytes in 1..PluginManifestCache.MAX_CACHE_BYTES)
        require(maxEntries in 1..PluginManifestCache.MAX_ENTRIES)
    }

    private val operations = Mutex()
    // Insertion order is access order: every successful read/write moves its entry to the end.
    private val entries = linkedMapOf<String, ByteArray>()
    private var sizeBytes = 0

    override suspend fun read(manifestSha256: String): ByteArray? = operations.withLock {
        val bytes = entries.remove(manifestSha256) ?: return@withLock null
        entries[manifestSha256] = bytes
        bytes.copyOf()
    }

    override suspend fun writeVerified(manifestSha256: String, bytes: ByteArray) {
        val snapshot = verifiedSnapshot(manifestSha256, bytes, minOf(maxManifestBytes, maxCacheBytes))
        operations.withLock {
            entries.remove(manifestSha256)?.let { sizeBytes -= it.size }
            entries[manifestSha256] = snapshot
            sizeBytes += snapshot.size
            while (sizeBytes > maxCacheBytes || entries.size > maxEntries) {
                val oldest = entries.keys.first()
                sizeBytes -= entries.remove(oldest)!!.size
            }
        }
    }

    override suspend fun remove(manifestSha256: String) = operations.withLock {
        entries.remove(manifestSha256)?.let { sizeBytes -= it.size }
        Unit
    }
}

@Entity(tableName = "plugin_manifest_cache")
data class PluginManifestCacheEntity(
    @PrimaryKey val manifestSha256: String,
    val bytes: ByteArray,
    val sizeBytes: Int,
    val lastUsedEpochMillis: Long,
)

@Dao
abstract class RoomPluginManifestCache : PluginManifestCache {
    @Query("SELECT * FROM plugin_manifest_cache WHERE manifestSha256 = :manifestSha256")
    protected abstract suspend fun find(manifestSha256: String): PluginManifestCacheEntity?

    @Upsert
    protected abstract suspend fun save(entity: PluginManifestCacheEntity)

    @Query("UPDATE plugin_manifest_cache SET lastUsedEpochMillis = :lastUsedEpochMillis WHERE manifestSha256 = :manifestSha256")
    protected abstract suspend fun touch(manifestSha256: String, lastUsedEpochMillis: Long)

    @Query("SELECT MAX(lastUsedEpochMillis) FROM plugin_manifest_cache")
    protected abstract suspend fun latestUse(): Long?

    // Count actual BLOB bytes so a damaged sizeBytes column cannot bypass the cache byte limit.
    @Query("SELECT COALESCE(SUM(length(bytes)), 0) FROM plugin_manifest_cache")
    protected abstract suspend fun totalSizeBytes(): Long

    @Query("SELECT COUNT(*) FROM plugin_manifest_cache")
    protected abstract suspend fun entryCount(): Int

    @Query("DELETE FROM plugin_manifest_cache WHERE manifestSha256 = (SELECT manifestSha256 FROM plugin_manifest_cache ORDER BY lastUsedEpochMillis, manifestSha256 LIMIT 1)")
    protected abstract suspend fun evictOldest()

    @Query("DELETE FROM plugin_manifest_cache WHERE manifestSha256 = :manifestSha256")
    abstract override suspend fun remove(manifestSha256: String)

    @Transaction
    override suspend fun read(manifestSha256: String): ByteArray? {
        if (!isManifestSha256(manifestSha256)) return null
        val entry = find(manifestSha256) ?: return null
        if (entry.manifestSha256 != manifestSha256 || entry.sizeBytes != entry.bytes.size ||
            !matchesManifest(manifestSha256, entry.bytes, PluginManifestCache.MAX_MANIFEST_BYTES)) {
            remove(manifestSha256)
            return null
        }
        touch(manifestSha256, nextUse())
        return entry.bytes.copyOf()
    }

    @Transaction
    override suspend fun writeVerified(manifestSha256: String, bytes: ByteArray) {
        val snapshot = verifiedSnapshot(manifestSha256, bytes, PluginManifestCache.MAX_MANIFEST_BYTES)
        save(PluginManifestCacheEntity(manifestSha256, snapshot, snapshot.size, nextUse()))
        while (totalSizeBytes() > PluginManifestCache.MAX_CACHE_BYTES || entryCount() > PluginManifestCache.MAX_ENTRIES) {
            evictOldest()
        }
    }

    private suspend fun nextUse(): Long {
        val now = Clock.System.now().toEpochMilliseconds()
        val latest = latestUse() ?: return now
        // Break same-millisecond ties so a just-read entry really is the most recently used.
        return if (latest >= now && latest < Long.MAX_VALUE) latest + 1 else now
    }
}

private fun isManifestSha256(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

private fun matchesManifest(manifestSha256: String, bytes: ByteArray, maxBytes: Int): Boolean =
    bytes.size in 1..maxBytes && bytes.toByteString().sha256().hex() == manifestSha256

private fun verifiedSnapshot(manifestSha256: String, bytes: ByteArray, maxBytes: Int): ByteArray {
    require(isManifestSha256(manifestSha256)) { "Manifest cache key must be a lowercase SHA-256 digest." }
    require(bytes.size in 1..maxBytes) { "Signed manifest bytes exceed the cache bounds or are empty." }
    val snapshot = bytes.copyOf()
    require(matchesManifest(manifestSha256, snapshot, maxBytes)) { "Signed manifest bytes do not match their SHA-256 cache key." }
    return snapshot
}
