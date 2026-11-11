package crow.evilo.app

import androidx.compose.runtime.Composable
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.Storage
import androidx.datastore.core.okio.OkioSerializer
import crow.evilo.architecture.closeResources
import crow.evilo.storage.EviloDatabase
import crow.evilo.storage.EviloPreferences
import crow.evilo.storage.EviloPreferencesCodec
import crow.evilo.storage.MemoryDataStore
import crow.evilo.storage.MemoryPluginCatalogStore
import crow.evilo.storage.PluginCatalogStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okio.Buffer
import okio.BufferedSink
import okio.BufferedSource

internal fun interface AppStorageFactory {
    suspend fun create(scope: CoroutineScope): AppStorage
}

@Composable
internal expect fun rememberAppStorageFactory(): AppStorageFactory

internal class AppStorage(
    val preferences: DataStore<EviloPreferences>,
    val catalogs: PluginCatalogStore,
    private val lifetime: Job? = null,
    private val closeBackend: suspend () -> Unit = {},
) {
    private val closeOperations = Mutex()
    private var closed = false

    suspend fun close() = withContext(NonCancellable) {
        closeOperations.withLock {
            if (!closed) {
                closed = true
                closeResources({ lifetime?.cancelAndJoin() }, { closeBackend() })
            }
        }
    }

    companion object {
        fun preview() = AppStorage(MemoryDataStore(EviloPreferences()), MemoryPluginCatalogStore())
    }
}

internal suspend fun createAppStorage(
    storage: Storage<EviloPreferences>,
    database: EviloDatabase,
    parentScope: CoroutineScope,
    afterDatabaseClose: suspend () -> Unit = {},
): AppStorage {
    val lifetime = SupervisorJob(parentScope.coroutineContext[Job])
    try {
        val preferences = DataStoreFactory.create(storage = storage, scope = CoroutineScope(Dispatchers.Default + lifetime))
        return AppStorage(preferences, database.catalogs(), lifetime) {
            closeResources({ database.close() }, { afterDatabaseClose() })
        }
    } catch (failure: Throwable) {
        try { closeResources({ lifetime.cancelAndJoin() }, { database.close() }, { afterDatabaseClose() }) }
        catch (closeFailure: Throwable) { if (failure !== closeFailure) failure.addSuppressed(closeFailure) }
        throw failure
    }
}

internal object EviloPreferencesSerializer : OkioSerializer<EviloPreferences> {
    override val defaultValue = EviloPreferences()

    override suspend fun readFrom(source: BufferedSource): EviloPreferences {
        val buffer = Buffer()
        while (buffer.size <= EviloPreferencesCodec.MAX_BYTES) {
            val count = source.read(buffer, minOf(8192L, EviloPreferencesCodec.MAX_BYTES + 1L - buffer.size))
            if (count == -1L) break
        }
        return EviloPreferencesCodec.decode(buffer.readByteArray())
    }

    override suspend fun writeTo(t: EviloPreferences, sink: BufferedSink) {
        sink.write(EviloPreferencesCodec.encode(t))
    }
}
