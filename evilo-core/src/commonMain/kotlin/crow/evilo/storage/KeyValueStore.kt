package crow.evilo.storage

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

interface KeyValueStore {
    fun read(namespace: String, key: String): String?
    fun write(namespace: String, key: String, value: String)
    fun remove(namespace: String, key: String): Boolean
    fun keys(namespace: String, prefix: String = ""): List<String>
    fun clear(namespace: String)
    fun updateNamespace(namespace: String, update: (Map<String, String>) -> Map<String, String>)
    fun edit(changes: List<StoreEdit>)
}

data class StoreEdit(val namespace: String, val key: String? = null, val value: String? = null)

interface SnapshotPersistence {
    fun read(): String?
    fun write(snapshot: String)
}

expect class StoreLock() {
    fun <T> withLock(block: () -> T): T
}

@Serializable
private data class StoreSnapshot(
    val schemaVersion: Int = 1,
    val values: Map<String, Map<String, String>> = emptyMap(),
)

class PersistentKeyValueStore(private val persistence: SnapshotPersistence) : KeyValueStore {
    private val lock = StoreLock()
    private val json = Json { ignoreUnknownKeys = true }
    private var values = persistence.read()?.let { encoded ->
        require(encoded.encodeToByteArray().size <= MAX_SNAPSHOT_BYTES) { "Stored application data exceeds its byte limit." }
        json.decodeFromString<StoreSnapshot>(encoded).also {
            require(it.schemaVersion == 1) { "Unsupported application storage schema ${it.schemaVersion}." }
        }.values
    } ?: emptyMap()

    override fun read(namespace: String, key: String): String? = lock.withLock { values[namespace]?.get(key) }

    override fun write(namespace: String, key: String, value: String) = lock.withLock {
        require(namespace.isNotBlank() && key.isNotBlank()) { "Storage namespaces and keys must not be empty." }
        commit(values + (namespace to (values[namespace].orEmpty() + (key to value))))
    }

    override fun remove(namespace: String, key: String): Boolean = lock.withLock {
        val current = values[namespace] ?: return@withLock false
        if (key !in current) return@withLock false
        val next = current - key
        commit(if (next.isEmpty()) values - namespace else values + (namespace to next))
        true
    }

    override fun keys(namespace: String, prefix: String): List<String> = lock.withLock {
        values[namespace].orEmpty().keys.filter { it.startsWith(prefix) }.sorted()
    }

    override fun clear(namespace: String) = lock.withLock { commit(values - namespace) }

    override fun updateNamespace(namespace: String, update: (Map<String, String>) -> Map<String, String>) = lock.withLock {
        require(namespace.isNotBlank()) { "Storage namespaces must not be empty." }
        val next = update(values[namespace].orEmpty()).toMap()
        commit(if (next.isEmpty()) values - namespace else values + (namespace to next))
    }

    override fun edit(changes: List<StoreEdit>) = lock.withLock {
        var next = values
        changes.forEach { change ->
            require(change.namespace.isNotBlank()) { "Storage namespaces must not be empty." }
            val key = change.key
            next = if (key == null) next - change.namespace else {
                require(key.isNotBlank()) { "Storage keys must not be empty." }
                val entries = next[change.namespace].orEmpty()
                val changed = if (change.value == null) entries - key else entries + (key to change.value)
                if (changed.isEmpty()) next - change.namespace else next + (change.namespace to changed)
            }
        }
        commit(next)
    }

    private fun commit(next: Map<String, Map<String, String>>) {
        val snapshot = json.encodeToString(StoreSnapshot(values = next))
        require(snapshot.encodeToByteArray().size <= MAX_SNAPSHOT_BYTES) { "Application storage exceeds its byte limit." }
        // Publish only after durable persistence succeeds; failed writes leave the previous snapshot intact.
        persistence.write(snapshot)
        values = next
    }

    private companion object {
        const val MAX_SNAPSHOT_BYTES = 32 * 1024 * 1024
    }
}

class MemorySnapshotPersistence : SnapshotPersistence {
    private var snapshot: String? = null
    override fun read(): String? = snapshot
    override fun write(snapshot: String) { this.snapshot = snapshot }
}
