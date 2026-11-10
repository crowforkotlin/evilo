package crow.evilo.feature.browse

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import crow.evilo.model.MediaKind
import crow.evilo.plugin.catalog.CatalogListing
import crow.evilo.plugin.catalog.InstalledPlugin
import crow.evilo.plugin.catalog.PluginCatalogRepository
import crow.evilo.plugin.catalog.PluginRelease
import crow.evilo.plugin.catalog.PluginRepositoryState
import crow.evilo.ui.EviloLayout
import crow.evilo.app.resources.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginHome(
    repository: PluginCatalogRepository,
    iconLoader: PluginIconLoader,
    destination: HomeDestination,
    onDestination: (HomeDestination) -> Unit,
    onMediaChanged: () -> Unit,
    onInstall: (CatalogListing, PluginRelease) -> Unit,
    onOpen: (InstalledPlugin) -> Unit,
    busy: Boolean,
    error: String?,
    onCancel: () -> Unit,
    onDismissError: () -> Unit,
    contentOverride: (@Composable () -> Unit)? = null,
) {
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    var showKinds by remember { mutableStateOf(false) }
    var showSources by remember { mutableStateOf(false) }
    var selectedListing by remember { mutableStateOf<CatalogListing?>(null) }
    var localBusy by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    val working = busy || localBusy || state.refreshing
    fun perform(onSuccess: () -> Unit = {}, action: suspend () -> Unit) {
        scope.launch {
            localBusy = true
            localError = null
            try {
                withContext(Dispatchers.Default) { action() }
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                localError = (failure.message ?: failure.toString()).take(2000)
            } finally {
                localBusy = false
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val expanded = maxWidth >= EviloLayout.RailBreakpoint
        Row(Modifier.fillMaxSize()) {
            if (expanded) {
                NavigationRail(modifier = Modifier.fillMaxHeight().width(104.dp).verticalScroll(rememberScrollState()),
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Spacer(Modifier.height(24.dp))
                    Text("evilo", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(20.dp))
                    FloatingActionButton(onClick = { showKinds = true }, containerColor = MaterialTheme.colorScheme.primaryContainer) {
                        Icon(BrowseIcons.kind(state.selectedKind), stringResource(Res.string.home_select_kind))
                    }
                    Text(mediaKindLabel(state.selectedKind), Modifier.padding(vertical = 12.dp), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(12.dp))
                    HomeDestination.entries.forEach { tab ->
                        NavigationRailItem(selected = destination == tab, onClick = { onDestination(tab) },
                            icon = { Icon(if (tab == HomeDestination.BROWSE) BrowseIcons.Browse else BrowseIcons.More, null) },
                            label = { Text(stringResource(if (tab == HomeDestination.BROWSE) Res.string.home_browse else Res.string.home_more)) })
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            Scaffold(
                modifier = Modifier.weight(1f).fillMaxHeight(), containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(title = {
                        Column {
                            Text(stringResource(if (destination == HomeDestination.BROWSE) Res.string.home_browse else Res.string.home_more),
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(mediaKindLabel(state.selectedKind), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }, actions = {
                        if (busy) TextButton(onClick = onCancel) { Text(stringResource(Res.string.home_cancel)) }
                        IconButton(onClick = { perform { repository.refresh() } }, enabled = !working) {
                            Icon(BrowseIcons.Refresh, stringResource(Res.string.home_refresh))
                        }
                        IconButton(onClick = { showSources = true }) { Icon(BrowseIcons.Add, stringResource(Res.string.home_manage_sources)) }
                    })
                },
                bottomBar = {
                    if (!expanded) CompactHomeNavigation(destination, state.selectedKind, onDestination, { showKinds = true })
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    Column(Modifier.widthIn(max = EviloLayout.ContentMaxWidth).fillMaxSize()) {
                        if (working) LinearProgressIndicator(Modifier.fillMaxWidth())
                        val message = localError ?: error ?: state.error
                        message?.let {
                            HomeError(it, onDismiss = { localError = null; repository.dismissError(); onDismissError() })
                        }
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            if (contentOverride != null && destination == HomeDestination.BROWSE) contentOverride()
                            else when (destination) {
                                HomeDestination.BROWSE -> AnimatedContent(targetState = state.selectedKind, label = "content-kind") { kind ->
                                    PluginBrowseScreen(state, kind, iconLoader, { selectedListing = it }, { showSources = true })
                                }
                                HomeDestination.MORE -> PluginMoreScreen(repository, state, iconLoader, onOpen,
                                    onPlugin = { selectedListing = it }, onSources = { showSources = true }, enabled = !working,
                                    onNsfw = { show -> perform { repository.setShowNsfw(show) } },
                                    onUninstall = { installation -> perform { repository.uninstall(installation.key, eraseData = true) } })
                            }
                        }
                    }
                }
            }
        }
    }
    if (showKinds) ModalBottomSheet(onDismissRequest = { showKinds = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(Res.string.home_select_kind), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(Res.string.home_kind_description), style = MaterialTheme.typography.bodyMedium)
            MediaKind.entries.forEach { kind ->
                Surface(onClick = {
                    showKinds = false
                    if (kind != state.selectedKind) onMediaChanged()
                    perform { repository.selectKind(kind) }
                }, color = if (kind == state.selectedKind) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.large) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(BrowseIcons.kind(kind), null)
                        Text(mediaKindLabel(kind), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
    if (showSources) PluginSourcesSheet(state, working, localError, onDismiss = { showSources = false },
        onAdd = { url, allowHttp -> perform { repository.addSource(url, allowHttp) } },
        onEnabled = { id, enabled -> perform { repository.setSourceEnabled(id, enabled) } },
        onRemove = { id -> perform { repository.removeSource(id) } })
    selectedListing?.let { listing ->
        PluginDetailsSheet(listing, repository, iconLoader, working, onDismiss = { selectedListing = null }, onInstall = { release ->
            selectedListing = null
            onInstall(listing, release)
        }, onOpen = { installation -> selectedListing = null; onOpen(installation) })
    }
}

@Composable
private fun CompactHomeNavigation(destination: HomeDestination, kind: MediaKind, onDestination: (HomeDestination) -> Unit, onKind: () -> Unit) {
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val centerWidth = if (maxWidth >= 420.dp) 164.dp else if (maxWidth >= 360.dp) 140.dp else 112.dp
        val showKindLabel = fontScale <= 1.3f && maxWidth >= 360.dp
        NavigationBar(Modifier.padding(top = 28.dp), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
            NavigationBarItem(selected = destination == HomeDestination.BROWSE, onClick = { onDestination(HomeDestination.BROWSE) },
                icon = { Icon(BrowseIcons.Browse, null) }, label = { Text(stringResource(Res.string.home_browse)) })
            Spacer(Modifier.width(centerWidth))
            NavigationBarItem(selected = destination == HomeDestination.MORE, onClick = { onDestination(HomeDestination.MORE) },
                icon = { Icon(BrowseIcons.More, null) }, label = { Text(stringResource(Res.string.home_more)) })
        }
        FloatingActionButton(onClick = onKind, modifier = Modifier.align(Alignment.TopCenter).widthIn(max = centerWidth - 8.dp),
            shape = MaterialTheme.shapes.large,
            containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer) {
            Row(Modifier.padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(BrowseIcons.kind(kind), if (showKindLabel) null else mediaKindLabel(kind), Modifier.size(20.dp))
                if (showKindLabel) Text(mediaKindLabel(kind), Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(BrowseIcons.Down, stringResource(Res.string.home_select_kind), Modifier.size(18.dp))
            }
        }
    }
}

private enum class CatalogFilter { ALL, INSTALLED, CERTIFIED }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginBrowseScreen(state: PluginRepositoryState, kind: MediaKind, icons: PluginIconLoader, onPlugin: (CatalogListing) -> Unit, onSources: () -> Unit) {
    var query by remember(kind) { mutableStateOf("") }
    var filter by remember(kind) { mutableStateOf(CatalogFilter.ALL) }
    val installedKeys = state.installed.map { it.key }.toSet()
    val listings = state.listings.filter { kind in it.plugin.kinds && (state.showNsfw || !it.plugin.nsfw) }
    val filtered = listings.filter { listing ->
        val matchesFilter = when (filter) {
            CatalogFilter.ALL -> true
            CatalogFilter.INSTALLED -> listing.key in installedKeys
            CatalogFilter.CERTIFIED -> listing.officiallyCertified
        }
        matchesFilter && (query.isBlank() || (listing.plugin.name + " " + listing.plugin.description + " " +
            listing.plugin.authors.joinToString { it.name }).contains(query.trim(), ignoreCase = true))
    }
    LazyVerticalGrid(columns = GridCells.Adaptive(300.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(EviloLayout.PagePadding),
        horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            CatalogHero(state, kind, listings.size, onSources)
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    shape = MaterialTheme.shapes.large, label = { Text(stringResource(Res.string.home_search)) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    CatalogFilter.entries.forEach { option ->
                        FilterChip(selected = filter == option, onClick = { filter = option }, label = {
                            Text(stringResource(when (option) {
                                CatalogFilter.ALL -> Res.string.home_filter_all
                                CatalogFilter.INSTALLED -> Res.string.home_filter_installed
                                CatalogFilter.CERTIFIED -> Res.string.home_filter_official
                            }))
                        })
                    }
                }
                Text(stringResource(Res.string.home_result_count, filtered.size), style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (filtered.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                Column(Modifier.fillMaxWidth().padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Icon(BrowseIcons.kind(kind), null, Modifier.padding(16.dp).size(32.dp))
                    }
                    Text(stringResource(if (listings.isEmpty()) Res.string.home_empty_title else Res.string.home_no_matches),
                        style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(if (listings.isEmpty()) Res.string.home_empty_description else Res.string.home_no_matches_description),
                        style = MaterialTheme.typography.bodyMedium)
                    if (listings.isEmpty()) Button(onClick = onSources) {
                        Text(stringResource(Res.string.home_add_source))
                    } else TextButton(onClick = { query = ""; filter = CatalogFilter.ALL }) {
                        Text(stringResource(Res.string.home_clear_filters))
                    }
                }
            }
        }
        items(filtered, key = { it.key }) { listing ->
            PluginCard(listing, icons, listing.key in installedKeys, onClick = { onPlugin(listing) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CatalogHero(state: PluginRepositoryState, kind: MediaKind, count: Int, onSources: () -> Unit) {
    val circle = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
    val installedCount = state.installed.count { kind in it.release.kinds &&
        (state.showNsfw || !(state.catalogPluginByKey[it.key] ?: it.plugin).nsfw) }
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.primaryContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth().drawBehind {
            drawCircle(circle, size.height * 0.85f, Offset(size.width * 0.96f, size.height * 0.05f))
            drawCircle(circle, size.height * 0.6f, Offset(size.width * 0.84f, size.height * 1.1f))
        }.padding(28.dp)) {
            val wide = maxWidth >= 600.dp
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(Res.string.home_browse_title, mediaKindLabel(kind)), style = MaterialTheme.typography.headlineLarge)
                    Text(stringResource(Res.string.home_browse_description), style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        PluginBadge(stringResource(Res.string.home_source_count, count, state.subscriptions.count { it.enabled }))
                        PluginBadge(stringResource(Res.string.home_installed_stat, installedCount))
                    }
                    TextButton(onClick = onSources) {
                        Icon(BrowseIcons.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.home_manage_sources))
                    }
                }
                if (wide) Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)) {
                    Icon(BrowseIcons.kind(kind), null, Modifier.padding(24.dp).size(64.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PluginCard(listing: CatalogListing, icons: PluginIconLoader, installed: Boolean, onClick: () -> Unit) {
    val plugin = listing.plugin
    Card(onClick = onClick, shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                PluginAvatar(plugin.name, plugin.iconUrl, listing.snapshot.subscription.allowHttp, icons)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(plugin.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(plugin.authors.joinToString { it.name }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                PluginBadge(stringResource(if (listing.officiallyCertified) Res.string.plugin_official else Res.string.plugin_community))
                PluginBadge(stringResource(if (plugin.overseas) Res.string.plugin_overseas else Res.string.plugin_domestic))
                if (plugin.nsfw) PluginBadge(stringResource(Res.string.plugin_nsfw), warning = true)
                if (installed) PluginBadge(stringResource(Res.string.home_filter_installed))
            }
            Text(plugin.description, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(plugin.kinds.map { mediaKindLabel(it) }.joinToString(" / ") + " | " + plugin.languages.joinToString(),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.plugin_version, plugin.version), style = MaterialTheme.typography.labelLarge)
                Text(stringResource(Res.string.plugin_updated, plugin.updatedAt.substringBefore('T')), style = MaterialTheme.typography.labelSmall)
            }
            if (plugin.forceUpdate) Text(stringResource(Res.string.plugin_force_update), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
internal fun PluginBadge(text: String, warning: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.small, color = if (warning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer) {
        Text(text, Modifier.padding(horizontal = 8.dp, vertical = 5.dp), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
internal fun PluginAvatar(name: String, url: String, allowHttp: Boolean, icons: PluginIconLoader) {
    var bitmap by remember(url, allowHttp, icons) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, allowHttp, icons) {
        try {
            bitmap = icons.load(url, allowHttp)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            bitmap = null
        }
    }
    Box(Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) Image(image, name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else Text(name.take(1).uppercase(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}

@Composable
private fun HomeError(message: String, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(Res.string.home_error), style = MaterialTheme.typography.titleSmall)
            Text(message, style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.home_dismiss)) }
        }
    }
}
