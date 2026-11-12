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
import kotlin.time.TimeMark
import kotlin.time.TimeSource

data class ActivePlugin(val installationKey: String, val session: MediaSourceSession)
data class InstallationCompletion(val serial: Long, val key: String)

data class PluginConnectionState(
    val busy: Boolean = false,
    val active: ActivePlugin? = null,
    val error: String? = null,
    val installationKey: String? = null,
    val phase: PluginLoadPhase? = null,
    val completedInstallation: InstallationCompletion? = null,
    val loadTimings: List<PluginLoadTiming> = emptyList(),
)

class AppPluginController(
    private val repository: PluginCatalogRepository,
    private val sessions: PluginSessionConnector,
    parentScope: CoroutineScope,
    private val debugDiagnostics: Boolean = false,
) {
    private val mutableState = MutableStateFlow(PluginConnectionState())
    val state = mutableState.asStateFlow()
    private val operations = LatestOperation(parentScope)
    private var active: MediaSourceSession? = null
    private var closed = false
    private var serial = 0L
    private var loadStarted: TimeMark? = null

    fun install(listing: CatalogListing, release: PluginRelease, openAfterInstall: Boolean = false) = replace(key = listing.key) { token ->
        val current = repository.state.value.listings.firstOrNull { it.key == listing.key }
            ?: error("This plugin source is no longer available.")
        require(repository.releaseBlockReason(current, release) == null) { repository.releaseBlockReason(current, release).orEmpty() }
        repository.prepareInstallation()
        val key = acquire(current.plugin, release, current.snapshot.subscription.allowHttp, token, openAfterInstall) { session ->
            phase(token, PluginLoadPhase.RECORDING_INSTALLATION)
            val installed = repository.state.value.installed.firstOrNull { it.key == current.key && it.release == release }
            if (installed == null) repository.recordInstallation(current, release, session.source.descriptor)
            else require(repository.installationBlockReason(installed) == null) { repository.installationBlockReason(installed).orEmpty() }
            if (openAfterInstall) repository.selectPreferredInstallation(current.key)
            current.key
        }
        currentCoroutineContext().ensureActive()
        mutableState.update { it.copy(completedInstallation = InstallationCompletion(token, key)) }
    }

    fun open(installation: InstalledPlugin, rememberSource: Boolean = false) = replace(key = installation.key) { token ->
        val current = repository.state.value.installed.firstOrNull { it.key == installation.key }
            ?: error("This plugin was uninstalled.")
        require(repository.installationBlockReason(current) == null) { repository.installationBlockReason(current).orEmpty() }
        if (rememberSource && repository.state.value.preferredInstallations[current.plugin.id] != current.key)
            repository.selectPreferredInstallation(current.key)
        currentCoroutineContext().ensureActive()
        acquire(current.plugin, current.release, current.allowHttp, token, activate = true) {
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
        allowHttp: Boolean, token: Long, activate: Boolean, accept: suspend (MediaSourceSession) -> String): String {
        closeSession()
        var acquired: MediaSourceSession? = null
        var retained = false
        var failure: Throwable? = null
        try {
            val session = sessions.connect(plugin, release, allowHttp) { phase(token, it) }
            acquired = session
            currentCoroutineContext().ensureActive()
            require(repository.state.value.selectedKind in session.source.descriptor.kinds) { "This release does not support the selected content type." }
            val key = accept(session)
            currentCoroutineContext().ensureActive()
            if (activate) {
                val installation = repository.state.value.installed.firstOrNull { it.key == key } ?: error("This plugin was uninstalled.")
                require(repository.installationBlockReason(installation) == null) { repository.installationBlockReason(installation).orEmpty() }
                require(repository.state.value.selectedKind in session.source.descriptor.kinds) { "The content type changed." }
                active = session
                mutableState.update { it.copy(active = ActivePlugin(key, session)) }
                retained = true
            }
            return key
        } catch (caught: Throwable) {
            failure = caught
            throw caught
        } finally {
            if (!retained) {
                try {
                    withContext(NonCancellable) { acquired?.close() }
                } catch (closeFailure: Throwable) {
                    val original = failure
                    if (original != null) original.addSuppressed(closeFailure) else throw closeFailure
                }
            }
        }
    }

    private suspend fun closeSession() = withContext(NonCancellable) {
        val previous = active
        active = null
        previous?.close()
    }

    private fun phase(token: Long, phase: PluginLoadPhase) {
        if (closed || token != serial) return
        val elapsed = loadStarted?.elapsedNow()?.inWholeMilliseconds ?: 0L
        mutableState.update { state ->
            val timings = if (!debugDiagnostics || state.phase == phase) state.loadTimings
            else (finishTimings(state.loadTimings, elapsed) + PluginLoadTiming(phase, elapsed)).takeLast(32)
            state.copy(phase = phase, loadTimings = timings)
        }
    }

    private fun finishTimings(timings: List<PluginLoadTiming>, elapsed: Long): List<PluginLoadTiming> =
        timings.map { if (it.finishedAtMs == null) it.copy(finishedAtMs = elapsed) else it }

    private fun replace(busy: Boolean = true, key: String? = null, block: suspend (Long) -> Unit) {
        if (closed) return
        val token = ++serial
        operations.launch(
            onStart = {
                loadStarted = if (busy && debugDiagnostics) TimeSource.Monotonic.markNow() else null
                mutableState.value = PluginConnectionState(busy = busy, installationKey = key,
                    phase = PluginLoadPhase.PRECHECK.takeIf { busy },
                    loadTimings = if (busy && debugDiagnostics) listOf(PluginLoadTiming(PluginLoadPhase.PRECHECK, 0)) else emptyList())
            },
            onFailure = { failure -> mutableState.update { it.copy(error = (failure.message ?: failure.toString()).take(4000)) } },
            onFinish = {
                val elapsed = loadStarted?.elapsedNow()?.inWholeMilliseconds ?: 0L
                mutableState.update { it.copy(busy = false, phase = null, loadTimings = finishTimings(it.loadTimings, elapsed)) }
            },
        ) { closeSession(); block(token) }
    }
}
