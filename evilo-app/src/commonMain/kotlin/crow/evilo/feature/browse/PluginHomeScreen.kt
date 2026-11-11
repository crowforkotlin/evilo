package crow.evilo.feature.browse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import crow.evilo.app.EviloImages
import crow.evilo.app.resources.*
import crow.evilo.feature.reader.MediaBrowserRoute
import crow.evilo.model.MediaKind
import crow.evilo.plugin.api.HostDescriptor
import crow.evilo.ui.EviloAsyncImage
import crow.evilo.ui.EviloElevation
import crow.evilo.ui.EviloEmptyState
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloSearchField
import crow.evilo.ui.EviloSectionHeading
import crow.evilo.ui.EviloSpacing
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

@Composable
internal fun PluginHome() {
    val store = koinInject<PluginHomeStore>()
    val images = koinInject<EviloImages>()
    val host = koinInject<HostDescriptor>()
    val state by store.state.collectAsState()
    PluginHomeScreen(state, images, host, store::dispatch)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PluginHomeScreen(state: PluginHomeState, images: EviloImages, host: HostDescriptor, dispatch: (PluginHomeIntent) -> Unit) {
    val active = state.connection.active.takeIf { state.destination == HomeDestination.BROWSE }
    val onDestination: (HomeDestination) -> Unit = { dispatch(PluginHomeIntent.Navigate(it)) }
    val onKinds = { dispatch(PluginHomeIntent.ShowContentKinds) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= EviloLayout.RailBreakpoint
        Row(Modifier.fillMaxSize()) {
            if (expanded) ExpandedHomeNavigation(state.destination, state.catalog.selectedKind, onDestination, onKinds)
            Scaffold(
                modifier = Modifier.weight(1f).fillMaxHeight(), containerColor = Color.Transparent,
                topBar = {
                    if (active == null) TopAppBar(title = {
                        Text(stringResource(if (state.destination == HomeDestination.BROWSE) Res.string.home_browse else Res.string.home_more),
                            style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }, actions = {
                        if (state.connection.busy) TextButton(onClick = { dispatch(PluginHomeIntent.ClosePlugin) }) {
                            Text(stringResource(Res.string.home_cancel))
                        }
                        if (state.destination == HomeDestination.BROWSE) {
                            IconButton(onClick = { dispatch(PluginHomeIntent.Refresh) }, enabled = !state.busy) {
                                Icon(EviloIcons.Refresh, stringResource(Res.string.home_refresh))
                            }
                            IconButton(onClick = { dispatch(PluginHomeIntent.ShowSources) }) {
                                Icon(EviloIcons.Add, stringResource(Res.string.home_add_source))
                            }
                        }
                    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent))
                },
                bottomBar = {
                    if (!expanded) CompactHomeNavigation(state.destination, state.catalog.selectedKind, onDestination, onKinds)
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = EviloLayout.ContentMaxWidth).fillMaxSize()) {
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                        state.error?.let { HomeError(it) { dispatch(PluginHomeIntent.DismissError) } }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            if (active != null) MediaBrowserRoute(active.session, state.catalog.selectedKind,
                                onClose = { dispatch(PluginHomeIntent.ClosePlugin) })
                            else when (state.destination) {
                                HomeDestination.BROWSE -> AnimatedContent(targetState = state.catalog.selectedKind,
                                    transitionSpec = { fadeIn(tween(140, delayMillis = 60)) togetherWith fadeOut(tween(60)) },
                                    label = "content-kind") { kind -> PluginBrowseScreen(state, kind, images, dispatch) }
                                HomeDestination.MORE -> PluginMoreScreen(state, images, host, dispatch)
                            }
                        }
                    }
                }
            }
        }
    }
    when (val overlay = state.overlay) {
        HomeOverlay.ContentKinds -> ContentTypeSheet(state.catalog, enabled = !state.changing,
            onDismiss = { dispatch(PluginHomeIntent.DismissOverlay) }, onSelect = { dispatch(PluginHomeIntent.SelectKind(it)) })
        HomeOverlay.Sources -> PluginSourcesSheet(state.catalog, state.busy, state.error,
            onDismiss = { dispatch(PluginHomeIntent.DismissOverlay) },
            onAdd = { url, allowHttp -> dispatch(PluginHomeIntent.AddSource(url, allowHttp)) },
            onEnabled = { id, enabled -> dispatch(PluginHomeIntent.EnableSource(id, enabled)) },
            onRemove = { dispatch(PluginHomeIntent.RequestSourceRemoval(it)) })
        is HomeOverlay.RemoveSource -> SourceRemovalDialog(state.busy,
            onDismiss = { dispatch(PluginHomeIntent.DismissOverlay) },
            onConfirm = { dispatch(PluginHomeIntent.ConfirmSourceRemoval(overlay.id)) })
        HomeOverlay.Safety -> PluginSafetyDialog { dispatch(PluginHomeIntent.DismissOverlay) }
        is HomeOverlay.PluginSettings -> state.entries[overlay.key]?.let { entry ->
            PluginSettingsSheet(entry, images, state.busy, state.error,
                onDismiss = { dispatch(PluginHomeIntent.DismissOverlay) },
                onReview = { dispatch(PluginHomeIntent.ReviewRelease(entry.key, it.versionCode)) },
                onOpen = { dispatch(PluginHomeIntent.OpenPlugin(entry.key)) },
                onUninstall = { dispatch(PluginHomeIntent.RequestUninstall(entry.key)) })
        }
        is HomeOverlay.PermissionReview -> state.entries[overlay.key]?.let { entry ->
            val choice = entry.releases.firstOrNull { it.release == overlay.release }
            PluginPermissionDialog(entry, overlay.release, state.busy,
                blocked = if (choice == null) stringResource(Res.string.plugin_release_changed) else choice.blocked,
                error = state.error, onDismiss = { dispatch(PluginHomeIntent.DismissOverlay) },
                onApprove = { dispatch(PluginHomeIntent.ApproveInstall(overlay)) })
        }
        is HomeOverlay.Uninstall -> state.entries[overlay.key]?.let { entry ->
            PluginUninstallDialog(entry.plugin.name, state.busy,
                onDismiss = { dispatch(PluginHomeIntent.DismissOverlay) },
                onConfirm = { dispatch(PluginHomeIntent.ConfirmUninstall(entry.key)) })
        }
        null -> Unit
    }
}

@Composable
private fun PluginBrowseScreen(state: PluginHomeState, kind: MediaKind, images: EviloImages, dispatch: (PluginHomeIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val available = state.entries.values.filter { it.listing?.snapshot?.subscription?.enabled == true && kind in it.plugin.kinds }
    val entries = available.filter { state.catalog.showNsfw || !it.plugin.nsfw }
    val hiddenCount = available.size - entries.size
    val query = state.query.trim()
    val filtered = entries.filter { entry ->
        val matchesFilter = when (state.filter) {
            CatalogFilter.ALL -> true
            CatalogFilter.INSTALLED -> entry.installation != null
            CatalogFilter.CERTIFIED -> entry.officiallyCertified
        }
        matchesFilter && (query.isEmpty() || (entry.plugin.name + " " + entry.plugin.description + " " +
            entry.plugin.authors.joinToString { it.name }).contains(query, ignoreCase = true))
    }
    val onSources = { dispatch(PluginHomeIntent.ShowSources) }
    val onPreferences = { dispatch(PluginHomeIntent.Navigate(HomeDestination.MORE)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val padding = if (maxWidth >= EviloLayout.ExpandedBreakpoint) EviloLayout.WidePagePadding else EviloLayout.PagePadding
        LazyVerticalGrid(columns = GridCells.Adaptive(EviloLayout.PluginCardMinWidth), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = padding, end = padding, bottom = padding),
            horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large),
                        verticalAlignment = Alignment.CenterVertically) {
                        EviloSectionHeading(stringResource(Res.string.home_browse_title, mediaKindLabel(kind)),
                            stringResource(Res.string.home_source_count, entries.size, state.catalog.subscriptions.count { it.enabled }), Modifier.weight(1f))
                        TextButton(onClick = onSources) {
                            Icon(EviloIcons.Link, null, Modifier.size(EviloLayout.InlineIconSize))
                            Spacer(Modifier.width(EviloSpacing.Small))
                            Text(stringResource(Res.string.home_manage_sources))
                        }
                    }
                    EviloSearchField(state.query, { dispatch(PluginHomeIntent.Search(it)) }, stringResource(Res.string.home_search), Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        CatalogFilter.entries.forEach { option ->
                            FilterChip(selected = state.filter == option, onClick = { dispatch(PluginHomeIntent.Filter(option)) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = colors.primaryContainer,
                                    selectedLabelColor = colors.onPrimaryContainer),
                                label = { Text(stringResource(when (option) {
                                    CatalogFilter.ALL -> Res.string.home_filter_all
                                    CatalogFilter.INSTALLED -> Res.string.home_filter_installed
                                    CatalogFilter.CERTIFIED -> Res.string.home_filter_official
                                })) })
                        }
                    }
                    if (hiddenCount > 0 && entries.isNotEmpty()) Surface(shape = MaterialTheme.shapes.medium, color = colors.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(EviloSpacing.Medium)) {
                            Text(stringResource(Res.string.home_hidden_nsfw, hiddenCount), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                            TextButton(onClick = onPreferences) { Text(stringResource(Res.string.more_preferences)) }
                        }
                    }
                    Text(stringResource(Res.string.home_result_count, filtered.size), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                }
            }
            if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                EviloEmptyState(EviloIcons.kind(kind), stringResource(when {
                    entries.isNotEmpty() -> Res.string.home_no_matches
                    hiddenCount > 0 -> Res.string.home_hidden_title
                    else -> Res.string.home_empty_title
                }), when {
                    entries.isNotEmpty() -> stringResource(Res.string.home_no_matches_description)
                    hiddenCount > 0 -> stringResource(Res.string.home_hidden_description, hiddenCount)
                    else -> stringResource(Res.string.home_empty_description)
                }, Modifier.fillMaxWidth()) {
                    if (entries.isEmpty()) Button(onClick = if (hiddenCount > 0) onPreferences else onSources) {
                        Text(stringResource(if (hiddenCount > 0) Res.string.more_preferences else Res.string.home_add_source))
                    } else TextButton(onClick = {
                        dispatch(PluginHomeIntent.Search(""))
                        dispatch(PluginHomeIntent.Filter(CatalogFilter.ALL))
                    }) { Text(stringResource(Res.string.home_clear_filters)) }
                }
            }
            items(filtered, key = { it.key }) { entry -> PluginRow(entry, images, !state.busy,
                onOpen = { dispatch(PluginHomeIntent.OpenPlugin(entry.key)) }, onSettings = { dispatch(PluginHomeIntent.ManagePlugin(entry.key)) }) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginRow(entry: PluginEntry, images: EviloImages, enabled: Boolean, onOpen: () -> Unit, onSettings: () -> Unit) {
    val plugin = entry.plugin
    val colors = MaterialTheme.colorScheme
    Card(onClick = onOpen, enabled = enabled, shape = MaterialTheme.shapes.large,
        border = BorderStroke(EviloLayout.BorderWidth, colors.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = EviloElevation.Flat),
        colors = CardDefaults.cardColors(containerColor = colors.surface, disabledContainerColor = colors.surface,
            disabledContentColor = colors.onSurface)) {
        Column(Modifier.fillMaxWidth().padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
            Row(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                PluginAvatar(plugin.name, plugin.iconUrl, entry.allowHttp, images)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                    Text(plugin.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(plugin.authors.joinToString { it.name }, style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = onSettings, enabled = enabled,
                    modifier = Modifier.size(EviloLayout.MinimumTouchTarget).clip(MaterialTheme.shapes.medium).background(colors.surfaceContainer)) {
                    Icon(EviloIcons.Settings, stringResource(Res.string.plugin_settings_for, plugin.name), tint = colors.onSurfaceVariant)
                }
                Surface(Modifier.size(EviloLayout.RowIndicatorSize), shape = MaterialTheme.shapes.small,
                    color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                    Box(contentAlignment = Alignment.Center) { Icon(EviloIcons.ChevronRight, null, Modifier.size(EviloLayout.InlineIconSize)) }
                }
            }
            Text(plugin.description, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                color = colors.onSurfaceVariant, overflow = TextOverflow.Ellipsis)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                entry.installation?.let { PluginBadge(stringResource(Res.string.plugin_installed_version, it.release.version)) }
                if (entry.officiallyCertified) PluginBadge(stringResource(Res.string.plugin_official))
                if (plugin.nsfw) PluginBadge(stringResource(Res.string.plugin_nsfw), warning = true)
                Text(plugin.languages.joinToString() + " | " + stringResource(if (plugin.overseas) Res.string.plugin_overseas else Res.string.plugin_domestic),
                    Modifier.padding(vertical = EviloSpacing.ExtraSmall), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
            entry.openBlockReason?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error, maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
internal fun PluginBadge(text: String, warning: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.small, color = if (warning) colors.errorContainer else colors.surfaceContainer,
        contentColor = if (warning) colors.onErrorContainer else colors.onSurfaceVariant) {
        Text(text, Modifier.padding(horizontal = EviloSpacing.Small, vertical = EviloSpacing.ExtraSmall), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun PluginAvatar(name: String, url: String, allowHttp: Boolean, images: EviloImages) {
    val request = remember(url, allowHttp, images) { images.iconRequest(url, allowHttp) }
    EviloAsyncImage(request, images.loader, name, Modifier.size(EviloLayout.AvatarSize).clip(MaterialTheme.shapes.medium),
        contentScale = ContentScale.Fit, placeholder = {
            Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        })
}

@Composable
private fun HomeError(message: String, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = EviloLayout.PagePadding, vertical = EviloSpacing.Small),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
            Text(stringResource(Res.string.home_error), style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_dismiss)) }
        }
    }
}
