package crow.evilo.feature.browse

import crow.evilo.app.AppPluginController
import crow.evilo.architecture.LatestOperation
import crow.evilo.architecture.MviStore
import crow.evilo.plugin.catalog.CatalogListing
import crow.evilo.plugin.catalog.PluginCatalogRepository
import crow.evilo.plugin.catalog.OFFICIAL_SOURCE_ID
import crow.evilo.plugin.catalog.PluginRepositoryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PluginHomeStore(
    private val repository: PluginCatalogRepository,
    private val connections: AppPluginController,
    parentScope: CoroutineScope,
) : MviStore<PluginHomeState, PluginHomeIntent> {
    private val lifetime = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + lifetime)
    private val operations = LatestOperation(scope)
    private val mutableState = MutableStateFlow(PluginHomeState())
    override val state = mutableState.asStateFlow()
    private var closed = false

    init {
        scope.launch {
            combine(repository.state, connections.state) { catalog, connection -> catalog to connection }.collect { (catalog, connection) ->
                val entries = entries(catalog)
                mutableState.update { current ->
                    val key = when (val overlay = current.overlay) {
                        is HomeOverlay.PluginSettings -> overlay.key
                        is HomeOverlay.PermissionReview -> overlay.key
                        is HomeOverlay.Uninstall -> overlay.key
                        else -> null
                    }
                    current.copy(catalog = catalog, connection = connection, entries = entries,
                        overlay = if (key != null && key !in entries) null else current.overlay)
                }
                val active = connection.active
                if (active != null) {
                    val installation = catalog.installed.firstOrNull { it.key == active.installationKey }
                    val reason = if (installation == null) "This plugin was uninstalled." else repository.installationBlockReason(installation)
                    if (reason != null || catalog.selectedKind !in active.session.source.descriptor.kinds) {
                        connections.invalidate(reason ?: "The content type changed.")
                    }
                }
            }
        }
    }

    override fun dispatch(intent: PluginHomeIntent) {
        if (closed) return
        when (intent) {
            is PluginHomeIntent.Navigate -> {
                connections.closeActive()
                mutableState.update { it.copy(destination = intent.destination, overlay = null) }
            }
            is PluginHomeIntent.Search -> mutableState.update { it.copy(query = intent.query) }
            is PluginHomeIntent.Filter -> mutableState.update { it.copy(filter = intent.filter) }
            PluginHomeIntent.ShowSources -> mutableState.update { it.copy(overlay = HomeOverlay.Sources) }
            PluginHomeIntent.ShowSafety -> mutableState.update { it.copy(overlay = HomeOverlay.Safety) }
            PluginHomeIntent.ShowContentKinds -> mutableState.update { it.copy(overlay = HomeOverlay.ContentKinds) }
            PluginHomeIntent.Refresh -> if (!state.value.busy) perform { repository.refresh() }
            is PluginHomeIntent.SelectKind -> if (!state.value.changing) {
                mutableState.update { it.copy(overlay = null, query = "", filter = CatalogFilter.ALL) }
                if (intent.kind != state.value.catalog.selectedKind) {
                    connections.closeActive()
                    perform { repository.selectKind(intent.kind) }
                }
            }
            is PluginHomeIntent.ShowNsfw -> if (!state.value.busy) perform { repository.setShowNsfw(intent.show) }
            is PluginHomeIntent.AddSource -> if (!state.value.busy) perform { repository.addSource(intent.url, intent.allowHttp) }
            is PluginHomeIntent.EnableSource -> if (!state.value.busy) perform { repository.setSourceEnabled(intent.id, intent.enabled) }
            is PluginHomeIntent.RequestSourceRemoval -> if (intent.id != OFFICIAL_SOURCE_ID &&
                state.value.catalog.subscriptions.any { it.id == intent.id }) {
                mutableState.update { it.copy(overlay = HomeOverlay.RemoveSource(intent.id)) }
            }
            is PluginHomeIntent.ConfirmSourceRemoval -> if (!state.value.busy && state.value.overlay == HomeOverlay.RemoveSource(intent.id)) {
                mutableState.update { it.copy(overlay = HomeOverlay.Sources) }
                perform {
                    val activeKey = connections.state.value.active?.installationKey
                    if (repository.state.value.installed.any { it.key == activeKey && it.sourceId == intent.id }) {
                        connections.closeActive()
                        connections.awaitIdle()
                    }
                    repository.removeSource(intent.id)
                }
            }
            is PluginHomeIntent.OpenPlugin -> open(intent.key)
            is PluginHomeIntent.ManagePlugin -> if (intent.key in state.value.entries) {
                mutableState.update { it.copy(overlay = HomeOverlay.PluginSettings(intent.key)) }
            }
            is PluginHomeIntent.ReviewRelease -> review(intent.key, intent.versionCode)
            is PluginHomeIntent.ApproveInstall -> approve(intent.review)
            is PluginHomeIntent.RequestUninstall -> if (state.value.entries[intent.key]?.installation != null) {
                mutableState.update { it.copy(overlay = HomeOverlay.Uninstall(intent.key)) }
            }
            is PluginHomeIntent.ConfirmUninstall -> if (!state.value.busy && state.value.overlay == HomeOverlay.Uninstall(intent.key)) {
                mutableState.update { it.copy(overlay = null) }
                perform {
                    if (connections.state.value.active?.installationKey == intent.key) {
                        connections.closeActive()
                        connections.awaitIdle()
                    }
                    repository.uninstall(intent.key, eraseData = true)
                }
            }
            PluginHomeIntent.DismissOverlay -> mutableState.update { current ->
                val previous = current.overlay
                val key = when (previous) {
                    is HomeOverlay.PermissionReview -> previous.key.takeIf { previous.returnToSettings }
                    is HomeOverlay.Uninstall -> previous.key
                    else -> null
                }
                current.copy(overlay = if (previous is HomeOverlay.RemoveSource) HomeOverlay.Sources
                    else key?.takeIf { it in current.entries }?.let { HomeOverlay.PluginSettings(it) })
            }
            PluginHomeIntent.ClosePlugin -> connections.closeActive()
            PluginHomeIntent.DismissError -> {
                repository.dismissError()
                connections.dismissError()
                mutableState.update { it.copy(actionError = null) }
            }
        }
    }

    private fun open(key: String) {
        if (state.value.busy) return
        val entry = state.value.entries[key] ?: return fail("This plugin source is no longer available.")
        val installed = entry.installation
        if (installed != null) {
            val reason = repository.installationBlockReason(installed)
            if (reason == null) {
                mutableState.update { it.copy(destination = HomeDestination.BROWSE, overlay = null, actionError = null) }
                connections.open(installed)
            } else {
                mutableState.update { it.copy(overlay = HomeOverlay.PluginSettings(key), actionError = reason) }
            }
        } else {
            val release = entry.recommended
            if (release == null) {
                mutableState.update { it.copy(overlay = HomeOverlay.PluginSettings(key), actionError = "No compatible plugin release is available.") }
            } else mutableState.update { it.copy(overlay = HomeOverlay.PermissionReview(key, release, returnToSettings = false)) }
        }
    }

    private fun review(key: String, versionCode: Long) {
        if (state.value.busy) return
        val entry = state.value.entries[key] ?: return fail("This plugin source is no longer available.")
        val listing = entry.listing ?: return fail("Refresh or enable this plugin source before installing.")
        val release = listing.plugin.availableVersions.firstOrNull { it.versionCode == versionCode }
            ?: return fail("This release was removed from its plugin source.")
        val reason = repository.releaseBlockReason(listing, release)
        if (reason != null) return fail(reason)
        if (entry.installation?.release == release && entry.openBlockReason == null) open(key)
        else mutableState.update { it.copy(overlay = HomeOverlay.PermissionReview(key, release, returnToSettings = true)) }
    }

    private fun approve(review: HomeOverlay.PermissionReview) {
        if (state.value.busy || state.value.overlay != review) return
        val entry = state.value.entries[review.key] ?: return fail("This plugin source is no longer available.")
        val listing = entry.listing ?: return fail("Refresh or enable this plugin source before installing.")
        val reason = repository.releaseBlockReason(listing, review.release)
        if (reason != null) return fail(reason)
        mutableState.update { it.copy(overlay = null, destination = HomeDestination.BROWSE, actionError = null) }
        val installed = entry.installation
        if (installed != null && installed.release == review.release && repository.installationBlockReason(installed) == null) {
            connections.open(installed)
        } else connections.install(listing, review.release)
    }

    private fun entries(catalog: PluginRepositoryState): Map<String, PluginEntry> {
        val listings = catalog.snapshots.flatMap { snapshot -> snapshot.catalog.plugins.map { CatalogListing(snapshot, it) } }.associateBy { it.key }
        val installed = catalog.installed.associateBy { it.key }
        return (listings.keys + installed.keys).associateWith { key ->
            val listing = listings[key]
            val installation = installed[key]
            PluginEntry(key, listing, installation,
                (listing?.plugin ?: checkNotNull(installation).plugin).availableVersions.sortedByDescending { it.versionCode }.map { release ->
                    PluginReleaseChoice(release, if (listing == null) "Refresh or enable this plugin source before installing."
                        else repository.releaseBlockReason(listing, release))
                },
                listing?.let(repository::recommendedRelease), installation?.let(repository::installationBlockReason))
        }
    }

    private fun perform(block: suspend () -> Unit) {
        operations.launch(
            onStart = { mutableState.update { it.copy(changing = true, actionError = null) } },
            onFailure = { failure -> fail((failure.message ?: failure.toString()).take(4000)) },
            onFinish = { mutableState.update { it.copy(changing = false) } },
        ) { block() }
    }

    private fun fail(message: String) { mutableState.update { it.copy(actionError = message) } }

    override suspend fun close() = withContext(NonCancellable) {
        if (!closed) {
            closed = true
            operations.close()
            lifetime.cancelAndJoin()
        }
    }
}
