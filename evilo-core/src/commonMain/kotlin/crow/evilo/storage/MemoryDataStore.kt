package crow.evilo.storage

import androidx.datastore.core.DataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Non-durable storage for Compose previews and isolated tests, never the platform app default. */
class MemoryDataStore<T>(initialValue: T) : DataStore<T> {
    private val values = MutableStateFlow(initialValue)
    private val operations = Mutex()
    override val data = values.asStateFlow()

    override suspend fun updateData(transform: suspend (t: T) -> T): T = operations.withLock {
        transform(values.value).also { values.value = it }
    }
}
