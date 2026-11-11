package crow.evilo.app

import crow.evilo.architecture.LatestOperation
import crow.evilo.plugin.catalog.CatalogListing
import crow.evilo.plugin.catalog.InstalledPlugin
import crow.evilo.plugin.catalog.PluginCatalogRepository
import crow.evilo.plugin.catalog.PluginRelease
import crow.evilo.feature.reader.MediaSourceSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

data class ActivePlugin(val installationKey: String, val session: MediaSourceSession)
data class PluginConnectionState(val busy: Boolean = false, val active: ActivePlugin? = null, val error: String? = null)

class AppPluginController(
    private val repository: PluginCatalogRepository,
    private val sessions: PluginSessionFactory,
    parentScope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(PluginConnectionState())
    val state = mutableState.asStateFlow()
    private val operations = LatestOperation(parentScope)
    private var active: MediaSourceSession? = null
    private var closed = false

    fun install(listing: CatalogListing, release: PluginRelease) = replace {
        val current = repository.state.value.listings.firstOrNull { it.key == listing.key }
            ?: error("This plugin source is no longer available.")
        require(repository.releaseBlockReason(current, release) == null) { repository.releaseBlockReason(current, release).orEmpty() }
        repository.prepareInstallation()
        acquire(current.plugin, release, current.snapshot.subscription.allowHttp) { session ->
            val installed = repository.state.value.installed.firstOrNull { it.key == current.key && it.release == release }
            if (installed == null) repository.recordInstallation(current, release, session.source.descriptor)
            else require(repository.installationBlockReason(installed) == null) { repository.installationBlockReason(installed).orEmpty() }
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
    suspend fun awaitIdle() = operations.awaitIdle()

    suspend fun close() = withContext(NonCancellable) {
        if (closed) return@withContext
        closed = true
        operations.close()
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
        operations.launch(
            onStart = { mutableState.value = PluginConnectionState(busy = busy) },
            onFailure = { failure -> mutableState.update { it.copy(error = (failure.message ?: failure.toString()).take(4000)) } },
            onFinish = { mutableState.update { it.copy(busy = false) } },
        ) { closeSession(); block() }
    }
}
