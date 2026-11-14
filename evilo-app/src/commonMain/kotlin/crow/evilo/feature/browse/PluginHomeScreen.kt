package crow.evilo.feature.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import crow.evilo.app.EviloImages
import crow.evilo.app.resources.*
import crow.evilo.feature.reader.MediaBrowserRoute
import crow.evilo.feature.reader.ImageFailure
import crow.evilo.feature.settings.AppAboutScreen
import crow.evilo.feature.settings.AppSafetyScreen
import crow.evilo.feature.settings.SettingsScreen
import crow.evilo.feature.settings.settingsSectionTitle
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.plugin.catalog.BUNDLED_SOURCE_ID
import crow.evilo.ui.EviloAsyncImage
import crow.evilo.ui.EviloBarTransition
import crow.evilo.ui.EviloContentTransition
import crow.evilo.ui.EviloElevation
import crow.evilo.ui.EviloEmptyState
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloMotion
import crow.evilo.ui.EviloPageTransition
import crow.evilo.ui.EviloPredictiveBackHandler
import crow.evilo.ui.rememberEviloBackTransition
import crow.evilo.ui.EviloProgressIndicator
import crow.evilo.ui.EviloSearchField
import crow.evilo.ui.EviloSpacing
import crow.evilo.ui.EviloToastHost
import crow.evilo.ui.EviloToastState
import crow.evilo.ui.ImagePresentation
import crow.evilo.ui.LocalEviloToastState
import crow.evilo.ui.eviloTransitionInput
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun PluginHome() {
    val store = koinInject<PluginHomeStore>()
    val state by store.state.collectAsState()
    val toasts = remember { EviloToastState() }
    CompositionLocalProvider(LocalEviloToastState provides toasts) {
        PluginHomeScreen(state, koinInject(), koinInject(), store::dispatch)
    }
}

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
private fun PluginHomeScreen(state: PluginHomeState, images: EviloImages, host: HostDescriptor, dispatch: (PluginHomeIntent) -> Unit) {
    val page = state.page
    val active = state.connection.active?.takeIf { page is HomePage.PluginBrowser && it.installationKey == page.key }
    val backEnabled = page != null || state.overlay != null
    val focus = remember { FocusRequester() }
    val savedPages = rememberSaveableStateHolder()
    val backTransition = rememberEviloBackTransition(state)
    val previous = if (page != null && state.overlay == null) state.copy(pages = state.pages.dropLast(1),
        connection = if (page is HomePage.PluginBrowser) state.connection.copy(active = null, busy = false) else state.connection,
        actionError = null) else null
    val toasts = LocalEviloToastState.current
    val latestState by rememberUpdatedState(state)
    var homeToastId by remember { mutableStateOf<Long?>(null) }
    val error = state.actionError ?: state.connection.error?.takeIf { active == null && page !is HomePage.PluginBrowser }
    val errorTitle = stringResource(Res.string.home_error)
    val installedMessage = stringResource(Res.string.plugin_installed_notice)
    val browseAction = stringResource(Res.string.plugin_browse)
    val searchLabels = browseSearchLabels()
    LaunchedEffect(page, active) { if (active == null) focus.requestFocus() }
    LaunchedEffect(searchLabels) { dispatch(PluginHomeIntent.SetSearchLabels(searchLabels)) }
    LaunchedEffect(error, state.actionError, state.connection.error, state.notice) {
        homeToastId?.let { toasts.dismiss(it, notify = false) }
        val notice = state.notice
        homeToastId = when {
            error != null -> toasts.show(error, title = errorTitle, error = true, onDismiss = {
                dispatch(PluginHomeIntent.DismissTransientError(expectedActionError = state.actionError,
                    expectedConnectionError = state.connection.error?.takeIf { state.actionError == null }))
            })
            notice != null -> toasts.show(installedMessage, actionLabel = browseAction,
                onAction = { dispatch(PluginHomeIntent.OpenPlugin(notice.installationKey)) },
                onDismiss = { if (latestState.notice == notice) dispatch(PluginHomeIntent.DismissNotice) })
            else -> null
        }
    }
    EviloPredictiveBackHandler(backEnabled, backTransition, state, previous,
        navigationKey = state.pages to state.overlay, onBack = { dispatch(PluginHomeIntent.Back) })
    EviloPageTransition(state, contentKey = ::homePageKey, depth = { it.pages.size }, label = "home-page", backState = backTransition) { displayed ->
    val state = displayed
    val page = displayed.page
    val active = displayed.connection.active?.takeIf { page is HomePage.PluginBrowser && it.installationKey == page.key }
    BoxWithConstraints(Modifier.fillMaxSize().eviloTransitionInput(!backTransition.previewing).onPreviewKeyEvent {
        if (backEnabled && active == null && it.key == Key.Escape && it.type == KeyEventType.KeyUp) {
            dispatch(PluginHomeIntent.Back)
            true
        } else false
    }.focusRequester(focus).focusable()) {
        val expanded = maxWidth >= EviloLayout.RailBreakpoint
        Row(Modifier.fillMaxSize()) {
            AnimatedVisibility(expanded && page !is HomePage.PluginBrowser,
                modifier = Modifier.eviloTransitionInput(expanded && page !is HomePage.PluginBrowser),
                enter = fadeIn(EviloMotion.componentSpec()) + expandHorizontally(EviloMotion.spatialSpec(), expandFrom = Alignment.Start),
                exit = fadeOut(EviloMotion.exitSpec()) + shrinkHorizontally(EviloMotion.spatialSpec(), shrinkTowards = Alignment.Start)) {
                ExpandedHomeNavigation(state.destination, state.catalog.selectedKind,
                    { dispatch(PluginHomeIntent.Navigate(it)) }, { dispatch(PluginHomeIntent.ShowContentKinds) })
            }
            Scaffold(Modifier.weight(1f).fillMaxHeight(), containerColor = Color.Transparent,
                topBar = {
                    // Keep system insets even when the animated app-bar content has zero height.
                    Box(Modifier.fillMaxWidth().windowInsetsPadding(TopAppBarDefaults.windowInsets)) {
                        EviloBarTransition(active == null) {
                            HomeAppBar(state, dispatch)
                        }
                    }
                },
                bottomBar = {
                    Box(Modifier.fillMaxWidth().windowInsetsPadding(NavigationBarDefaults.windowInsets)) {
                        EviloBarTransition(!expanded && page == null, alignment = Alignment.Bottom) {
                            CompactHomeNavigation(state.destination, state.catalog.selectedKind,
                                { dispatch(PluginHomeIntent.Navigate(it)) }, { dispatch(PluginHomeIntent.ShowContentKinds) })
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                        val displayedPage = displayed.page
                        val contentWidth = when (displayedPage) {
                            null, is HomePage.SourceCatalog, is HomePage.PluginBrowser, HomePage.InstalledPlugins -> EviloLayout.ContentMaxWidth
                            is HomePage.Settings -> if (displayedPage.section == SettingsSection.ROOT) EviloLayout.ContentMaxWidth else EviloLayout.ReaderMaxWidth
                            else -> EviloLayout.ReaderMaxWidth
                        }
                        Box(Modifier.widthIn(max = contentWidth).fillMaxSize()) {
                            savedPages.SaveableStateProvider(homePageKey(displayed)) {
                                when (displayedPage) {
                                    null -> when (displayed.destination) {
                                        HomeDestination.BROWSE -> EviloContentTransition(displayed.catalog.selectedKind, label = "browse-kind") {
                                            kind -> savedPages.SaveableStateProvider("browse:$kind") {
                                                PluginBrowseScreen(displayed.copy(catalog = displayed.catalog.copy(selectedKind = kind),
                                                    browse = projectBrowse(displayed.catalog, displayed.entries, kind, displayed.query, displayed.browseSourceId, searchLabels)), images, dispatch,
                                                    enabled = kind == displayed.catalog.selectedKind)
                                            }
                                        }
                                        HomeDestination.MORE -> PluginMoreScreen(displayed, dispatch)
                                    }
                                    HomePage.Sources -> PluginSourcesScreen(displayed, dispatch)
                                    HomePage.AddSource -> AddPluginSourceScreen(displayed, dispatch)
                                    is HomePage.SourceCatalog -> SourceCatalogScreen(displayed, displayedPage.id, images, dispatch)
                                    is HomePage.SourceSettings -> PluginSourceSettingsScreen(displayed, displayedPage.id, dispatch)
                                    is HomePage.PluginDetail -> displayed.entries[displayedPage.key]?.let { PluginDetailScreen(displayed, it, images, dispatch) }
                                    is HomePage.ChooseSource -> ChoosePluginSourceScreen(displayed, displayedPage.pluginId, images, dispatch)
                                    HomePage.InstalledPlugins -> InstalledPluginsScreen(displayed, images, dispatch)
                                    is HomePage.Settings -> SettingsScreen(displayed, displayedPage.section, host, dispatch)
                                    HomePage.Safety -> AppSafetyScreen()
                                    HomePage.About -> AppAboutScreen(host)
                                    is HomePage.PluginBrowser -> {
                                        DisposableEffect(displayedPage) { onDispose { savedPages.removeState(displayedPage.toString()) } }
                                        EviloContentTransition(displayed, Modifier.fillMaxSize(), contentKey = {
                                            it.connection.active?.takeIf { connection -> connection.installationKey == displayedPage.key }?.session
                                        }, label = "plugin-connection") { browserState ->
                                            val connection = browserState.connection.active?.takeIf { it.installationKey == displayedPage.key }
                                            if (connection != null) key(connection.session) {
                                                MediaBrowserRoute(connection.session, browserState.catalog.selectedKind,
                                                    onClose = { dispatch(PluginHomeIntent.ClosePlugin) },
                                                    onReopen = { dispatch(PluginHomeIntent.OpenPlugin(displayedPage.key, rememberSource = false)) },
                                                    onPermissions = { dispatch(PluginHomeIntent.ManagePlugin(displayedPage.key)) },
                                                    loadTimings = browserState.connection.loadTimings)
                                            } else PluginLoadingScreen(browserState, displayedPage.key, dispatch)
                                        }
                                    }
                                }
                            }
                        }
                    EviloProgressIndicator(active == null && (state.changing || state.catalog.refreshing),
                        Modifier.align(Alignment.TopCenter).widthIn(max = EviloLayout.ContentMaxWidth).fillMaxWidth())
                    Box(Modifier.fillMaxSize().imePadding()) { EviloToastHost(toasts, expanded) }
                }
            }
        }
    }
    }
    when (val overlay = state.overlay) {
        HomeOverlay.ContentKinds -> ContentTypeSheet(state.catalog, state.kindCounts, enabled = state.canMutate,
            onDismiss = { dispatch(PluginHomeIntent.DismissOverlay) }, onSelect = { dispatch(PluginHomeIntent.SelectKind(it)) })
        HomeOverlay.BrowseSources -> BrowseSourcesDialog(state, dispatch)
        is HomeOverlay.RemoveSource -> SourceRemovalDialog(sourceName(state, overlay.id),
            state.catalog.installed.count { it.sourceId == overlay.id }, !state.canMutate,
            onDismiss = { dispatch(PluginHomeIntent.DismissOverlay) }, onConfirm = { dispatch(PluginHomeIntent.ConfirmSourceRemoval(overlay.id)) })
        is HomeOverlay.PermissionReview -> state.entries[overlay.key]?.let { entry ->
            val choice = entry.releases.firstOrNull { it.release == overlay.release }
            PluginPermissionDialog(entry, overlay.release, !state.canMutate,
                blocked = if (choice == null) stringResource(Res.string.plugin_release_changed) else pluginBlockLabel(choice.block) ?: choice.blocked,
                openAfterInstall = overlay.openAfterInstall,
                sharedPluginData = state.catalog.installed.any { it.plugin.id == entry.plugin.id },
                onDismiss = { dispatch(PluginHomeIntent.DismissOverlay) },
                onApprove = { publisherApproved -> dispatch(PluginHomeIntent.ApproveInstall(overlay, publisherApproved)) })
        }
        is HomeOverlay.Uninstall -> state.entries[overlay.key]?.let { entry ->
            PluginUninstallDialog(entry.plugin.name, sourceName(state, entry.sourceId),
                (state.catalog.installed.count { it.plugin.id == entry.plugin.id } - 1).coerceAtLeast(0), !state.canMutate,
                onDismiss = { dispatch(PluginHomeIntent.DismissOverlay) }, onConfirm = { dispatch(PluginHomeIntent.ConfirmUninstall(entry.key)) })
        }
        null -> Unit
    }
}

private fun homePageKey(state: PluginHomeState): String = state.page?.toString() ?: "root:${state.destination}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeAppBar(state: PluginHomeState, dispatch: (PluginHomeIntent) -> Unit) {
    TopAppBar(title = { EviloContentTransition(pageTitle(state), label = "home-title") {
            Text(it, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } },
        navigationIcon = { AnimatedVisibility(state.page != null,
            enter = fadeIn(EviloMotion.componentSpec()) + expandHorizontally(EviloMotion.componentSpec(), expandFrom = Alignment.Start),
            exit = fadeOut(EviloMotion.exitSpec()) + shrinkHorizontally(EviloMotion.componentSpec(), shrinkTowards = Alignment.Start)) {
            IconButton(onClick = { dispatch(PluginHomeIntent.Back) }) { Icon(EviloIcons.Back, stringResource(Res.string.source_back)) }
        } },
        actions = {
            if (state.connection.busy) TextButton(onClick = { dispatch(PluginHomeIntent.ClosePlugin) }) { Text(stringResource(Res.string.home_cancel)) }
            when (val page = state.page) {
                null -> if (state.destination == HomeDestination.BROWSE) IconButton(onClick = { dispatch(PluginHomeIntent.ShowSources) }) {
                    Icon(EviloIcons.Link, stringResource(Res.string.home_manage_sources))
                }
                HomePage.Sources -> {
                    IconButton(onClick = { dispatch(PluginHomeIntent.ShowAddSource) }) { Icon(EviloIcons.Add, stringResource(Res.string.source_add_title)) }
                    var menu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(EviloIcons.More, stringResource(Res.string.source_check_all)) }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(Res.string.source_check_all)) },
                                onClick = { menu = false; dispatch(PluginHomeIntent.Refresh) },
                                enabled = !state.catalog.refreshing && state.catalog.subscriptions.any { it.enabled })
                        }
                    }
                }
                is HomePage.SourceCatalog -> {
                    IconButton(onClick = { dispatch(PluginHomeIntent.ShowContentKinds) }) {
                        Icon(EviloIcons.kind(state.catalog.selectedKind), stringResource(Res.string.home_select_kind))
                    }
                    IconButton(onClick = { dispatch(PluginHomeIntent.RefreshSource(page.id)) },
                        enabled = !state.catalog.refreshing && state.catalog.subscriptions.any { it.id == page.id && it.enabled }) {
                        Icon(EviloIcons.Refresh, stringResource(Res.string.source_check_updates))
                    }
                    IconButton(onClick = { dispatch(PluginHomeIntent.ShowSourceSettings(page.id)) }) { Icon(EviloIcons.Settings, stringResource(Res.string.source_settings)) }
                }
                else -> Unit
            }
        }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
}

@Composable
private fun pageTitle(state: PluginHomeState): String = when (val page = state.page) {
    null -> stringResource(if (state.destination == HomeDestination.BROWSE) Res.string.home_browse else Res.string.home_more)
    HomePage.Sources -> stringResource(Res.string.source_title)
    HomePage.AddSource -> stringResource(Res.string.source_add_title)
    is HomePage.SourceCatalog -> sourceName(state, page.id)
    is HomePage.SourceSettings -> stringResource(Res.string.source_settings)
    is HomePage.PluginDetail -> stringResource(Res.string.plugin_details)
    is HomePage.ChooseSource -> stringResource(Res.string.plugin_choose_source)
    HomePage.InstalledPlugins -> stringResource(Res.string.browse_manage_installed)
    is HomePage.Settings -> settingsSectionTitle(page.section)
    HomePage.Safety -> stringResource(Res.string.more_safety)
    HomePage.About -> stringResource(Res.string.settings_about)
    is HomePage.PluginBrowser -> state.entries[page.key]?.plugin?.name ?: stringResource(Res.string.home_browse)
}

@Composable
private fun PluginBrowseScreen(state: PluginHomeState, images: EviloImages, dispatch: (PluginHomeIntent) -> Unit, enabled: Boolean = true) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val padding = if (maxWidth >= EviloLayout.ExpandedBreakpoint) EviloLayout.WidePagePadding else EviloLayout.PagePadding
        LazyVerticalGrid(GridCells.Adaptive(EviloLayout.PluginCardMinWidth), Modifier.fillMaxSize(), contentPadding = PaddingValues(padding),
            horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        EviloSearchField(state.query, { dispatch(PluginHomeIntent.Search(it)) }, stringResource(Res.string.browse_search_installed), Modifier.weight(1f), enabled = enabled)
                        val filterDescription = state.browseSourceId?.let { sourceName(state, it) } ?: stringResource(Res.string.browse_all_sources)
                        IconButton(onClick = { dispatch(PluginHomeIntent.ShowBrowseSources) }, enabled = enabled, modifier = Modifier.semantics { stateDescription = filterDescription }) {
                            Icon(EviloIcons.Tune, stringResource(Res.string.browse_filter_source),
                                tint = if (state.browseSourceId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    state.browseSourceId?.let { Text(sourceName(state, it), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (state.query.isNotBlank()) Text(stringResource(Res.string.home_result_count, state.browse.count), style = MaterialTheme.typography.labelMedium)
                    if (state.browse.hiddenNsfw > 0) {
                        Text(stringResource(Res.string.home_hidden_nsfw, state.browse.hiddenNsfw), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { dispatch(PluginHomeIntent.ShowSettings(SettingsSection.BROWSE)) }, enabled = enabled) { Text(stringResource(Res.string.settings_browse)) }
                    }
                }
            }
            state.browse.groups.forEach { group ->
                item(key = "group:${group.sourceId}", span = { GridItemSpan(maxLineSpan) }) {
                    val sourceId = group.sourceId
                    val name = group.name ?: sourceId?.let { sourceName(state, it) } ?: stringResource(Res.string.browse_pending_source)
                    if (sourceId != null) TextButton(onClick = { dispatch(PluginHomeIntent.ShowSourceCatalog(sourceId)) }, enabled = enabled) {
                        Text(stringResource(Res.string.browse_source_group, name, group.items.size), style = MaterialTheme.typography.titleMedium)
                    } else Text(stringResource(Res.string.browse_source_group, name, group.items.size), style = MaterialTheme.typography.titleMedium)
                }
                items(group.items, key = { it.key }) { row ->
                    val onUse = {
                        if (row.needsSourceChoice) dispatch(PluginHomeIntent.ChoosePluginSource(row.pluginId))
                        else dispatch(PluginHomeIntent.OpenPlugin(checkNotNull(row.installationKey), rememberSource = state.browseSourceId == null))
                    }
                    PluginRow(row.entry, images, enabled = enabled && !state.changing, nsfw = row.nsfw,
                        status = if (row.needsSourceChoice) stringResource(Res.string.plugin_choose_source) else null,
                        onOpen = onUse, onSettings = {
                            dispatch(PluginHomeIntent.ManagePlugin(row.entry.key, rememberSourceOnOpen = !row.needsSourceChoice && state.browseSourceId == null))
                        })
                }
            }
            if (state.browse.count == 0) item(span = { GridItemSpan(maxLineSpan) }) { BrowseEmptyState(state, dispatch, enabled) }
        }
    }
}

@Composable
private fun BrowseEmptyState(state: PluginHomeState, dispatch: (PluginHomeIntent) -> Unit, enabled: Boolean) {
    val ready = state.catalog.subscriptions.any { it.id != BUNDLED_SOURCE_ID } ||
        state.catalog.snapshots.any { it.catalog.plugins.isNotEmpty() }
    val title = when (state.browse.emptyReason) {
        BrowseEmptyReason.NO_INSTALLATIONS -> if (ready) Res.string.browse_sources_ready else Res.string.browse_empty_installed
        BrowseEmptyReason.SOURCES_DISABLED -> Res.string.browse_disabled_title
        BrowseEmptyReason.UNSUPPORTED_KIND -> Res.string.browse_kind_empty
        BrowseEmptyReason.NSFW_HIDDEN -> Res.string.home_hidden_title
        BrowseEmptyReason.SOURCE_FILTER -> Res.string.browse_source_empty
        else -> Res.string.home_no_matches
    }
    val description = when (state.browse.emptyReason) {
        BrowseEmptyReason.NO_INSTALLATIONS -> if (ready) Res.string.browse_sources_ready_hint else Res.string.browse_empty_install_hint
        BrowseEmptyReason.SOURCES_DISABLED -> Res.string.browse_disabled_description
        BrowseEmptyReason.UNSUPPORTED_KIND -> Res.string.browse_kind_description
        BrowseEmptyReason.NSFW_HIDDEN -> Res.string.more_nsfw_description
        BrowseEmptyReason.SOURCE_FILTER -> Res.string.browse_source_description
        else -> Res.string.home_no_matches_description
    }
    EviloEmptyState(EviloIcons.kind(state.catalog.selectedKind), stringResource(title), stringResource(description), Modifier.fillMaxWidth()) {
        when (state.browse.emptyReason) {
            BrowseEmptyReason.NO_MATCHES, BrowseEmptyReason.SOURCE_FILTER -> TextButton(onClick = {
                dispatch(PluginHomeIntent.Search("")); dispatch(PluginHomeIntent.SelectBrowseSource(null))
            }, enabled = enabled) { Text(stringResource(Res.string.home_clear_filters)) }
            BrowseEmptyReason.NSFW_HIDDEN -> Button(onClick = { dispatch(PluginHomeIntent.ShowSettings(SettingsSection.BROWSE)) }, enabled = enabled) { Text(stringResource(Res.string.settings_browse)) }
            BrowseEmptyReason.UNSUPPORTED_KIND -> Button(onClick = { dispatch(PluginHomeIntent.ShowContentKinds) }, enabled = enabled) { Text(stringResource(Res.string.home_select_kind)) }
            else -> Button(onClick = { dispatch(PluginHomeIntent.ShowSources) }, enabled = enabled) { Text(stringResource(Res.string.home_manage_sources)) }
        }
    }
}

@Composable
private fun InstalledPluginsScreen(state: PluginHomeState, images: EviloImages, dispatch: (PluginHomeIntent) -> Unit) {
    val projection = projectInstalledManagement(state)
    LazyVerticalGrid(GridCells.Adaptive(EviloLayout.PluginCardMinWidth), Modifier.fillMaxSize(), contentPadding = PaddingValues(EviloLayout.PagePadding),
        horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            EviloSearchField(state.installedQuery, { dispatch(PluginHomeIntent.SearchInstalled(it)) }, stringResource(Res.string.browse_search_installed), Modifier.fillMaxWidth())
        }
        if (projection.hiddenNsfw > 0) item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = { dispatch(PluginHomeIntent.ShowSettings(SettingsSection.BROWSE)) }) {
                Text(stringResource(Res.string.home_hidden_nsfw, projection.hiddenNsfw))
            }
        }
        projection.groups.forEach { group ->
            item(key = "installed:${group.sourceId}", span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(Res.string.browse_source_group, sourceName(state, group.sourceId), group.entries.size), style = MaterialTheme.typography.titleMedium)
            }
            items(group.entries, key = { it.key }) { entry -> PluginRow(entry, images, !state.changing, compact = false,
                onOpen = { dispatch(PluginHomeIntent.ManagePlugin(entry.key)) }, onSettings = { dispatch(PluginHomeIntent.ManagePlugin(entry.key)) }) }
        }
        if (projection.groups.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            val onlyHidden = state.catalog.installed.isNotEmpty() && projection.hiddenNsfw == state.catalog.installed.size
            EviloEmptyState(EviloIcons.Book, stringResource(if (onlyHidden) Res.string.home_hidden_title else Res.string.more_no_installed),
                stringResource(if (onlyHidden) Res.string.more_nsfw_description else if (state.catalog.installed.isEmpty()) Res.string.more_install_hint else Res.string.home_no_matches_description),
                Modifier.fillMaxWidth()) {
                if (onlyHidden) Button(onClick = { dispatch(PluginHomeIntent.ShowSettings(SettingsSection.BROWSE)) }) { Text(stringResource(Res.string.settings_browse)) }
                else if (state.installedQuery.isNotBlank()) TextButton(onClick = { dispatch(PluginHomeIntent.SearchInstalled("")) }) { Text(stringResource(Res.string.home_clear_search)) }
                else Button(onClick = { dispatch(PluginHomeIntent.ShowSources) }) { Text(stringResource(Res.string.home_manage_sources)) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginRow(entry: PluginEntry, images: EviloImages, enabled: Boolean, onOpen: () -> Unit, onSettings: () -> Unit,
    compact: Boolean = true, nsfw: Boolean = entry.plugin.nsfw, status: String? = null) {
    val plugin = entry.plugin
    val colors = MaterialTheme.colorScheme
    val nameLines = if (LocalDensity.current.fontScale > 1.2f) 2 else 1
    val languages = if (compact) entry.installation?.release?.languages ?: plugin.languages else plugin.languages
    val update = entry.installation?.let { installed -> entry.recommended?.versionCode?.let { it > installed.release.versionCode } } == true
    Card(onClick = onOpen, enabled = enabled, shape = MaterialTheme.shapes.large,
        border = BorderStroke(EviloLayout.BorderWidth, colors.outlineVariant), elevation = CardDefaults.cardElevation(defaultElevation = EviloElevation.Flat),
        colors = CardDefaults.cardColors(containerColor = colors.surface, disabledContainerColor = colors.surface)) {
        Column(Modifier.fillMaxWidth().padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
            Row(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                PluginAvatar(plugin.name, plugin.iconUrl, entry.allowHttp, images)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                    Text(plugin.name, style = MaterialTheme.typography.titleMedium, maxLines = nameLines, overflow = TextOverflow.Ellipsis)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                        PluginBadge(stringResource(if (plugin.overseas) Res.string.plugin_overseas else Res.string.plugin_domestic))
                        if (nsfw) PluginBadge(stringResource(Res.string.plugin_nsfw), warning = true)
                        if (update) PluginBadge(stringResource(Res.string.plugin_update_available))
                        if (!nsfw && !update && languages.size == 1) PluginBadge(languages.single())
                    }
                }
                IconButton(onClick = onSettings, enabled = enabled, modifier = Modifier.size(EviloLayout.MinimumTouchTarget).clip(MaterialTheme.shapes.medium).background(colors.surfaceContainer)) {
                    Icon(EviloIcons.Info, stringResource(Res.string.plugin_about_for, plugin.name), tint = colors.onSurfaceVariant)
                }
            }
            if (!compact) {
                Text(plugin.description, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(entry.installation?.let { stringResource(Res.string.plugin_installed_version, it.release.version) } ?: stringResource(Res.string.plugin_not_installed),
                    style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            (status ?: pluginBlockLabel(entry.openBlock))?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
internal fun PluginBadge(text: String, warning: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.small, color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (warning) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(text, Modifier.padding(horizontal = EviloSpacing.Small, vertical = EviloSpacing.ExtraSmall), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun PluginAvatar(name: String, url: String, allowHttp: Boolean, images: EviloImages, attempt: Int = 0, onFailure: ((ImageFailure?) -> Unit)? = null) {
    val request = remember(url, allowHttp, images, attempt) { if (url.isBlank()) null else images.iconRequest(url, allowHttp, attempt) }
    EviloAsyncImage(request, images.loader, name, Modifier.size(EviloLayout.AvatarSize).clip(MaterialTheme.shapes.medium), contentScale = ContentScale.Fit,
        placeholder = { Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) },
        presentation = ImagePresentation.ICON, address = url, onFailure = onFailure)
}

@Composable
private fun PluginLoadingScreen(state: PluginHomeState, key: String, dispatch: (PluginHomeIntent) -> Unit) {
    val loading = state.connection.busy
    val message = if (loading) pluginLoadPhaseLabel(state.connection.phase) else state.connection.error ?: stringResource(Res.string.plugin_session_closed)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(EviloLayout.PagePadding),
        verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large, Alignment.CenterVertically)) {
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        Text(message, style = MaterialTheme.typography.bodyMedium, color = if (loading) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
        PluginLoadDiagnostics(state.connection.loadTimings)
        if (!loading) {
            Button(onClick = { dispatch(PluginHomeIntent.OpenPlugin(key, rememberSource = false)) }) { Text(stringResource(Res.string.source_retry)) }
            TextButton(onClick = { dispatch(PluginHomeIntent.ManagePlugin(key)) }) { Text(stringResource(Res.string.plugin_details)) }
        }
        TextButton(onClick = { dispatch(PluginHomeIntent.ClosePlugin) }) { Text(stringResource(if (loading) Res.string.home_cancel else Res.string.source_back)) }
    }
}

@Composable
private fun BrowseSourcesDialog(state: PluginHomeState, dispatch: (PluginHomeIntent) -> Unit) {
    AlertDialog(onDismissRequest = { dispatch(PluginHomeIntent.DismissOverlay) }, title = { Text(stringResource(Res.string.browse_filter_source)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Surface(onClick = { dispatch(PluginHomeIntent.SelectBrowseSource(null)) }, color = Color.Transparent) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(state.browseSourceId == null, onClick = null)
                        Text(stringResource(Res.string.browse_all_sources))
                    }
                }
                state.catalog.subscriptions.filter { it.enabled }.forEach { source ->
                    Surface(onClick = { dispatch(PluginHomeIntent.SelectBrowseSource(source.id)) }, color = Color.Transparent) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(state.browseSourceId == source.id, onClick = null)
                            Text(sourceName(state, source.id), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = { dispatch(PluginHomeIntent.DismissOverlay) }) { Text(stringResource(Res.string.home_cancel)) } })
}

