package crow.evilo.feature.browse

import crow.evilo.app.AppPluginController
import crow.evilo.architecture.LatestOperation
import crow.evilo.architecture.MviStore
import crow.evilo.model.MediaKind
import crow.evilo.plugin.catalog.BUNDLED_SOURCE_ID
import crow.evilo.plugin.catalog.CatalogListing
import crow.evilo.plugin.catalog.PluginBlock
import crow.evilo.plugin.catalog.PluginBlockCode
import crow.evilo.plugin.catalog.PluginCatalogRepository
import crow.evilo.plugin.catalog.PluginRepositoryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
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
    private val refreshes = LatestOperation(scope)
    private val mutableState = MutableStateFlow(PluginHomeState())
    override val state = mutableState.asStateFlow()
    private var closed = false
    private var observedInstallation = 0L
    private var pendingReturnPage: HomePage? = null

    init {
        scope.launch {
            combine(repository.state, connections.state) { _, _ -> Unit }.collect {
                val catalog = repository.state.value
                val connection = connections.state.value
                val entries = entries(catalog)
                val completed = connection.completedInstallation?.takeIf { it.serial != observedInstallation }
                if (completed != null) observedInstallation = completed.serial
                update { current ->
                    val invalidPage = current.pages.indexOfFirst { !validPage(it, catalog, entries) }
                    current.copy(catalog = catalog, connection = connection, entries = entries,
                        pages = if (invalidPage < 0) current.pages else current.pages.take(invalidPage),
                        overlay = current.overlay?.takeIf { validOverlay(it, catalog, entries) },
                        browseSourceId = current.browseSourceId?.takeIf { id -> catalog.subscriptions.any { it.id == id } },
                        notice = (if (completed != null && connection.active == null) HomeNotice(completed.key) else current.notice)
                            ?.takeIf { notice -> catalog.installed.any { it.key == notice.installationKey &&
                                (catalog.showNsfw || !catalog.isPluginNsfw(it.plugin.id)) } })
                }
                connection.active?.let { active ->
                    val installation = catalog.installed.firstOrNull { it.key == active.installationKey }
                    val reason = if (installation == null) "This plugin was uninstalled." else repository.installationBlockReason(installation)
                    val browser = state.value.page as? HomePage.PluginBrowser
                    if (browser?.key != active.installationKey) connections.closeActive()
                    else if (reason != null || catalog.selectedKind !in active.session.source.descriptor.kinds) {
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
                cancelReturningOperation()
                connections.closeActive()
                update { it.copy(destination = intent.destination, pages = emptyList(), overlay = null, actionError = null) }
            }
            PluginHomeIntent.Back -> back()
            is PluginHomeIntent.Search -> update { it.copy(query = intent.query) }
            is PluginHomeIntent.SetSearchLabels -> update { it.copy(searchLabels = intent.labels) }
            PluginHomeIntent.ShowBrowseSources -> update { it.copy(overlay = HomeOverlay.BrowseSources) }
            is PluginHomeIntent.SelectBrowseSource -> update { it.copy(browseSourceId = intent.id, overlay = null) }
            is PluginHomeIntent.SearchSource -> update { it.copy(sourceQueries = it.sourceQueries + (intent.id to intent.query)) }
            is PluginHomeIntent.FilterSource -> update { it.copy(sourceFilters = it.sourceFilters + (intent.id to intent.filter)) }
            is PluginHomeIntent.SearchInstalled -> update { it.copy(installedQuery = intent.query) }
            PluginHomeIntent.ShowSources -> push(HomePage.Sources)
            PluginHomeIntent.ShowAddSource -> push(HomePage.AddSource)
            is PluginHomeIntent.ShowSourceCatalog -> if (hasSource(intent.id)) push(HomePage.SourceCatalog(intent.id))
            is PluginHomeIntent.ShowSourceSettings -> if (hasSource(intent.id)) push(HomePage.SourceSettings(intent.id))
            is PluginHomeIntent.ShowSettings -> push(HomePage.Settings(intent.section))
            PluginHomeIntent.ShowInstalled -> push(HomePage.InstalledPlugins)
            PluginHomeIntent.ShowSafety -> push(HomePage.Safety)
            PluginHomeIntent.ShowAbout -> push(HomePage.About)
            PluginHomeIntent.ShowContentKinds -> update { it.copy(overlay = HomeOverlay.ContentKinds) }
            PluginHomeIntent.Refresh -> refresh(null)
            is PluginHomeIntent.RefreshSource -> refresh(intent.id)
            is PluginHomeIntent.SelectKind -> if (!state.value.changing) {
                if (state.value.page is HomePage.PluginBrowser) {
                    connections.closeActive()
                    update { it.copy(pages = it.pages.dropLast(1), overlay = null) }
                } else update { it.copy(overlay = null) }
                if (intent.kind != state.value.catalog.selectedKind) perform { repository.selectKind(intent.kind) }
            }
            is PluginHomeIntent.ShowNsfw -> if (state.value.canMutate) perform { repository.setShowNsfw(intent.show) }
            is PluginHomeIntent.MergeDuplicatePlugins -> if (state.value.canMutate) perform { repository.setMergeDuplicatePlugins(intent.merge) }
            is PluginHomeIntent.AddSource -> if (state.value.canMutate) perform(returnPage = HomePage.AddSource) {
                val previous = repository.state.value.subscriptions.map { it.id }.toSet()
                repository.addSource(intent.url, intent.allowHttp)
                currentCoroutineContext().ensureActive()
                val added = repository.state.value.subscriptions.first { it.id !in previous }
                update { current ->
                    if (current.page == HomePage.AddSource) current.copy(pages = current.pages.dropLast(1) + HomePage.SourceCatalog(added.id))
                    else current
                }
            }
            is PluginHomeIntent.EnableSource -> if (state.value.canMutate) perform {
                if (!intent.enabled) closeSourceSession(intent.id)
                repository.setSourceEnabled(intent.id, intent.enabled)
            }
            is PluginHomeIntent.RequestSourceRemoval -> if (intent.id != BUNDLED_SOURCE_ID && hasSource(intent.id) && state.value.canMutate) {
                update { it.copy(overlay = HomeOverlay.RemoveSource(intent.id)) }
            }
            is PluginHomeIntent.ConfirmSourceRemoval -> if (state.value.canMutate && state.value.overlay == HomeOverlay.RemoveSource(intent.id)) {
                update { it.copy(overlay = null) }
                perform { closeSourceSession(intent.id); repository.removeSource(intent.id) }
            }
            is PluginHomeIntent.OpenPlugin -> open(intent.key, intent.rememberSource)
            is PluginHomeIntent.ManagePlugin -> manage(intent.key, intent.rememberSourceOnOpen)
            is PluginHomeIntent.ChoosePluginSource -> if (state.value.catalog.installed.any { it.plugin.id == intent.pluginId }) {
                if (hidden(intent.pluginId)) push(HomePage.Settings(SettingsSection.BROWSE))
                else push(HomePage.ChooseSource(intent.pluginId))
            }
            is PluginHomeIntent.SelectPreferredInstallation -> selectPreferred(intent.key)
            is PluginHomeIntent.ReviewRelease -> review(intent)
            is PluginHomeIntent.ApproveInstall -> approve(intent.review)
            is PluginHomeIntent.RequestUninstall -> if (state.value.entries[intent.key]?.installation != null && state.value.canMutate) {
                update { it.copy(overlay = HomeOverlay.Uninstall(intent.key)) }
            }
            is PluginHomeIntent.ConfirmUninstall -> if (state.value.canMutate && state.value.overlay == HomeOverlay.Uninstall(intent.key)) {
                update { it.copy(overlay = null) }
                perform {
                    if (connections.state.value.active?.installationKey == intent.key) {
                        connections.closeActive()
                        connections.awaitIdle()
                    }
                    repository.uninstall(intent.key, eraseData = true)
                }
            }
            PluginHomeIntent.DismissOverlay -> update { it.copy(overlay = null) }
            PluginHomeIntent.ClosePlugin -> {
                connections.closeActive()
                if (state.value.page is HomePage.PluginBrowser) update { it.copy(pages = it.pages.dropLast(1), overlay = null) }
            }
            PluginHomeIntent.DismissError -> {
                repository.dismissError()
                connections.dismissError()
                update { it.copy(actionError = null) }
            }
            is PluginHomeIntent.DismissTransientError -> {
                update { current -> if (intent.expectedActionError != null && current.actionError == intent.expectedActionError)
                    current.copy(actionError = null) else current }
                if (intent.expectedConnectionError != null && state.value.page !is HomePage.PluginBrowser &&
                    connections.state.value.error == intent.expectedConnectionError) connections.dismissError()
            }
            PluginHomeIntent.DismissNotice -> update { it.copy(notice = null) }
        }
    }

    private fun manage(key: String, rememberSource: Boolean = true) {
        val entry = state.value.entries[key] ?: return fail("This plugin source is no longer available.")
        if (!state.value.catalog.showNsfw && (entry.plugin.nsfw || hidden(entry.plugin.id))) push(HomePage.Settings(SettingsSection.BROWSE))
        else {
            val browser = state.value.page as? HomePage.PluginBrowser
            val previous = state.value.pages.filterIsInstance<HomePage.PluginDetail>().lastOrNull { it.key == key }
            push(if (browser?.key == key && previous != null) previous else HomePage.PluginDetail(key,
                if (browser?.key == key) browser.rememberSourceOnOpen else rememberSource))
        }
    }

    private fun open(key: String, rememberSource: Boolean) {
        if (state.value.changing) return
        val entry = state.value.entries[key] ?: return fail("This plugin source is no longer available.")
        val installation = entry.installation ?: return manage(key, rememberSource)
        if (hidden(installation.plugin.id)) return push(HomePage.Settings(SettingsSection.BROWSE))
        if (repository.installationBlockReason(installation) != null) return manage(key, rememberSource)
        update { current -> current.copy(overlay = null, actionError = null,
            pages = current.pages.let { if (it.lastOrNull() is HomePage.PluginBrowser) it.dropLast(1) else it } + HomePage.PluginBrowser(key, rememberSource)) }
        connections.open(installation, rememberSource)
    }

    private fun selectPreferred(key: String) {
        if (!state.value.canMutate) return
        val entry = state.value.entries[key] ?: return
        val installation = entry.installation ?: return
        if (!state.value.catalog.subscriptions.any { it.id == installation.sourceId && it.enabled } ||
            state.value.catalog.selectedKind !in installation.release.kinds || hidden(installation.plugin.id) || entry.openBlock != null) return
        perform(returnPage = state.value.page as? HomePage.ChooseSource) {
            repository.selectPreferredInstallation(key)
            currentCoroutineContext().ensureActive()
            update { current -> if (current.page == HomePage.ChooseSource(installation.plugin.id))
                current.copy(pages = current.pages.dropLast(1)) else current }
        }
    }

    private fun review(intent: PluginHomeIntent.ReviewRelease) {
        if (!state.value.canMutate) return
        val entry = state.value.entries[intent.key] ?: return fail("This plugin source is no longer available.")
        val listing = entry.listing ?: return fail("Refresh or enable this plugin source before installing.")
        val release = listing.plugin.availableVersions.firstOrNull { it.versionCode == intent.versionCode }
            ?: return fail("This release was removed from its plugin source.")
        val reason = repository.releaseBlockReason(listing, release)
        if (reason != null) return fail(reason)
        if (entry.installation?.release == release && entry.openBlockReason == null) open(entry.key, rememberSource = false)
        else update { it.copy(overlay = HomeOverlay.PermissionReview(entry.key, release, intent.openAfterInstall)) }
    }

    private fun approve(review: HomeOverlay.PermissionReview) {
        if (!state.value.canMutate || state.value.overlay != review) return
        val entry = state.value.entries[review.key] ?: return fail("This plugin source is no longer available.")
        val listing = entry.listing ?: return fail("Refresh or enable this plugin source before installing.")
        val reason = repository.releaseBlockReason(listing, review.release)
        if (reason != null) return fail(reason)
        update { it.copy(overlay = null, actionError = null, notice = null,
            pages = if (review.openAfterInstall) it.pages + HomePage.PluginBrowser(entry.key) else it.pages) }
        val installed = entry.installation
        if (installed != null && installed.release == review.release && repository.installationBlockReason(installed) == null) {
            if (review.openAfterInstall) connections.open(installed)
            else update { it.copy(notice = HomeNotice(installed.key)) }
        } else connections.install(listing, review.release, review.openAfterInstall)
    }

    private fun back() {
        if (state.value.overlay != null) return update { it.copy(overlay = null) }
        if (pendingReturnPage == state.value.page) cancelReturningOperation()
        if (state.value.page is HomePage.PluginBrowser || state.value.connection.busy) connections.closeActive()
        update { it.copy(pages = it.pages.dropLast(1), actionError = null) }
    }

    private fun push(page: HomePage) {
        if (page != pendingReturnPage) cancelReturningOperation()
        if (state.value.page is HomePage.PluginBrowser || state.value.connection.busy) connections.closeActive()
        update { current ->
            val pages = if (current.page is HomePage.PluginBrowser) current.pages.dropLast(1) else current.pages
            val existing = pages.indexOfLast { it == page }
            current.copy(pages = if (existing >= 0) pages.take(existing + 1) else pages + page,
                overlay = null, actionError = null)
        }
    }

    private fun hasSource(id: String) = state.value.catalog.subscriptions.any { it.id == id }
    private fun hidden(id: String) = !state.value.catalog.showNsfw && state.value.catalog.isPluginNsfw(id)

    private suspend fun closeSourceSession(id: String) {
        val activeKey = connections.state.value.active?.installationKey ?: return
        if (repository.state.value.installed.any { it.key == activeKey && it.sourceId == id }) {
            connections.closeActive()
            connections.awaitIdle()
        }
    }

    private fun entries(catalog: PluginRepositoryState): Map<String, PluginEntry> {
        val listings = catalog.snapshots.flatMap { snapshot -> snapshot.catalog.plugins.map { CatalogListing(snapshot, it) } }.associateBy { it.key }
        val installed = catalog.installed.associateBy { it.key }
        return (listings.keys + installed.keys).associateWith { key ->
            val listing = listings[key]
            val installation = installed[key]
            val openBlock = installation?.let(repository::installationBlock)
            val versions = (listing?.plugin?.availableVersions.orEmpty() + listOfNotNull(installation?.release))
                .distinctBy { it.versionCode }.sortedByDescending { it.versionCode }
            PluginEntry(key, listing, installation,
                versions.map { release ->
                    val block = if (listing == null) PluginBlock(PluginBlockCode.SOURCE_MISSING,
                        "Refresh or enable this plugin source before installing.")
                    else if (release == installation?.release && release !in listing.plugin.availableVersions) openBlock
                    else repository.releaseBlock(listing, release)
                    PluginReleaseChoice(release, block?.message, block)
                }, listing?.let(repository::recommendedRelease), openBlock?.message, openBlock)
        }
    }

    private fun validPage(page: HomePage, catalog: PluginRepositoryState, entries: Map<String, PluginEntry>): Boolean = when (page) {
        is HomePage.SourceCatalog -> catalog.subscriptions.any { it.id == page.id }
        is HomePage.SourceSettings -> catalog.subscriptions.any { it.id == page.id }
        is HomePage.PluginDetail -> entries[page.key]?.let { visible(it, catalog) } == true
        is HomePage.PluginBrowser -> entries[page.key]?.let { visible(it, catalog) } == true
        is HomePage.ChooseSource -> catalog.installed.any { it.plugin.id == page.pluginId } &&
            (catalog.showNsfw || !catalog.isPluginNsfw(page.pluginId))
        else -> true
    }

    private fun validOverlay(overlay: HomeOverlay, catalog: PluginRepositoryState, entries: Map<String, PluginEntry>): Boolean = when (overlay) {
        is HomeOverlay.PermissionReview -> entries[overlay.key]?.let { visible(it, catalog) } == true
        is HomeOverlay.Uninstall -> entries[overlay.key]?.let { it.installation != null && visible(it, catalog) } == true
        is HomeOverlay.RemoveSource -> catalog.subscriptions.any { it.id == overlay.id }
        else -> true
    }

    private fun visible(entry: PluginEntry, catalog: PluginRepositoryState): Boolean =
        catalog.showNsfw || (!entry.plugin.nsfw && !catalog.isPluginNsfw(entry.plugin.id))

    private fun update(transform: (PluginHomeState) -> PluginHomeState) {
        mutableState.update { current ->
            val next = transform(current)
            val countSource = (next.page as? HomePage.SourceCatalog)?.id ?: next.browseSourceId
            next.copy(browse = projectBrowse(next.catalog, next.entries, query = next.query, sourceId = next.browseSourceId,
                searchLabels = next.searchLabels),
                kindCounts = MediaKind.entries.associateWith { projectBrowse(next.catalog, next.entries, kind = it, sourceId = countSource).count })
        }
    }

    private fun refresh(id: String?) {
        if (state.value.catalog.refreshing) return
        refreshes.launch(onStart = { update { it.copy(actionError = null) } },
            onFailure = { failure -> fail((failure.message ?: failure.toString()).take(4000)) }) { repository.refresh(id) }
    }

    private fun perform(returnPage: HomePage? = null, block: suspend () -> Unit) {
        pendingReturnPage = returnPage
        operations.launch(
            onStart = { update { it.copy(changing = true, actionError = null) } },
            onFailure = { failure -> fail((failure.message ?: failure.toString()).take(4000)) },
            onFinish = { pendingReturnPage = null; update { it.copy(changing = false) } },
        ) { block() }
    }

    private fun cancelReturningOperation() {
        if (pendingReturnPage == null) return
        pendingReturnPage = null
        operations.cancel()
        update { it.copy(changing = false) }
    }

    suspend fun awaitIdle() {
        operations.awaitIdle()
        refreshes.awaitIdle()
        connections.awaitIdle()
    }

    private fun fail(message: String) { update { it.copy(actionError = message) } }

    override suspend fun close() = withContext(NonCancellable) {
        if (!closed) {
            closed = true
            refreshes.close()
            operations.close()
            lifetime.cancelAndJoin()
        }
    }
}
