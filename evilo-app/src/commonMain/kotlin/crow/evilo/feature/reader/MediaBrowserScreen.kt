package crow.evilo.feature.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import crow.evilo.app.EviloImages
import crow.evilo.app.SessionImages
import crow.evilo.app.LocalAppDiagnostics
import crow.evilo.app.PluginLoadTiming
import crow.evilo.feature.browse.PluginLoadDiagnosticsDialog
import crow.evilo.architecture.closeResources
import crow.evilo.model.MediaItem
import crow.evilo.model.MediaKind
import crow.evilo.ui.EviloLayout
import crow.evilo.ui.EviloAsyncImage
import crow.evilo.ui.EviloElevation
import crow.evilo.ui.EviloEmptyState
import crow.evilo.ui.EviloIcons
import crow.evilo.ui.EviloPageTransition
import crow.evilo.ui.EviloPredictiveBackHandler
import crow.evilo.ui.rememberEviloBackTransition
import crow.evilo.ui.eviloTransitionInput
import crow.evilo.ui.EviloProgressIndicator
import crow.evilo.ui.EviloSearchField
import crow.evilo.ui.EviloSpacing
import crow.evilo.ui.LocalEviloPageActive
import crow.evilo.ui.LocalEviloToastState
import crow.evilo.app.resources.*
import kotlinx.coroutines.awaitCancellation
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

@Suppress("DEPRECATION")
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MediaBrowserRoute(session: MediaSourceSession, kind: MediaKind, onClose: () -> Unit, modifier: Modifier = Modifier,
    onReopen: () -> Unit = onClose, onPermissions: (() -> Unit)? = null, loadTimings: List<PluginLoadTiming> = emptyList()) {
    val parameters = remember(session, kind) { parametersOf(session, kind) }
    val controller = koinInject<MediaBrowserController>(parameters = { parameters })
    val imageLoader = koinInject<EviloImages>()
    val images = remember(session, imageLoader) { imageLoader.sessionImages(session) }
    val state by controller.state.collectAsState()
    val connected by images.connected.collectAsState()
    val savedPages = rememberSaveableStateHolder()
    val backTransition = rememberEviloBackTransition(state)
    val focus = remember { FocusRequester() }
    val pageActive = LocalEviloPageActive.current
    val toasts = LocalEviloToastState.current
    var errorToastId by remember { mutableStateOf<Long?>(null) }
    var showLoadDiagnostics by remember(session) { mutableStateOf(false) }
    if (showLoadDiagnostics) PluginLoadDiagnosticsDialog(loadTimings) { showLoadDiagnostics = false }
    val errorTitle = mediaFailureTitle(state.page)
    val retryLabel = stringResource(Res.string.source_retry)
    val back = { if (!connected || state.page == MediaBrowserPage.CATALOG) onClose() else controller.dispatch(MediaBrowserIntent.Back) }
    // Catalog/disconnected Back belongs to the home stack, which can preview its real destination.
    EviloPredictiveBackHandler(pageActive && connected && state.page != MediaBrowserPage.CATALOG && !showLoadDiagnostics,
        backTransition, state, state.backDestination(), navigationKey = mediaPageKey(state), onBack = back)
    LaunchedEffect(controller, state.page, pageActive) { if (pageActive) focus.requestFocus() }
    LaunchedEffect(state.error, state.page, pageActive, connected) {
        errorToastId?.let { toasts.dismiss(it, notify = false) }
        errorToastId = state.error?.takeIf { pageActive && connected }?.let { error ->
            toasts.show(error, title = errorTitle, error = true, actionLabel = retryLabel,
                onAction = { controller.dispatch(MediaBrowserIntent.Retry) })
        }
    }
    DisposableEffect(toasts) { onDispose { errorToastId?.let { toasts.dismiss(it, notify = false) } } }
    DisposableEffect(images) { onDispose { images.close() } }
    LaunchedEffect(controller, images) {
        try {
            controller.dispatch(MediaBrowserIntent.Search(""))
            awaitCancellation()
        } finally {
            closeResources({ images.close() }, { controller.close() })
        }
    }
    BoxWithConstraints(modifier.fillMaxSize().eviloTransitionInput(!backTransition.previewing).onPreviewKeyEvent {
        if (pageActive && it.key == Key.Escape && it.type == KeyEventType.KeyUp) { back(); true } else false
    }.focusRequester(focus).focusable()) {
        val pagePadding = if (maxWidth >= EviloLayout.ExpandedBreakpoint) EviloLayout.WidePagePadding else EviloLayout.PagePadding
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                        Text(session.source.descriptor.name, style = MaterialTheme.typography.titleLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(Res.string.source_plugin_version, session.source.descriptor.version,
                            session.source.descriptor.protocol.major), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = back) {
                        Icon(EviloIcons.Back, stringResource(Res.string.source_back))
                    }
                },
                actions = {
                    if (LocalAppDiagnostics.current && loadTimings.isNotEmpty()) IconButton(onClick = { showLoadDiagnostics = true }) {
                        Icon(EviloIcons.Info, stringResource(Res.string.plugin_debug_timings))
                    }
                    IconButton(onClick = onClose) { Icon(EviloIcons.Close, stringResource(Res.string.source_disconnect)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                windowInsets = WindowInsets(0, 0, 0, 0),
            )
            if (!connected) {
                EviloEmptyState(EviloIcons.Link, stringResource(Res.string.plugin_session_closed), stringResource(Res.string.image_error_session_hint),
                    Modifier.padding(pagePadding).fillMaxWidth()) {
                    Button(onClick = onReopen) { Text(stringResource(Res.string.plugin_reopen)) }
                }
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    EviloPageTransition(state, contentKey = ::mediaPageKey, depth = { it.page.ordinal }, label = "media-page",
                        backState = backTransition) { displayed ->
                        savedPages.SaveableStateProvider(mediaPageKey(displayed)) {
                            val retry = { controller.dispatch(MediaBrowserIntent.Retry) }
                            when (displayed.page) {
                                MediaBrowserPage.CATALOG -> MediaCatalog(displayed, images,
                                    onSearch = { controller.dispatch(MediaBrowserIntent.Search(it)) },
                                    onMore = { controller.dispatch(MediaBrowserIntent.LoadMore) },
                                    onItem = { controller.dispatch(MediaBrowserIntent.OpenItem(it)) }, pagePadding = pagePadding,
                                    onPermissions = onPermissions, onRetry = retry)
                                MediaBrowserPage.DETAILS -> MediaDetails(displayed, images,
                                    { controller.dispatch(MediaBrowserIntent.OpenChapter(it)) }, pagePadding, onPermissions, retry)
                                MediaBrowserPage.READER -> MediaReader(displayed, session, images,
                                    { controller.dispatch(MediaBrowserIntent.OpenChapter(it)) }, pagePadding, onPermissions, retry)
                            }
                        }
                    }
                    EviloProgressIndicator(state.busy, Modifier.align(Alignment.TopCenter).fillMaxWidth())
                }
            }
        }
    }
}

private fun mediaPageKey(state: MediaBrowserState): String = when (state.page) {
    MediaBrowserPage.CATALOG -> "catalog"
    MediaBrowserPage.DETAILS -> "details:${state.selectedItem?.id}"
    MediaBrowserPage.READER -> "reader:${state.chapters.getOrNull(state.chapterIndex ?: -1)?.id}"
}

@Composable
private fun mediaFailureTitle(page: MediaBrowserPage): String = stringResource(when (page) {
    MediaBrowserPage.CATALOG -> Res.string.source_catalog_failed
    MediaBrowserPage.DETAILS -> Res.string.source_details_failed
    MediaBrowserPage.READER -> Res.string.source_content_failed
})

@Composable
private fun MediaCatalog(state: MediaBrowserState, images: SessionImages, onSearch: (String) -> Unit,
    onMore: () -> Unit, onItem: (MediaItem) -> Unit, pagePadding: Dp, onPermissions: (() -> Unit)?, onRetry: () -> Unit) {
    var query by rememberSaveable { mutableStateOf(state.query) }
    val gridState = rememberLazyGridState()
    val colors = MaterialTheme.colorScheme
    LazyVerticalGrid(columns = GridCells.Adaptive(if (state.kind in setOf(MediaKind.FILM, MediaKind.ANIME))
        EviloLayout.VideoCardMinWidth else EviloLayout.MediaCardMinWidth),
        state = gridState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(pagePadding),
        horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(EviloSpacing.Medium)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                    EviloSearchField(query, { query = it }, stringResource(Res.string.source_search_hint), Modifier.weight(1f),
                        onSearch = { onSearch(query) })
                    FilledIconButton(onClick = { onSearch(query) }) {
                        Icon(EviloIcons.Search, stringResource(Res.string.source_search))
                    }
                }
                Text(stringResource(Res.string.source_result_count, state.items.size), style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant)
            }
        }
        if (state.items.isEmpty() && !state.busy) item(span = { GridItemSpan(maxLineSpan) }) {
            if (state.error != null) MediaLoadFailure(state, onRetry)
            else EviloEmptyState(EviloIcons.kind(state.kind), stringResource(Res.string.source_no_results),
                stringResource(Res.string.source_no_results_description), Modifier.fillMaxWidth()) {
                Button(onClick = { onSearch(query) }) { Text(stringResource(Res.string.source_retry)) }
            }
        }
        items(state.items, key = { "${it.id.pluginId}:${it.id.kind}:${it.id.value}" }) { item ->
            Card(onClick = { onItem(item) }, shape = MaterialTheme.shapes.medium,
                border = BorderStroke(EviloLayout.BorderWidth, colors.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = EviloElevation.Flat),
                colors = CardDefaults.cardColors(containerColor = colors.surface)) {
                MediaImage(item.coverUrl, emptyMap(), images, item.title, Modifier.fillMaxWidth().aspectRatio(2f / 3f), onPermissions = onPermissions)
                Column(Modifier.padding(EviloSpacing.Medium), verticalArrangement = Arrangement.spacedBy(EviloSpacing.ExtraSmall)) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.authors.isNotEmpty()) Text(item.authors.joinToString(", "), style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (state.items.isNotEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (state.hasNextPage) OutlinedButton(onClick = onMore, enabled = !state.busy) {
                    Text(stringResource(Res.string.source_load_more))
                } else Text(stringResource(Res.string.source_results_end), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MediaDetails(state: MediaBrowserState, images: SessionImages, onChapter: (Int) -> Unit, pagePadding: Dp, onPermissions: (() -> Unit)?, onRetry: () -> Unit) {
    val item = state.selectedItem ?: return
    val colors = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = EviloLayout.ReaderMaxWidth).fillMaxSize(), contentPadding = PaddingValues(pagePadding),
            verticalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large)) {
                    MediaImage(item.coverUrl, emptyMap(), images, item.title,
                        Modifier.width(EviloLayout.DetailCoverWidth).aspectRatio(2f / 3f).clip(MaterialTheme.shapes.medium), onPermissions = onPermissions)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                        Text(item.title, style = MaterialTheme.typography.titleLarge)
                        if (item.authors.isNotEmpty()) Text(item.authors.joinToString(", "), style = MaterialTheme.typography.bodyMedium,
                            color = colors.onSurfaceVariant)
                        Text(item.id.value, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    }
                }
            }
            item.description?.takeIf(String::isNotBlank)?.let { description -> item { Text(description, style = MaterialTheme.typography.bodyMedium) } }
            item { Text(stringResource(if (state.kind in setOf(MediaKind.ANIME, MediaKind.FILM)) Res.string.source_episodes
                else Res.string.source_chapters, state.chapters.size), style = MaterialTheme.typography.titleMedium) }
            if (state.chapters.isEmpty() && !state.busy) item {
                if (state.error != null) MediaLoadFailure(state, onRetry)
                else Text(stringResource(Res.string.source_no_chapters), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            itemsIndexed(state.chapters, key = { _, chapter -> chapter.id }) { index, chapter ->
                Surface(onClick = { onChapter(index) }, shape = MaterialTheme.shapes.medium, color = colors.surface,
                    border = BorderStroke(EviloLayout.BorderWidth, colors.outlineVariant)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = EviloLayout.MinimumTouchTarget).padding(EviloSpacing.Large),
                        horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = MaterialTheme.shapes.small, color = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
                            Text((index + 1).toString().padStart(2, '0'),
                                Modifier.padding(horizontal = EviloSpacing.Small, vertical = EviloSpacing.ExtraSmall), style = MaterialTheme.typography.labelMedium)
                        }
                        Text(chapter.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Icon(EviloIcons.ChevronRight, null, tint = colors.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaReader(state: MediaBrowserState, session: MediaSourceSession, images: SessionImages, onChapter: (Int) -> Unit, pagePadding: Dp, onPermissions: (() -> Unit)?, onRetry: () -> Unit) {
    val index = state.chapterIndex ?: return
    val chapter = state.chapters.getOrNull(index) ?: return
    val content = state.content
    val listState = rememberLazyListState()
    val uriHandler = LocalUriHandler.current
    val toasts = LocalEviloToastState.current
    val errorTitle = stringResource(Res.string.home_error)
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Row(Modifier.widthIn(max = EviloLayout.ReaderMaxWidth).fillMaxWidth().padding(horizontal = pagePadding, vertical = EviloSpacing.Small),
                horizontalArrangement = Arrangement.spacedBy(EviloSpacing.Small), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChapter(index - 1) }, enabled = index > 0 && !state.busy) {
                    Icon(EviloIcons.Back, stringResource(Res.string.source_previous_chapter))
                }
                Text(chapter.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onChapter(index + 1) }, enabled = index + 1 < state.chapters.size && !state.busy) {
                    Icon(EviloIcons.ChevronRight, stringResource(Res.string.source_next_chapter))
                }
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(state = listState, modifier = Modifier.widthIn(max = EviloLayout.ReaderMaxWidth).fillMaxSize(),
                contentPadding = PaddingValues(bottom = EviloSpacing.ExtraLarge), verticalArrangement = Arrangement.spacedBy(EviloSpacing.Small)) {
                if (content == null && !state.busy && state.error != null) item {
                    MediaLoadFailure(state, onRetry, Modifier.padding(horizontal = pagePadding))
                }
                content?.text?.let { text -> item { Text(text, Modifier.padding(pagePadding), style = MaterialTheme.typography.bodyLarge) } }
                if (!content?.streams.isNullOrEmpty()) {
                    item { Text(stringResource(Res.string.source_external_player_description), Modifier.padding(pagePadding), style = MaterialTheme.typography.bodyMedium) }
                    itemsIndexed(content?.streams.orEmpty()) { _, stream ->
                        OutlinedButton(onClick = {
                            try {
                                uriHandler.openUri(session.externalStreamUrl(stream))
                            } catch (failure: Exception) {
                                toasts.show((failure.message ?: failure.toString()).take(2000), title = errorTitle, error = true)
                            }
                        }, modifier = Modifier.padding(horizontal = pagePadding)) { Text(stream.title) }
                    }
                }
                itemsIndexed(content?.images.orEmpty(), key = { _, page -> page.index }) { _, page ->
                    Column {
                        Text(stringResource(Res.string.source_image_position, page.index + 1), Modifier.padding(horizontal = pagePadding, vertical = EviloSpacing.ExtraSmall),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MediaImage(page.imageUrl, page.headers, images, stringResource(Res.string.image_page_description, chapter.title, page.index + 1),
                            Modifier.fillMaxWidth(), naturalSize = true, pageNumber = page.index + 1, onPermissions = onPermissions)
                    }
                }
                if (content != null && content.text.isNullOrEmpty() && content.images.isEmpty() && content.streams.isEmpty()) {
                    item { Text(stringResource(Res.string.source_empty_content), Modifier.padding(pagePadding)) }
                }
            }
        }
    }
}

@Composable
private fun MediaLoadFailure(state: MediaBrowserState, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EviloEmptyState(EviloIcons.kind(state.kind), mediaFailureTitle(state.page),
        stringResource(Res.string.source_load_failed_hint), modifier.fillMaxWidth()) {
        Button(onClick = onRetry) { Text(stringResource(Res.string.source_retry)) }
    }
}

@Composable
internal fun MediaImage(url: String?, headers: Map<String, String>, loader: SessionImages, description: String,
    modifier: Modifier = Modifier, naturalSize: Boolean = false, pageNumber: Int? = null, onPermissions: (() -> Unit)? = null) {
    var attempt by remember(url, headers, loader) { mutableStateOf(0) }
    val request = remember(url, headers, loader, attempt, naturalSize) { loader.request(url, headers, attempt, naturalSize) }
    EviloAsyncImage(request, loader.loader, description, modifier, naturalSize, onRetry = { attempt++ },
        address = url, pageNumber = pageNumber, onPermissions = onPermissions)
}
