package crow.evilo.app

import crow.evilo.plugin.catalog.CatalogListing
import crow.evilo.plugin.catalog.InstalledPlugin
import crow.evilo.plugin.catalog.PluginCatalogRepository
import crow.evilo.plugin.catalog.PluginRelease
import crow.evilo.feature.reader.MediaSourceSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class ActivePlugin(val installationKey: String, val session: MediaSourceSession)
data class PluginConnectionState(val busy: Boolean = false, val active: ActivePlugin? = null, val error: String? = null)

class AppPluginController(
    private val repository: PluginCatalogRepository,
    private val sessions: PluginSessionFactory,
    parentScope: CoroutineScope,
) {
    private val lifetime = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + lifetime)
    private val mutableState = MutableStateFlow(PluginConnectionState())
    val state = mutableState.asStateFlow()
    private val operations = Mutex()
    private var operation: Job? = null
    private var active: MediaSourceSession? = null
    private var generation = 0L
    private var closed = false

    fun install(listing: CatalogListing, release: PluginRelease) = replace {
        val current = repository.state.value.listings.firstOrNull { it.key == listing.key }
            ?: error("This plugin source is no longer available.")
        require(repository.releaseBlockReason(current, release) == null) { repository.releaseBlockReason(current, release).orEmpty() }
        acquire(current.plugin, release, current.snapshot.subscription.allowHttp) { session ->
            repository.recordInstallation(current, release, session.source.descriptor)
            current.key
        }
    }

    fun open(installation: InstalledPlugin) = replace {
        val current = repository.state.value.installed.firstOrNull { it.key == installation.key }
            ?: error("This plugin was uninstalled.")
        require(repository.installationBlockReason(current) == null) { repository.installationBlockReason(current).orEmpty() }
        acquire(current.plugin, current.release, current.allowHttp) {
            require(repository.installationBlockReason(current) == null) { repository.installationBlockReason(current).orEmpty() }
            current.key
        }
    }

    fun closeActive() = replace(busy = false) { closeSession() }
    fun invalidate(reason: String) = replace(busy = false) { closeSession(); error(reason) }
    fun dismissError() { mutableState.update { it.copy(error = null) } }

    suspend fun close() = withContext(NonCancellable) {
        if (closed) return@withContext
        closed = true
        generation++
        lifetime.cancelAndJoin()
        closeSession()
    }

    private suspend fun acquire(plugin: crow.evilo.plugin.catalog.CatalogPlugin, release: PluginRelease,
        allowHttp: Boolean, accept: suspend (MediaSourceSession) -> String) {
        closeSession()
        var acquired: MediaSourceSession? = null
        var accepted = false
        try {
            val session = sessions.connect(plugin, release, allowHttp)
            acquired = session
            currentCoroutineContext().ensureActive()
            require(repository.state.value.selectedKind in session.source.descriptor.kinds) { "This release does not support the selected content type." }
            val key = accept(session)
            currentCoroutineContext().ensureActive()
            active = session
            mutableState.update { it.copy(active = ActivePlugin(key, session)) }
            accepted = true
        } catch (failure: Throwable) {
            if (!accepted) {
                try {
                    withContext(NonCancellable) { acquired?.close() }
                } catch (closeFailure: Throwable) {
                    failure.addSuppressed(closeFailure)
                }
            }
            throw failure
        }
    }

    private suspend fun closeSession() = withContext(NonCancellable) {
        val previous = active
        active = null
        previous?.close()
    }

    private fun replace(busy: Boolean = true, block: suspend () -> Unit) {
        if (closed) return
        val token = ++generation
        operation?.cancel()
        mutableState.value = PluginConnectionState(busy = busy)
        operation = scope.launch {
            try {
                operations.withLock { closeSession(); block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (generation == token) mutableState.update { it.copy(error = (failure.message ?: failure.toString()).take(4000)) }
            } finally {
                if (generation == token) mutableState.update { it.copy(busy = false) }
            }
        }
    }
}
